package org.geoserver.databricks.ng;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
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
import org.geotools.referencing.CRS;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WFSDatabricksOnlineTest extends AbstractDatabricksOnlineTestSupport {

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);

        Catalog catalog = getCatalog();
        DataStoreInfo dataStoreInfo =
                catalog.getDataStoreByName(catalog.getDefaultWorkspace(), "online-test");
        dataStoreInfo.setEnabled(true);
        addLayer(catalog.getDefaultWorkspace(), dataStoreInfo, "test_positions");
    }

    @Test
    public void testWFSGetFeature() throws Exception {
        JSON json =
                getAsJSON(
                        "wfs?request=GetFeature&version=1.1.0&typename=gs:test_positions"
                                + "&outputFormat=application/json");
        JSONObject jsonObject = (JSONObject) json;
        JSONArray features = jsonObject.getJSONArray("features");
        assertEquals(5, features.size());

        int expectedId = 5;
        JSONObject properties = getPropertiesById(features, expectedId);
        assertEquals(4, properties.size());

        assertEquals(expectedId, properties.get("position_id"));
        assertEquals("test5", properties.get("name"));
        assertEquals(66, properties.get("longitude"));
        assertEquals(51, properties.get("latitude"));
    }

    private JSONObject getPropertiesById(JSONArray features, int id) {
        for (int i = 0; i < features.size(); i++) {
            JSONObject jsonObject = features.getJSONObject(i);
            JSONObject properties = jsonObject.getJSONObject("properties");
            int tempId = (int) properties.get("position_id");
            if (Objects.equals(tempId, id)) return properties;
        }
        return null;
    }

    @Test
    public void testWFSGetFeatureWithFilter() throws Exception {
        JSON json =
                getAsJSON(
                        "wfs?request=GetFeature&version=1.1.0&typename=gs:test_positions"
                                + "&outputFormat=application/json&cql_filter=name='test3'");
        JSONObject actualJson = (JSONObject) json;
        JSONArray features = actualJson.getJSONArray("features");
        assertEquals(1, features.size());
        JSONObject jsonObject1 = features.getJSONObject(0);

        JSONObject properties = jsonObject1.getJSONObject("properties");
        assertEquals(4, properties.size());

        assertEquals(3, properties.get("position_id"));
        assertEquals("test3", properties.get("name"));
        assertEquals(15, properties.get("longitude"));
        assertEquals(40, properties.get("latitude"));
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
}
