package org.geoserver.databricks.ng;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.jdbc.BasicSQLDialect;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.type.GeometryDescriptor;

public final class DatabricksSqlDialect extends BasicSQLDialect {

    protected DatabricksSqlDialect(JDBCDataStore dataStore) {
        super(dataStore);
    }

    @Override
    public FilterToSQL createFilterToSQL() {
        return new DatabricksFilterToSQL();
    }

    @Override
    public void encodeGeometryValue(Geometry value, int dimension, int srid, StringBuffer sql)
            throws IOException {
        throw new RuntimeException("Spatial geometries are not supported yet!");
    }

    @Override
    public void encodeGeometryEnvelope(String tableName, String geometryColumn, StringBuffer sql) {
        throw new RuntimeException("Spatial geometries are not supported yet!");
    }

    @Override
    public Envelope decodeGeometryEnvelope(ResultSet rs, int column, Connection cx)
            throws SQLException, IOException {
        throw new RuntimeException("Spatial geometries are not supported yet!");
    }

    @Override
    public Geometry decodeGeometryValue(
            GeometryDescriptor descriptor,
            ResultSet rs,
            String column,
            GeometryFactory factory,
            Connection cx,
            Hints hints)
            throws IOException, SQLException {
        throw new RuntimeException("Spatial geometries are not supported yet!");
    }

    @Override
    public boolean isAutoCommitQuery() {
        return true;
    }
}
