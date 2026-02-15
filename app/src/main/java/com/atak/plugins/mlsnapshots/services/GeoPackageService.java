
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.ExtensionManager;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureResultSet;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.tiles.user.TileDao;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GeoPackageService {

    private final Context context;
    private GeoPackage geoPackage;

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
        // The extension name for vector tiles is not standardized, but this is a common one.
        String vectorTilesExtension = "gpkg_mapbox_vector_tiles";
        for (String tileTable : getTileTables()) {
            if (extensionManager.has(vectorTilesExtension, tileTable, null)) {
                vectorTileTables.add(tileTable);
            }
        }
        // If no tables with the extension are found, we can make an educated guess.
        // This part is tricky and depends on the GeoPackage contents.
        // For now, we only return tables that explicitly have the extension.
        return vectorTileTables;
    }

    public byte[] getTile(String table, long z, long x, long y) {
        TileDao tileDao = geoPackage.getTileDao(table);
        if (tileDao == null) return null;
        return tileDao.queryForTileBytes((int) x, (int) y, (int) z);
    }

    public ListFeatureCollection getFeatures(String tableName) {
        FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
        if (featureDao == null) {
            return null;
        }

        try {
            // Create the FeatureType
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(tableName);
            // Set the CRS from the Feature DAO
            CoordinateReferenceSystem crs = CRS.decode("EPSG:" + featureDao.getSrs().getOrganizationCoordsysId());
            builder.setCRS(crs);
            // Add the geometry attribute
            builder.add(featureDao.getGeometryColumnName(), com.vividsolutions.jts.geom.Geometry.class);
            // Add other attributes
            featureDao.getColumns().stream()
                    .filter(c -> !c.isGeometry())
                    .forEach(c -> builder.add(c.getName(), c.getDataType().getClassType()));

            SimpleFeatureType featureType = builder.buildFeatureType();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
            List<SimpleFeature> features = new ArrayList<>();

            FeatureResultSet resultSet = featureDao.queryForAll();
            while (resultSet.moveToNext()) {
                FeatureRow row = resultSet.getRow();
                GeoPackageGeometryData geomData = row.getGeometry();
                if (geomData != null && !geomData.isEmpty()) {
                    com.vividsolutions.jts.geom.Geometry geometry = geomData.getGeometry();
                    featureBuilder.add(geometry);
                    for (String columnName : featureType.getDescriptor().getIdentifiers().stream().map(Object::toString).collect(java.util.stream.Collectors.toList())) {
                         if(row.hasColumn(columnName)) {
                            featureBuilder.add(row.getValue(columnName));
                        }
                    }
                    features.add(featureBuilder.buildFeature(row.getId() + ""));
                }
            }
            resultSet.close();
            return new ListFeatureCollection(featureType, features);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get features for table: " + tableName, e);
            return null;
        }
    }

}
