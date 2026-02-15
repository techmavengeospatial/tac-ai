package com.atak.plugins.mlsnapshots.services;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelDownloadService {

    private static final String TAG = "ModelDownloadService";

    public interface DownloadListener {
        void onProgress(int progress);
        void onComplete(File file);
        void onError(String error);
    }

    public void downloadModel(String fileUrl, File destinationFile, DownloadListener listener) {
        new Thread(() -> {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(fileUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    listener.onError("Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                    return;
                }

                int fileLength = connection.getContentLength();

                input = new BufferedInputStream(url.openStream(), 8192);
                output = new FileOutputStream(destinationFile);

                byte[] data = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        listener.onProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                listener.onComplete(destinationFile);

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError(e.getMessage());
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {
                }
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
