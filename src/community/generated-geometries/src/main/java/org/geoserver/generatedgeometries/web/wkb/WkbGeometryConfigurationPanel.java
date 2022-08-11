package org.geoserver.generatedgeometries.web.wkb;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.generatedgeometries.core.GeneratedGeometryConfigurationException;
import org.geoserver.generatedgeometries.core.GeometryGenerationStrategy;
import org.geoserver.generatedgeometries.core.longitudelatitude.LongLatGeometryGenerationStrategy;
import org.geoserver.generatedgeometries.core.longitudelatitude.WkbGeometryGenerationStrategy;
import org.geoserver.generatedgeometries.core.longitudelatitude.WkbGeometryGenerationStrategy.WkbConfiguration;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.CRSPanel;
import org.geoserver.web.wicket.SRSToCRSModel;

public class WkbGeometryConfigurationPanel extends Panel {

    private TextField<String> geometryAttributeNameTextField;

    private String geometryAttributeName;
    private AttributeTypeInfo selectWkbGeometryAttribute;
    private boolean useInMemoryFilter;

    private final transient Supplier<GeoServerApplication> geoServerApplicationSupplier;

    private ChoiceRenderer<AttributeTypeInfo> choiceRenderer =
            new ChoiceRenderer<AttributeTypeInfo>() {
                @Override
                public Object getDisplayValue(AttributeTypeInfo attributeTypeInfo) {
                    return attributeTypeInfo.getName();
                }
            };

    private DropDownChoice<AttributeTypeInfo> wkbAttributeDropDown;

    private CRSPanel declaredCRS;
    private CheckBox inMemoryFilter;

    public WkbGeometryConfigurationPanel(String panelId, IModel model) {
        this(panelId, model, GeoServerApplication::get);
    }

    public WkbGeometryConfigurationPanel(
            String id,
            final IModel model,
            Supplier<GeoServerApplication> geoServerApplicationSupplier) {
        super(id, model);
        this.geoServerApplicationSupplier = geoServerApplicationSupplier;
        initComponents(model);
    }

    private void initComponents(IModel model) {
        List<AttributeTypeInfo> attributes = getAttributes((FeatureTypeInfo) model.getObject());
        populate(model, attributes);
        add(new Label("attrLabel", new ResourceModel("geometryAttributeNameLabel")));
        geometryAttributeNameTextField =
                new TextField<>("geometryAttributeName", forExpression("geometryAttributeName"));
        add(geometryAttributeNameTextField);

        wkbAttributeDropDown =
                new DropDownChoice<>(
                        "wkbAttributesDropDown",
                        forExpression("selectWkbGeometryAttribute"),
                        attributes,
                        choiceRenderer);
        add(wkbAttributeDropDown);

        declaredCRS =
                new CRSPanel("srsPicker", new SRSToCRSModel(new PropertyModel<>(model, "sRS")));
        add(declaredCRS);

        inMemoryFilter = new CheckBox("inMemoryCheckBox", forExpression("useInMemoryFilter"));
        add(inMemoryFilter);

        addAjaxTrigger(
                geometryAttributeNameTextField, wkbAttributeDropDown, declaredCRS, inMemoryFilter);
    }

    private List<AttributeTypeInfo> getAttributes(FeatureTypeInfo fti) {
        Catalog catalog = geoServerApplicationSupplier.get().getCatalog();
        final ResourcePool resourcePool = catalog.getResourcePool();
        try {
            return resourcePool.loadAttributes(fti);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void populate(IModel model, List<AttributeTypeInfo> attributes) {
        // try to populate the UI fields if model belongs to this Strategy
        Optional<String> modelStrategy =
                GeometryGenerationStrategy.getStrategyName(((FeatureTypeInfo) model.getObject()));

        if (!modelStrategy.isPresent()) return;
        if (!modelStrategy.get().equalsIgnoreCase(WkbGeometryGenerationStrategy.NAME)) return;

        // checking wkb attribute | check again
        MetadataMap metadata = ((FeatureTypeInfo) model.getObject()).getMetadata();
        if (metadata.containsKey(WkbGeometryGenerationStrategy.WKB_ATTRIBUTE_NAME)) {
            geometryAttributeName =
                    metadata.get(WkbGeometryGenerationStrategy.GEOMETRY_ATTRIBUTE_NAME).toString();

            if (metadata.containsKey(WkbGeometryGenerationStrategy.WKB_ATTRIBUTE_NAME)) {
                selectWkbGeometryAttribute =
                        getAttributeTypeInfo(
                                attributes,
                                metadata.get(WkbGeometryGenerationStrategy.WKB_ATTRIBUTE_NAME)
                                        .toString());
                Serializable inMemoryObj =
                        metadata.get(LongLatGeometryGenerationStrategy.IN_MEMORY_FILTER);
                useInMemoryFilter =
                        inMemoryObj != null && Boolean.parseBoolean((String) inMemoryObj);
            }
        }
    }

    private boolean isValid() {
        return isNotEmpty(geometryAttributeName)
                && selectWkbGeometryAttribute != null
                && declaredCRS.getCRS() != null;
    }

    WkbConfiguration getWkbConfiguration() {
        if (!isValid()) {
            throw new GeneratedGeometryConfigurationException("invalid configuration");
        }
        return new WkbConfiguration(
                geometryAttributeName,
                selectWkbGeometryAttribute.getName(),
                declaredCRS.getCRS(),
                useInMemoryFilter);
    }

    private AttributeTypeInfo getAttributeTypeInfo(
            List<AttributeTypeInfo> attributes, String find) {

        for (AttributeTypeInfo attribute : attributes) {
            if (attribute.getName().equalsIgnoreCase(find)) return attribute;
        }

        return null;
    }

    private <T> PropertyModel<T> forExpression(String expression) {
        return new PropertyModel<>(this, expression);
    }

    /**
     * Empty behavior factory for triggering inputs' updates via Ajax calls.
     *
     * @return a behavior with empty body
     */
    private Behavior onChangeAjaxTrigger() {
        return new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                // intentionally empty
            }
        };
    }

    private void addAjaxTrigger(Component... components) {
        Stream.of(components).forEach(c -> c.add(onChangeAjaxTrigger()));
    }
}
