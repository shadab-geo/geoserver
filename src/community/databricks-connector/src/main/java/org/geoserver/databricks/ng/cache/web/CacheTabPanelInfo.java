package org.geoserver.databricks.ng.cache.web;

import org.geoserver.catalog.LayerInfo;
import org.geoserver.web.publish.PublishedEditTabPanelInfo;

/** Information about panels plugged into additional tabs on layergroup edit page */
public class CacheTabPanelInfo extends PublishedEditTabPanelInfo<LayerInfo> {

    private static final long serialVersionUID = -388475157541960108L;

    @Override
    public Class<LayerInfo> getPublishedInfoClass() {
        return LayerInfo.class;
    }
}
