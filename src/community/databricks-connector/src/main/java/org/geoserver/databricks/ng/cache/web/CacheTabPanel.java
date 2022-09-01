package org.geoserver.databricks.ng.cache.web;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.web.publish.PublishedEditTabPanel;

/**
 * Plugs into the layer page to do cache setting on a separate tab
 */
public class CacheTabPanel extends PublishedEditTabPanel<LayerInfo> {

    public CacheTabPanel(String id, IModel<LayerInfo> model) {
        super(id, model);

        PropertyModel<ResourceInfo> resource = new PropertyModel<>(model, "resource");
        PropertyModel<MetadataMap> metadata = new PropertyModel<>(resource, "metadata");

        if (metadata.getObject() == null) {
            metadata.setObject(new MetadataMap());
        }

        // cache panel including the checkbox, dropdown and the text field
        CachePanel tablePanel = new CachePanel("cacheTab", metadata);
        tablePanel.setOutputMarkupId(true);
        add(tablePanel);
    }
}
