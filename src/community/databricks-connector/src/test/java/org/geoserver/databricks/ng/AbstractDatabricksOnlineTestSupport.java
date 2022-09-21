package org.geoserver.databricks.ng;

import static org.junit.Assume.assumeNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.data.DataAccess;
import org.geotools.data.Transaction;
import org.geotools.data.util.NullProgressListener;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.test.FixtureUtilities;
import org.junit.BeforeClass;

public class AbstractDatabricksOnlineTestSupport extends GeoServerSystemTestSupport {

    protected Connection connection;
    protected JDBCDataStore dataStore;

    protected Connection cacheConnection;
    protected JDBCDataStore cachedataStore;

    private static Properties fixture;

    private static final String fixtureId = "databricks-online-tests";

    private boolean dropTestTable = true;

    @BeforeClass
    public static void before() {
        fixture = getFixture();
        assumeNotNull(fixture);
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        setUpMockData();
        super.onSetUp(testData);
    }

    private static Properties getFixture() {
        File fixtureFile = FixtureUtilities.getFixtureFile(getFixtureDirectory(), fixtureId);
        if (fixtureFile.exists()) {
            return FixtureUtilities.loadProperties(fixtureFile);
        } else {
            Properties exampleFixture = createExampleFixture();
            if (exampleFixture != null) {
                File exFixtureFile = new File(fixtureFile.getAbsolutePath() + ".example");
                if (!exFixtureFile.exists()) {
                    createExampleFixture(exFixtureFile, exampleFixture);
                }
            }
            FixtureUtilities.printSkipNotice(fixtureId, fixtureFile);
            return null;
        }
    }

    private static void createExampleFixture(File exFixtureFile, Properties exampleFixture) {
        FileOutputStream fout = null;
        try {
            exFixtureFile.getParentFile().mkdirs();
            exFixtureFile.createNewFile();

            fout = new FileOutputStream(exFixtureFile);

            exampleFixture.store(
                    fout,
                    "This is an example fixture. Update the "
                            + "values and remove the .example suffix to enable the test");
            fout.flush();
            fout.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException e) {
                }
            }
        }
    }

    static File getFixtureDirectory() {
        return new File(System.getProperty("user.home") + File.separator + ".geoserver");
    }

    static Properties createExampleFixture() {
        Properties fixture = new Properties();

        // databricks store properties
        fixture.put("databricks.online.dataStore.dbtype", "databricks");

        fixture.put("databricks.online.dataStore.host", "databricks");

        fixture.put("databricks.online.dataStore.port", "databricks");

        fixture.put("databricks.online.dataStore.database", "databricks");

        fixture.put("databricks.online.dataStore.schema", "databricks");

        fixture.put("databricks.online.dataStore.user", "databricks");

        fixture.put("databricks.online.dataStore.passwd", "databricks");

        fixture.put("databricks.online.dataStore.transportMode", "databricks");

        fixture.put("databricks.online.dataStore.ssl", "databricks");

        fixture.put("databricks.online.dataStore.httpPath", "databricks");

        fixture.put("databricks.online.dataStore.AuthMech", "databricks");

        // cache store properties
        fixture.put("postgis.online.dataStore.dbtype", "postgis");

        fixture.put("postgis.online.dataStore.database", "postgis");

        fixture.put("postgis.online.dataStore.port", "postgis");

        fixture.put("postgis.online.dataStore.host", "postgis");

        fixture.put("postgis.online.dataStore.user", "postgis");

        fixture.put("postgis.online.dataStore.passwd", "postgis");

        return fixture;
    }

    public void setUpMockData() {

        try {
            // add the cache store
            cachedataStore =
                    extractDataStore(getOrCreateDataStore(getCatalog(), "postgis", "cache-store"));
            cacheConnection = cachedataStore.getConnection(Transaction.AUTO_COMMIT);

            // add the databricks store
            dataStore =
                    extractDataStore(
                            getOrCreateDataStore(getCatalog(), "databricks", "online-test"));
            connection = dataStore.getConnection(Transaction.AUTO_COMMIT);
            createTable();

        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Error while inserting mock data...", e);
        }
    }

    private void createTable() throws SQLException {
        String createTestTable =
                "CREATE TABLE TEST_POSITIONS "
                        + "(position_id int NOT NULL, name varchar(255), longitude double, latitude double);";
        try {
            JDBCTestUtils.executeStatement(connection, createTestTable, true);
        } catch (SQLException sqlException) {
            dropTestTable = false;
        }
        String insertData =
                "INSERT INTO TEST_POSITIONS "
                        + "(position_id , name , longitude , latitude) VALUES "
                        + "(1, 'test1', 45, 54), "
                        + "(2, 'test2', 55, 30), "
                        + "(3, 'test3', 15, 40), "
                        + "(4, 'test4', 30, 35), "
                        + "(5, 'test5', 66, 51)";
        JDBCTestUtils.executeStatement(connection, insertData, false);
    }

    public JDBCDataStore extractDataStore(DataStoreInfo dataStoreInfo) {
        DataAccess dataStore;
        try {
            dataStore = dataStoreInfo.getDataStore(new NullProgressListener());
        } catch (Exception exception) {
            throw new RuntimeException("Error accessing data store.", exception);
        }
        if (dataStore instanceof JDBCDataStore) {
            return (JDBCDataStore) dataStore;
        }
        throw new RuntimeException("Not a JDBC based data store.");
    }

    public DataStoreInfo getOrCreateDataStore(Catalog catalog, String dbtype, String dataStoreName)
            throws IOException {
        DataStoreInfo existing = catalog.getDataStoreByName(dataStoreName);
        if (existing != null) {
            return existing;
        }
        DataStoreInfoImpl dataStoreInfo = new DataStoreInfoImpl(catalog);
        dataStoreInfo.setName(dataStoreName);
        dataStoreInfo.setType(dbtype);
        dataStoreInfo.setWorkspace(catalog.getDefaultWorkspace());
        dataStoreInfo.setConnectionParameters(getDataStoreParameters(dbtype));
        dataStoreInfo.setEnabled(true);
        catalog.add(dataStoreInfo);
        return catalog.getDataStoreByName(catalog.getDefaultWorkspace(), dataStoreName);
    }

    private Map<String, Serializable> getDataStoreParameters(String dbtype) {
        Properties properties = getFixture();
        Map<String, Serializable> map = new HashMap();
        String prefix = getPrefixByType(dbtype);
        map.putAll(
                properties.entrySet().stream()
                        .filter(entry -> entry.getKey().toString().startsWith(prefix))
                        .collect(
                                Collectors.toMap(
                                        e -> e.getKey().toString().substring(prefix.length()),
                                        e -> e.getValue().toString())));
        map.put("Expose primary keys", true);
        return map;
    }

    private String getPrefixByType(String dbtype) {
        switch (dbtype) {
            case "databricks":
                return "databricks.online.dataStore.";
            case "postgis":
                return "postgis.online.dataStore.";
            default:
                return "";
        }
    }

    @Override
    protected void onTearDown(SystemTestData testData) throws Exception {
        super.onTearDown(testData);
        cleanUp();
        connection.close();
    }

    private void cleanUp() throws SQLException {
        if (dropTestTable) {
            String dropTestPositions = "DROP TABLE TEST_POSITIONS";
            JDBCTestUtils.executeStatement(connection, dropTestPositions, true);
        }
    }
}
