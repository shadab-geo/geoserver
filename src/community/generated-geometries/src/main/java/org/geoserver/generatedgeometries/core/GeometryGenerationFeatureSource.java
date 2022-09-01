/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.generatedgeometries.core;

import java.io.IOException;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.security.decorators.DecoratingSimpleFeatureSource;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

class GeometryGenerationFeatureSource extends DecoratingSimpleFeatureSource {

    private final FeatureTypeInfo featureTypeInfo;
    private final GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> strategy;
    private SimpleFeatureType cachedFeatureType;

    GeometryGenerationFeatureSource(
            FeatureTypeInfo featureTypeInfo,
            SimpleFeatureSource delegate,
            GeometryGenerationStrategy strategy) {
        super(delegate);
        this.featureTypeInfo = featureTypeInfo;
        this.strategy = strategy;
    }

    @Override
    public SimpleFeatureType getSchema() {
        if (cachedFeatureType == null) {
            cachedFeatureType = defineFeatureType();
        }
        return cachedFeatureType;
    }

    private SimpleFeatureType defineFeatureType() {
        SimpleFeatureType src = super.getSchema();
        try {
            return strategy.defineGeometryAttributeFor(featureTypeInfo, src);
        } catch (GeneratedGeometryConfigurationException e) {
            e.printStackTrace();
        }
        return src;
    }

    @Override
    public SimpleFeatureCollection getFeatures() throws IOException {
        SimpleFeatureCollection features = super.getFeatures();
        return new GeometryGenerationFeatureCollection(
                features, featureTypeInfo, getSchema(), strategy);
    }

    @Override
    public SimpleFeatureCollection getFeatures(Filter srcFilter) throws IOException {
        Filter filter = strategy.convertFilter(featureTypeInfo, srcFilter);
        SimpleFeatureCollection features = super.getFeatures(filter);
        return new GeometryGenerationFeatureCollection(
                features,
                featureTypeInfo,
                getSchema(),
                strategy,srcFilter);
    }

    @Override
    public SimpleFeatureCollection getFeatures(Query srcQuery) throws IOException {
        Query query = strategy.convertQuery(featureTypeInfo, srcQuery);
        SimpleFeatureCollection features = super.getFeatures(query);
        return new GeometryGenerationFeatureCollection(
                features,
                featureTypeInfo,
                getSchema(),
                strategy,srcQuery.getFilter());
    }

    @Override
    public int getCount(Query srcQuery) throws IOException {
        Query query = strategy.convertQuery(featureTypeInfo, srcQuery);
        return super.getCount(query);
    }

    @Override
    public ReferencedEnvelope getBounds() throws IOException {

        SimpleFeatureCollection features =
                super.getFeatures(strategy.convertQuery(featureTypeInfo, Query.ALL));
        GeometryGenerationFeatureCollection generatedGeomFC =
                new GeometryGenerationFeatureCollection(
                        features, featureTypeInfo, getSchema(), strategy);
        GeneratedGeometryBoundsFinder boundsFinder =
                new GeneratedGeometryBoundsFinder(featureTypeInfo);
        generatedGeomFC.accepts(boundsFinder, null);
        return boundsFinder.getBounds();
    }

    @Override
    public ReferencedEnvelope getBounds(Query query) throws IOException {
        SimpleFeatureCollection fc = getFeatures(query);
        GeneratedGeometryBoundsFinder boundsFinder =
                new GeneratedGeometryBoundsFinder(featureTypeInfo);
        fc.accepts(boundsFinder, null);
        return boundsFinder.getBounds();
    }
}
