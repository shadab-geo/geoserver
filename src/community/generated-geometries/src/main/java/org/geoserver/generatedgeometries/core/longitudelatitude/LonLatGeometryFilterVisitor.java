/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.generatedgeometries.core.longitudelatitude;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.generatedgeometries.core.longitudelatitude.LongLatGeometryGenerationStrategy.LongLatConfiguration;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.And;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Intersects;
import org.opengis.geometry.BoundingBox;

/**
 * This class converts any BBOX filter to a In Between Expression using configured X and Y fields.
 */
public class LonLatGeometryFilterVisitor extends DuplicatingFilterVisitor {

    private static final Logger LOGGER = Logging.getLogger(LonLatGeometryFilterVisitor.class);

    private FilterFactory ff;
    private LongLatConfiguration configuration;

    private boolean requiresInMemoryFiltering = false;

    /**
     * @param ff filter factory
     * @param configuration with information about X/Y Fields
     */
    public LonLatGeometryFilterVisitor(FilterFactory ff, LongLatConfiguration configuration) {
        super();
        this.ff = ff;
        this.configuration = configuration;
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {
        BoundingBox bounds = filter.getBounds();
        return getLongLatFilters(bounds);
    }

    @Override
    public Object visit(Intersects filter, Object extraData) {
        IntersectsOperation intop =
                new IntersectsOperation(filter.getExpression1(), filter.getExpression2());
        // if it's a valid intersection operation, build and return the compound native filter
        if (intop.isValid()) {
            if (configuration.inMemoryFilter) {
                requiresInMemoryFiltering = true;
            }
            return buildIntersectFilter(intop);
        }
        return super.visit(filter, extraData);
    }

    public boolean isRequiresInMemoryFiltering() {
        return requiresInMemoryFiltering;
    }

    private Filter buildIntersectFilter(IntersectsOperation intop) {
        Envelope bounds = intop.getGeometryBounds();
        And longLatFilters =
                getLongLatFilters(
                        bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        Filter result = intop.addIntersectionOperation(longLatFilters);
        LOGGER.log(Level.FINE, "Built intersection filter: {0}", result);

        return result;
    }

    private PropertyIsBetween createBetweenFilter(
            FilterFactory ff, String name, double minValue, double maxValue) {
        PropertyName propertyName = ff.property(name);
        Literal min = ff.literal(minValue);
        Literal max = ff.literal(maxValue);
        return ff.between(propertyName, min, max);
    }

    private And getLongLatFilters(BoundingBox bounds) {
        return getLongLatFilters(
                bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
    }

    private And getLongLatFilters(double minx, double miny, double maxx, double maxy) {
        PropertyIsBetween longitudeFilter =
                createBetweenFilter(ff, configuration.longAttributeName, minx, maxx);
        PropertyIsBetween latitudeFilter =
                createBetweenFilter(ff, configuration.latAttributeName, miny, maxy);
        return ff.and(Arrays.asList(longitudeFilter, latitudeFilter));
    }

    /**
     * Intersection operation utility class to group the methods related to building the native
     * filter.
     */
    private class IntersectsOperation {

        private final PropertyName property;
        private final Literal geometry;

        IntersectsOperation(Expression exp1, Expression exp2) {
            List<Expression> expressions = Arrays.asList(exp1, exp2);
            Optional<Expression> geomOpt =
                    expressions.stream().filter(exp -> isGeometry(exp)).findFirst();
            this.geometry = (Literal) geomOpt.orElse(null);
            Optional<Expression> propertyOpt =
                    expressions.stream().filter(exp -> exp instanceof PropertyName).findFirst();
            this.property = (PropertyName) propertyOpt.orElse(null);
        }

        boolean isValid() {
            return property != null && geometry != null;
        }

        private boolean isGeometry(Expression expression) {
            if (expression instanceof Literal) {
                return ((Literal) expression).getValue() instanceof Geometry;
            }
            return false;
        }

        private String getGeometryWkt() {
            Geometry geom = (Geometry) geometry.getValue();
            return geom.toText();
        }

        private Envelope getGeometryBounds() {
            Geometry geom = (Geometry) geometry.getValue();
            return geom.getEnvelopeInternal();
        }

        Filter addIntersectionOperation(Filter latlonFilter) {
            return configuration.inMemoryFilter
                    ? latlonFilter
                    : ff.and(latlonFilter, buildNativeIntersectionFilter());
        }

        Filter buildNativeIntersectionFilter() {
            String nativeFilterSql =
                    "SDO_ANYINTERACT(SDO_GEOMETRY('POINT('|| "
                            + configuration.longAttributeName
                            + " ||' ' || "
                            + configuration.latAttributeName
                            + " || ')'), ? ) = 'TRUE'";
            LOGGER.log(Level.FINE, "Generated native intersect filter: {0}", nativeFilterSql);

            NativeParametrizedFilter filter =
                    new NativeParametrizedFilterImpl(
                            nativeFilterSql, Arrays.asList(geometry.getValue()));
            return filter;
        }
    }
}
