
package com.atak.plugins.mlsnapshots;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.atak.plugins.mlsnapshots.services.FeatureData;
import java.util.List;
import java.util.Map;

public class AttributeTableAdapter extends RecyclerView.Adapter<AttributeTableAdapter.ViewHolder> {

    private final FeatureData featureData;
    private final List<String> columnNames;

    public AttributeTableAdapter(FeatureData featureData) {
        this.featureData = featureData;
        this.columnNames = featureData.getColumnNames();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This layout will be created in the next step
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_attribute_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> rowData = featureData.getRows().get(position);
        holder.bind(rowData, columnNames);
    }

    @Override
    public int getItemCount() {
        return featureData.getRows().size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        ViewHolder(View view) {
            super(view);
            // This ID corresponds to an element in the item layout
            textView = view.findViewById(R.id.row_data_textview);
        }

        void bind(Map<String, Object> rowData, List<String> columnNames) {
            StringBuilder rowText = new StringBuilder();
            for (String colName : columnNames) {
                Object value = rowData.get(colName);
                rowText.append(colName).append(": ").append(value != null ? value.toString() : "NULL").append("\n");
            }
            textView.setText(rowText.toString());
        }
    }
}
