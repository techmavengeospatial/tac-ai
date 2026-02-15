
package com.atak.plugins.mlsnapshots.services;

import org.duckdb.DuckDBConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.maplibre.gl.geometry.LatLngBounds;

public class DuckDBService {

    private Connection conn;

    public DuckDBService(String dbPath) throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSTALL spatial;");
                stmt.execute("LOAD spatial;");
                stmt.execute("INSTALL tributary;");
                stmt.execute("LOAD tributary;");
                stmt.execute("INSTALL radio;");
                stmt.execute("LOAD radio;");
                stmt.execute("INSTALL webdavfs;");
                stmt.execute("LOAD webdavfs;");
                stmt.execute("INSTALL sshfs;");
                stmt.execute("LOAD sshfs;");
                stmt.execute("INSTALL http_request;");
                stmt.execute("LOAD http_request;");
                stmt.execute("INSTALL crawler;");
                stmt.execute("LOAD crawler;");
                stmt.execute("INSTALL cron;");
                stmt.execute("LOAD cron;");
                stmt.execute("INSTALL http_client;");
                stmt.execute("LOAD http_client;");
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new SQLException("Failed to initialize DuckDBService", e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public FeatureData getTableData(String tableName, String orderBy, String filter) throws SQLException {
        StringBuilder query = new StringBuilder("SELECT * FROM ").append(tableName);

        if (filter != null && !filter.trim().isEmpty()) {
            query.append(" WHERE ").append(filter);
        }

        if (orderBy != null && !orderBy.trim().isEmpty()) {
            query.append(" ORDER BY ").append(orderBy);
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query.toString())) {

            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(md.getColumnName(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String columnName : columnNames) {
                    row.put(columnName, rs.getObject(columnName));
                }
                rows.add(row);
            }
            return new FeatureData(columnNames, rows);
        }
    }
    
    public FeatureData getFeaturesByIntersection(String tableName, LatLngBounds bounds) throws SQLException {
        String filter = String.format("ST_Intersects(geom, ST_MakeEnvelope(%f, %f, %f, %f))",
                                    bounds.getLonWest(), bounds.getLatSouth(), 
                                    bounds.getLonEast(), bounds.getLatNorth());
        return getTableData(tableName, null, filter);
    }
    
    public FeatureData getNearestNeighbors(String tableName, double lat, double lon, int k) throws SQLException {
        String query = String.format("SELECT * FROM %s ORDER BY ST_Distance(geom, ST_Point(%f, %f)) LIMIT %d",
                                    tableName, lon, lat, k);
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query.toString())) {

            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(md.getColumnName(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String columnName : columnNames) {
                    row.put(columnName, rs.getObject(columnName));
                }
                rows.add(row);
            }
            return new FeatureData(columnNames, rows);
        }
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
