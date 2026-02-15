
package com.atak.plugins.mlsnapshots.servers;

import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureResultSet;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xml.Encoder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class OgcApiServer {

    private final Javalin app;
    private final DuckDBService duckDBService;
    private final GeoPackageService geoPackageService;
    private final Gson gson = new Gson();

    public OgcApiServer(int port, DuckDBService duckDBService, GeoPackageService geoPackageService) {
        this.duckDBService = duckDBService;
        this.geoPackageService = geoPackageService;
        this.app = Javalin.create().start(port);
        setupRoutes();
    }

    private void setupRoutes() {
        app.get("/collections/{collectionId}/items", this::getFeatures);
        app.get("/collections/{collectionId}/items.kml", this::getFeaturesAsKml);
        app.get("/collections/{collectionId}/items.shp", this::getFeaturesAsShapefile);
        app.get("/geopackage/{table}/{z}/{x}/{y}", this::getGeoPackageTile);
        app.get("/geopackage/features/{table}", this::getGeoPackageFeatures);
        app.get("/geopackage/vectortiles/{table}/{z}/{x}/{y}", this::getGeoPackageVectorTile);
    }

    private void getGeoPackageVectorTile(Context ctx) {
        String table = ctx.pathParam("table");
        long z = Long.parseLong(ctx.pathParam("z"));
        long x = Long.parseLong(ctx.pathParam("x"));
        long y = Long.parseLong(ctx.pathParam("y"));

        if (geoPackageService == null || !geoPackageService.isGeoPackageOpen()) {
            ctx.status(503).result("GeoPackage service not available or no GeoPackage is open.");
            return;
        }

        try {
            byte[] tileData = geoPackageService.getTile(table, z, x, y);
            if (tileData != null) {
                ctx.contentType("application/vnd.mapbox-vector-tile");
                ctx.result(tileData);
            } else {
                ctx.status(404).result("Vector tile not found");
            }
        } catch (Exception e) {
            ctx.status(500).result("Error retrieving vector tile: " + e.getMessage());
        }
    }

    private void getGeoPackageFeatures(Context ctx) {
        String table = ctx.pathParam("table");

        if (geoPackageService == null || !geoPackageService.isGeoPackageOpen()) {
            ctx.status(503).result("GeoPackage service not available or no GeoPackage is open.");
            return;
        }

        try {
            SimpleFeatureCollection featureCollection = geoPackageService.getFeatures(table);
            if (featureCollection == null) {
                ctx.status(404).result("Feature table not found or failed to read features.");
                return;
            }

            FeatureJSON featureJSON = new FeatureJSON();
            StringWriter writer = new StringWriter();
            featureJSON.writeFeatureCollection(featureCollection, writer);

            ctx.contentType("application/geo+json");
            ctx.result(writer.toString());

        } catch (Exception e) {
            ctx.status(500).result("Error converting features to GeoJSON: " + e.getMessage());
        }
    }


    private void getGeoPackageTile(Context ctx) {
        String table = ctx.pathParam("table");
        long z = Long.parseLong(ctx.pathParam("z"));
        long x = Long.parseLong(ctx.pathParam("x"));
        long y = Long.parseLong(ctx.pathParam("y"));

        if (geoPackageService == null || !geoPackageService.isGeoPackageOpen()) {
            ctx.status(503).result("GeoPackage service not available or no GeoPackage is open.");
            return;
        }

        try {
            byte[] tileData = geoPackageService.getTile(table, z, x, y);
            if (tileData != null) {
                ctx.contentType("image/png");
                ctx.result(tileData);
            } else {
                ctx.status(404).result("Tile not found");
            }
        } catch (Exception e) {
            ctx.status(500).result("Error retrieving tile: " + e.getMessage());
        }
    }

    private void getFeatures(Context ctx) {
        String collectionId = ctx.pathParam("collectionId");
        try (Statement stmt = duckDBService.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT *, ST_AsText(geom) as wkt FROM " + collectionId);
            List<Map<String, Object>> features = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> feature = new HashMap<>();
                feature.put("type", "Feature");
                Map<String, Object> geometry = new HashMap<>();
                geometry.put("type", "Point"); // Assuming Point for now
                geometry.put("coordinates", rs.getString("wkt"));
                feature.put("geometry", geometry);
                Map<String, Object> properties = new HashMap<>();
                // Add other properties from the result set
                feature.put("properties", properties);
                features.add(feature);
            }
            Map<String, Object> featureCollection = new HashMap<>();
            featureCollection.put("type", "FeatureCollection");
            featureCollection.put("features", features);
            ctx.json(featureCollection);
        } catch (SQLException e) {
            ctx.status(500).result("Error executing query: " + e.getMessage());
        }
    }

    private void getFeaturesAsKml(Context ctx) {
        String collectionId = ctx.pathParam("collectionId");
        try {
            SimpleFeatureCollection featureCollection = getDuckDBFeatureCollection(collectionId);
            Encoder encoder = new Encoder(new KMLConfiguration());
            encoder.setIndenting(true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            encoder.encode(featureCollection, KML.kml, bos);
            ctx.contentType("application/vnd.google-earth.kml+xml");
            ctx.result(bos.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            ctx.status(500).result("Error converting to KML: " + e.getMessage());
        }
    }

    private void getFeaturesAsShapefile(Context ctx) {
        String collectionId = ctx.pathParam("collectionId");
        try {
            SimpleFeatureCollection featureCollection = getDuckDBFeatureCollection(collectionId);
            ShapefileDataStore newDataStore = new ShapefileDataStore(new java.io.File("temp.shp").toURI().toURL());
            newDataStore.createSchema(featureCollection.getSchema());
            Transaction transaction = new DefaultTransaction("create");
            SimpleFeatureStore featureStore = (SimpleFeatureStore) newDataStore.getFeatureSource();
            featureStore.setTransaction(transaction);
            featureStore.addFeatures(featureCollection);
            transaction.commit();
            transaction.close();

            // For simplicity, this is not sending the file, but a real implementation would
            ctx.result("Shapefile created at temp.shp");
        } catch (Exception e) {
            ctx.status(500).result("Error converting to Shapefile: " + e.getMessage());
        }
    }

    private SimpleFeatureCollection getDuckDBFeatureCollection(String collectionId) throws SQLException, IOException {
        try (Statement stmt = duckDBService.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT *, ST_AsText(geom) as wkt FROM " + collectionId);

            SimpleFeatureType featureType = DataUtilities.createType(collectionId, "geom:Point,name:String");
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
            List<SimpleFeature> features = new ArrayList<>();

            while (rs.next()) {
                featureBuilder.add(rs.getString("wkt"));
                featureBuilder.add(rs.getString("name")); // Assuming a 'name' column
                features.add(featureBuilder.buildFeature(null));
            }
            return new ListFeatureCollection(featureType, features);
        }
    }

    public void stop() {
        app.stop();
    }
}
