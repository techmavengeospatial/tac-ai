package com.example.atak.services;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RestController {

    private final DuckDBService duckDBService;

    public RestController(DuckDBService duckDBService) {
        this.duckDBService = duckDBService;
    }

    @GetMapping("/")
    public String index() {
        return "Greetings from the ATAK AI Plugin!";
    }

    @GetMapping("/load-data")
    public String loadData() {
        duckDBService.execute("CREATE OR REPLACE TABLE features (id INTEGER, name VARCHAR, latitude DOUBLE, longitude DOUBLE)");
        duckDBService.execute("INSERT INTO features VALUES (1, 'Point 1', 40.0, -105.0)");
        duckDBService.execute("INSERT INTO features VALUES (2, 'Point 2', 41.0, -106.0)");
        return "Data loaded successfully!";
    }

    @GetMapping("/collections")
    public Map<String, Object> getCollections() {
        Map<String, Object> collection = new HashMap<>();
        collection.put("id", "features");
        collection.put("title", "Sample Features");
        collection.put("description", "A collection of sample geospatial features.");

        Map<String, Object> extent = new HashMap<>();
        Map<String, Object> spatial = new HashMap<>();
        spatial.put("bbox", Collections.singletonList(Arrays.asList(-180.0, -90.0, 180.0, 90.0)));
        extent.put("spatial", spatial);
        collection.put("extent", extent);

        Map<String, Object> selfLink = new HashMap<>();
        selfLink.put("href", "/collections/features");
        selfLink.put("rel", "self");
        selfLink.put("type", "application/json");
        selfLink.put("title", "This document");

        Map<String, Object> itemsLink = new HashMap<>();
        itemsLink.put("href", "/collections/features/items");
        itemsLink.put("rel", "items");
        itemsLink.put("type", "application/geo+json");
        itemsLink.put("title", "The features in this collection");

        collection.put("links", Arrays.asList(selfLink, itemsLink));

        Map<String, Object> response = new HashMap<>();
        response.put("collections", Collections.singletonList(collection));

        Map<String, Object> selfLinkRoot = new HashMap<>();
        selfLinkRoot.put("href", "/collections");
        selfLinkRoot.put("rel", "self");
        selfLinkRoot.put("type", "application/json");

        response.put("links", Collections.singletonList(selfLinkRoot));

        return response;
    }

    @GetMapping("/collections/{collectionId}/items")
    public Map<String, Object> getCollectionItems(@PathVariable String collectionId, @RequestParam(required = false) String bbox) {
        StringBuilder sql = new StringBuilder("SELECT id, name, latitude, longitude FROM features");

        if (bbox != null && !bbox.isEmpty()) {
            String[] parts = bbox.split(",");
            if (parts.length == 4) {
                sql.append(" WHERE longitude >= ").append(parts[0])
                   .append(" AND latitude >= ").append(parts[1])
                   .append(" AND longitude <= ").append(parts[2])
                   .append(" AND latitude <= ").append(parts[3]);
            }
        }

        return duckDBService.query(sql.toString(), rs -> {
            List<Map<String, Object>> features = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> feature = new HashMap<>();
                feature.put("type", "Feature");

                Map<String, Object> geometry = new HashMap<>();
                geometry.put("type", "Point");
                geometry.put("coordinates", Arrays.asList(rs.getDouble("longitude"), rs.getDouble("latitude")));
                feature.put("geometry", geometry);

                Map<String, Object> properties = new HashMap<>();
                properties.put("id", rs.getInt("id"));
                properties.put("name", rs.getString("name"));
                feature.put("properties", properties);

                features.add(feature);
            }

            Map<String, Object> featureCollection = new HashMap<>();
            featureCollection.put("type", "FeatureCollection");
            featureCollection.put("features", features);

            return featureCollection;
        });
    }

    @PostMapping("/poll")
    public String poll(@RequestBody Map<String, Object> payload) {
        String url = (String) payload.get("url");
        int interval = (int) payload.get("interval");
        duckDBService.startPolling(url, interval);
        return "Polling started for " + url;
    }

    @GetMapping("/crawl")
    public String crawl(@RequestParam String url) {
        String sql = String.format("CREATE OR REPLACE TABLE crawled_data AS SELECT * FROM crawl('%s')", url);
        duckDBService.execute(sql);
        return "Crawling " + url + " and storing results in crawled_data";
    }

    @GetMapping("/listen")
    public String listen(@RequestParam String url) {
        String sql = String.format("CREATE OR REPLACE TABLE websocket_data AS SELECT * FROM radio_listen('%s')", url);
        duckDBService.execute(sql);
        return "Listening to " + url + " and storing results in websocket_data";
    }

    @PostMapping("/send")
    public String send(@RequestBody Map<String, Object> payload) {
        String url = (String) payload.get("url");
        String message = (String) payload.get("message");
        String sql = String.format("SELECT radio_send('%s', '%s')", url, message);
        duckDBService.execute(sql);
        return "Sent message to " + url;
    }
}
