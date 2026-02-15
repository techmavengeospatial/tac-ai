
package com.atak.plugins.mlsnapshots.services;

import android.graphics.Color;
import androidx.annotation.NonNull;
import com.atakmap.android.maps.MapView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.maplibre.gl.maps.MapLibreMap;
import org.maplibre.gl.maps.Style;
import org.maplibre.gl.style.layers.CircleLayer;
import org.maplibre.gl.style.layers.FillLayer;
import org.maplibre.gl.style.layers.Layer;
import org.maplibre.gl.style.layers.LineLayer;
import org.maplibre.gl.style.layers.PropertyFactory;
import org.maplibre.gl.style.layers.RasterLayer;
import org.maplibre.gl.style.sources.GeoJsonSource;
import org.maplibre.gl.style.sources.RasterSource;
import org.maplibre.gl.style.sources.Source;
import org.maplibre.gl.style.sources.VectorSource;
import org.maplibre.gl.style.sources.TileSet;
import org.maplibre.gl.geometry.LatLngBounds;

import java.util.HashMap;
import java.util.Map;

public class MapLibreService {

    private final MapView mapView;
    private MapLibreMap map;
    private Style style;
    private final Gson gson = new Gson();
    private final Map<String, String> sourceUrls = new HashMap<>();

    public MapLibreService(MapView mapView) {
        this.mapView = mapView;
        this.mapView.getMapAsync(this::onMapReady);
    }

    private void onMapReady(MapLibreMap map) {
        this.map = map;
        map.setStyle(new Style.Builder().fromUri("https://demotiles.maplibre.org/style.json"), style -> this.style = style);
    }

    public void addRasterTileSource(String sourceId, String url) {
        if (style == null) return;
        sourceUrls.put(sourceId, url);
        TileSet tileSet = new TileSet("2.2.0", url);
        RasterSource rasterSource = new RasterSource(sourceId, tileSet, 256);
        style.addSource(rasterSource);
        RasterLayer rasterLayer = new RasterLayer(sourceId + "-layer", sourceId);
        style.addLayer(rasterLayer);
    }

    public void addVectorSource(String sourceId, String url) {
        if (style == null) return;
        GeoJsonSource geoJsonSource = new GeoJsonSource(sourceId, url);
        style.addSource(geoJsonSource);
    }

    public void updateFeatureLayer(String layerId, String sourceId, String styleJson) {
        if (style == null) return;

        if (style.getLayer(layerId) != null) {
            style.removeLayer(layerId);
        }

        Map<String, Object> styleMap = gson.fromJson(styleJson, new TypeToken<Map<String, Object>>() {}.getType());
        String geometryType = (String) styleMap.get("geometryType");

        if (geometryType == null) return;

        switch (geometryType.toLowerCase()) {
            case "point":
                CircleLayer circleLayer = new CircleLayer(layerId, sourceId);
                circleLayer.setProperties(
                        PropertyFactory.circleColor(Color.parseColor((String) styleMap.get("fill"))),
                        PropertyFactory.circleRadius(((Double) styleMap.get("size")).floatValue())
                );
                style.addLayer(circleLayer);
                break;
            case "line":
                LineLayer lineLayer = new LineLayer(layerId, sourceId);
                lineLayer.setProperties(
                        PropertyFactory.lineColor(Color.parseColor((String) styleMap.get("stroke"))),
                        PropertyFactory.lineWidth(((Double) styleMap.get("stroke-width")).floatValue())
                );
                style.addLayer(lineLayer);
                break;
            case "polygon":
                FillLayer fillLayer = new FillLayer(layerId, sourceId);
                fillLayer.setProperties(
                        PropertyFactory.fillColor(Color.parseColor((String) styleMap.get("fill"))),
                        PropertyFactory.fillOpacity(((Double) styleMap.get("fill-opacity")).floatValue())
                );
                style.addLayer(fillLayer);
                break;
        }
    }

    public void addVectorTileSource(String sourceId, String url, String sourceLayer) {
        if (style == null) return;
        sourceUrls.put(sourceId, url);
        TileSet tileSet = new TileSet("2.2.0", url);
        VectorSource vectorSource = new VectorSource(sourceId, tileSet);
        style.addSource(vectorSource);

        CircleLayer circleLayer = new CircleLayer(sourceId + "-circle-layer", sourceId);
        circleLayer.setSourceLayer(sourceLayer);
        circleLayer.setProperties(
                PropertyFactory.circleColor(Color.BLUE),
                PropertyFactory.circleRadius(3f)
        );
        style.addLayer(circleLayer);
    }

    public void updateRasterTileSources(double bearing) {
        if (style == null) return;

        for (Source source : style.getSources()) {
            if (source instanceof RasterSource || source instanceof VectorSource) {
                String sourceId = source.getId();
                String originalUrl = sourceUrls.get(sourceId);
                if (originalUrl != null) {
                    String newUrl = originalUrl + (originalUrl.contains("?") ? "&" : "?") + "bearing=" + bearing;
                    TileSet newTileSet = new TileSet("2.2.0", newUrl);

                    if (source instanceof RasterSource) {
                        style.addSource(new RasterSource(sourceId, newTileSet));
                    } else {
                        style.addSource(new VectorSource(sourceId, newTileSet));
                    }
                }
            }
        }
    }

    public LatLngBounds getMapBounds() {
        if (map == null) return null;
        return map.getProjection().getVisibleRegion().latLngBounds;
    }

    public double getMapZoom() {
        if (map == null) return 0.0;
        return map.getCameraPosition().zoom;
    }

    public void takeSnapshot(MapLibreMap.SnapshotReadyCallback callback) {
        if (map == null) return;
        map.snapshot(callback);
    }
}
