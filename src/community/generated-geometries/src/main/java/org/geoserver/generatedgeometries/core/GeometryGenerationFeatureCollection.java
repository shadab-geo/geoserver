/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.generatedgeometries.core;

import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

class GeometryGenerationFeatureCollection extends DecoratingSimpleFeatureCollection {

    private static final Logger LOGGER =
            Logging.getLogger(GeometryGenerationFeatureCollection.class);

    private final FeatureTypeInfo featureTypeInfo;
    private final SimpleFeatureType schema;
    private final GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy;
    private final Filter filter;

    GeometryGenerationFeatureCollection(
            SimpleFeatureCollection delegate,
            FeatureTypeInfo featureTypeInfo,
            SimpleFeatureType schema,
            GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy) {
        this(delegate, featureTypeInfo, schema, strategy, Filter.INCLUDE);
    }

    GeometryGenerationFeatureCollection(
            SimpleFeatureCollection delegate,
            FeatureTypeInfo featureTypeInfo,
            SimpleFeatureType schema,
            GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy,
            Filter filter) {
        super(delegate);

        this.featureTypeInfo = featureTypeInfo;
        this.schema = schema;
        this.strategy = strategy;
        this.filter = filter;
    }

    @Override
    public SimpleFeatureType getSchema() {
        return this.schema;
    }

    @Override
    public SimpleFeatureIterator features() {
        return new GeometryGenerationCollectionIterator(super.features());
    }

    private class GeometryGenerationCollectionIterator implements SimpleFeatureIterator {

        private final SimpleFeatureIterator delegate;

        private SimpleFeature simpleFeature = null;

        private GeometryGenerationCollectionIterator(SimpleFeatureIterator delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            while (simpleFeature == null && delegate.hasNext()) {
                SimpleFeature feature = buildFinalFeature(delegate.next());
                if (feature != null) {
                    simpleFeature = feature;
                }
            }
            return simpleFeature != null;
        }

        @Override
        public SimpleFeature next() throws NoSuchElementException {
            SimpleFeature feature = simpleFeature;
            simpleFeature = null;
            return feature;
        }

        /**
         * Builds the final feature with the generated geometry attribute and checks feature
         * validation with provided filter.
         *
         * @param feature the original feature
         * @return the new feature with generated geometry value, or null if filter validations was
         *     unsuccessful
         */
        private SimpleFeature buildFinalFeature(SimpleFeature feature) {
            SimpleFeature generatedFeature =
                    strategy.generateGeometry(featureTypeInfo, schema, feature);
            //            LOGGER.log(
            //                    Level.FINE,
            //                    () ->
            //                            "Evaluating filter="
            //                                    + filter
            //                                    + " on generated feature="
            //                                    + generatedFeature);
            if (filter.evaluate(generatedFeature)) {
                // LOGGER.log(Level.FINE, () -> "Successful evaluation for filter=" + filter);
                return generatedFeature;
            }
            LOGGER.log(Level.FINE, () -> "NOT successful evaluation for filter=" + filter);
            return null;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
