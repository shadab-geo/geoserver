package org.geoserver.generatedgeometries.strategy.wkt;

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
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WktGeometryGenerationStrategy
        implements GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> {

    private static final Logger LOGGER = Logging.getLogger(WktGeometryGenerationStrategy.class);

    private Set<String> featureTypeInfos = new HashSet<>();
    public static final String NAME = "wktStrategy";

    public static final String WKT_ATTRIBUTE_NAME = "wktAttributeName";
    public static final String GEOMETRY_ATTRIBUTE_NAME = "geometryAttributeName";
    public static final String GEOMETRY_CRS = "geometryCRS";

    private final transient Map<Name, SimpleFeatureType> cache = new HashMap<>();

    private Map<String, WktConfiguration> configurations = new HashMap<>();

    public static class WktConfiguration implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String geomAttributeName;
        public final String wktAttributeName;
        public final CoordinateReferenceSystem crs;
        public final int srid;

        public WktConfiguration(
                String geomAttributeName, String wktAttributeName, CoordinateReferenceSystem crs) {
            this.geomAttributeName = geomAttributeName;
            this.wktAttributeName = wktAttributeName;
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
        info.setSRS(CRS.toSRS(getWktConfiguration(info).crs));
        info.setNativeCRS(getWktConfiguration(info).crs);
        featureTypeInfos.add(info.getName());
    }

    public void setConfigurationForLayer(String layerId, WktConfiguration configuration) {
        configurations.put(layerId, configuration);
        cache.clear();
    }

    private WktConfiguration getWktConfiguration(FeatureTypeInfo info) {
        String layerId = info.getName();
        WktConfiguration configuration = configurations.get(layerId);
        if (configuration == null) {
            configuration = getConfigurationFromMetadata(info);
            configurations.put(layerId, configuration);
        }
        return configuration;
    }

    private WktConfiguration getConfigurationFromMetadata(FeatureTypeInfo info) {
        MetadataMap metadata = info.getMetadata();
        if (metadata.containsKey(GEOMETRY_ATTRIBUTE_NAME)) {
            try {
                return new WktConfiguration(
                        metadata.get(GEOMETRY_ATTRIBUTE_NAME).toString(),
                        metadata.get(WKT_ATTRIBUTE_NAME).toString(),
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

        WktConfiguration configuration = getWktConfiguration(info);
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

    private void storeConfiguration(FeatureTypeInfo info, WktConfiguration configuration) {
        MetadataMap metadata = info.getMetadata();
        metadata.put(STRATEGY_METADATA_KEY, getName());
        metadata.put(GEOMETRY_ATTRIBUTE_NAME, configuration.geomAttributeName);
        metadata.put(WKT_ATTRIBUTE_NAME, configuration.wktAttributeName);
        metadata.put(GEOMETRY_CRS, CRS.toSRS(configuration.crs));
    }

    @Override
    public SimpleFeature generateGeometry(
            FeatureTypeInfo info, SimpleFeatureType schema, SimpleFeature simpleFeature) {
        if (simpleFeature != null) {
            try {
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
                WktConfiguration configuration = getConfigurationFromMetadata(info);

                String wktPoint = getAsString(simpleFeature, configuration.wktAttributeName);
                Point point = (Point) new WKTReader().read(wktPoint);
                point.setSRID(getWktConfiguration(info).srid);
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

    private String getAsString(SimpleFeature simpleFeature, String name) {
        return ofNullable(simpleFeature.getProperty(name))
                .flatMap(property -> ofNullable(property.getValue()))
                .map(Object::toString)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        format("cannot get value of property [%s]", name)));
    }

    @Override
    public Filter convertFilter(FeatureTypeInfo info, Filter filter) {
        final FilterFactory ff = CommonFactoryFinder.getFilterFactory();
        WktFilterVisitor visitor = new WktFilterVisitor(ff, getWktConfiguration(info));
        return (Filter) filter.accept(visitor, ff);
    }

    @Override
    public Query convertQuery(FeatureTypeInfo info, Query query) {
        Query q = new Query(query);
        q.setFilter(convertFilter(info, query.getFilter()));
        WktConfiguration configuration = getWktConfiguration(info);
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
