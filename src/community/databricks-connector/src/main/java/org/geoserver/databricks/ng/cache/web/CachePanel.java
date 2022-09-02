package org.geoserver.databricks.ng.cache.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.data.DataStore;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;

/** A panel to configure the cache setting */
public class CachePanel extends Panel {

    public static final String CACHING_ENABLED = "cachingEnabled";
    public static final String DATASTORE_NAME = "datastoreName";
    public static final String SCHEMA_NAME = "schemaName";

    DropDownChoice<String> storeChoice;
    DropDownChoice<String> featureTypeChoice;

    private static final Logger LOGGER = Logging.getLogger(CachePanel.class);

    public CachePanel(String id, IModel<?> model) {
        super(id, model);

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
        configs.add(featureTypeChoice);
    }

    private void updateFeatureTypes(AjaxRequestTarget target) {
        featureTypeChoice.setChoices(getFeatureTypes(storeChoice.getModelObject()));
        if (target != null) {
            target.add(featureTypeChoice);
        }
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
        return featureTypes;
    }
}
