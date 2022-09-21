package org.geoserver.databricks.ng;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/** Utils method for JDBC test setup. */
public class JDBCTestUtils {

    private static Logger LOG = Logging.getLogger(JDBCTestUtils.class);

    /**
     * Execute a statement.
     *
     * @param connection the jdbc connection.
     * @param statement the string statement.
     * @param lenient true if should not throw exception when failing, false otherwise.
     * @throws java.sql.SQLException
     */
    public static void executeStatement(Connection connection, String statement, boolean lenient)
            throws SQLException {
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            stmt.execute(statement);
        } catch (SQLException e) {
            if (lenient)
                LOG.log(
                        Level.SEVERE,
                        "Error occurred while closing the DB connection. Error is "
                                + e.getMessage(),
                        e);
            else throw e;
        } finally {
            close(stmt);
        }
    }

    /**
     * Execute a statement.
     *
     * @param connection the jdbc connection.
     * @param statement the string statement.
     */
    public static ResultSet executeQuery(Connection connection, String statement)
            throws SQLException {
        Statement stmt = connection.createStatement();
        return stmt.executeQuery(statement);
    }

    /**
     * Close a connection.
     *
     * @param connection the connection to close.
     */
    public static void close(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOG.severe(
                    "Error occurred while closing the DB connection. Error is " + e.getMessage());
        }
    }

    /**
     * Close a Statement.
     *
     * @param stmt the statement to close.
     */
    public static void close(Statement stmt) {
        try {
            if (stmt != null && !stmt.isClosed()) {
                stmt.close();
            }
        } catch (SQLException e) {
            LOG.severe("Error occurred while closing the Statement. Error is " + e.getMessage());
        }
    }
}
