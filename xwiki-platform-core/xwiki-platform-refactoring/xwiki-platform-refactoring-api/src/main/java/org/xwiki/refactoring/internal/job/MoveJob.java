/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.refactoring.internal.job;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.refactoring.internal.LinkRefactoring;
import org.xwiki.refactoring.job.EntityJobStatus;
import org.xwiki.refactoring.job.MoveRequest;
import org.xwiki.refactoring.job.OverwriteQuestion;
import org.xwiki.refactoring.job.RefactoringJobs;
import org.xwiki.security.authorization.Right;

/**
 * A job that can move entities to a new parent within the hierarchy.
 * 
 * @version $Id$
 * @since 7.2M1
 */
@Component
@Named(RefactoringJobs.MOVE)
public class MoveJob extends AbstractEntityJob<MoveRequest, EntityJobStatus<MoveRequest>>
{
    /**
     * Specifies whether all entities with the same name are to be overwritten on not. When {@code true} all entities
     * with the same name are overwritten. When {@code false} all entities with the same name are skipped. If
     * {@code null} then a question is asked for each entity.
     */
    private Boolean overwriteAll;

    /**
     * The component used to refactor document links after a document is rename or moved.
     */
    @Inject
    private LinkRefactoring linkRefactoring;

    @Override
    public String getType()
    {
        return RefactoringJobs.MOVE;
    }

    @Override
    protected EntityJobStatus<MoveRequest> createNewStatus(MoveRequest request)
    {
        return new EntityJobStatus<MoveRequest>(request, this.observationManager, this.loggerManager, null);
    }

    @Override
    protected void runInternal() throws Exception
    {
        if (this.request.getDestination() != null) {
            super.runInternal();
        }
    }

    @Override
    protected void process(EntityReference source)
    {
        // Perform generic checks that don't depend on the source/destination type.

        EntityReference destination = this.request.getDestination();
        if (isDescendantOrSelf(destination, source)) {
            this.logger.error("Cannot make [{}] a descendant of itself.", source);
            return;
        }

        // Dispatch the move operation based on the source entity type.

        switch (source.getType()) {
            case DOCUMENT:
                process(new DocumentReference(source), destination);
                break;
            case SPACE:
                process(new SpaceReference(source), destination);
                break;
            default:
                this.logger.error("Unsupported source entity type [{}].", source.getType());
        }
    }

    private boolean isDescendantOrSelf(EntityReference alice, EntityReference bob)
    {
        EntityReference parent = alice;
        while (parent != null && !parent.equals(bob)) {
            parent = parent.getParent();
        }
        return parent != null;
    }

    protected void process(DocumentReference source, EntityReference destination)
    {
        if (this.request.isDeep() && isSpaceHomeReference(source)) {
            process(source.getLastSpaceReference(), destination);
        } else if (destination.getType() == EntityType.SPACE) {
            maybeMove(source, new DocumentReference(source.getName(), new SpaceReference(destination)));
        } else if (destination.getType() == EntityType.DOCUMENT
            && isSpaceHomeReference(new DocumentReference(destination))) {
            maybeMove(source, new DocumentReference(source.getName(), new SpaceReference(destination.getParent())));
        } else {
            this.logger.error("Unsupported destination entity type [{}] for a document.", destination.getType());
        }
    }

    protected void process(SpaceReference source, EntityReference destination)
    {
        if (destination.getType() == EntityType.SPACE || destination.getType() == EntityType.WIKI) {
            process(source, new SpaceReference(source.getName(), destination));
        } else if (destination.getType() == EntityType.DOCUMENT
            && isSpaceHomeReference(new DocumentReference(destination))) {
            process(source, new SpaceReference(source.getName(), destination.getParent()));
        } else {
            this.logger.error("Unsupported destination entity type [{}] for a space.", destination.getType());
        }
    }

    protected void process(final SpaceReference source, final SpaceReference destination)
    {
        visitDocuments(source, new Visitor<DocumentReference>()
        {
            @Override
            public void visit(DocumentReference oldChildReference)
            {
                DocumentReference newChildReference = oldChildReference.replaceParent(source, destination);
                maybeMove(oldChildReference, newChildReference);
            }
        });
    }

