
package com.atak.plugins.mlsnapshots.services;

import com.atak.coremap.log.Log;
import com.atak.plugins.mlsnapshots.PluginExecutor;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class Google3DTilesService {
    public static final String TAG = "Google3DTilesService";
    private static final String TILES_BASE_URL = "https://tile.googleapis.com/v1/3dtiles/";

    private final GeoPackageService geoPackageService;
    private final Executor executor = PluginExecutor.getExecutor();
    private final Gson gson = new Gson();
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private MathTransform ecefToWgs84Transform;

    public interface ProgressListener {
        void onProgress(String message);
        void onComplete(boolean success, String finalMessage);
    }

    public Google3DTilesService(GeoPackageService geoPackageService) {
        this.geoPackageService = geoPackageService;
        try {
            CoordinateReferenceSystem ecef = CRS.decode("EPSG:4978"); // ECEF
            CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326"); // WGS84
            this.ecefToWgs84Transform = CRS.findMathTransform(ecef, wgs84, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize coordinate transforms", e);
        }
    }

    public void startImport(String apiKey, Envelope aoi, ProgressListener listener) {
        if (ecefToWgs84Transform == null) {
            listener.onComplete(false, "CRS transform not initialized.");
            return;
        }

        executor.execute(() -> {
            try {
                listener.onProgress("Starting import...");
                DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, createFeatureType());

                String rootUrl = TILES_BASE_URL + "root.json?key=" + apiKey;
                processTile(rootUrl, apiKey, aoi, featureCollection, listener);

                if (featureCollection.isEmpty()) {
                    listener.onComplete(true, "Import complete. No new building footprints found in the specified area.");
                    return;
                }

                listener.onProgress("Saving " + featureCollection.size() + " building footprints to GeoPackage...");
                boolean success = geoPackageService.addFeatures("google_buildings", featureCollection);

                String finalMessage = success ? "Successfully saved " + featureCollection.size() + " features." : "Failed to save features to GeoPackage.";
                listener.onComplete(success, finalMessage);

            } catch (Exception e) {
                Log.e(TAG, "Error during 3D tiles import", e);
                listener.onComplete(false, "Error: " + e.getMessage());
            }
        });
    }

    private void processTile(String tileUrl, String apiKey, Geometry aoi, DefaultFeatureCollection featureCollection, ProgressListener listener) throws Exception {
        JsonObject tile = fetchJson(tileUrl);
        if (tile == null) return;

        JsonObject root = tile.getAsJsonObject("root");
        if (root == null) return;

        Polygon footprint = getFootprint(root);
        if (footprint != null) {
            if (aoi.intersects(footprint)) {
                listener.onProgress("Found intersecting tile. Adding to collection.");
                featureCollection.add(SimpleFeatureBuilder.build(featureCollection.getSchema(), new Object[]{footprint, "Google 3D Tiles"}, null));
            } else {
                // If the root doesn't intersect, no need to check children
                return;
            }
        }

        // Recursively process children
        if (root.has("children")) {
            JsonArray children = root.getAsJsonArray("children");
            for (JsonElement childElement : children) {
                JsonObject childObject = childElement.getAsJsonObject();
                if (getFootprint(childObject) != null && getFootprint(childObject).intersects(aoi)) {
                    if (childObject.has("content") && childObject.getAsJsonObject("content").has("uri")) {
                        String childUri = childObject.getAsJsonObject("content").get("uri").getAsString();
                        String childUrl = TILES_BASE_URL + childUri + "?key=" + apiKey;
                        // This is where we would typically load a b3dm file and parse it.
                        // For footprints, we can just use the bounding box of the child.
                        Polygon childFootprint = getFootprint(childObject);
                        if(childFootprint != null) {
                             featureCollection.add(SimpleFeatureBuilder.build(featureCollection.getSchema(), new Object[]{childFootprint, "Google 3D Tiles"}, null));
                        }
                    }
                     // In a full implementation, we'd recursively call processTile for subtree files here
                }
            }
        }
    }

    private Polygon getFootprint(JsonObject tileNode) throws Exception {
        if (!tileNode.has("boundingVolume") || !tileNode.getAsJsonObject("boundingVolume").has("box")) {
            return null;
        }
        JsonArray boxArray = tileNode.getAsJsonObject("boundingVolume").getAsJsonArray("box");

        // Extract OBB parameters
        double cx = boxArray.get(0).getAsDouble();
        double cy = boxArray.get(1).getAsDouble();
        double cz = boxArray.get(2).getAsDouble();

        double ux_x = boxArray.get(3).getAsDouble();
        double ux_y = boxArray.get(4).getAsDouble();
        double ux_z = boxArray.get(5).getAsDouble();

        double uy_x = boxArray.get(6).getAsDouble();
        double uy_y = boxArray.get(7).getAsDouble();
        double uy_z = boxArray.get(8).getAsDouble();

        double uz_x = boxArray.get(9).getAsDouble();
        double uz_y = boxArray.get(10).getAsDouble();
        double uz_z = boxArray.get(11).getAsDouble();

        // Calculate 8 corner points in ECEF
        List<double[]> ecefCorners = new ArrayList<>();
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                for (int k = -1; k <= 1; k += 2) {
                    double x = cx + i * ux_x + j * uy_x + k * uz_x;
                    double y = cy + i * ux_y + j * uy_y + k * uz_y;
                    double z = cz + i * ux_z + j * uy_z + k * uz_z;
                    ecefCorners.add(new double[]{x, y, z});
                }
            }
        }

        // Transform corners to WGS84
        List<Coordinate> wgs84Corners = new ArrayList<>();
        for (double[] ecefCorner : ecefCorners) {
            double[] wgs84Corner = new double[3];
            JTS.transform(ecefCorner, 0, wgs84Corner, 0, ecefToWgs84Transform, 1);
            wgs84Corners.add(new Coordinate(wgs84Corner[0], wgs84Corner[1]));
        }

        // Create convex hull of the 2D projected points to form the footprint
        Geometry convexHull = geometryFactory.createMultiPointFromCoords(wgs84Corners.toArray(new Coordinate[0])).convexHull();
        if (convexHull instanceof Polygon) {
            return (Polygon) convexHull;
        }
        return null;
    }

    private JsonObject fetchJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "Failed to fetch JSON from " + urlString + ". Response code: " + conn.getResponseCode());
                return null;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            return gson.fromJson(in, JsonObject.class);
        } catch (Exception e) {
            Log.e(TAG, "Exception while fetching JSON from " + urlString, e);
            return null;
        }
    }

    private SimpleFeatureType createFeatureType() {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("GoogleBuildingFootprint");
        builder.setCRS(null); // Let GeoPackage handle CRS
        builder.add("the_geom", Polygon.class);
        builder.add("source", String.class);
        return builder.buildFeatureType();
    }
}
