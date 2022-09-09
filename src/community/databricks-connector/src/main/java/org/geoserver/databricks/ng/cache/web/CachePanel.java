package org.geoserver.databricks.ng.cache.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.DataStore;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

/** A panel to configure the cache setting */
public class CachePanel extends Panel {

    public static final String CACHING_ENABLED = "cachingEnabled";
    public static final String DATASTORE_NAME = "datastoreName";
    public static final String SCHEMA_NAME = "schemaName";

    DropDownChoice<String> storeChoice;
    DropDownChoice<String> featureTypeChoice;

    Label warningLabel;
    Model<String> warningModel;

    private String layerName;

    private static final Logger LOGGER = Logging.getLogger(CachePanel.class);

    public CachePanel(String id, IModel<?> model, String layerName) {
        super(id, model);

        this.layerName = layerName;
        final WebMarkupContainer configsContainer = new WebMarkupContainer("configContainer");
        configsContainer.setOutputMarkupId(true);
        add(configsContainer);
        final WebMarkupContainer configs = new WebMarkupContainer("configs");
        configs.setOutputMarkupId(true);
        configs.setVisible(true);
        configsContainer.add(configs);

        final PropertyModel<Boolean> cacheEnabledModel =
                new PropertyModel<>(model, CACHING_ENABLED);
        if (cacheEnabledModel.getObject() == null) {
            cacheEnabledModel.setObject(false);
        }

        // checkbox to 'enable cache'
        CheckBox enabled = new CheckBox(CACHING_ENABLED, cacheEnabledModel);
        add(enabled);
        enabled.add(
                new AjaxFormComponentUpdatingBehavior("click") {

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        Boolean visible = enabled.getModelObject();
                        configs.setVisible(visible);
                        target.add(configsContainer);
                    }
                });

        List<String> dataStores = getDataStores();
        final PropertyModel<String> storeNameModel = new PropertyModel<>(model, DATASTORE_NAME);
        if (storeNameModel.getObject() == null) {
            storeNameModel.setObject("");
        }

        // dropdown to choose datastore name
        storeChoice = new DropDownChoice<>(DATASTORE_NAME, storeNameModel, dataStores);
        add(storeChoice);
        storeChoice.add(
                new AjaxFormComponentUpdatingBehavior("change") {

                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        updateFeatureTypes(target);
                        warningLabel.setVisible(false);
                        target.add(warningLabel);
                    }
                });
        configs.add(storeChoice);

        List<String> featureTypes = new ArrayList<>();
        final PropertyModel<String> schemaNameModel = new PropertyModel<>(model, SCHEMA_NAME);
        if (schemaNameModel.getObject() == null) {
            schemaNameModel.setObject("");
        } else {
            featureTypes = getFeatureTypes(storeChoice.getModelObject());
        }

        // dropdown to choose feature type name
        featureTypeChoice = new DropDownChoice<>(SCHEMA_NAME, schemaNameModel, featureTypes);
        featureTypeChoice.setOutputMarkupId(true);
        add(featureTypeChoice);
        featureTypeChoice.add(
                new AjaxFormComponentUpdatingBehavior("change") {
                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        updateWarningMessage(target);
                    }
                });
        configs.add(featureTypeChoice);

        warningModel = Model.of("");

        // warning label to indicate the attribute differences
        warningLabel = new Label("warning", warningModel);
        warningLabel.setOutputMarkupPlaceholderTag(true);
        warningLabel.setVisible(false);
        configs.add(warningLabel);

    }

    private void updateFeatureTypes(AjaxRequestTarget target) {
        featureTypeChoice.setChoices(getFeatureTypes(storeChoice.getModelObject()));
        if (target != null) {
            target.add(featureTypeChoice);
        }
    }

    private void updateWarningMessage(AjaxRequestTarget target) {
        if (target != null) {
            String warningMsg = getAttributesDiff(featureTypeChoice.getModelObject());
            warningModel.setObject(warningMsg);
            warningLabel.setVisible(!warningMsg.isEmpty());
            target.add(warningLabel);
        }
    }

    private String getAttributesDiff(String featureType) {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");

        FeatureTypeInfo cacheFeatureTypeInfo = catalog.getFeatureTypeByName(featureType);
        if (cacheFeatureTypeInfo == null)
            return "layer " + featureType + " is not published";

        Set<String> cacheFeatureTypeAttributes;
        try {
            cacheFeatureTypeAttributes = cacheFeatureTypeInfo.getFeatureType().getDescriptors()
                    .stream().map(PropertyDescriptor::getName).map(Name::getLocalPart)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return "Failed to read " + featureType + " layer schema with exception: " + e.getMessage();
        }

        FeatureTypeInfo layerFeatureTypeInfo = catalog.getFeatureTypeByName(layerName);
        Set<String> layerAttributes;
        try {
            layerAttributes = layerFeatureTypeInfo.getFeatureType().getDescriptors()
                    .stream().map(PropertyDescriptor::getName).map(Name::getLocalPart)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return "Failed to read " + layerName + " layer schema with exception: " + e.getMessage();
        }
        layerAttributes.removeAll(cacheFeatureTypeAttributes);
        return layerAttributes.isEmpty() ? "" : "Missing attributes: " + layerAttributes;
    }

    private List<String> getDataStores() {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        return catalog.getDataStores().stream()
                .map(StoreInfo::getName)
                .collect(Collectors.toList());
    }

    private List<String> getFeatureTypes(String storeName) {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        DataStoreInfo dataStoreInfo;
        List<String> featureTypes = new ArrayList<>();
        try {
            dataStoreInfo = catalog.getDataStoreByName(storeName);
            if (dataStoreInfo != null) {
                DataStore ds = (DataStore) dataStoreInfo.getDataStore(null);
                featureTypes =
                        ds.getNames().stream().map(Name::getLocalPart).collect(Collectors.toList());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to load feature types for store " + storeName, e);
        }
        featureTypes.add(0, "None");
        return featureTypes;
    }
}
