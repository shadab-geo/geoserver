/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.generatedgeometries.core.longitudelatitude;

import static java.lang.Double.valueOf;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.WARNING;
import static org.geoserver.generatedgeometries.core.GeometryGenerationStrategy.getStrategyName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.generatedgeometries.core.GeneratedGeometryConfigurationException;
import org.geoserver.generatedgeometries.core.GeometryGenerationStrategy;
import org.geotools.data.DataUtilities;
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
import org.opengis.referencing.crs.CoordinateReferenceSystem;

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
    public static final String IN_MEMORY_FILTER = "inMemoryFilter";

    private final transient Map<Name, SimpleFeatureType> cache = new HashMap<>();
    private final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
    private Set<String> featureTypeInfos = new HashSet<>();
    private Map<String, LongLatConfiguration> configurations = new HashMap<>();

    public static class LongLatConfiguration implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String geomAttributeName;
        public final String longAttributeName;
        public final String latAttributeName;
        public final CoordinateReferenceSystem crs;
        public final int srid;
        public final boolean inMemoryFilter;

        public LongLatConfiguration(
                String geomAttributeName,
                String longAttributeName,
                String latAttributeName,
                CoordinateReferenceSystem crs,
                boolean inMemoryFilter) {
            this.geomAttributeName = geomAttributeName;
            this.longAttributeName = longAttributeName;
            this.latAttributeName = latAttributeName;
            this.crs = crs;
            this.inMemoryFilter = inMemoryFilter;
            try {
                this.srid = CRS.lookupEpsgCode(crs, true);
            } catch (FactoryException e) {
                throw new GeneratedGeometryConfigurationException(e);
            }
        }
    }

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

        // guess, this is not required always
        storeConfiguration(info, configuration);
        LOGGER.log(Level.INFO, "Built feature type: {0}.", simpleFeatureType);
        return simpleFeatureType;
    }

    @Override
    public boolean requiresInMemoryFiltering(FeatureTypeInfo info, Filter filter) {
        final FilterFactory ff = CommonFactoryFinder.getFilterFactory();
        LongLatConfiguration configuration = getLongLatConfiguration(info);
        LonLatGeometryFilterVisitor dfv = new LonLatGeometryFilterVisitor(ff, configuration);
        filter.accept(dfv, ff);
        return dfv.isRequiresInMemoryFiltering();
    }

    public void setConfigurationForLayer(String layerId, LongLatConfiguration configuration) {
        configurations.put(layerId, configuration);
        cache.clear();
    }

    private LongLatConfiguration getLongLatConfiguration(FeatureTypeInfo info) {
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
                Serializable memoryFilterObj = metadata.get(IN_MEMORY_FILTER);
                boolean inMemoryFilter =
                        memoryFilterObj != null && Boolean.parseBoolean((String) memoryFilterObj);
                return new LongLatConfiguration(
                        metadata.get(GEOMETRY_ATTRIBUTE_NAME).toString(),
                        metadata.get(LONGITUDE_ATTRIBUTE_NAME).toString(),
                        metadata.get(LATITUDE_ATTRIBUTE_NAME).toString(),
                        CRS.decode(metadata.get(GEOMETRY_CRS).toString()),
                        inMemoryFilter);
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
        metadata.put(IN_MEMORY_FILTER, String.valueOf(configuration.inMemoryFilter));
    }

    @Override
    public SimpleFeature generateGeometry(
            FeatureTypeInfo info, SimpleFeatureType schema, SimpleFeature simpleFeature) {
        if (simpleFeature != null) {
            try {
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
                LongLatConfiguration configuration = getConfigurationFromMetadata(info);
                Double x = valueOf(getAsString(simpleFeature, configuration.longAttributeName));
                Double y = valueOf(getAsString(simpleFeature, configuration.latAttributeName));

                //                String wktPoint = getAsString(simpleFeature,
                // configuration.geomAttributeName);
                //
                //                Point testWktPoint = (Point) new WKTReader().read("");
                //                Point testWkbPoint = (Point) new WKBReader().read(new byte[1]);

                Point point = geometryFactory.createPoint(new Coordinate(x, y));
                point.setSRID(getLongLatConfiguration(info).srid);
                // LOGGER.log(Level.FINE, "Generated geometry: {0}", point);

                featureBuilder.add(point);
                for (Property prop : simpleFeature.getProperties()) {
                    featureBuilder.set(prop.getName(), prop.getValue());
                }
                simpleFeature = featureBuilder.buildFeature(simpleFeature.getID());
                //                LOGGER.log(
                //                        Level.FINE,
                //                        "Resulting feature with generated geometry: {0}",
                //                        simpleFeature);
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
        boolean requiresInMemmoryFiltering = requiresInMemoryFiltering(info, query.getFilter());
        List<String> properties = new ArrayList<>();
        try {
            // no fields were sent, use all fields excluding geom field
            if (query.getPropertyNames() == null) {
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
            } else {
                // else use the passed fields of this query
                // but make sure geom field is replaced with Long and Lat fields
                List<String> existingProperties =
                        new LinkedList<String>(Arrays.asList(query.getPropertyNames()));
                // add the filter involved attribute only if filter will be evaluated in memory
                if (requiresInMemmoryFiltering) {
                    addFilterAttributes(query.getFilter(), existingProperties);
                }
                // remove geom column
                existingProperties.remove(configuration.geomAttributeName);
                // make sure longitude field is present
                if (!existingProperties.contains(configuration.longAttributeName))
                    existingProperties.add(configuration.longAttributeName);
                // make sure latitude field is present
                if (!existingProperties.contains(configuration.latAttributeName))
                    existingProperties.add(configuration.latAttributeName);

                properties = new ArrayList<>(existingProperties);
            }

        } catch (Exception e) {
            String message = format("could not convert query [%s]", query);
            LOGGER.log(Level.SEVERE, message, e);
        }
        q.setPropertyNames(properties);
        LOGGER.log(Level.FINE, " {0}", q);
        return q;
    }

    /**
     * Adds the attributes involved on filter to the requested properties list to evaluate filters
     * in memory.
     *
     * @param filter the filter
     * @param existingProperties requested properties list
     */
    private void addFilterAttributes(Filter filter, List<String> existingProperties) {
        String[] filterAttributes = DataUtilities.attributeNames(filter);
        for (String attr : filterAttributes) {
            if (StringUtils.isNotBlank(attr) && !existingProperties.contains(attr)) {
                existingProperties.add(attr);
            }
        }
    }
}