    protected void maybeMove(DocumentReference oldReference, DocumentReference newReference)
    {
        // Perform checks that are specific to the document source/destination type.

        if (!this.modelBridge.exists(oldReference)) {
            this.logger.warn("Skipping [{}] because it doesn't exist.", oldReference);
        } else if (this.request.isDeleteSource() && !hasAccess(Right.DELETE, oldReference)) {
            // The move operation is implemented as Copy + Delete.
            this.logger.error("You are not allowed to delete [{}].", oldReference);
        } else if (!hasAccess(Right.VIEW, newReference) || !hasAccess(Right.EDIT, newReference)
            || (this.modelBridge.exists(newReference) && !hasAccess(Right.DELETE, newReference))) {
            this.logger
                .error("You don't have sufficient permissions over the destination document [{}].", newReference);
        } else {
            move(oldReference, newReference);
        }
    }

    private void move(DocumentReference oldReference, DocumentReference newReference)
    {
        this.progressManager.pushLevelProgress(5, this);

        try {
            // Step 1: Delete the destination document if needed.
            this.progressManager.startStep(this);
            if (this.modelBridge.exists(newReference)) {
                if (this.request.isInteractive() && !confirmOverwrite(oldReference, newReference)) {
                    this.logger.warn(
                        "Skipping [{}] because [{}] already exists and the user doesn't want to overwrite it.",
                        oldReference, newReference);
                    return;
                } else if (!this.modelBridge.delete(newReference, this.request.getUserReference())) {
                    return;
                }
            }

            // Step 2: Copy the source document to the destination.
            this.progressManager.startStep(this);
            if (!this.modelBridge.copy(oldReference, newReference, this.request.getUserReference())) {
                return;
            }

            // Step 3: Update the links.
            this.progressManager.startStep(this);
            if (this.request.isUpdateLinks()) {
                updateLinks(oldReference, newReference);
            }

            // Step 4: Delete the source document.
            this.progressManager.startStep(this);
            if (this.request.isDeleteSource()) {
                this.modelBridge.delete(oldReference, this.request.getUserReference());
            }

            // Step 5: Create an automatic redirect.
            this.progressManager.startStep(this);
            if (this.request.isDeleteSource() && this.request.isAutoRedirect()) {
                this.modelBridge.createRedirect(oldReference, newReference);
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }
    }

    private boolean confirmOverwrite(EntityReference source, EntityReference destination)
    {
        if (this.overwriteAll == null) {
            OverwriteQuestion question = new OverwriteQuestion(source, destination);
            try {
                this.status.ask(question);
                if (!question.isAskAgain()) {
                    // Use the same answer for the following overwrite questions.
                    this.overwriteAll = question.isOverwrite();
                }
                return question.isOverwrite();
            } catch (InterruptedException e) {
                this.logger.warn("Overwrite question has been interrupted.");
                return false;
            }
        } else {
            return this.overwriteAll;
        }
    }

    private void updateLinks(DocumentReference oldReference, DocumentReference newReference)
    {
        this.progressManager.pushLevelProgress(2, this);

        try {
            // Step 1: Update the links that target the old reference to point to the new reference.
            this.progressManager.startStep(this);
            if (this.request.isDeleteSource()) {
                updateBackLinks(oldReference, newReference);
            }

            // Step 2: Update the relative links from the document content.
            this.progressManager.startStep(this);
            this.linkRefactoring.updateRelativeLinks(oldReference, newReference);
        } finally {
            this.progressManager.popLevelProgress(this);
        }
    }

    private void updateBackLinks(DocumentReference oldReference, DocumentReference newReference)
    {
        List<DocumentReference> backlinkDocumentReferences = this.modelBridge.getBackLinkedReferences(oldReference);
        this.progressManager.pushLevelProgress(backlinkDocumentReferences.size(), this);

        try {
            for (DocumentReference backlinkDocumentReference : backlinkDocumentReferences) {
                this.progressManager.startStep(this);
                if (hasAccess(Right.EDIT, backlinkDocumentReference)) {
                    this.linkRefactoring.renameLinks(backlinkDocumentReference, oldReference, newReference);
                }
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }
    }

    @Override
    protected String getTargetWiki()
    {
        List<EntityReference> entityReferences = new LinkedList<>(this.request.getEntityReferences());
        entityReferences.add(this.request.getDestination());
        return getTargetWiki(entityReferences);
    }
}
