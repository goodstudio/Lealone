/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.lealone.db.constraint;

import java.util.HashSet;

import com.lealone.common.util.StatementBuilder;
import com.lealone.common.util.StringUtils;
import com.lealone.db.index.Index;
import com.lealone.db.index.IndexColumn;
import com.lealone.db.lock.DbObjectLock;
import com.lealone.db.result.Row;
import com.lealone.db.schema.Schema;
import com.lealone.db.session.ServerSession;
import com.lealone.db.table.Column;
import com.lealone.db.table.Table;

/**
 * A unique constraint. This object always backed by a unique index.
 */
public class ConstraintUnique extends Constraint {

    private Index index;
    private boolean indexOwner;
    private IndexColumn[] columns;
    private final boolean primaryKey;

    public ConstraintUnique(Schema schema, int id, String name, Table table, boolean primaryKey) {
        super(schema, id, name, table);
        this.primaryKey = primaryKey;
    }

    @Override
    public String getConstraintType() {
        return primaryKey ? Constraint.PRIMARY_KEY : Constraint.UNIQUE;
    }

    private String getCreateSQLForCopy(Table forTable, String quotedName, boolean internalIndex) {
        StatementBuilder buff = new StatementBuilder("ALTER TABLE ");
        buff.append(forTable.getSQL()).append(" ADD CONSTRAINT ");
        if (forTable.isHidden()) {
            buff.append("IF NOT EXISTS ");
        }
        buff.append(quotedName);
        if (comment != null) {
            buff.append(" COMMENT ").append(StringUtils.quoteStringSQL(comment));
        }
        buff.append(' ').append(getTypeName()).append('(');
        for (IndexColumn c : columns) {
            buff.appendExceptFirst(", ");
            buff.append(forTable.getDatabase().quoteIdentifier(c.column.getName()));
        }
        buff.append(')');
        if (internalIndex && indexOwner && forTable == this.table) {
            buff.append(" INDEX ").append(index.getSQL());
        }
        return buff.toString();
    }

    private String getTypeName() {
        if (primaryKey) {
            return "PRIMARY KEY";
        }
        return "UNIQUE";
    }

    @Override
    public String getCreateSQLWithoutIndexes() {
        return getCreateSQLForCopy(table, getSQL(), false);
    }

    @Override
    public String getCreateSQL() {
        return getCreateSQLForCopy(table, getSQL(), true);
    }

    public void setColumns(IndexColumn[] columns) {
        this.columns = columns;
    }

    public IndexColumn[] getColumns() {
        return columns;
    }

    /**
     * Set the index to use for this unique constraint.
     *
     * @param index the index
     * @param isOwner true if the index is generated by the system and belongs
     *            to this constraint
     */
    public void setIndex(Index index, boolean isOwner) {
        this.index = index;
        this.indexOwner = isOwner;
    }

    @Override
    public void removeChildrenAndResources(ServerSession session, DbObjectLock lock) {
        table.removeConstraint(this);
        if (indexOwner) {
            table.removeIndexOrTransferOwnership(session, index, lock);
        }
    }

    @Override
    public void invalidate() {
        index = null;
        columns = null;
        table = null;
        super.invalidate();
    }

    @Override
    public void checkRow(ServerSession session, Table t, Row oldRow, Row newRow) {
        // unique index check is enough
    }

    @Override
    public boolean usesIndex(Index idx) {
        return idx == index;
    }

    @Override
    public void setIndexOwner(Index index) {
        indexOwner = true;
    }

    @Override
    public HashSet<Column> getReferencedColumns(Table table) {
        HashSet<Column> result = new HashSet<>(columns.length);
        for (IndexColumn c : columns) {
            result.add(c.column);
        }
        return result;
    }

    @Override
    public boolean isBefore() {
        return true;
    }

    @Override
    public void checkExistingData(ServerSession session) {
        // no need to check: when creating the unique index any problems are
        // found
    }

    @Override
    public Index getUniqueIndex() {
        return index;
    }

    @Override
    public void rebuild() {
        // nothing to do
    }
}