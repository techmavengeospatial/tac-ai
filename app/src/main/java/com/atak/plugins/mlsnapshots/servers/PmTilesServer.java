
package com.atak.plugins.mlsnapshots.servers;

import com.atak.plugins.mlsnapshots.services.PmTilesService;
import com.atak.coremap.log.Log;
import io.javalin.Javalin;
import io.javalin.http.Context;

public class PmTilesServer {

    public static final String TAG = "PmTilesServer";
    private final int port;
    private final PmTilesService pmtilesService;
    private Javalin server;

    public PmTilesServer(int port, PmTilesService pmtilesService) {
        this.port = port;
        this.pmtilesService = pmtilesService;
    }

    public void start() {
        if (server != null) {
            return; // Already started
        }
        server = Javalin.create().start(port);
        Log.d(TAG, "PmTilesServer started on port " + port);

        server.get("/tiles/{z}/{x}/{y}", this::handleTileRequest);
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            Log.d(TAG, "PmTilesServer stopped.");
        }
    }

    private void handleTileRequest(Context ctx) {
        try {
            int z = Integer.parseInt(ctx.pathParam("z"));
            int x = Integer.parseInt(ctx.pathParam("x"));
            int y = Integer.parseInt(ctx.pathParam("y"));

            byte[] tileData = pmtilesService.getTile(z, x, y);

            if (tileData != null) {
                ctx.contentType(pmtilesService.getContentType());
                ctx.result(tileData);
            } else {
                ctx.status(404).result("Tile not found");
            }
        } catch (NumberFormatException e) {
            ctx.status(400).result("Invalid tile coordinates");
        }
    }

    public String getTileUrl() {
        return "http://127.0.0.1:" + port + "/tiles/{z}/{x}/{y}";
    }
}
