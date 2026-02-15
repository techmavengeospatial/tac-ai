
package com.atak.plugins.mlsnapshots;

import android.app.Activity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.atak.plugins.mlsnapshots.services.DuckDBService;
import com.atak.plugins.mlsnapshots.services.FeatureData;
import com.atak.coremap.log.Log;
import java.sql.SQLException;

public class AttributeTableActivity extends Activity {

    public static final String TAG = "AttributeTableActivity";

    private RecyclerView recyclerView;
    private AttributeTableAdapter adapter;
    private DuckDBService duckDBService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // This layout will be created in the next step.
        setContentView(R.layout.activity_attribute_table); 

        recyclerView = findViewById(R.id.attribute_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        String tableName = getIntent().getStringExtra("tableName");
        if (tableName == null || tableName.isEmpty()) {
            Log.e(TAG, "No tableName provided in Intent.");
            finish(); // Close the activity if there's no table to show
            return;
        }

        try {
            // This path should ideally be managed by a central configuration service
            String dbPath = getApplicationContext().getFilesDir() + "/atak.db";
            duckDBService = new DuckDBService(dbPath);

            // Fetch data and display it
            FeatureData featureData = duckDBService.getTableData(tableName, null, null);
            adapter = new AttributeTableAdapter(featureData);
            recyclerView.setAdapter(adapter);

        } catch (SQLException e) {
            Log.e(TAG, "Failed to initialize DuckDB or fetch data.", e);
            // Optionally, show an error message to the user
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (duckDBService != null) {
            try {
                duckDBService.close();
            } catch (SQLException e) {
                Log.e(TAG, "Error closing DuckDB connection.", e);
            }
        }
    }
}
