/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import org.h2.api.ErrorCode;
import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.expression.function.FunctionCall;
import org.h2.message.DbException;
import org.h2.result.ResultInterface;
import org.h2.schema.Schema;
import org.h2.value.Value;
import org.h2.value.ValueNull;
import org.h2.value.ValueResultSet;

/**
 * A table backed by a system or user-defined function that returns a result
 * set.
 */
public class FunctionTable extends VirtualConstructedTable {

    private final FunctionCall function;
    private Expression functionExpr;

    public FunctionTable(Schema schema, SessionLocal session, Expression functionExpr, FunctionCall function) {
        super(schema, 0, function.getName());
        this.functionExpr = functionExpr;
        this.function = function;
        function.optimize(session);
        int type = function.getValueType();
        if (type != Value.RESULT_SET) {
            throw DbException.get(ErrorCode.FUNCTION_MUST_RETURN_RESULT_SET_1, function.getName());
        }
        Expression[] args = function.getArgs();
        int numParams = args.length;
        Expression[] columnListArgs = new Expression[numParams];
        for (int i = 0; i < numParams; i++) {
            args[i] = args[i].optimize(session);
            columnListArgs[i] = args[i];
        }
        ValueResultSet template = function.getValueForColumnList(session, columnListArgs);
        if (template == null) {
            throw DbException.get(ErrorCode.FUNCTION_MUST_RETURN_RESULT_SET_1, function.getName());
        }
        ResultInterface result = template.getResult();
        int columnCount = result.getVisibleColumnCount();
        Column[] cols = new Column[columnCount];
        for (int i = 0; i < columnCount; i++) {
            cols[i] = new Column(result.getColumnName(i), result.getColumnType(i));
        }
        setColumns(cols);
    }

    @Override
    public boolean canGetRowCount(SessionLocal session) {
        return false;
    }

    @Override
    public long getRowCount(SessionLocal session) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getRowCountApproximation(SessionLocal session) {
        return Long.MAX_VALUE;
    }

    @Override
    public ResultInterface getResult(SessionLocal session) {
        functionExpr = functionExpr.optimize(session);
        Value v = functionExpr.getValue(session);
        if (v == ValueNull.INSTANCE) {
            return null;
        }
        return ((ValueResultSet) v).getResult();
    }

    @Override
    public String getSQL(int sqlFlags) {
        return function.getSQL(sqlFlags);
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, int sqlFlags) {
        return builder.append(function.getSQL(sqlFlags));
    }

    @Override
    public boolean isDeterministic() {
        return function.isDeterministic();
    }

}
