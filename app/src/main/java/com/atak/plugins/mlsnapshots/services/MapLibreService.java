
package com.atak.plugins.mlsnapshots.services;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.atakmap.android.maps.MapView;
import org.maplibre.gl.maps.MapLibreMap;
import org.maplibre.gl.maps.Style;
import org.maplibre.gl.style.layers.RasterLayer;
import org.maplibre.gl.style.layers.VectorLayer;
import org.maplibre.gl.style.sources.RasterSource;
import org.maplibre.gl.style.sources.VectorSource;
import org.maplibre.gl.style.sources.TileSet;

public class MapLibreService {

    private final MapView mapView;
    private MapLibreMap map;
    private Style style;

    public MapLibreService(MapView mapView) {
        this.mapView = mapView;
        this.mapView.getMapAsync(this::onMapReady);
    }

    private void onMapReady(MapLibreMap map) {
        this.map = map;
        // Here we configure the map by setting a style.
        // This example loads a style from a URL. This style will be the base map.
        map.setStyle(new Style.Builder().fromUri("https://demotiles.maplibre.org/style.json"), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                // The style is loaded. We can now safely add our own sources and layers.
                MapLibreService.this.style = style;
            }
        });
    }

    public void addRasterTileSource(String sourceId, String url, int tileSize) {
        if (style == null) return; // Style not loaded yet

        TileSet tileSet = new TileSet("2.2.0", url);
        RasterSource rasterSource = new RasterSource(sourceId, tileSet, tileSize);
        style.addSource(rasterSource);

        RasterLayer rasterLayer = new RasterLayer(sourceId + "-layer", sourceId);
        style.addLayer(rasterLayer);
    }

    public void addVectorTileSource(String sourceId, String url) {
        if (style == null) return; // Style not loaded yet

        TileSet tileSet = new TileSet("2.2.0", url);
        VectorSource vectorSource = new VectorSource(sourceId, tileSet);
        style.addSource(vectorSource);

        // Note: You'll need to add a VectorLayer and specify the source-layer, type, and paint properties
        // to actually visualize the vector data. This is just the source.
        // Example:
        // VectorLayer vectorLayer = new VectorLayer(sourceId + "-layer", sourceId);
        // vectorLayer.setSourceLayer("your-source-layer-name");
        // style.addLayer(vectorLayer);
    }

    public void takeSnapshot(MapLibreMap.SnapshotReadyCallback callback) {
        if (map == null) return;
        map.snapshot(callback);
    }
}
