package com.atak.plugins.mlsnapshots;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.atak.map.ui.DropDownReceiver;
import com.atak.plugins.mlsnapshots.services.GeoPackageService;
import com.atak.plugins.mlsnapshots.services.MapLibreService;
import com.atakmap.android.maps.MapView;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StylingWidgetDropDownReceiver extends DropDownReceiver {

    public static final String TAG = "StylingWidgetDropDownReceiver";
    public static final String SHOW_STYLING_WIDGET = "com.atak.plugins.mlsnapshots.SHOW_STYLING_WIDGET";

    private final Context pluginContext;
    private final GeoPackageService geoPackageService;
    private final MapLibreService mapLibreService;
    private final Gson gson = new Gson();

    private View stylingWidgetView;
    private Spinner featureTableSpinner;
    private Spinner geometryTypeSpinner;
    private LinearLayout pointStyleLayout, lineStyleLayout, polygonStyleLayout;
    private EditText pointColorInput, pointSizeInput;
    private EditText lineColorInput, lineWidthInput;
    private EditText fillColorInput, strokeColorInput, strokeWidthInput;
    private SeekBar fillOpacitySeekbar;
    private Button applyStyleButton;

    public StylingWidgetDropDownReceiver(final MapView mapView, final Context context, final GeoPackageService geoPackageService, final MapLibreService mapLibreService) {
        super(mapView, SHOW_STYLING_WIDGET);
        this.pluginContext = context;
        this.geoPackageService = geoPackageService;
        this.mapLibreService = mapLibreService;
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) {
            if (stylingWidgetView == null) {
                LayoutInflater inflater = LayoutInflater.from(pluginContext);
                stylingWidgetView = inflater.inflate(R.layout.styling_widget, null);
                initializeViews(stylingWidgetView);
            }
            showDropDown(stylingWidgetView, DropDownReceiver.HALF_WIDTH, DropDownReceiver.FULL_HEIGHT, DropDownReceiver.FULL_WIDTH, DropDownReceiver.HALF_HEIGHT, false);
            populateFeatureTableSpinner();
            populateGeometryTypeSpinner();
        }
    }

    private void initializeViews(View view) {
        featureTableSpinner = view.findViewById(R.id.feature_table_spinner);
        geometryTypeSpinner = view.findViewById(R.id.geometry_type_spinner);
        pointStyleLayout = view.findViewById(R.id.point_style_layout);
        lineStyleLayout = view.findViewById(R.id.line_style_layout);
        polygonStyleLayout = view.findViewById(R.id.polygon_style_layout);
        pointColorInput = view.findViewById(R.id.point_color_input);
        pointSizeInput = view.findViewById(R.id.point_size_input);
        lineColorInput = view.findViewById(R.id.line_color_input);
        lineWidthInput = view.findViewById(R.id.line_width_input);
        fillColorInput = view.findViewById(R.id.fill_color_input);
        strokeColorInput = view.findViewById(R.id.stroke_color_input);
        strokeWidthInput = view.findViewById(R.id.stroke_width_input);
        fillOpacitySeekbar = view.findViewById(R.id.fill_opacity_seekbar);
        applyStyleButton = view.findViewById(R.id.apply_style_button);

        geometryTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateStyleLayouts(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        applyStyleButton.setOnClickListener(v -> applyStyle());
    }

    private void populateFeatureTableSpinner() {
        if (geoPackageService.isGeoPackageOpen()) {
            List<String> featureTables = geoPackageService.getFeatureTables();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(pluginContext, android.R.layout.simple_spinner_item, featureTables);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            featureTableSpinner.setAdapter(adapter);
        }
    }

    private void populateGeometryTypeSpinner() {
        List<String> geometryTypes = Arrays.asList("Point", "Line", "Polygon");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(pluginContext, android.R.layout.simple_spinner_item, geometryTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        geometryTypeSpinner.setAdapter(adapter);
    }

    private void updateStyleLayouts(String geometryType) {
        pointStyleLayout.setVisibility("point".equalsIgnoreCase(geometryType) ? View.VISIBLE : View.GONE);
        lineStyleLayout.setVisibility("line".equalsIgnoreCase(geometryType) ? View.VISIBLE : View.GONE);
        polygonStyleLayout.setVisibility("polygon".equalsIgnoreCase(geometryType) ? View.VISIBLE : View.GONE);
    }

    private void applyStyle() {
        String tableName = featureTableSpinner.getSelectedItem().toString();
        String geometryType = geometryTypeSpinner.getSelectedItem().toString();

        String sourceId = "gpkg-features-" + tableName;
        String layerId = "gpkg-layer-" + tableName;

        Map<String, Object> styleMap = new HashMap<>();
        styleMap.put("geometryType", geometryType);

        try {
            switch (geometryType.toLowerCase()) {
                case "point":
                    styleMap.put("fill", validateColor(pointColorInput.getText().toString()));
                    styleMap.put("size", Double.parseDouble(pointSizeInput.getText().toString()));
                    break;
                case "line":
                    styleMap.put("stroke", validateColor(lineColorInput.getText().toString()));
                    styleMap.put("stroke-width", Double.parseDouble(lineWidthInput.getText().toString()));
                    break;
                case "polygon":
                    styleMap.put("fill", validateColor(fillColorInput.getText().toString()));
                    styleMap.put("fill-opacity", fillOpacitySeekbar.getProgress() / 100f);
                    styleMap.put("stroke", validateColor(strokeColorInput.getText().toString()));
                    styleMap.put("stroke-width", Double.parseDouble(strokeWidthInput.getText().toString()));
                    break;
            }

            String styleJson = gson.toJson(styleMap);

            mapLibreService.updateFeatureLayer(layerId, sourceId, styleJson);
        } catch (NumberFormatException e) {
            Toast.makeText(pluginContext, "Invalid number format. Please enter a valid number.", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(pluginContext, "Invalid color format. Please use #RRGGBB or a valid color name.", Toast.LENGTH_SHORT).show();
        }
    }

    private String validateColor(String colorString) throws IllegalArgumentException {
        Color.parseColor(colorString);
        return colorString;
    }
}
