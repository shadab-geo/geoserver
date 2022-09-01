/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.generatedgeometries.core;

import static java.lang.String.format;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.RetypeFeatureTypeCallback;
import org.geoserver.platform.ExtensionPriority;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;

public class GeometryGenerationRetypingCallback
        implements RetypeFeatureTypeCallback, ExtensionPriority {

    private static Logger LOGGER = Logging.getLogger(GeometryGenerationRetypingCallback.class);

    private GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy;

    private boolean canHandleFeatureType(FeatureTypeInfo featureTypeInfo) {
        if (strategy != null) return strategy.canHandle(featureTypeInfo, null);
        return false;
    }

    @Override
    public FeatureType retypeFeatureType(FeatureTypeInfo featureTypeInfo, FeatureType featureType) {
        //        String strategyName =
        //                (String) featureTypeInfo.getMetadata().get("geometryGenerationStrategy");
        //        LOGGER.log(Level.FINE, "geometry strategy is: " + strategyName);
        strategy =
                (GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature>)
                        GeoServerExtensions.bean("longLatStrategy");

        if (canHandleFeatureType(featureTypeInfo)) {
            try {
                return strategy.defineGeometryAttributeFor(
                        featureTypeInfo, (SimpleFeatureType) featureType);
            } catch (GeneratedGeometryConfigurationException e) {
                LOGGER.log(Level.WARNING, format("cannot build feature type [%s]", featureType), e);
            }
        }
        return featureType;
    }

    @Override
    public <T extends FeatureType, U extends Feature> FeatureSource<T, U> wrapFeatureSource(
            FeatureTypeInfo featureTypeInfo, FeatureSource<T, U> featureSource) {
        if (canHandleFeatureType(featureTypeInfo)) {
            return (FeatureSource<T, U>)
                    new GeometryGenerationFeatureSource(
                            featureTypeInfo, (SimpleFeatureSource) featureSource, strategy);
        }
        return featureSource;
    }

    @Override
    public int getPriority() {
        return 99;
    }
}
