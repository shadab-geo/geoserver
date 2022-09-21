package org.geoserver.databricks.ng;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

public class WMSDatabricksOnlineTest extends AbstractDatabricksOnlineTestSupport {

    private XpathEngine xpath;

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        xpath = XMLUnit.newXpathEngine();
        Catalog catalog = getCatalog();
        DataStoreInfo dataStoreInfo =
                catalog.getDataStoreByName(catalog.getDefaultWorkspace(), "online-test");
        dataStoreInfo.setEnabled(true);
        addLayer(catalog.getDefaultWorkspace(), dataStoreInfo, "test_positions");
    }

    @Test
    public void testWMSGetMap() throws Exception {

        MockHttpServletResponse result =
                getAsServletResponse(
                        "wms?service=WMS&version=1.1.1&request=GetMap&layers=gs:test_positions"
                                + "&bbox=-180.0,-90.0,180.0,90.0&width=768&height=384&srs=EPSG:4326"
                                + "&styles="
                                + "&format=image/png&TRANSPARENT=true"
                                + "&exceptions=application/vnd.ogc.se_inimage");

        assertEquals(result.getStatus(), 200);
        assertEquals(result.getContentType(), "image/png");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(getBinary(result)));
        ImageAssert.assertEquals(
                URLs.urlToFile(
                        Thread.currentThread()
                                .getContextClassLoader()
                                .getResource("./wms/test_positions_1.png")),
                image,
                250);
    }

    @Test
    public void testWMSGetMapWithFilter() throws Exception {

        MockHttpServletResponse result =
                getAsServletResponse(
                        "wms?service=WMS&version=1.1.1&request=GetMap&layers=gs:test_positions"
                                + "&bbox=-180.0,-90.0,180.0,90.0&width=768&height=384&srs=EPSG:4326"
                                + "&styles="
                                + "&format=image/png&TRANSPARENT=true"
                                + "&CQL_FILTER=position_id IN (1,2,3)"
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
