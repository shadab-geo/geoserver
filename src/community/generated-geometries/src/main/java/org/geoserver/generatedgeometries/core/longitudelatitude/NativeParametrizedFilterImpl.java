package org.geoserver.generatedgeometries.core.longitudelatitude;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.FilterVisitor;

public class NativeParametrizedFilterImpl implements NativeParametrizedFilter {

    private String filterStr;
    private List<Object> values;

    public NativeParametrizedFilterImpl(String filterStr, List<Object> values) {
        this.filterStr = Objects.requireNonNull(filterStr);
        this.values = Collections.unmodifiableList(Objects.requireNonNull(values));
    }

    @Override
    public String getParametrizedString() {
        return filterStr;
    }

    @Override
    public List<Object> getParameters() {
        return values;
    }

    @Override
    public String getNative() {
        return filterStr;
    }

    @Override
    public boolean evaluate(Object object) {
        throw new RuntimeException(
                String.format("Native geometry filter '%s' can not be executed in memory.", this));
    }

    @Override
    public Object accept(FilterVisitor visitor, Object extraData) {
        if (visitor instanceof DuplicatingFilterVisitor) {
            return new NativeParametrizedFilterImpl(filterStr, values);
        }
        return visitor.visit(this, extraData);
    }

    @Override
    public String toString() {
        return "NativeParametrizedFilterImpl{"
                + "filterStr='"
                + filterStr
                + '\''
                + ", values="
                + values
                + '}';
    }
}
