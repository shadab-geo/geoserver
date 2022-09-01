package org.geoserver.generatedgeometries.strategy.longitudelatitude;

import java.io.Serializable;
import org.geoserver.generatedgeometries.core.GeneratedGeometryConfigurationException;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class LongLatConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String geomAttributeName;
    public final String longAttributeName;
    public final String latAttributeName;
    public final CoordinateReferenceSystem crs;
    public final int srid;

    public LongLatConfiguration(
            String geomAttributeName,
            String longAttributeName,
            String latAttributeName,
            CoordinateReferenceSystem crs) {
        this.geomAttributeName = geomAttributeName;
        this.longAttributeName = longAttributeName;
        this.latAttributeName = latAttributeName;
        this.crs = crs;
        try {
            this.srid = CRS.lookupEpsgCode(crs, true);
        } catch (FactoryException e) {
            throw new GeneratedGeometryConfigurationException(e);
        }
    }
}
