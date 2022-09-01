package org.geoserver.databricks.ng.store;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.geotools.data.Parameter;
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
                    "databricks",
                    Collections.singletonMap(Parameter.LEVEL, "program"));

    /** Default port number for databricks */
    public static final Param PORT = new Param("port", Integer.class, "Port", true, 443);

    public static final Param TRANSPORT_MODE =
            new Param("transportMode", String.class, "http", true, "http");

    public static final Param SSL = new Param("ssl", Integer.class, "1", true, 1);

    public static final Param HTTP_PATH = new Param("httpPath", String.class, "httpPath", true);

    public static final Param AUTH_MECH = new Param("AuthMech", Integer.class, "3", true, 3);

    /** Default username when connecting using personal access token */
    public static final Param USER =
            new Param("user", String.class, "user name to login as", true, "token");

    @Override
    protected String getDatabaseID() {
        return (String) DATABRICKS_DB_TYPE.sample;
    }

    @Override
    public String getDisplayName() {
        return "Databricks";
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
    protected String getJDBCUrl(Map<String, ?> params) throws IOException {
        String url =
                "jdbc:spark://" + HOST.lookUp(params) + ":" + PORT.lookUp(params) + "/default;";
        url = url + "transportMode=" + TRANSPORT_MODE.lookUp(params) + ";";
        url = url + "ssl=" + SSL.lookUp(params) + ";";
        url = url + "httpPath=" + HTTP_PATH.lookUp(params) + ";";
        url = url + "AuthMech=" + AUTH_MECH.lookUp(params) + ";";
        url = url + "UID=" + USER.lookUp(params) + ";";
        url = url + "PWD=" + PASSWD.lookUp(params);
        return url;
    }

    protected void setupParameters(Map<String, Object> parameters) {
        super.setupParameters(parameters);
        parameters.put(PORT.key, PORT);
        parameters.put(USER.key, USER);
        parameters.put(TRANSPORT_MODE.key, TRANSPORT_MODE);
        parameters.put(SSL.key, SSL);
        parameters.put(HTTP_PATH.key, HTTP_PATH);
        parameters.put(AUTH_MECH.key, AUTH_MECH);
    }
}
