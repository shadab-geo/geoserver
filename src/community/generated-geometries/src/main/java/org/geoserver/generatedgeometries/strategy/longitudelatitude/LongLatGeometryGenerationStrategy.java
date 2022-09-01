/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.generatedgeometries.strategy.longitudelatitude;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.WARNING;
import static org.geoserver.generatedgeometries.core.GeometryGenerationStrategy.getStrategyName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.FactoryException;

/** Implementation of geometry generation strategy for long/lat attributes in the layer. */
public class LongLatGeometryGenerationStrategy
        implements GeometryGenerationStrategy<SimpleFeatureType, SimpleFeature> {

    private static final Logger LOGGER = Logging.getLogger(LongLatGeometryGenerationStrategy.class);

    private static final long serialVersionUID = 1L;

    public static final String NAME = "longLatStrategy";

    public static final String LONGITUDE_ATTRIBUTE_NAME = "longitudeAttributeName";
    public static final String LATITUDE_ATTRIBUTE_NAME = "latitudeAttributeName";
    public static final String GEOMETRY_ATTRIBUTE_NAME = "geometryAttributeName";
    public static final String GEOMETRY_CRS = "geometryCRS";

    private final transient Map<Name, SimpleFeatureType> cache = new HashMap<>();
    private final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
    private Set<String> featureTypeInfos = new HashSet<>();
    private Map<String, LongLatConfiguration> configurations = new HashMap<>();

    Logger logger() {
        return LOGGER;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canHandle(FeatureTypeInfo info, SimpleFeatureType unused) {
        boolean canHandle =
                info != null
                        && (featureTypeInfos.contains(info.getName())
                                || getStrategyName(info).map(NAME::equals).orElse(false));
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(
                    Level.FINE,
                    "Can be handled check result on generated geometry for feature type {0} is {1}",
                    new Object[] {info, canHandle});
        }
        return canHandle;
    }

    @Override
    public void configure(FeatureTypeInfo info) {
        info.setSRS(CRS.toSRS(getLongLatConfiguration(info).crs));
        info.setNativeCRS(getLongLatConfiguration(info).crs);
        featureTypeInfos.add(info.getName());
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

        LongLatConfiguration configuration = getLongLatConfiguration(info);
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

    public void setConfigurationForLayer(String layerId, LongLatConfiguration configuration) {
        configurations.put(layerId, configuration);
        cache.clear();
    }

    public LongLatConfiguration getLongLatConfiguration(FeatureTypeInfo info) {
        String layerId = info.getName();
        LongLatConfiguration configuration = configurations.get(layerId);
        if (configuration == null) {
            configuration = getConfigurationFromMetadata(info);
            configurations.put(layerId, configuration);
        }
        return configuration;
    }

    private LongLatConfiguration getConfigurationFromMetadata(FeatureTypeInfo info) {
        MetadataMap metadata = info.getMetadata();
        if (metadata.containsKey(GEOMETRY_ATTRIBUTE_NAME)) {
            try {
                return new LongLatConfiguration(
                        metadata.get(GEOMETRY_ATTRIBUTE_NAME).toString(),
                        metadata.get(LONGITUDE_ATTRIBUTE_NAME).toString(),
                        metadata.get(LATITUDE_ATTRIBUTE_NAME).toString(),
                        CRS.decode(metadata.get(GEOMETRY_CRS).toString()));
            } catch (FactoryException e) {
                throw new GeneratedGeometryConfigurationException(e);
            }
        }
        throw new GeneratedGeometryConfigurationException(
                "configuration does not contain geometry attribute");
    }

    private void storeConfiguration(FeatureTypeInfo info, LongLatConfiguration configuration) {
        MetadataMap metadata = info.getMetadata();
        metadata.put(STRATEGY_METADATA_KEY, getName());
        metadata.put(GEOMETRY_ATTRIBUTE_NAME, configuration.geomAttributeName);
        metadata.put(LONGITUDE_ATTRIBUTE_NAME, configuration.longAttributeName);
        metadata.put(LATITUDE_ATTRIBUTE_NAME, configuration.latAttributeName);
        metadata.put(GEOMETRY_CRS, CRS.toSRS(configuration.crs));
    }

    @Override
    public SimpleFeature generateGeometry(
            FeatureTypeInfo info, SimpleFeatureType schema, SimpleFeature simpleFeature) {
        if (simpleFeature != null) {
            try {
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
                LongLatConfiguration configuration = getConfigurationFromMetadata(info);
                double x =
                        Double.parseDouble(
                                getAsString(simpleFeature, configuration.longAttributeName));
                double y =
                        Double.parseDouble(
                                getAsString(simpleFeature, configuration.latAttributeName));

                Point point = geometryFactory.createPoint(new Coordinate(x, y));
                point.setSRID(getLongLatConfiguration(info).srid);
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
                logger().log(WARNING, message, e);
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
        LongLatConfiguration configuration = getLongLatConfiguration(info);
        LonLatGeometryFilterVisitor dfv = new LonLatGeometryFilterVisitor(ff, configuration);
        Filter outFilter = (Filter) filter.accept(dfv, ff);
        LOGGER.log(Level.FINE, "Converted filter: {0}", outFilter);
        return outFilter;
    }

    @Override
    public Query convertQuery(FeatureTypeInfo info, Query query) {
        Query q = new Query(query);
        q.setFilter(convertFilter(info, query.getFilter()));
        LongLatConfiguration configuration = getLongLatConfiguration(info);
        List<String> properties = new ArrayList<>();
        try {
            properties =
                    info.getFeatureType()
                            .getDescriptors()
                            .stream()
                            .filter(
                                    propertyDescriptor ->
                                            !propertyDescriptor
                                                    .getName()
                                                    .toString()
                                                    .equals(configuration.geomAttributeName))
                            .map(propertyDescriptor -> propertyDescriptor.getName().toString())
                            .collect(Collectors.toList());
        } catch (Exception e) {
            String message = format("could not convert query [%s]", query);
            LOGGER.log(Level.SEVERE, message, e);
        }
        q.setPropertyNames(properties);
        LOGGER.log(Level.FINE, " {0}", q);
        return q;
    }

}
