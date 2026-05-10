package com.example.hideseekbudapest

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.hideseekbudapest.style.MapStyleManager
import com.example.hideseekbudapest.tools.*
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    // ViewModel handles the state and math
    private val viewModel: MapToolViewModel by viewModels()

    // Map variables
    private lateinit var mapView: MapView
    private var mapboxMap: MapLibreMap? = null

    // Core UI
    private lateinit var reticle: ImageView
    private lateinit var editPanel: LinearLayout
    private lateinit var mainToolbar: HorizontalScrollView
    private lateinit var toolSettingsContainer: FrameLayout

    // Overlays
    private lateinit var radiusOverlay: RadiusOverlayView
    private lateinit var hotColdOverlay: HotColdOverlayView

    // Tool State
    private var activeTool: ActiveTool = ActiveTool.NONE
    private var savedPreviousLocation: LatLng? = null

    enum class ActiveTool { NONE, CIRCLE, HOT_COLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        mapView = findViewById(R.id.mapView)
        reticle = findViewById(R.id.targetReticle)
        editPanel = findViewById(R.id.editPanel)
        mainToolbar = findViewById(R.id.mainToolbar)
        toolSettingsContainer = findViewById(R.id.toolSettingsContainer)
        radiusOverlay = findViewById(R.id.radiusOverlay)
        hotColdOverlay = findViewById(R.id.hotColdOverlay)

        mapView.onCreate(savedInstanceState)

        // 1. Load DB in background, then init map
        lifecycleScope.launch {
            val dbFile = copyDatabaseFromAssets(this@MainActivity, "budapest_vector.mbtiles")

            mapView.getMapAsync { map ->
                this@MainActivity.mapboxMap = map

                // 1. Create the boundary box for Inner Budapest
                val budapestBounds = LatLngBounds.Builder()
                    .include(LatLng(47.530, 19.110)) // North East Corner
                    .include(LatLng(47.460, 19.010)) // South West Corner
                    .build()

                // 2. Lock the camera panning to this box
                map.setLatLngBoundsForCameraTarget(budapestBounds)

                // 3. Prevent zooming out too far (Zoom 11 or 12 is usually good for a city)
                map.setMinZoomPreference(10.5)

                // 4. (Optional) Prevent zooming in too close if you want to hide street-level detail
                // map.setMaxZoomPreference(18.0)

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(47.4979, 19.0402)) // Budapest Center
                    .zoom(12.5) // Start slightly zoomed in
                    .build()

                map.setStyle(Style.Builder().fromUri("asset://awsStyle.json")) { style ->
                    MapStyleManager.setupTransitAndExclusionStyle(style, dbFile)
                    setupEditModeListeners()
                }
            }
        }

        // 3. Start watching the ViewModel for updates
        setupObservers()
    }

    private fun setupObservers() {
        // Automatically update the map when the ViewModel merges new shapes
        lifecycleScope.launch {
            viewModel.mergedGeoJson.collect { geoJsonString ->
                if (geoJsonString != null) {
                    mapboxMap?.style?.getSourceAs<GeoJsonSource>(MapStyleManager.EXCLUSION_SOURCE_ID)
                        ?.setGeoJson(geoJsonString)
                }
            }
        }

        // Automatically toggle the UI when ViewModel changes Edit Mode
        lifecycleScope.launch {
            viewModel.isEditing.collect { isEditing ->
                toggleEditModeUI(isEditing)
            }
        }
    }

    private suspend fun copyDatabaseFromAssets(context: Context, dbName: String): File {
        val dbPath = context.getDatabasePath(dbName)
        withContext(Dispatchers.IO) {
            if (!dbPath.exists()) {
                dbPath.parentFile?.mkdirs()
                context.assets.open(dbName).use { inputStream ->
                    FileOutputStream(dbPath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
        return dbPath
    }

    private fun setupEditModeListeners() {
        val map = mapboxMap ?: return

        // Start Tools
        findViewById<Button>(R.id.btnStartCircle).setOnClickListener {
            startTool(ActiveTool.CIRCLE)
        }

        findViewById<Button>(R.id.btnStartHotCold).setOnClickListener {
            savedPreviousLocation = null
            startTool(ActiveTool.HOT_COLD)
        }

        // Update previews when camera moves
        map.addOnCameraMoveListener {
            if (editPanel.isVisible) updateLivePreview()
        }

        // Confirm / Cancel
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            viewModel.toggleEditMode(false)
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            executeActiveTool(map)
        }
    }

    private fun startTool(tool: ActiveTool) {
        activeTool = tool
        toolSettingsContainer.removeAllViews()

        val layoutRes = when (tool) {
            ActiveTool.CIRCLE -> R.layout.layout_tool_circle
            ActiveTool.HOT_COLD -> R.layout.layout_tool_hot_cold
            ActiveTool.NONE -> return
        }

        val view = layoutInflater.inflate(layoutRes, toolSettingsContainer, true)

        // Attach layout-specific listeners
        if (tool == ActiveTool.CIRCLE) {
            val inputRadius = view.findViewById<EditText>(R.id.inputRadius)
            val switchInOut = view.findViewById<Switch>(R.id.switchInOut)

            inputRadius.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateLivePreview()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            switchInOut.setOnCheckedChangeListener { _, _ -> updateLivePreview() }

        } else if (tool == ActiveTool.HOT_COLD) {
            val btnSetPrevious = view.findViewById<Button>(R.id.btnSetPrevious)
            val switchHotter = view.findViewById<Switch>(R.id.switchHotter)

            btnSetPrevious.setOnClickListener {
                savedPreviousLocation = mapboxMap?.cameraPosition?.target
                updateLivePreview()
            }
            switchHotter.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        }

        viewModel.toggleEditMode(true)
        updateLivePreview()
    }

    private fun updateLivePreview() {
        val map = mapboxMap ?: return
        val centerLatLng = map.cameraPosition.target ?: return

        when (activeTool) {
            ActiveTool.CIRCLE -> {
                val view = toolSettingsContainer.getChildAt(0)
                val inputRadius = view?.findViewById<EditText>(R.id.inputRadius)
                val switchInOut = view?.findViewById<Switch>(R.id.switchInOut) // Grab the switch
                val radiusKm = inputRadius?.text.toString().toDoubleOrNull() ?: 0.0

                // Pass the switch state to the view
                radiusOverlay.isInside = switchInOut?.isChecked ?: true
                radiusOverlay.radiusPixels = PreviewCalculator.calculateRadiusInPixels(
                    centerLatLng, radiusKm, map.projection
                )
            }
            ActiveTool.HOT_COLD -> {
                // (This remains exactly the same as before, since we are already
                //  passing isHotter into updatePoints!)
                val prevLoc = savedPreviousLocation
                if (prevLoc != null) {
                    val prevPixel = map.projection.toScreenLocation(prevLoc)
                    val currPixel = map.projection.toScreenLocation(centerLatLng)

                    val view = toolSettingsContainer.getChildAt(0)
                    val isHotter = view?.findViewById<Switch>(R.id.switchHotter)?.isChecked ?: true

                    hotColdOverlay.updatePoints(prevPixel, currPixel, isHotter)
                } else {
                    hotColdOverlay.updatePoints(null, null, true)
                }
            }
            ActiveTool.NONE -> {}
        }
    }

    private fun executeActiveTool(map: MapLibreMap) {
        val centerLatLng = map.cameraPosition.target ?: return
        val view = toolSettingsContainer.getChildAt(0)

        when (activeTool) {
            ActiveTool.CIRCLE -> {
                val radiusKm = view.findViewById<EditText>(R.id.inputRadius).text.toString().toDoubleOrNull() ?: 0.0
                if (radiusKm <= 0.0) return

                val isInside = view.findViewById<Switch>(R.id.switchInOut).isChecked
                val params = CircleParams(
                    Point.fromLngLat(centerLatLng.longitude, centerLatLng.latitude),
                    radiusKm,
                    isInside
                )
                viewModel.applyTool(CircleTool, params)
            }
            ActiveTool.HOT_COLD -> {
                val prevLoc = savedPreviousLocation ?: return
                val isHotter = view.findViewById<Switch>(R.id.switchHotter).isChecked

                val params = HotterColderParams(
                    Point.fromLngLat(prevLoc.longitude, prevLoc.latitude),
                    Point.fromLngLat(centerLatLng.longitude, centerLatLng.latitude),
                    isHotter
                )
                viewModel.applyTool(HotterColderTool, params)
            }
            ActiveTool.NONE -> return
        }

        viewModel.toggleEditMode(false)
    }

    private fun toggleEditModeUI(isEditing: Boolean) {
        editPanel.isVisible = isEditing
        reticle.isVisible = isEditing
        mainToolbar.isVisible = !isEditing

        radiusOverlay.isVisible = isEditing && activeTool == ActiveTool.CIRCLE
        hotColdOverlay.isVisible = isEditing && activeTool == ActiveTool.HOT_COLD

        if (!isEditing) {
            radiusOverlay.radiusPixels = 0f
            hotColdOverlay.updatePoints(null, null, true)
            activeTool = ActiveTool.NONE
        }
    }

    // Standard MapLibre Lifecycle Methods
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}