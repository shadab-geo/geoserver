package org.geoserver.databricks.ng;

import java.io.IOException;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.BinarySpatialOperator;

public class DatabricksFilterToSQL extends FilterToSQL {
    @Override
    protected FilterCapabilities createFilterCapabilities() {
        System.out.println("DatabricksFilterToSQL");
        return super.createFilterCapabilities();
    }

    @Override
    protected void encodeBinaryComparisonOperator(
            BinaryComparisonOperator filter,
            Object extraData,
            Expression left,
            Expression right,
            Class leftContext,
            Class rightContext) {
        System.out.println("DatabricksFilterToSQL");
        super.encodeBinaryComparisonOperator(
                filter, extraData, left, right, leftContext, rightContext);
    }

    @Override
    protected void writeBinaryExpressionMember(Expression exp, Class context) throws IOException {
        System.out.println("DatabricksFilterToSQL");
        super.writeBinaryExpressionMember(exp, context);
    }

    @Override
    protected void writeBinaryExpression(Expression e, Class context) throws IOException {
        System.out.println("DatabricksFilterToSQL");
        super.writeBinaryExpression(e, context);
    }

    @Override
    protected boolean isBinaryExpression(Expression e) {
        System.out.println("DatabricksFilterToSQL");
        return super.isBinaryExpression(e);
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {
        System.out.println("DatabricksFilterToSQL");
        return super.visit(filter, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter, Object extraData) {
        System.out.println("DatabricksFilterToSQL");
        return super.visitBinarySpatialOperator(filter, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(
            BinarySpatialOperator filter,
            PropertyName property,
            Literal geometry,
            boolean swapped,
            Object extraData) {
        System.out.println("DatabricksFilterToSQL");
        return super.visitBinarySpatialOperator(filter, property, geometry, swapped, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(
            BinarySpatialOperator filter, Expression e1, Expression e2, Object extraData) {
        System.out.println("DatabricksFilterToSQL");
        return super.visitBinarySpatialOperator(filter, e1, e2, extraData);
    }

    @Override
    protected void visitBinaryComparisonOperator(BinaryComparisonOperator filter, Object extraData)
            throws RuntimeException {
        System.out.println("DatabricksFilterToSQL");

        super.visitBinaryComparisonOperator(filter, extraData);
    }
}
