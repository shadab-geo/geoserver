package org.geoserver.generatedgeometries.web.wkb;

import java.io.Serializable;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.generatedgeometries.strategy.wkb.WkbGeometryGenerationStrategy;
import org.geoserver.generatedgeometries.web.GeometryGenerationStrategyUIGenerator;

public class WkbStrategyUIGenerator implements GeometryGenerationStrategyUIGenerator, Serializable {

    private static final long serialVersionUID = 1L;

    private WkbGeometryConfigurationPanel wkbGeometryConfigurationPanel;

    private final WkbGeometryGenerationStrategy strategy;

    public WkbStrategyUIGenerator(WkbGeometryGenerationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return strategy.getName();
    }

    @Override
    public Component createUI(String id, IModel model) {
        wkbGeometryConfigurationPanel = new WkbGeometryConfigurationPanel(id, model);
        return wkbGeometryConfigurationPanel;
    }

    @Override
    public void configure(FeatureTypeInfo info) {
        strategy.setConfigurationForLayer(
                info.getName(), wkbGeometryConfigurationPanel.getWkbConfiguration());
        strategy.configure(info);
        info.getMetadata().put("geometryGenerationStrategy", strategy.getName());
    }
}
