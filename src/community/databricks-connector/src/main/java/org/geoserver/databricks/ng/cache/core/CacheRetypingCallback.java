package org.geoserver.databricks.ng.cache.core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.RetypeFeatureTypeCallback;
import org.geoserver.platform.ExtensionPriority;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;

public class CacheRetypingCallback implements RetypeFeatureTypeCallback, ExtensionPriority {

    public static final String DATASTORE_NAME = "datastoreName";
    public static final String CACHING_ENABLED = "cachingEnabled";
    public static final String DB_TYPE = "Databricks";

    private static Logger LOGGER = Logging.getLogger(CacheRetypingCallback.class);

    @Override
    public FeatureType retypeFeatureType(FeatureTypeInfo featureTypeInfo, FeatureType featureType) {
        return RetypeFeatureTypeCallback.super.retypeFeatureType(featureTypeInfo, featureType);
    }

    @Override
    public <T extends FeatureType, U extends Feature> FeatureSource<T, U> wrapFeatureSource(
            FeatureTypeInfo featureTypeInfo, FeatureSource<T, U> featureSource) {

        String storeType = featureTypeInfo.getStore().getType();
        boolean cacheEnabled = getAsBoolean(featureTypeInfo.getMetadata(), CACHING_ENABLED);
        if (storeType.equalsIgnoreCase(DB_TYPE) && cacheEnabled)
            return (FeatureSource<T, U>)
                    new CacheFeatureSource(
                            featureTypeInfo,
                            (SimpleFeatureSource) featureSource,
                            getJdbcDataStore(featureTypeInfo));
        return featureSource;
    }

    @Override
    public int getPriority() {
        return 99;
    }

    private JDBCDataStore jdbcDataStore;

    public synchronized JDBCDataStore getJdbcDataStore(FeatureTypeInfo featureTypeInfo) {
        if (jdbcDataStore == null) {
            Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
            MetadataMap metadata = featureTypeInfo.getMetadata();
            DataStoreInfo dataStoreInfo = null;
            try {
                if (metadata.get(DATASTORE_NAME) != null) {
                    dataStoreInfo =
                            catalog.getDataStoreByName((String) metadata.get(DATASTORE_NAME));
                    jdbcDataStore =
                            (JDBCDataStore) dataStoreInfo.getDataStore(new NullProgressListener());
                    LOGGER.log(
                            Level.INFO, "Retrieving a jdbc datastore : " + dataStoreInfo.getName());
                }
            } catch (IOException e) {
                LOGGER.log(
                        Level.INFO,
                        "Failed to retrieve a jdbc datastore : " + dataStoreInfo.getName());
                throw new RuntimeException("Error obtain store: ", e);
            }
        }
        return jdbcDataStore;
    }

    boolean getAsBoolean(MetadataMap metadataMap, String attributeName) {
        if (metadataMap.get(attributeName) == null) return false;
        if (metadataMap.get(attributeName) instanceof Boolean)
            return (boolean) metadataMap.get(attributeName);
        return Boolean.parseBoolean((String) metadataMap.get(attributeName));
    }
}
