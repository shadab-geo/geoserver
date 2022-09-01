package org.geoserver.databricks.ng.cache.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.ows.Dispatcher;
import org.geoserver.security.decorators.DecoratingSimpleFeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;

/** Feature source to cache the features based on databricks selector parameter */
public class CacheFeatureSource extends DecoratingSimpleFeatureSource {

    private final FeatureTypeInfo featureTypeInfo;
    private JDBCDataStore jdbcDataStore;

    private static final ConcurrentHashMap<String, String> requests = new ConcurrentHashMap<>();

    public static final String SCHEMA_NAME = "schemaName";
    public static final String DATABRICKS_SELECTOR = "databricksSelector";
    public static final String GEOMETRY_GENERATION_STRATEGY = "geometryGenerationStrategy";
    public static final String LONGITUDE_ATTRIBUTE_NAME = "longitudeAttributeName";
    public static final String LATITUDE_ATTRIBUTE_NAME = "latitudeAttributeName";
    public static final String SERVICE = "SERVICE";
    public static final String WMS = "WMS";

    public static final List<String> supported =
            Arrays.asList("INT", "BIGINT", "TIMESTAMP", "STRING", "DOUBLE", "BOOLEAN");

    final FilterFactory ff = CommonFactoryFinder.getFilterFactory();

    private static Logger LOGGER = Logging.getLogger(CacheFeatureSource.class);

    public CacheFeatureSource(
            FeatureTypeInfo featureTypeInfo,
            SimpleFeatureSource delegate,
            JDBCDataStore jdbcDataStore) {
        super(delegate);
        this.featureTypeInfo = featureTypeInfo;
        this.jdbcDataStore = jdbcDataStore;
    }

    @Override
    public SimpleFeatureType getSchema() {
        return super.getSchema();
    }

    @Override
    public SimpleFeatureCollection getFeatures() throws IOException {
        return super.getFeatures();
    }

    @Override
    public SimpleFeatureCollection getFeatures(Filter filter) throws IOException {
        return super.getFeatures(filter);
    }

    @Override
    public SimpleFeatureCollection getFeatures(Query srcQuery) throws IOException {

        MetadataMap metadata = featureTypeInfo.getMetadata();
        SimpleFeatureCollection features;
        String databricksSelector =
                (String) Dispatcher.REQUEST.get().getKvp().get(DATABRICKS_SELECTOR);
        srcQuery.setFilter(getFinalFilter(databricksSelector, srcQuery.getFilter()));

        String candidateRequest = requests.get(databricksSelector);
        String strategy = (String) metadata.get(GEOMETRY_GENERATION_STRATEGY);

        // cache the request if it is supported and not already cached
        if (candidateRequest == null && isSupported(strategy)) {
            LOGGER.log(Level.INFO, "Requesting the features from databricks");
            features = super.getFeatures(srcQuery);
            writeFeatures(features, metadata);
        } else {
            LOGGER.log(Level.INFO, "Request already cached");
        }
        requests.put(databricksSelector, databricksSelector);
        features =
                jdbcDataStore
                        .getFeatureSource((String) metadata.get(SCHEMA_NAME))
                        .getFeatures(srcQuery);
        return features;
    }

    /**
     * Write the feature to cache store
     *
     * @param features the source features
     * @param metadata metadata containing geometry info
     * @throws IOException
     */
    private void writeFeatures(SimpleFeatureCollection features, MetadataMap metadata)
            throws IOException {
        LOGGER.log(Level.INFO, "Writing the features to cache");
        long start = System.currentTimeMillis();
        try (SimpleFeatureIterator featureIterator = features.features();
                FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                        getFeatureWriter((String) metadata.get(SCHEMA_NAME), jdbcDataStore)) {
            int count = 0;
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                SimpleFeature cacheFeature = writer.next();
                SimpleFeatureType simpleFeatureType = feature.getFeatureType();
                for (int i = 0; i < simpleFeatureType.getAttributeCount(); i++) {
                    AttributeDescriptor descriptor = feature.getFeatureType().getDescriptor(i);
                    String attrName = descriptor.getLocalName();
                    if (isSupported(descriptor))
                        cacheFeature.setAttribute(attrName, feature.getAttribute(attrName));
                    String strategy = (String) metadata.get(GEOMETRY_GENERATION_STRATEGY);
                    if (strategy != null) setGeometry(cacheFeature, feature, metadata);
                }
                writer.write();
                count++;
            }
            LOGGER.log(Level.INFO, "Total features fetched: " + count);
            LOGGER.log(
                    Level.INFO, "Time to cache: " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    /**
     * Returns true request if supported
     *
     * @param strategy the geometry strategy name
     * @return boolean flag
     * @throws RuntimeException if no geometry is specified while processing a WMS request
     */
    private boolean isSupported(String strategy) {
        String service = (String) Dispatcher.REQUEST.get().getKvp().get(SERVICE);
        if (service.equalsIgnoreCase(WMS)) {
            if (strategy != null) return true;
            else throw new RuntimeException("Geometry strategy not specified");
        }
        return true;
    }

    /**
     * Returns true is feature column datatype is supported
     *
     * @param descriptor the attribute descriptor
     * @return boolean flag
     */
    private boolean isSupported(AttributeDescriptor descriptor) {
        String type = (String) descriptor.getUserData().get("org.geotools.jdbc.nativeTypeName");
        return supported.contains(type);
    }

    private FeatureWriter<SimpleFeatureType, SimpleFeature> getFeatureWriter(
            String schemaName, JDBCDataStore dataStore) throws IOException {
        return dataStore.getFeatureWriterAppend(schemaName, Transaction.AUTO_COMMIT);
    }

    /**
     * Returns a final filter
     *
     * @param filter the databricks selector filter
     * @param srcFilter the query filter
     * @return filter
     */
    private Filter getFinalFilter(String filter, Filter srcFilter) {
        Filter databricksSelectorFilter;
        if (filter == null || filter.isEmpty())
            throw new RuntimeException(
                    "Please specify the cql filter using 'databricksSelectorFilter' request parameter");

        try {
            databricksSelectorFilter = ECQL.toFilter(filter);
        } catch (CQLException e) {
            throw new RuntimeException("Failed to parse databricks selector filter: ", e);
        }
        return ff.and(Arrays.asList(srcFilter, databricksSelectorFilter));
    }

    /**
     * Sets the geometry
     *
     * @param cacheFeature the feature to be cached
     * @param feature the source feature
     * @param metadata geometry metadata
     */
    private void setGeometry(
            SimpleFeature cacheFeature, SimpleFeature feature, MetadataMap metadata) {
        GeometryFactory factory = JTSFactoryFinder.getGeometryFactory();
        double longitude =
                (double) feature.getAttribute((String) metadata.get(LONGITUDE_ATTRIBUTE_NAME));
        double latitude =
                (double) feature.getAttribute((String) metadata.get(LATITUDE_ATTRIBUTE_NAME));
        Coordinate coordinate = new Coordinate(longitude, latitude);
        Point point = factory.createPoint(coordinate);
        cacheFeature.setDefaultGeometry(point);
    }
}
