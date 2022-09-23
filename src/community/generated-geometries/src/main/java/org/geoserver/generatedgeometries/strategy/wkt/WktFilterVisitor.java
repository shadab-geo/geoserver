package org.geoserver.generatedgeometries.strategy.wkt;

import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;

public class WktFilterVisitor extends DuplicatingFilterVisitor {

    private FilterFactory ff;
    private WktGeometryGenerationStrategy.WktConfiguration configuration;

    public WktFilterVisitor(
            FilterFactory ff, WktGeometryGenerationStrategy.WktConfiguration configuration) {
        this.ff = ff;
        this.configuration = configuration;
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {

        Expression e1 = filter.getExpression1();
        if (e1 instanceof PropertyName) {
            e1 = ff.property(configuration.wktAttributeName);
        }
        return getFactory(extraData).bbox(e1, filter.getExpression2());
    }
}
