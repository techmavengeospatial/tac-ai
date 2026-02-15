
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import com.atak.plugins.mlsnapshots.helpers.TileRenderer;
import com.google.gson.Gson;
import com.vividsolutions.jts.geom.Envelope;
import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.ExtensionManager;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureResultSet;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.proj.Projection;
import mil.nga.proj.ProjectionConstants;
import mil.nga.proj.ProjectionFactory;
import mil.nga.proj.ProjectionTransform;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeoPackageService {

    private static final String TAG = "GeoPackageService";
    private final Context context;
    private GeoPackage geoPackage;
    private final Gson gson = new Gson();

    public GeoPackageService(Context context) {
        this.context = context;
    }

    public boolean openGeoPackage(String path) {
        File geoPackageFile = new File(path);
        if (!geoPackageFile.exists()) {
            return false;
        }
        geoPackage = GeoPackageManager.open(geoPackageFile);
        return geoPackage != null;
    }

    public boolean isGeoPackageOpen() {
        return geoPackage != null;
    }

    public void close() {
        if (geoPackage != null) {
            geoPackage.close();
        }
    }

    public List<String> getTileTables() {
        return geoPackage.getTileTables();
    }

    public List<String> getFeatureTables() {
        return geoPackage.getFeatureTables();
    }

    public List<String> getVectorTileTables() {
        List<String> vectorTileTables = new ArrayList<>();
        ExtensionManager extensionManager = geoPackage.getExtensionManager();
        String vectorTilesExtension = "gpkg_mapbox_vector_tiles";
        for (String tileTable : getTileTables()) {
            if (extensionManager.has(vectorTilesExtension, tileTable, null)) {
                vectorTileTables.add(tileTable);
            }
        }
        return vectorTileTables;
    }

    public byte[] getTile(String table, long z, long x, long y) {
        TileDao tileDao = geoPackage.getTileDao(table);
        if (tileDao == null) return null;
        return tileDao.queryForTileBytes((int) x, (int) y, (int) z);
    }

    public byte[] getFeatureTile(String tableName, long z, long x, long y, String styleJson) {
        FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
        if (featureDao == null) {
            return null;
        }

        try {
            BoundingBox tileBBox = new BoundingBox(
                    TileRenderer.tile2lon((int) x, (int) z),
                    TileRenderer.tile2lat((int) y + 1, (int) z),
                    TileRenderer.tile2lon((int) x + 1, (int) z),
                    TileRenderer.tile2lat((int) y, (int) z)
            );

            Projection projection = ProjectionFactory.getProjection(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);
            ProjectionTransform transform = projection.getTransformation(featureDao.getProjection());
            BoundingBox transformedTileBBox = transform.transform(tileBBox);

            SimpleFeatureCollection featureCollection = getFeaturesInBBox(transformedTileBBox, featureDao);

            MapContent map = new MapContent();
            map.setTitle(tableName);

            Style style = createStyle(tableName, styleJson);
            map.addLayer(new FeatureLayer(featureCollection, style));

            GTRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);

            Bitmap image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            android.graphics.Rect screenRect = new android.graphics.Rect(0, 0, 256, 256);

            ReferencedEnvelope mapBounds = new ReferencedEnvelope(
                    tileBBox.getMinLongitude(), tileBBox.getMaxLongitude(),
                    tileBBox.getMinLatitude(), tileBBox.getMaxLatitude(),
                    CRS.decode("EPSG:4326")
            );

            renderer.paint(canvas, screenRect, mapBounds);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 90, bos);
            return bos.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Failed to render feature tile for table: " + tableName, e);
            return null;
        }
    }

    private SimpleFeatureCollection getFeaturesInBBox(BoundingBox bbox, FeatureDao featureDao) {
        FeatureResultSet resultSet = featureDao.queryForBoundingBox(bbox);
        return resultSetToFeatureCollection(resultSet, featureDao);
    }

    private SimpleFeatureCollection resultSetToFeatureCollection(FeatureResultSet resultSet, FeatureDao featureDao) {
        try {
            SimpleFeatureType featureType = createFeatureType(featureDao);
            List<SimpleFeature> features = new ArrayList<>();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);

            while (resultSet.moveToNext()) {
                FeatureRow row = resultSet.getRow();
                GeoPackageGeometryData geomData = row.getGeometry();
                if (geomData != null && !geomData.isEmpty()) {
                    com.vividsolutions.jts.geom.Geometry geometry = geomData.getGeometry();
                    featureBuilder.add(geometry);
                    for (String columnName : featureType.getDescriptor().getIdentifiers().stream().map(Object::toString).collect(java.util.stream.Collectors.toList())) {
                        if (row.hasColumn(columnName)) {
                            featureBuilder.add(row.getValue(columnName));
                        }
                    }
                    features.add(featureBuilder.buildFeature(row.getId() + ""));
                }
            }
            return new ListFeatureCollection(featureType, features);
        } finally {
            resultSet.close();
        }
    }

    private SimpleFeatureType createFeatureType(FeatureDao featureDao) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(featureDao.getTableName());
        try {
            CoordinateReferenceSystem crs = CRS.decode("EPSG:" + featureDao.getSrs().getOrganizationCoordsysId());
            builder.setCRS(crs);
        } catch (Exception e) {
            Log.w(TAG, "Could not set CRS for feature type");
        }
        builder.add(featureDao.getGeometryColumnName(), com.vividsolutions.jts.geom.Geometry.class);
        featureDao.getColumns().stream()
                .filter(c -> !c.isGeometry())
                .forEach(c -> builder.add(c.getName(), c.getDataType().getClassType()));
        return builder.buildFeatureType();
    }

    private Style createStyle(String tableName, String styleJson) {
        if (styleJson != null && !styleJson.isEmpty()) {
            try {
                // This is a simplified approach. A real implementation would need a more robust
                // way to parse and apply styles. This assumes a simple map of style properties.
                Map<String, String> styleProps = gson.fromJson(styleJson, Map.class);
                return createDynamicStyle(styleProps);
            } catch (Exception e) {
                Log.e(TAG, "Invalid style JSON: " + styleJson, e);
            }
        }
        // Return a default style if no JSON is provided or if it's invalid
        return createDefaultStyle();
    }

    private Style createDefaultStyle() {
        StyleFactory sf = new StyleFactoryImpl();
        Symbolizer symbolizer = sf.createPolygonSymbolizer(sf.createFill(sf.createGraphicFill(null, sf.createMark("square"), sf.createFill("#FF0000"))), sf.createStroke("#000000", 1.0));
        Rule rule = sf.createRule();
        rule.symbolizers().add(symbolizer);
        FeatureTypeStyle fts = sf.createFeatureTypeStyle(new Rule[]{rule});
        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }

    private Style createDynamicStyle(Map<String, String> styleProps) {
        StyleFactory sf = new StyleFactoryImpl();
        StyleBuilder sb = new StyleBuilder();

        String geometryType = styleProps.getOrDefault("geometryType", "polygon");

        switch (geometryType) {
            case "point":
                Mark mark = sb.createMark(styleProps.getOrDefault("mark", "circle"), styleProps.getOrDefault("fill", "#FF0000"), styleProps.getOrDefault("stroke", "#000000"), Double.parseDouble(styleProps.getOrDefault("size", "6")));
                Graphic graphic = sb.createGraphic(null, mark, null);
                PointSymbolizer pointSymbolizer = sb.createPointSymbolizer(graphic);
                return sb.createStyle(pointSymbolizer);
            case "line":
                Stroke stroke = sb.createStroke(styleProps.getOrDefault("stroke", "#000000"), Double.parseDouble(styleProps.getOrDefault("stroke-width", "2")));
                LineSymbolizer lineSymbolizer = sb.createLineSymbolizer(stroke);
                return sb.createStyle(lineSymbolizer);
            case "polygon":
            default:
                Fill fill = sb.createFill(styleProps.getOrDefault("fill", "#FF0000"), Double.parseDouble(styleProps.getOrDefault("fill-opacity", "0.5")));
                Stroke polyStroke = sb.createStroke(styleProps.getOrDefault("stroke", "#000000"), Double.parseDouble(styleProps.getOrDefault("stroke-width", "1")));
                PolygonSymbolizer polygonSymbolizer = sb.createPolygonSymbolizer(polyStroke, fill);
                return sb.createStyle(polygonSymbolizer);
        }
    }

    public ListFeatureCollection getFeatures(String tableName) {
        FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
        if (featureDao == null) {
            return null;
        }
        return resultSetToFeatureCollection(featureDao.queryForAll(), featureDao);
    }
}
