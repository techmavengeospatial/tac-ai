
package com.atak.plugins.mlsnapshots.services;

import android.graphics.Color;
import androidx.annotation.NonNull;
import com.atakmap.android.maps.MapView;
import org.maplibre.gl.maps.MapLibreMap;
import org.maplibre.gl.maps.Style;
import org.maplibre.gl.style.layers.CircleLayer;
import org.maplibre.gl.style.layers.RasterLayer;
import org.maplibre.gl.style.layers.VectorLayer;
import org.maplibre.gl.style.sources.GeoJsonSource;
import org.maplibre.gl.style.sources.RasterSource;
import org.maplibre.gl.style.sources.VectorSource;
import org.maplibre.gl.style.sources.TileSet;

import static org.maplibre.gl.style.layers.PropertyFactory.circleColor;
import static org.maplibre.gl.style.layers.PropertyFactory.circleRadius;

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
        map.setStyle(new Style.Builder().fromUri("https://demotiles.maplibre.org/style.json"), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                MapLibreService.this.style = style;
            }
        });
    }

    public void addRasterTileSource(String sourceId, String url) {
        if (style == null) return; 

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

    public void addVectorLayer(String layerId, String sourceId) {
        if (style == null) return;

        CircleLayer circleLayer = new CircleLayer(layerId, sourceId);
        circleLayer.setProperties(
                circleColor(Color.RED),
                circleRadius(5f)
        );
        style.addLayer(circleLayer);
    }

    public void addVectorTileSource(String sourceId, String url, String sourceLayer) {
        if (style == null) return;

        TileSet tileSet = new TileSet("2.2.0", url);
        VectorSource vectorSource = new VectorSource(sourceId, tileSet);
        style.addSource(vectorSource);

        // The sourceLayer is the name of the layer within the vector tile
        VectorLayer vectorLayer = new VectorLayer(sourceId + "-layer", sourceId);
        vectorLayer.setSourceLayer(sourceLayer);

        // Add styling based on geometry type
        // This is a simple example. You would likely need more complex styling logic.
        // For now, we'll just add a circle layer, assuming point data.
        CircleLayer circleLayer = new CircleLayer(sourceId + "-circle-layer", sourceId);
        circleLayer.setSourceLayer(sourceLayer);
        circleLayer.setProperties(
                circleColor(Color.BLUE),
                circleRadius(3f)
        );
        style.addLayer(circleLayer);
    }

    public void takeSnapshot(MapLibreMap.SnapshotReadyCallback callback) {
        if (map == null) return;
        map.snapshot(callback);
    }
}
