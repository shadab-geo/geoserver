package org.geoserver.generatedgeometries.core.longitudelatitude;

import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;

public class AttributeRenameVisitor extends DuplicatingFilterVisitor {

    String source;
    String target;

    public AttributeRenameVisitor(String source, String target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public Object visit(PropertyName expression, Object extraData) {
        final String propertyName = expression.getPropertyName();
        if (propertyName != null && propertyName.equals(source)) {
            return getFactory(extraData).property(target);
        } else {
            return super.visit(expression, extraData);
        }
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {
        // rename if necessary
        Expression e1 = filter.getExpression1();
        if (e1 instanceof PropertyName) {
            PropertyName pname = (PropertyName) e1;
            String name = pname.getPropertyName();
            if (name != null && name.equals(source)) {
                e1 = ff.property(target);
            }
        }

        return getFactory(extraData).bbox(e1, filter.getExpression2());
    }
}
