
package com.atak.plugins.mlsnapshots.services;

import java.util.List;
import java.util.Map;

public class FeatureData {
    private final List<String> columnNames;
    private final List<Map<String, Object>> rows;

    public FeatureData(List<String> columnNames, List<Map<String, Object>> rows) {
        this.columnNames = columnNames;
        this.rows = rows;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}
