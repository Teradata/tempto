/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */

package com.teradata.test.query;

import com.teradata.test.context.TestContext;
import org.slf4j.Logger;

import javax.inject.Inject;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static com.teradata.test.context.ThreadLocalTestContextHolder.runWithTextContext;
import static com.teradata.test.query.QueryResult.forSingleIntegerValue;
import static com.teradata.test.query.QueryResult.toSqlIndex;
import static com.teradata.test.query.QueryType.SELECT;
import static org.slf4j.LoggerFactory.getLogger;

public class JdbcQueryExecutor
        implements QueryExecutor
{
    private static final Logger LOGGER = getLogger(JdbcQueryExecutor.class);

    private final Connection connection;

    @Inject
    public JdbcQueryExecutor(JdbcConnectivityParamsState jdbcParamsState,
                             JdbcConnectionsPool jdbcConnectionsPool,
                             TestContext testContext)
            throws SQLException
    {
        this(jdbcConnectionsPool.connectionFor(jdbcParamsState), testContext);
    }

    public JdbcQueryExecutor(Connection connection, TestContext testContext)
    {
        this.connection = connection;
        testContext.registerCloseCallback(context -> this.close());
    }

    @Override
    public QueryResult executeQuery(String sql, QueryParam... params)
            throws QueryExecutionException
    {
        return execute(sql, isSelect(sql), params);
    }

    @Override
    public QueryResult executeQuery(String sql, QueryType queryType, QueryParam... params)
            throws QueryExecutionException
    {
        return execute(sql, queryType == SELECT, params);
    }

    private QueryResult execute(String sql, boolean isSelect, QueryParam... params)
            throws QueryExecutionException
    {
        LOGGER.debug("executing query {} with params {}", sql, params);

        try {
            if (params.length == 0) {
                return executeQueryNoParams(sql, isSelect);
            }
            else {
                return executeQueryWithParams(sql, isSelect, params);
            }
        }
        catch (SQLException e) {
            throw new QueryExecutionException(e);
        }
    }

    private QueryResult executeQueryNoParams(String sql, boolean isSelect)
            throws SQLException
    {
        try (Statement statement = connection.createStatement()) {
            if (isSelect) {
                ResultSet rs = statement.executeQuery(sql);
                return buildQueryResult(rs);
            }
            else {
                return forSingleIntegerValue(statement.executeUpdate(sql));
            }
        }
    }

    // TODO - remove this method as soon as Presto supports prepared statements
    private QueryResult executeQueryWithParams(String sql, boolean isSelect, QueryParam[] params)
            throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setQueryParams(statement, params);

            if (isSelect) {
                ResultSet rs = statement.executeQuery();
                return buildQueryResult(rs);
            }
            else {
                return forSingleIntegerValue(statement.executeUpdate());
            }
        }
    }

    private QueryResult buildQueryResult(ResultSet rs)
            throws SQLException
    {
        return QueryResult.builder(rs.getMetaData())
                .addRows(rs)
                .build();
    }

    boolean isSelect(String sql)
    {
        return sql.trim().toLowerCase().startsWith("select");
    }

    private static void setQueryParams(PreparedStatement statement, QueryParam[] params)
            throws SQLException
    {
        for (int i = 0; i < params.length; ++i) {
            QueryParam param = params[i];
            statement.setObject(toSqlIndex(i), param.value, param.type.getVendorTypeNumber());
        }
    }

    @Override
    public void close()
    {
        try {
            connection.close();
        }
        catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
