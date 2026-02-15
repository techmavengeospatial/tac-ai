
package com.atak.plugins.mlsnapshots.services;

import android.content.Context;
import com.atak.coremap.log.Log;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataIngestionService {

    public static final String TAG = "DataIngestionService";
    private final File importDir;
    private final GeoPackageService geoPackageService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DataIngestionService(Context context, GeoPackageService geoPackageService) {
        this.importDir = new File(context.getExternalFilesDir(null), "imports");
        if (!importDir.exists() && !importDir.mkdirs()) {
            Log.e(TAG, "Failed to create import directory");
        }
        this.geoPackageService = geoPackageService;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::pollImportDirectory, 0, 10, TimeUnit.SECONDS);
        Log.d(TAG, "Started watching for files in " + importDir.getAbsolutePath());
    }

    private void pollImportDirectory() {
        File[] files = importDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            try {
                if (file.isFile()) {
                    importFile(file);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error importing file: " + file.getName(), e);
            }
        }
    }

    private void importFile(File file) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());

        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            Log.w(TAG, "No DataStore found that can handle: " + file.getName());
            return;
        }

        String[] typeNames = dataStore.getTypeNames();
        for (String typeName : typeNames) {
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            if (featureSource != null) {
                geoPackageService.createOrUpdateGeoPackage(typeName, featureSource);
            }
        }
        
        // Optionally, delete the file after successful import
        // file.delete();
    }

    public void stop() {
        scheduler.shutdown();
    }
}
