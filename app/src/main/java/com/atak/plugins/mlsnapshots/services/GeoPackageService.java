
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.tiles.user.TileDao;

import java.io.File;
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

    public void close() {
        if (geoPackage != null) {
            geoPackage.close();
        }
    }

    public List<String> getVectorTileTables() {
        return geoPackage.getTileTables();
    }

    public byte[] getTile(String table, int x, int y, int z) {
        TileDao tileDao = geoPackage.getTileDao(table);
        // TODO: Handle TMS vs XYZ conversion if necessary
        // For now, assume XYZ
        return tileDao.queryForTileBytes(x, y, z);
    }
}
