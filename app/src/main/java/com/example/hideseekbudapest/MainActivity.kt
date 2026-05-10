package com.example.hideseekbudapest

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.hideseekbudapest.style.MapStyleManager
import com.example.hideseekbudapest.tools.*
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {
    private val viewModel: MapToolViewModel by viewModels()
    private lateinit var mapView: MapView
    private var mapboxMap: MapLibreMap? = null
    private val allExclusions = mutableListOf<Feature>()

    private lateinit var reticle: ImageView
    private lateinit var editPanel: LinearLayout
    private lateinit var mainToolbar: HorizontalScrollView
    private lateinit var switchInOut: Switch
    private lateinit var inputRadius: EditText
    private lateinit var radiusOverlay: RadiusOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        mapView = findViewById(R.id.mapView)
        reticle = findViewById(R.id.targetReticle)
        editPanel = findViewById(R.id.editPanel)
        mainToolbar = findViewById(R.id.mainToolbar)
        inputRadius = findViewById(R.id.inputRadius)
        switchInOut = findViewById(R.id.switchInOut)
        radiusOverlay = findViewById(R.id.radiusOverlay)

        mapView.onCreate(savedInstanceState)

        // Launch a coroutine tied to the Activity lifecycle
        lifecycleScope.launch {
            // This runs on the background thread and pauses execution here until finished
            val dbFile = copyDatabaseFromAssets(this@MainActivity, "budapest_vector.mbtiles")

            // Once the DB is ready, this continues on the Main Thread to set up the UI
            mapView.getMapAsync { map ->
                this@MainActivity.mapboxMap = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(47.4979, 19.0402))
                    .zoom(12.0)
                    .build()

                map.setStyle(Style.Builder().fromUri("asset://awsStyle.json")) { style ->
                    MapStyleManager.setupTransitAndExclusionStyle(style, dbFile)
                    setupEditModeListeners()
                }
            }
        }

        setupObservers()
    }

    private fun setupObservers() {
        // 2. Observe the GeoJSON state. Whenever the ViewModel updates it, the map updates automatically!
        lifecycleScope.launch {
            viewModel.mergedGeoJson.collect { geoJsonString ->
                if (geoJsonString != null) {
                    mapboxMap?.style?.getSourceAs<GeoJsonSource>("exclusion-source")
                        ?.setGeoJson(geoJsonString)
                }
            }
        }

        // 3. Observe the Edit Mode state to toggle UI
        lifecycleScope.launch {
            viewModel.isEditing.collect { isEditing ->
                editPanel.isVisible = isEditing
                reticle.isVisible = isEditing
                radiusOverlay.isVisible = isEditing
                mainToolbar.isVisible = !isEditing

                if (!isEditing) {
                    radiusOverlay.radiusPixels = 0f
                }
            }
        }
    }
    private suspend fun copyDatabaseFromAssets(context: Context, dbName: String): File {
        val dbPath = context.getDatabasePath(dbName)

        // Switch to the IO thread for file operations
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

        findViewById<Button>(R.id.btnStartCircle).setOnClickListener {
            toggleEditModeUI(true)
            updateLivePreview()
        }

        map.addOnCameraMoveListener {
            if (editPanel.isVisible) updateLivePreview()
        }

        inputRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLivePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        switchInOut.setOnCheckedChangeListener { _, _ -> updateLivePreview() }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            toggleEditModeUI(false)
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val centerLatLng = map.cameraPosition.target ?: return@setOnClickListener
            val radiusKm = inputRadius.text.toString().toDoubleOrNull() ?: 0.0

            if (radiusKm <= 0.0) return@setOnClickListener

            val finalFeature = CircleTool.generateShape(
                Point.fromLngLat(centerLatLng.longitude, centerLatLng.latitude),
                radiusKm,
                switchInOut.isChecked
            )

            allExclusions.add(finalFeature)
            val mergedFeature = UnionHelper.mergeFeatures(allExclusions)

            val displayList = listOfNotNull(mergedFeature)
            map.style?.getSourceAs<GeoJsonSource>("exclusion-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(displayList).toJson())

            toggleEditModeUI(false)
        }
    }

    private fun updateLivePreview() {
        val map = mapboxMap ?: return
        val radiusKm = inputRadius.text.toString().toDoubleOrNull() ?: 0.0
        val centerLatLng = map.cameraPosition.target ?: return

        radiusOverlay.radiusPixels = PreviewCalculator.calculateRadiusInPixels(
            centerLatLng,
            radiusKm,
            map.projection
        )
    }

    private fun toggleEditModeUI(isEditing: Boolean) {
        editPanel.isVisible = isEditing
        reticle.isVisible = isEditing
        radiusOverlay.isVisible = isEditing
        mainToolbar.isVisible = !isEditing

        if (!isEditing) {
            radiusOverlay.radiusPixels = 0f
        }
    }

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