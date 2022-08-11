package org.geoserver.generatedgeometries.core.longitudelatitude;

import java.util.List;
import org.opengis.filter.NativeFilter;

/** Native filter extended interface to inject parametrized literals on JDBC prepared statements. */
public interface NativeParametrizedFilter extends NativeFilter {

    /** Returns the parametrized SQL filter with ? placeholders to represent the parameters. */
    String getParametrizedString();

    /** Returns the parameters to register on the SQL prepared statement. */
    List<Object> getParameters();
}
