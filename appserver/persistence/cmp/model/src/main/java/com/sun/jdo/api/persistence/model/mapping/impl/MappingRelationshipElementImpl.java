/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 * MappingRelationshipElementImpl.java
 *
 * Created on March 3, 2000, 1:11 PM
 */

package com.sun.jdo.api.persistence.model.mapping.impl;

import com.sun.jdo.api.persistence.model.ModelException;
import com.sun.jdo.api.persistence.model.ModelVetoException;
import com.sun.jdo.api.persistence.model.jdo.RelationshipElement;
import com.sun.jdo.api.persistence.model.mapping.MappingClassElement;
import com.sun.jdo.api.persistence.model.mapping.MappingFieldElement;
import com.sun.jdo.api.persistence.model.mapping.MappingRelationshipElement;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.ListIterator;

import org.glassfish.persistence.common.I18NHelper;
import org.netbeans.modules.dbschema.ColumnPairElement;
import org.netbeans.modules.dbschema.DBMemberElement;
import org.netbeans.modules.dbschema.util.NameUtil;

/**
 *
 * @author Mark Munro
 * @author Rochelle Raccah
 * @version %I%
 */
public class MappingRelationshipElementImpl extends MappingFieldElementImpl
    implements MappingRelationshipElement
{
    // for join tables -- traverse through the middle table
    private ArrayList _associatedColumns;    // of column pair names
    //@olsen: made transient to prevent from serializing into mapping files
    private transient ArrayList _associatedColumnObjects;// of ColumnPairElement (for runtime)

    /*
    // possibly for EJB use later
    // private String EJBType;
    // public String EJBReference;
    // end possibly for EJB use later
    */

    /** Create new MappingRelationshipElementImpl with no corresponding name or
     * declaring class.  This constructor should only be used for cloning and
     * archiving.
     */
    public MappingRelationshipElementImpl ()
    {
        this(null, null);
    }

    /** Create new MappingRelationshipElementImpl with the corresponding name
     * and declaring class.
     * @param name the name of the element
     * @param declaringClass the class to attach to
     */
    public MappingRelationshipElementImpl (String name,
        MappingClassElement declaringClass)
    {
        super(name, declaringClass);
        setFetchGroupInternal(GROUP_NONE);
    }

// TBD?
/*    public void clear ()
    {
        super.clear();
        _associatedColumns = null;
    }*/
// end TBD

    //=================== column handling for join tables ====================

    /** Returns the list of associated column names to which this
     * mapping field is mapped.  This is used for join tables.
     * @return the names of the columns mapped by this mapping field
     * @see MappingFieldElement#getColumns
     */
    public ArrayList getAssociatedColumns ()
    {
        if (_associatedColumns == null)
            _associatedColumns = new ArrayList();

        return _associatedColumns;
    }

    /** Adds a column to the list of columns mapped by this mapping field.
     * Call this method instead of <code>addColumn</code> when mapping join
     * tables.  This method is used to map between the local column and the
     * join table, while <code>addAssociatedColumn</code> is used to
     * map between the join table and the foreign table.
     * @param column column pair element to be added to the mapping
     * @exception ModelException if impossible
     * @see MappingFieldElement#addColumn
     * @see #addAssociatedColumn
     */
    public void addLocalColumn (ColumnPairElement column)
        throws ModelException
    {
        // can't call addColumn in this class because there will be an
        // exception since the associated columns will be (legally) populated
        super.addColumn(column);
    }

    /** Adds a column to the list of associated columns mapped by this mapping
     * field.  Call this method instead of <code>addColumn</code> when mapping
     * join tables.  This method is used to map between the join table column
     * and the foreign table column, while <code>addLocalColumn</code> is used
     * to map between the local table and the join table.
     * @param column column pair element to be added to the mapping
     * @exception ModelException if impossible
     * @see MappingFieldElement#addColumn
     * @see #addLocalColumn
     */
    public void addAssociatedColumn (ColumnPairElement column)
        throws ModelException
    {
        if (column != null)
        {
            ArrayList columns = getAssociatedColumns();
            String columnName = NameUtil.getRelativeMemberName(
                column.getName().getFullName());

            // double check that this pair is not already in the column list
            if (!columns.contains(columnName))
            {
                try
                {
                    fireVetoableChange(PROP_ASSOCIATED_COLUMNS, null, null);
                    columns.add(columnName);
                    firePropertyChange(PROP_ASSOCIATED_COLUMNS, null, null);

                    // sync up runtime's object list too
                    _associatedColumnObjects = null;
                }
                catch (PropertyVetoException e)
                {
                    throw new ModelVetoException(e);
                }
            }
            else
            {
                throw new ModelException(I18NHelper.getMessage(getMessages(),
                    "mapping.column.column_defined", columnName));    // NOI18N
            }
        }
        else
        {
            throw new ModelException(I18NHelper.getMessage(getMessages(),
                "mapping.element.null_argument"));                    // NOI18N
        }
    }

    //================= overridden column handling methods ===================

    /** Adds a column to the list of columns mapped by this mapping
     * relationship.  This method overrides the one in MappingFieldElement to
     * check that the argument is a ColumnPairElement.
     * @param column column element to be added to the mapping
     * @exception ModelException if impossible
     */
    public void addColumn (DBMemberElement column) throws ModelException
    {
        if (column instanceof ColumnPairElement)
        {
            if (!getAssociatedColumns().isEmpty())
            {
                throw new ModelException(I18NHelper.getMessage(getMessages(),
                    "mapping.column.associated_columns_defined",         // NOI18N
                    NameUtil.getRelativeMemberName(
                    column.getName().getFullName())));
            }

            super.addColumn(column);
        }
        else
        {
            throw new ModelException(I18NHelper.getMessage(getMessages(),
                "mapping.column.column_invalid",             // NOI18N
                NameUtil.getRelativeMemberName(
                column.getName().getFullName())));
        }
    }

    /** Removes a column from the list of columns mapped by this mapping field.
     * This method overrides the one in MappingFieldElement to
     * remove the argument from the associated columns if necessary.
     * @param columnName the relative name of the column to be removed from
     * the mapping
     * @exception ModelException if impossible
     */
    public void removeColumn (String columnName) throws ModelException
    {
        try
        {
            super.removeColumn(columnName);
        }
        catch (ModelException e)    // not found in regular columns
        {
            try
            {
                fireVetoableChange(PROP_ASSOCIATED_COLUMNS, null, null);

                if (!getAssociatedColumns().remove(columnName))
                {
                    throw new ModelException(
                        I18NHelper.getMessage(getMessages(),
                        "mapping.element.element_not_removed",        // NOI18N
                        columnName));
                }

                firePropertyChange(PROP_ASSOCIATED_COLUMNS, null, null);

                // sync up runtime's object list too
                _associatedColumnObjects = null;
            }
            catch (PropertyVetoException ve)
            {
                throw new ModelVetoException(ve);
            }
        }
    }

    //============= extra object support for runtime ========================

    /** Returns the list of associated columns (ColumnPairElements) to
     * which this mapping field is mapped.  This is used for join tables.
     * This method should only be used by the runtime.
     * @return the columns mapped by this mapping field
     * @see MappingFieldElement#getColumns
     */
    public ArrayList getAssociatedColumnObjects ()
    {
        if (_associatedColumnObjects == null)
        {
            _associatedColumnObjects = MappingClassElementImpl.
                toColumnObjects(getDeclaringClass().getDatabaseRoot(),
                getAssociatedColumns());
        }

        return _associatedColumnObjects;
    }

    //============= delegation to RelationshipElement ===========

    final RelationshipElement getRelationshipElement ()
    {
        return ((MappingClassElementImpl)getDeclaringClass()).
            getPersistenceElement().getRelationship(getName());
    }

    /** Get the element class for this relationship element.  If primitive
     * types are supported, you can use <code><i>wrapperclass</i>.TYPE</code>
     * to specify them.
     * @return the element class
     */
    public String getElementClass ()
    {
        return getRelationshipElement().getElementClass();
    }

    /** Get the update action for this relationship element.
     * @return the update action, one of
     * {@link RelationshipElement#NONE_ACTION},
     * {@link RelationshipElement#NULLIFY_ACTION},
     * {@link RelationshipElement#RESTRICT_ACTION},
     * {@link RelationshipElement#CASCADE_ACTION}, or
     * {@link RelationshipElement#AGGREGATE_ACTION}
     */
    public int getUpdateAction ()
    {
        return getRelationshipElement().getUpdateAction();
    }

    /** Get the delete action for this relationship element.
     * @return the delete action, one of
     * {@link RelationshipElement#NONE_ACTION},
     * {@link RelationshipElement#NULLIFY_ACTION},
     * {@link RelationshipElement#RESTRICT_ACTION},
     * {@link RelationshipElement#CASCADE_ACTION}, or
     * {@link RelationshipElement#AGGREGATE_ACTION}
     */
    public int getDeleteAction ()
    {
        return getRelationshipElement().getDeleteAction();
    }

    /** Get the upper cardinality bound for this relationship element.  Returns
     * {@link java.lang.Integer#MAX_VALUE} for <code>n</code>
     * @return the upper cardinality bound
     */
    public int getUpperBound ()
    {
        return getRelationshipElement().getUpperBound();
    }

    /** Get the lower cardinality bound for this relationship element.
     * @return the lower cardinality bound
     */
    public int getLowerBound ()
    {
        return getRelationshipElement().getLowerBound();
    }

    //=============== extra set methods needed for xml archiver ==============

    /** Set the list of associated column names to which this mapping field is
     * mapped.  This method should only be used internally and for cloning
     * and archiving.
     * @param associatedColumns the list of names of the columns mapped by
     * this mapping field
     */
    public void setAssociatedColumns (ArrayList associatedColumns)
    {
        _associatedColumns = associatedColumns;
    }

    //================== possibly for EJB use later ===========================

    //public String getEJBType() { return this.EJBType; }

    //============== extra method for Boston -> Pilsen conversion ============

    /** Boston to Pilsen conversion.
     * This method converts the absolute column names to relative names.
     */
    protected void stripSchemaName ()
    {
        // call super to handle the columns stored in _columns
        super.stripSchemaName();

        // handle _associatedColumns
        if (_associatedColumns != null)
        {
            // Use ListIterator here, because I want to replace the value
            // stored in the ArrayList.  The ListIterator returned by
            // ArrayList.listIterator() supports the set method.
            ListIterator i = _associatedColumns.listIterator();

            while (i.hasNext())
                i.set(NameUtil.getRelativeMemberName((String)i.next()));
        }
    }
}
