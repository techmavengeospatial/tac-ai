
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import com.atak.coremap.log.Log;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.FeatureEntry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import java.io.File;
import java.io.IOException;

public class GeoPackageService {

    public static final String TAG = "GeoPackageService";
    private final File geoPackageFile;

    public GeoPackageService(Context context, String dbName) {
        File internalStorage = context.getFilesDir();
        this.geoPackageFile = new File(internalStorage, dbName);
    }

    public void createOrUpdateGeoPackage(String tableName, SimpleFeatureSource featureSource) throws IOException {
        GeoPackage geoPackage = new GeoPackage(geoPackageFile);
        try {
            SimpleFeatureType schema = featureSource.getSchema();
            FeatureCollection<SimpleFeatureType, SimpleFeature> features = featureSource.getFeatures();

            // Check if the table exists
            boolean tableExists = false;
            for (FeatureEntry entry : geoPackage.features()) {
                if (entry.getTableName().equalsIgnoreCase(tableName)) {
                    tableExists = true;
                    break;
                }
            }

            if (!tableExists) {
                // Create the feature entry for the new table
                FeatureEntry newEntry = new FeatureEntry();
                newEntry.setTableName(tableName);
                newEntry.setIdentifier(tableName);
                newEntry.setDescription("Imported from " + featureSource.getName().getLocalPart());
                newEntry.setBounds(features.getBounds());

                // Create the table in the GeoPackage
                geoPackage.create(newEntry, schema);
            }

            // Get a feature store and write the features
            SimpleFeatureStore featureStore = (SimpleFeatureStore) geoPackage.getFeatureSource(tableName);
            featureStore.addFeatures(features);
            Log.d(TAG, "Successfully wrote " + features.size() + " features to table " + tableName);

        } finally {
            geoPackage.close();
        }
    }
    
    public String getGeoPackagePath() {
        return geoPackageFile.getAbsolutePath();
    }
}
