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
package org.xwiki.rendering.internal.wiki;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.bridge.SkinAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.AttachmentReferenceResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.SpaceReferenceResolver;
import org.xwiki.rendering.internal.configuration.XWikiRenderingConfiguration;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.wiki.WikiModel;

import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.SACParserCSS21;

/**
 * Implementation using the Document Access Bridge ({@link DocumentAccessBridge}).
 *
 * @version $Id$
 * @since 2.0M1
 */
@Component
@Singleton
public class XWikiWikiModel implements WikiModel
{
    /**
     * The suffix used to mark an amount of pixels.
     */
    private static final String PIXELS = "px";

    /**
     * The name of the {@code width} image parameter.
     */
    private static final String WIDTH = "width";

    /**
     * The name of the {@code height} image parameter.
     */
    private static final String HEIGHT = "height";

    /**
     * The component used to access configuration parameters.
     */
    @Inject
    private XWikiRenderingConfiguration xwikiRenderingConfiguration;

    /**
     * The component used to access the underlying XWiki model.
     */
    @Inject
    private DocumentAccessBridge documentAccessBridge;

    /**
     * Used to find the URL for an icon.
     */
    @Inject
    private SkinAccessBridge skinAccessBridge;

    /**
     * The component used to serialize entity references to strings.
     */
    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    /**
     * Convert an Attachment Reference from a String (specified using the document reference format) into an Attachment
     * object.
     */
    @Inject
    @Named("current")
    private AttachmentReferenceResolver<String> currentAttachmentReferenceResolver;

    /**
     * Convert an Attachment Reference from a String (specified using the space reference format) into an Attachment
     * object.
     */
    @Inject
    @Named("currentspace")
    private AttachmentReferenceResolver<String> currentSpaceAttachmentReferenceResolver;

    /**
     * Used to resolve a Resource Reference into a proper Document Reference.
     */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentDocumentReferenceResolver;

    /**
     * Used to resolve a DocumentReference from a SpaceReference, i.e. the space's homepage.
     */
    @Inject
    private DocumentReferenceResolver<EntityReference> defaultReferenceDocumentReferenceResolver;

    /**
     * Used to resolve a Resource Reference into a Space Reference.
     */
    @Inject
    @Named("current")
    private SpaceReferenceResolver<String> currentSpaceReferenceResolver;

    @Inject
    private EntityReferenceProvider defaultEntityReferenceProvider;

    /**
     * Provides logging for this class.
     */
    @Inject
    private Logger logger;

    /**
     * The object used to parse the CSS from the image style parameter.
     * <p>
     * NOTE: We explicitly pass the CSS SAC parser because otherwise (e.g. using the default constructor)
     * {@link CSSOMParser} sets the {@code org.w3c.css.sac.parser} system property to its own implementation, i.e.
     * {@link com.steadystate.css.parser.SACParserCSS2}, affecting other components that require a CSS SAC parser (e.g.
     * PDF export).
     *
     * @see <a href="http://jira.xwiki.org/jira/browse/XWIKI-5625">XWIKI-5625: PDF styling doesn't work anymore</a>
     */
    private final CSSOMParser cssParser = new CSSOMParser(new SACParserCSS21());

    /**
     * {@inheritDoc}
     * @since 2.5RC1
     */
    @Override
    public String getLinkURL(ResourceReference linkReference)
    {
        // Note that we don't ask for a full URL because links should be relative as much as possible
        return this.documentAccessBridge.getAttachmentURL(resolveAttachmentReference(linkReference),
            linkReference.getParameter(AttachmentResourceReference.QUERY_STRING), false);
    }

    /**
     * {@inheritDoc}
     * @since 2.5RC1
     */
    @Override
    public String getImageURL(ResourceReference imageReference, Map<String, String> parameters)
    {
        // Handle icon references
        if (imageReference.getType().equals(ResourceType.ICON)) {
            return this.skinAccessBridge.getIconURL(imageReference.getReference());
        }

        // Handle attachment references
        if (this.xwikiRenderingConfiguration.isImageDimensionsIncludedInImageURL()) {
            String extraQueryString = StringUtils.removeStart(getImageURLQueryString(parameters), "&");
            if (!extraQueryString.isEmpty()) {
                // Handle scaled image attachments.
                String queryString = imageReference.getParameter(AttachmentResourceReference.QUERY_STRING);
                if (StringUtils.isEmpty(queryString)) {
                    queryString = extraQueryString;
                } else {
                    queryString += '&' + extraQueryString;
                }
                ResourceReference scaledImageReference = imageReference.clone();
                scaledImageReference.setParameter(AttachmentResourceReference.QUERY_STRING, queryString);
                return getLinkURL(scaledImageReference);
            }
        }

        return getLinkURL(imageReference);
    }

