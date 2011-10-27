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
package org.xwiki.model.reference;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.model.EntityType;

/**
 * Represents a reference to an Entity (Document, Attachment, Space, Wiki, etc).
 *  
 * @version $Id$
 * @since 2.2M1
 */
public class EntityReference implements Serializable, Cloneable, Comparable<EntityReference>
{
    /**
     * The version identifier for this Serializable class. Increment only if the <i>serialized</i> form of the class
     * changes.
     */
    private static final long serialVersionUID = 1L;
    
    private String name;

    private EntityReference parent;

    private EntityType type;

    private Map<String,Object> parameters;

    /**
     * Clone an EntityReference.
     *
     * @since 3.3M1
     */
    public EntityReference(EntityReference reference)
    {
        this(reference, reference.parent);
    }

    /**
     * Clone an EntityReference, but use the specified parent for its new parent
     *
     * @since 3.3M1
     */
    public EntityReference(EntityReference reference, EntityReference parent)
    {
        this(reference.name, reference.type, parent, reference.parameters);
    }

    /**
     * Clone an EntityReference, but replace one of the parent in the chain by a new one
     *
     * @param reference the reference that is cloned
     * @param oldReference the old parent that will be replaced
     * @param newReference the new parent that will replace oldReference in the chain
     * @since 3.3M1
     */
    public EntityReference(EntityReference reference, EntityReference oldReference, EntityReference newReference)
    {
        if (reference == null) {
            throw new IllegalArgumentException("Cloned reference must not be null");
        }

        setName(reference.name);
        setType(reference.type);
        setParameters(reference.parameters);
        if (reference.parent == null) {
            if (oldReference == null) {
                setParent(newReference);
            } else {
                throw new IllegalArgumentException("The old reference [" + oldReference
                    + "] does not belong to the parents chain of the reference [" + reference + "]");
            }
        } else if (reference.parent.equals(oldReference)) {
            setParent(newReference);
        } else {
            setParent(new EntityReference(reference.parent, oldReference, newReference));
        }
    }

    /**
     * Create a new root EntityReference.
     * Note: Entity reference are immutable since 3.3M1.
     *
     * @param name name for the newly created entity reference, could not be null.
     * @param type type for the newly created entity reference, could not be null.
     */
    public EntityReference(String name, EntityType type)
    {
        this(name, type, null, null);
    }

    /**
     * Create a new EntityReference.
     * Note: Entity reference are immutable since 3.3M1.
     *
     * @param name name for the newly created entity reference, could not be null.
     * @param type type for the newly created entity reference, could not be null.
     * @param parent parent reference for the newly created entity reference, may be null.
     */
    public EntityReference(String name, EntityType type, EntityReference parent)
    {
        setName(name);
        setType(type);
        setParent(parent);
    }

    /**
     * Create a new EntityReference.
     * Note: Entity reference are immutable since 3.3M1.
     *
     * @param name name for the newly created entity, could not be null.
     * @param type type for the newly created entity, could not be null.
     * @param parent parent reference for the newly created entity reference, may be null.
     * @param parameters parameters for this reference, may be null
     * @since 3.3M1
     */
    public EntityReference(String name, EntityType type, EntityReference parent, Map<String,Object> parameters)
    {
        setName(name);
        setType(type);
        setParent(parent);
        setParameters(parameters);
    }

