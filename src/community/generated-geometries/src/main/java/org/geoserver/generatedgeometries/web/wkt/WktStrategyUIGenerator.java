package org.geoserver.generatedgeometries.web.wkt;

import java.io.Serializable;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.generatedgeometries.strategy.wkt.WktGeometryGenerationStrategy;
import org.geoserver.generatedgeometries.web.GeometryGenerationStrategyUIGenerator;

public class WktStrategyUIGenerator implements GeometryGenerationStrategyUIGenerator, Serializable {

    private static final long serialVersionUID = 1L;

    private WktGeometryConfigurationPanel wktGeometryConfigurationPanel;

    private final WktGeometryGenerationStrategy strategy;

    public WktStrategyUIGenerator(WktGeometryGenerationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return strategy.getName();
    }

    @Override
    public Component createUI(String id, IModel model) {
        wktGeometryConfigurationPanel = new WktGeometryConfigurationPanel(id, model);
        return wktGeometryConfigurationPanel;
    }

    @Override
    public void configure(FeatureTypeInfo info) {
        strategy.setConfigurationForLayer(
                info.getName(), wktGeometryConfigurationPanel.getWktConfiguration());
        strategy.configure(info);
        info.getMetadata().put("geometryGenerationStrategy", strategy.getName());
    }
}
