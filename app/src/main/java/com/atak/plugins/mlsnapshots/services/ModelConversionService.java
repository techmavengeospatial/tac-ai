
package com.atak.plugins.mlsnapshots.services;

import com.atak.coremap.log.Log;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.obj.ObjWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class ModelConversionService {

    public static final String TAG = "ModelConversionService";

    public interface ConversionListener {
        void onProgress(String message);
        void onComplete(boolean success, String outputPath);
    }

    public ModelConversionService() {
        // Initialization, if needed
    }

    public void convertGltfToObj(File gltfFile, File outputDir, ConversionListener listener) {
        listener.onProgress("Starting conversion of " + gltfFile.getName());

        try (FileInputStream fis = new FileInputStream(gltfFile)) {
            GltfModelReader reader = new GltfModelReader();
            GltfModel gltfModel = reader.read(fis);

            File objFile = new File(outputDir, gltfFile.getName().replace(".gltf", ".obj"));

            try (FileOutputStream fos = new FileOutputStream(objFile)) {
                ObjWriter.write(gltfModel, fos);
                listener.onProgress("Conversion successful.");
                listener.onComplete(true, objFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Failed to write OBJ file", e);
                listener.onComplete(false, e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read glTF file", e);
            listener.onComplete(false, e.getMessage());
        }
    }
}
