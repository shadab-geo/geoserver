package org.geoserver.databricks.ng.cache.web;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.platform.GeoServerExtensions;

/** A panel to configure the cache setting */
public class CachePanel extends Panel {

    public static final String CACHING_ENABLED = "cachingEnabled";
    public static final String DATASTORE_NAME = "datastoreName";
    public static final String SCHEMA_NAME = "schemaName";

    public CachePanel(String id, IModel<?> model) {
        super(id, model);

        final PropertyModel<Boolean> cacheEnabledModel =
                new PropertyModel<>(model, CACHING_ENABLED);
        if (cacheEnabledModel.getObject() == null) {
            cacheEnabledModel.setObject(false);
        }

        // checkbox to 'enable cache'
        add(new CheckBox(CACHING_ENABLED, cacheEnabledModel));

        List<String> options = getDataStores();
        final PropertyModel<String> storeNameModel = new PropertyModel<>(model, DATASTORE_NAME);
        if (storeNameModel.getObject() == null) {
            storeNameModel.setObject("");
        }

        // dropdown to choose datastore name
        DropDownChoice<String> choice =
                new DropDownChoice<>(DATASTORE_NAME, storeNameModel, options);
        add(choice);

        final PropertyModel<String> schemaNameModel = new PropertyModel<>(model, SCHEMA_NAME);
        if (schemaNameModel.getObject() == null) {
            schemaNameModel.setObject("");
        }

        // text field to specify the schema name
        add(new TextField<>(SCHEMA_NAME, schemaNameModel));
    }

    private List<String> getDataStores() {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        return catalog.getDataStores().stream()
                .map(StoreInfo::getName)
                .collect(Collectors.toList());
    }
}
