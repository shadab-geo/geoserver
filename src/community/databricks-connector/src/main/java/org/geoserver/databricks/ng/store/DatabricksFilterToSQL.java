package org.geoserver.databricks.ng.store;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.geotools.util.logging.Logging;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.BinarySpatialOperator;

public class DatabricksFilterToSQL extends FilterToSQL {

    private static final Logger LOGGER = Logging.getLogger(DatabricksFilterToSQL.class);

    @Override
    protected FilterCapabilities createFilterCapabilities() {
        LOGGER.log(Level.FINE, "create filter capabilities");
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
        LOGGER.log(Level.FINE, "encode binary comparison operator");
        super.encodeBinaryComparisonOperator(
                filter, extraData, left, right, leftContext, rightContext);
    }

    @Override
    protected void writeBinaryExpressionMember(Expression exp, Class context) throws IOException {
        LOGGER.log(Level.FINE, "write binary expression member");
        super.writeBinaryExpressionMember(exp, context);
    }

    @Override
    protected void writeBinaryExpression(Expression e, Class context) throws IOException {
        LOGGER.log(Level.FINE, "write binary expression");
        super.writeBinaryExpression(e, context);
    }

    @Override
    protected boolean isBinaryExpression(Expression e) {
        LOGGER.log(Level.FINE, "is binary expression");
        return super.isBinaryExpression(e);
    }

    @Override
    public Object visit(BBOX filter, Object extraData) {
        return super.visit(filter, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter, Object extraData) {
        LOGGER.log(Level.FINE, "binary spatial operator");
        return super.visitBinarySpatialOperator(filter, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(
            BinarySpatialOperator filter,
            PropertyName property,
            Literal geometry,
            boolean swapped,
            Object extraData) {
        return super.visitBinarySpatialOperator(filter, property, geometry, swapped, extraData);
    }

    @Override
    protected Object visitBinarySpatialOperator(
            BinarySpatialOperator filter, Expression e1, Expression e2, Object extraData) {
        return super.visitBinarySpatialOperator(filter, e1, e2, extraData);
    }

    @Override
    protected void visitBinaryComparisonOperator(BinaryComparisonOperator filter, Object extraData)
            throws RuntimeException {
        super.visitBinaryComparisonOperator(filter, extraData);
    }
}
