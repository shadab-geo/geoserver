package org.geoserver.databricks.ng;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.generatedgeometries.core.GeometryGenerationStrategy;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.DataAccess;
import org.geotools.data.FeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.image.test.ImageAssert;
import org.geotools.referencing.CRS;
import org.geotools.util.URLs;
import org.junit.After;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.mock.web.MockHttpServletResponse;

public class WMSDatabricksWithCacheOnlineTest extends AbstractDatabricksOnlineTestSupport {

    boolean dropCacheTable = true;

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        DataStoreInfo dataStoreInfo =
                catalog.getDataStoreByName(catalog.getDefaultWorkspace(), "online-test");
        dataStoreInfo.setEnabled(true);
        addLayer(catalog.getDefaultWorkspace(), dataStoreInfo, "test_positions");

        // create cache table
        createCacheTable();
    }

    private void createCacheTable() {
        String createTestTable =
                "CREATE TABLE TEST_POSITIONS_CACHE "
                        + "(position_id int NOT null PRIMARY KEY , name varchar(255), longitude numeric , latitude numeric  , geom Geometry(Geometry, 4326) null)";
        try {
            JDBCTestUtils.executeStatement(cacheConnection, createTestTable, true);
        } catch (SQLException sqlException) {
            dropCacheTable = false;
        }
    }

    @Test
    public void testWMSGetMapWithCache() throws Exception {

        MockHttpServletResponse result =
                getAsServletResponse(
                        "wms?service=WMS&version=1.1.1&request=GetMap&layers=gs:test_positions"
                                + "&bbox=-180.0,-90.0,180.0,90.0&width=768&height=384&srs=EPSG:4326"
                                + "&styles="
                                + "&format=image/png&TRANSPARENT=true"
                                + "&databricksSelector=position_id IN (1,2,3)"
                                + "&exceptions=application/vnd.ogc.se_inimage");
        assertEquals(result.getStatus(), 200);
        assertEquals(result.getContentType(), "image/png");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(getBinary(result)));
        ImageAssert.assertEquals(
                URLs.urlToFile(
                        Thread.currentThread()
                                .getContextClassLoader()
                                .getResource("./wms/test_positions_2.png")),
                image,
                250);

        // check if cache table has the new features
        String cacheQuery = "select count(*) from test_positions_cache";
        ResultSet resultSet = JDBCTestUtils.executeQuery(cacheConnection, cacheQuery);

        assertTrue(resultSet.next());
        assertEquals(3, resultSet.getLong("count"));
    }

    protected void addLayer(WorkspaceInfo ws, DataStoreInfo store, String typeName)
            throws IOException, FactoryException {
        Catalog catalog = getCatalog();
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(ws);
        builder.setStore(store);
        DataAccess dataAccess = store.getDataStore(new NullProgressListener());
        Map<String, FeatureTypeInfo> featureTypesByNativeName = new HashMap<>();

        @SuppressWarnings("unchecked")
        FeatureSource fs =
                ((DataAccess<FeatureType, Feature>) dataAccess)
                        .getFeatureSource(new NameImpl(typeName));

        FeatureTypeInfo ftinfo = featureTypesByNativeName.get(typeName);

        if (ftinfo == null) {
            ftinfo = builder.buildFeatureType(fs);
            MetadataMap metadata = ftinfo.getMetadata();
            metadata.put("geometryGenerationStrategy", "longLat");
            metadata.put("geometryAttributeName", "geom");
            metadata.put("longitudeAttributeName", "longitude");
            metadata.put("latitudeAttributeName", "latitude");
            metadata.put("geometryCRS", "EPSG:4326");

            // do the cache settings
            metadata.put("cachingEnabled", true);
            metadata.put("datastoreName", "cache-store");
            metadata.put("schemaName", "test_positions_cache");

            GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy =
                    (GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature>)
                            GeoServerExtensions.bean("longLatStrategy");
            strategy.configure(ftinfo);
        }
        ftinfo.setSRS("EPSG:4326");
        CoordinateReferenceSystem epsg4326 = CRS.decode("EPSG:4326", true);
        ReferencedEnvelope bbox = new ReferencedEnvelope(-180, 180, -90, 90, epsg4326);
        ftinfo.setNativeBoundingBox(bbox);
        ftinfo.setLatLonBoundingBox(bbox);

        if (ftinfo.getId() == null) {
            catalog.validate(ftinfo, true).throwIfInvalid();
            catalog.add(ftinfo);
        }
        LayerInfo layer = builder.buildLayer(ftinfo);

        boolean valid = true;
        try {
            if (!catalog.validate(layer, true).isValid()) {
                valid = false;
            }
        } catch (Exception e) {
            valid = false;
        }
        layer.setEnabled(valid);
        catalog.add(layer);
    }

    @After
    public void tearDown() throws Exception {
        if (dropCacheTable) {
            String dropTestPositions = "DROP TABLE test_positions_cache";
            JDBCTestUtils.executeStatement(cacheConnection, dropTestPositions, true);
        }
    }
}
