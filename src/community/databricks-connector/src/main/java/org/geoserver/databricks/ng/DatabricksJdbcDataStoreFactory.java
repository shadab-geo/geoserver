package org.geoserver.databricks.ng;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.data.Parameter;
import org.geotools.data.jdbc.datasource.DBCPDataSource;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.jdbc.SQLDialect;

public final class DatabricksJdbcDataStoreFactory extends JDBCDataStoreFactory {

    private static final Param DATABRICKS_DB_TYPE =
            new Param(
                    "dbtype",
                    String.class,
                    "Data store type",
                    true,
                    "Databricks JDBC Connector",
                    Collections.singletonMap(Parameter.LEVEL, "program"));

    private static final Param DATABRICKS_JDBC_URL =
            new Param(
                    "JDBC Connection URL",
                    String.class,
                    "JDBC connection URL to Databricks SQL API",
                    true,
                    "jdbc:spark://<server-hostname>:443/default;"
                            + "transportMode=http;ssl=1;"
                            + "httpPath=sql/protocolv1/o/0/xxxx-xxxxxx-xxxxxxxx;"
                            + "AuthMech=3;"
                            + "UID=token;"
                            + "PWD=<personal-access-token>",
                    Collections.singletonMap(Parameter.IS_LARGE_TEXT, Boolean.TRUE));

    @Override
    protected String getDatabaseID() {
        return (String) DATABRICKS_DB_TYPE.sample;
    }

    @Override
    protected String getDriverClassName() {
        return "com.simba.spark.jdbc.Driver";
    }

    @Override
    protected SQLDialect createSQLDialect(JDBCDataStore dataStore) {
        return new DatabricksSqlDialect(dataStore);
    }

    @Override
    protected String getValidationQuery() {
        return "select 1;";
    }

    @Override
    public String getDescription() {
        return "Databricks JDBC Connector";
    }

    @Override
    protected DataSource createDataSource(Map<String, ?> params, SQLDialect dialect)
            throws IOException {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(getDriverClassName());
        dataSource.setUrl((String) DATABRICKS_JDBC_URL.lookUp(params));
        dataSource.setValidationQuery("select 1;");
        return new DBCPDataSource(dataSource);
    }

    protected void setupParameters(Map<String, Object> parameters) {
        parameters.put(DATABRICKS_DB_TYPE.key, DATABRICKS_DB_TYPE);
        parameters.put(DATABRICKS_JDBC_URL.key, DATABRICKS_JDBC_URL);
    }
}