    /**
     * Entity reference are immutable since 3.3M1, so this method is now protected.
     * @param name the name for this entity
     * @exception IllegalArgumentException if the passed name is null or empty
     */
    protected void setName(String name)
    {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("An Entity Reference name cannot be null or empty");
        }
        this.name = name;
    }

    /**
     * @return the name of this entity
     */
    public final String getName()
    {
        return this.name;
    }

    /**
     * Entity reference are immutable since 3.3M1, so this method is now protected.
     * @param parent the parent for this entity, may be null for a root entity.
     */
    protected void setParent(EntityReference parent)
    {
        this.parent = parent;
    }

    /**
     * @return the parent of this entity, may be null for a root entity.
     */
    public final EntityReference getParent()
    {
        return this.parent;
    }

    /**
     * Entity reference are immutable since 3.3M1, so this method is now protected.
     * @param type the type for this entity
     * @exception IllegalArgumentException if the passed type is null
     */
    protected void setType(EntityType type)
    {
        if (type == null) {
            throw new IllegalArgumentException("An Entity Reference type cannot be null");
        }
        this.type = type;
    }

    /**
     * @return the type of this entity
     */
    public final EntityType getType()
    {
        return this.type;
    }

    /**
     * Set multiple parameters at once
     * @param parameters the map of parameter to set
     * @since 3.3M1
     */
    protected void setParameters(Map<String,Object> parameters) {
        if( parameters != null ) {
            for(Map.Entry<String,Object> entry : parameters.entrySet()) {
                setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Add or set a parameter value. Parameters should be immutable objects to prevent any weird behavior.
     * @param name the name of the parameter
     * @param value the value of the parameter
     * @since 3.3M1
     */
    protected void setParameter(String name, Object value) {
        if( value != null ) {
            if (parameters == null) {
                parameters = new TreeMap<String,Object>();
            }
            parameters.put(name, value);
        } else if (parameters != null) {
            parameters.remove(name);
            if (parameters.size() == 0) {
                parameters = null;
            }
        }
    }

    /**
     * Get the value of a parameter. Return null if the parameter is not set.
     * @param name the name of the parameter to get
     * @return the value of the parameter
     * @since 3.3M1
     */
    public final Object getParameter(String name) {
        return (parameters == null) ? null : parameters.get(name);
    }

    /**
     * @return the root parent of this entity
     */
    public EntityReference getRoot()
    {
        EntityReference reference = this;
        while (reference.getParent() != null) {
            reference = reference.getParent();
        }
        return reference;
    }

    /**
     * @return a list of references in the parents chain of this reference, ordered from root to this reference.
     */
    public List<EntityReference> getReversedReferenceChain() {
        LinkedList<EntityReference> referenceList = new LinkedList<EntityReference>();
        EntityReference reference = this;
        do {
            referenceList.push(reference);
        } while ((reference = reference.getParent()) != null);
        return referenceList;
    }

    /**
     * Extract the entity of the given type from this one. This entity may be returned if it has the type requested.
     * @param type the type of the entity to be extracted
     * @return the entity of the given type
     */
    public EntityReference extractReference(EntityType type)
    {
        EntityReference reference = this;

        while (reference != null && reference.getType() != type) {
            reference = reference.getParent();
        }

        return reference;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(64);
        sb.append("name = [")
            .append(getName())
            .append("], type = [")
            .append(getType())
            .append("], parent = [")
            .append(getParent())
            .append(']');
        if (parameters != null) {
            sb.append(" parameters = {");
            boolean first = true;
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(entry.getKey())
                    .append(" = [")
                    .append(entry.getValue().toString())
                    .append(']');
            }
            sb.append('}');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof EntityReference)) {
            return false;
        }

        EntityReference ref = (EntityReference) obj;

        return name.equals(ref.name) && type.equals(ref.type) &&
            (parent == null ? ref.parent == null : parent.equals(ref.parent)) &&
            (parameters == null ? ref.parameters == null : parameters.equals(ref.parameters));
    }

    @Override
    public int hashCode()
    {
        return toString().hashCode();
    }

    @Override
    public int compareTo(EntityReference ref)
    {
        if (ref == null) {
            throw new NullPointerException("Provided reference should not be null");
        }

        if (ref == this) {
            return 0;
        }

        if (parent != null && ref.parent == null) {
            return 1;
        }

        int cmp;
        if (parent != null && (cmp = parent.compareTo(ref.parent)) != 0) {
            return cmp;
        }

        if (!type.equals(ref.type)) {
            return type.compareTo(ref.type);
        }

        if (!name.equals(ref.name)) {
            return name.compareTo(ref.name);
        }

        if (parameters != null && ref.parameters == null) {
            return 1;
        }

        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                Object obj = ref.parameters.get(entry.getKey());
                Object myobj = entry.getValue();
                if (myobj != null && myobj instanceof Comparable) {
                    if (obj == null) {
                        return 1;
                    }
                    return ((Comparable) myobj).compareTo(obj);
                }
            }
        }

        return 0;
    }
}
