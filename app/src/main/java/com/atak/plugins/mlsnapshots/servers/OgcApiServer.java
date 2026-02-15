
package com.atak.plugins.mlsnapshots.servers;

import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.wfs.v2_0.WFS;
import org.geotools.xml.Encoder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class OgcApiServer {

    private final Javalin app;
    private final DuckDBService duckDBService;
    private final Gson gson = new Gson();

    public OgcApiServer(int port, DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
        this.app = Javalin.create().start(port);
        setupRoutes();
    }

    private void setupRoutes() {
        app.get("/collections/{collectionId}/items", this::getFeatures);
        app.get("/collections/{collectionId}/items.kml", this::getFeaturesAsKml);
        app.get("/collections/{collectionId}/items.shp", this::getFeaturesAsShapefile);
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
            SimpleFeatureCollection featureCollection = getFeatureCollection(collectionId);
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
            SimpleFeatureCollection featureCollection = getFeatureCollection(collectionId);
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

    private SimpleFeatureCollection getFeatureCollection(String collectionId) throws SQLException, IOException {
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