    @Override
    public boolean isDocumentAvailable(ResourceReference resourceReference)
    {
        DocumentReference documentReference = resolveDocumentReference(resourceReference);
        return this.documentAccessBridge.exists(documentReference);
    }

    @Override
    public String getDocumentViewURL(ResourceReference resourceReference)
    {
        DocumentReference documentReference = resolveDocumentReference(resourceReference);
        return this.documentAccessBridge.getDocumentURL(documentReference, "view",
            resourceReference.getParameter(DocumentResourceReference.QUERY_STRING),
            resourceReference.getParameter(DocumentResourceReference.ANCHOR));
    }

    @Override
    public String getDocumentEditURL(ResourceReference resourceReference)
    {
        // Add the parent=<current document name> parameter to the query string of the edit URL so that
        // the new document is created with the current page as its parent.
        String modifiedQueryString = resourceReference.getParameter(DocumentResourceReference.QUERY_STRING);
        if (StringUtils.isBlank(modifiedQueryString)) {
            DocumentReference reference = this.documentAccessBridge.getCurrentDocumentReference();
            if (reference != null) {
                try {
                    // Note 1: we encode using UTF8 since it's the W3C recommendation.
                    // See http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars

                    // Note 2: We need to be careful to use a compact serializer so that the wiki part is not
                    // part of the generated String so that when the user clicks on the link, the new page is created
                    // with a relative parent (and thus the new page can be moved from one wiki to another easily
                    // without having to change the parent reference).

                    // TODO: Once the xwiki-url module is usable, refactor this code to use it and remove the need to
                    // perform explicit encoding here.

                    modifiedQueryString = "parent=" + URLEncoder.encode(
                        this.compactEntityReferenceSerializer.serialize(reference), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // Not supporting UTF-8 as a valid encoding for some reasons. We consider XWiki cannot work
                    // without that encoding.
                    throw new RuntimeException("Failed to URL encode ["
                        + this.compactEntityReferenceSerializer.serialize(reference) + "] using UTF-8.", e);
                }
            }
        }

        DocumentReference documentReference = resolveDocumentReference(resourceReference);
        return this.documentAccessBridge.getDocumentURL(documentReference, "create", modifiedQueryString,
            resourceReference.getParameter(DocumentResourceReference.ANCHOR));
    }

    /**
     * Resolve the reference passed as a String into a DocumentReference object.
     *
     * @param resourceReference the resource to transform into a {@link DocumentReference} object
     * @return the reference resolved using the base resource reference if any
     */
    private DocumentReference resolveDocumentReference(ResourceReference resourceReference)
    {
        DocumentReference baseReference = null;
        if (!resourceReference.getBaseReferences().isEmpty()) {
            // If the passed reference has a base reference, resolve it first with a current resolver (it should
            // normally be absolute but who knows what the API caller has specified...)
            baseReference = resolveBaseReference(resourceReference.getBaseReferences(), resourceReference.getType());
        }

        DocumentReference documentReference =
            resolveReferenceWithBase(resourceReference.getReference(), resourceReference.getType(), baseReference);

        return documentReference;
    }

    /**
     * Resolve the list of base references taking the first element in the list, resolving it and then resolving the
     * other elements based on the previous resolving.
     *
     * @param baseReferences the list of base references to resolve
     * @param resourceType the type of resources you are resolving
     * @return the resolved base reference against which to resolve the target resource reference
     */
    private DocumentReference resolveBaseReference(List<String> baseReferences, ResourceType resourceType)
    {
        DocumentReference resolvedBaseReference = null;
        for (String baseReference : baseReferences) {
            resolvedBaseReference = resolveReferenceWithBase(baseReference, resourceType, resolvedBaseReference);
        }
        return resolvedBaseReference;
    }

    /**
     * @param resourceReference string reference
     * @param resourceType type of reference to resolve
     * @param resolvedBaseReference a previously resolved base reference to use when resolving the current one, or
     *            {@code null} if none is available
     * @return the resolved DocumentReference
     */
    private DocumentReference resolveReferenceWithBase(String resourceReference, ResourceType resourceType,
        DocumentReference resolvedBaseReference)
    {
        // Use any previously resolved base reference when resolving the current one.
        Object[] resolveParameters = null;
        if (resolvedBaseReference != null) {
            resolveParameters = new Object[] {resolvedBaseReference};
        } else {
            resolveParameters = new Object[0];
        }

        DocumentReference result = null;
        // Resolve the current reference.
        if (ResourceType.DOCUMENT.equals(resourceType)) {
            result = this.currentDocumentReferenceResolver.resolve(resourceReference, resolveParameters);
        } else if (ResourceType.SPACE.equals(resourceType)) {
            // Extract the space's homepage.
            SpaceReference spaceReference =
                this.currentSpaceReferenceResolver.resolve(resourceReference, resolveParameters);
            result = this.defaultReferenceDocumentReferenceResolver.resolve(spaceReference);
        }

        return result;
    }

    /**
     * Resolve the reference passed as a String into an AttachmentReference object.
     *
     * @param attachmentResourceReference the resource to transform into a {@link AttachmentReference} object
     * @return the resolved reference resolved using the base resource reference if any
     */
    private AttachmentReference resolveAttachmentReference(ResourceReference attachmentResourceReference)
    {
        String stringReference = attachmentResourceReference.getReference();

        AttachmentReference attachmentReference;
        if (!attachmentResourceReference.getBaseReferences().isEmpty()) {
            // If the passed reference has a base reference, resolve it first with a current resolver (it should
            // normally be absolute but who knows what the API caller has specified...)
            DocumentReference baseReference =
                resolveBaseReference(attachmentResourceReference.getBaseReferences(), ResourceType.DOCUMENT);
            attachmentReference = this.currentAttachmentReferenceResolver.resolve(stringReference, baseReference);
        } else {
            attachmentReference = this.currentAttachmentReferenceResolver.resolve(stringReference);
        }

        // See if the resolved (terminal or WebHome) document exists and, if so, use it.
        DocumentReference documentReference = attachmentReference.getDocumentReference();
        if (!documentAccessBridge.exists(documentReference)) {
            // Also consider explicit "WebHome" references (i.e. the ones ending in "WebHome").
            String defaultDocumentName =
                defaultEntityReferenceProvider.getDefaultReference(EntityType.DOCUMENT).getName();
            if (!documentReference.getName().equals(defaultDocumentName)) {
                // Otherwise, handle it as a space reference for both cases when it exists or when it doesn't exist.
                attachmentReference = this.currentSpaceAttachmentReferenceResolver.resolve(stringReference);
            }
        }

        return attachmentReference;
    }

    /**
     * Extracts the specified image dimension from the image parameters.
     *
     * @param dimension either {@code width} or {@code height}
     * @param imageParameters the image parameters; may include the {@code width}, {@code height} and {@code style}
     *            parameters
     * @return the value of the passed dimension if it is specified in the image parameters, {@code null} otherwise
     */
    private String getImageDimension(String dimension, Map<String, String> imageParameters)
    {
        // Check first if the style parameter contains information about the given dimension. In-line style has priority
        // over the dimension parameters.
        String value = null;
        String style = imageParameters.get("style");
        if (StringUtils.isNotBlank(style)) {
            try {
                CSSStyleDeclaration sd = this.cssParser.parseStyleDeclaration(new InputSource(new StringReader(style)));
                value = sd.getPropertyValue(dimension);
            } catch (Exception e) {
                // Ignore the style parameter but log a warning to let the user know.
                this.logger.warn("Failed to parse CSS style [{}]", style);
            }
        }
        if (StringUtils.isBlank(value)) {
            // Fall back on the value of the dimension parameter.
            value = imageParameters.get(dimension);
        }
        return value;
    }

    /**
     * Creates the query string that can be added to an image URL to resize the image on the server side.
     *
     * @param imageParameters image parameters, including width and height then they are specified
     * @return the query string to be added to an image URL in order to resize the image on the server side
     */
    private String getImageURLQueryString(Map<String, String> imageParameters)
    {
        String width = StringUtils.removeEnd(getImageDimension(WIDTH, imageParameters), PIXELS);
        String height = StringUtils.removeEnd(getImageDimension(HEIGHT, imageParameters), PIXELS);
        boolean useHeight = StringUtils.isNotEmpty(height) && StringUtils.isNumeric(height);
        StringBuilder queryString = new StringBuilder();
        if (StringUtils.isEmpty(width) || !StringUtils.isNumeric(width)) {
            // Width is unspecified or is not measured in pixels.
            if (useHeight) {
                // Height is specified in pixels.
                queryString.append('&').append(HEIGHT).append('=').append(height);
            } else {
                // If image width and height are unspecified or if they are not expressed in pixels then limit the image
                // size to best fit the rectangle specified in the configuration (keeping aspect ratio).
                int widthLimit = this.xwikiRenderingConfiguration.getImageWidthLimit();
                if (widthLimit > 0) {
                    queryString.append('&').append(WIDTH).append('=').append(widthLimit);
                }
                int heightLimit = this.xwikiRenderingConfiguration.getImageHeightLimit();
                if (heightLimit > 0) {
                    queryString.append('&').append(HEIGHT).append('=').append(heightLimit);
                }
                if (widthLimit > 0 && heightLimit > 0) {
                    queryString.append("&keepAspectRatio=").append(true);
                }
            }
        } else {
            // Width is specified in pixels.
            queryString.append('&').append(WIDTH).append('=').append(width);
            if (useHeight) {
                // Height is specified in pixels.
                queryString.append('&').append(HEIGHT).append('=').append(height);
            }
        }
        return queryString.toString();
    }
}
