package org.geoserver.generatedgeometries.strategy.wkb;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.generatedgeometries.core.GeneratedGeometryConfigurationException;
import org.geoserver.generatedgeometries.core.GeometryGenerationStrategy;
import org.geotools.data.Query;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKBReader;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WkbGeometryGenerationStrategy
        implements GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> {

    private static final Logger LOGGER = Logging.getLogger(WkbGeometryGenerationStrategy.class);

    private Set<String> featureTypeInfos = new HashSet<>();
    public static final String NAME = "wkbStrategy";

    public static final String WKB_ATTRIBUTE_NAME = "wkbAttributeName";
    public static final String GEOMETRY_ATTRIBUTE_NAME = "geometryAttributeName";
    public static final String GEOMETRY_CRS = "geometryCRS";

    private final transient Map<Name, SimpleFeatureType> cache = new HashMap<>();

    private Map<String, WkbConfiguration> configurations = new HashMap<>();

    public static class WkbConfiguration implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String geomAttributeName;
        public final String wkbAttributeName;
        public final CoordinateReferenceSystem crs;
        public final int srid;

        public WkbConfiguration(
                String geomAttributeName, String wkbAttributeName, CoordinateReferenceSystem crs) {
            this.geomAttributeName = geomAttributeName;
            this.wkbAttributeName = wkbAttributeName;
            this.crs = crs;
            try {
                this.srid = CRS.lookupEpsgCode(crs, true);
            } catch (FactoryException e) {
                throw new GeneratedGeometryConfigurationException(e);
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canHandle(FeatureTypeInfo info, SimpleFeatureType featureType) {
        boolean canHandle =
                info != null
                        && (featureTypeInfos.contains(info.getName())
                                || getStrategyName(info).map(NAME::equals).orElse(false));

        return canHandle;
    }

    @Override
    public void configure(FeatureTypeInfo info) {
        info.setSRS(CRS.toSRS(getWkbConfiguration(info).crs));
        info.setNativeCRS(getWkbConfiguration(info).crs);
        featureTypeInfos.add(info.getName());
    }

    public void setConfigurationForLayer(String layerId, WkbConfiguration configuration) {
        configurations.put(layerId, configuration);
        cache.clear();
    }

    private WkbConfiguration getWkbConfiguration(FeatureTypeInfo info) {
        String layerId = info.getName();
        WkbConfiguration configuration = configurations.get(layerId);
        if (configuration == null) {
            configuration = getConfigurationFromMetadata(info);
            configurations.put(layerId, configuration);
        }
        return configuration;
    }

    private WkbConfiguration getConfigurationFromMetadata(FeatureTypeInfo info) {
        MetadataMap metadata = info.getMetadata();
        if (metadata.containsKey(GEOMETRY_ATTRIBUTE_NAME)) {
            try {
                return new WkbConfiguration(
                        metadata.get(GEOMETRY_ATTRIBUTE_NAME).toString(),
                        metadata.get(WKB_ATTRIBUTE_NAME).toString(),
                        CRS.decode(metadata.get(GEOMETRY_CRS).toString()));
            } catch (FactoryException e) {
                throw new GeneratedGeometryConfigurationException(e);
            }
        }
        throw new GeneratedGeometryConfigurationException(
                "configuration does not contain geometry attribute");
    }

    Optional<String> getStrategyName(FeatureTypeInfo info) {
        if (info == null || info.getMetadata() == null) {
            return empty();
        }
        return ofNullable(info.getMetadata().get(STRATEGY_METADATA_KEY, String.class));
    }

    @Override
    public SimpleFeatureType defineGeometryAttributeFor(
            FeatureTypeInfo info, SimpleFeatureType src) {
        LOGGER.log(Level.FINE, "Defining geometry attribute for {0}.", info);
        if (cache.containsKey(src.getName())) {
            SimpleFeatureType sft = cache.get(src.getName());
            LOGGER.log(Level.FINE, "Found cached feature type: {0}.", sft);
            return sft;
        }
        LOGGER.log(Level.FINE, "Cached feature type not found for {0}.", info);

        WkbConfiguration configuration = getWkbConfiguration(info);
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();

        builder.setName(src.getName());
        builder.setCRS(configuration.crs);

        builder.add(configuration.geomAttributeName, Point.class);
        for (AttributeDescriptor ad : src.getAttributeDescriptors()) {
            if (!ad.getLocalName().equalsIgnoreCase(configuration.geomAttributeName)) {
                builder.add(ad);
            }
        }
        SimpleFeatureType simpleFeatureType = builder.buildFeatureType();
        cache.put(simpleFeatureType.getName(), simpleFeatureType);

        storeConfiguration(info, configuration);
        LOGGER.log(Level.FINE, "Built feature type: {0}.", simpleFeatureType);
        return simpleFeatureType;
    }

    private void storeConfiguration(FeatureTypeInfo info, WkbConfiguration configuration) {
        MetadataMap metadata = info.getMetadata();
        metadata.put(STRATEGY_METADATA_KEY, getName());
        metadata.put(GEOMETRY_ATTRIBUTE_NAME, configuration.geomAttributeName);
        metadata.put(WKB_ATTRIBUTE_NAME, configuration.wkbAttributeName);
        metadata.put(GEOMETRY_CRS, CRS.toSRS(configuration.crs));
    }

    @Override
    public SimpleFeature generateGeometry(
            FeatureTypeInfo info, SimpleFeatureType schema, SimpleFeature simpleFeature) {
        if (simpleFeature != null) {
            try {
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
                WkbConfiguration configuration = getConfigurationFromMetadata(info);

                byte[] wkbPoint = getAsBytes(simpleFeature, configuration.wkbAttributeName);
                Point point = (Point) new WKBReader().read(wkbPoint);
                point.setSRID(getWkbConfiguration(info).srid);
                LOGGER.log(Level.FINE, "Generated geometry: {0}", point);

                featureBuilder.add(point);
                for (Property prop : simpleFeature.getProperties()) {
                    featureBuilder.set(prop.getName(), prop.getValue());
                }
                simpleFeature = featureBuilder.buildFeature(simpleFeature.getID());
                LOGGER.log(
                        Level.FINE,
                        "Resulting feature with generated geometry: {0}",
                        simpleFeature);
            } catch (Exception e) {
                String message =
                        format(
                                "could not generate geometry for feature [%s] of type: %s",
                                simpleFeature.getID(), schema.getName());
                LOGGER.log(WARNING, message, e);
            }
        }
        return simpleFeature;
    }

    private byte[] getAsBytes(SimpleFeature simpleFeature, String name) {
        Object value =
                ofNullable(simpleFeature.getProperty(name))
                        .flatMap(property -> ofNullable(property.getValue()))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                format("cannot get value of property [%s]", name)));
        return (byte[]) value;
    }

    @Override
    public Filter convertFilter(FeatureTypeInfo info, Filter filter) {
        final FilterFactory ff = CommonFactoryFinder.getFilterFactory();
        WkbFilterVisitor visitor = new WkbFilterVisitor(ff, getWkbConfiguration(info));
        return (Filter) filter.accept(visitor, ff);
    }

    @Override
    public Query convertQuery(FeatureTypeInfo info, Query query) {
        Query q = new Query(query);

        /*
        TODO: the filter return using WkbFilterVisitor does not work , hence setting
        Filter.INCLUDE temporarily
        q.setFilter(convertFilter(info, query.getFilter()));
        */
        q.setFilter(Filter.INCLUDE);

        WkbConfiguration configuration = getWkbConfiguration(info);
        List<String> properties = new ArrayList<>();
        try {

            properties =
                    info.getFeatureType().getDescriptors().stream()
                            .filter(
                                    propertyDescriptor ->
                                            !propertyDescriptor
                                                    .getName()
                                                    .toString()
                                                    .equals(configuration.geomAttributeName))
                            .map(propertyDescriptor -> propertyDescriptor.getName().toString())
                            .collect(Collectors.toList());
        } catch (IOException exception) {
            String message = format("could not convert query [%s]", query);
            LOGGER.log(Level.SEVERE, message, exception);
        }
        q.setPropertyNames(properties);
        LOGGER.log(Level.FINE, " {0}", q);
        return q;
    }
}
