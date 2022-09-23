package org.geoserver.generatedgeometries.strategy.wkb;

import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;

public class WkbFilterVisitor extends DuplicatingFilterVisitor {

    private FilterFactory ff;
    private WkbGeometryGenerationStrategy.WkbConfiguration configuration;

    public WkbFilterVisitor(
            FilterFactory ff, WkbGeometryGenerationStrategy.WkbConfiguration configuration) {
        this.ff = ff;
        this.configuration = configuration;
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {

        Expression e1 = filter.getExpression1();
        if (e1 instanceof PropertyName) {
            e1 = ff.property(configuration.wkbAttributeName);
        }
        return getFactory(extraData).bbox(e1, filter.getExpression2());
    }
}
