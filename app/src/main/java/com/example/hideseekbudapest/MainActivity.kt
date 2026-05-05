package com.example.hideseekbudapest

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.hideseekbudapest.tools.*
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gt
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.toColorInt
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.Switch
import androidx.core.view.isVisible
import android.text.Editable // Make sure this is at the top of your file
import android.text.TextWatcher
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var activeTool: MapTool? = null
    private var mapboxMap: org.maplibre.android.maps.MapLibreMap? = null
    private val allExclusions = mutableListOf<Feature>()

    private lateinit var reticle: ImageView
    private lateinit var editPanel: LinearLayout
    private lateinit var mainToolbar: HorizontalScrollView
    private lateinit var switchInOut: Switch
    private lateinit var inputRadius: android.widget.EditText
    private lateinit var radiusOverlay: RadiusOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapLibre MUST be initialized before setting the content view[cite: 1]
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        reticle = findViewById(R.id.targetReticle)
        editPanel = findViewById(R.id.editPanel)
        mainToolbar = findViewById(R.id.mainToolbar)
        inputRadius = findViewById(R.id.inputRadius)
        switchInOut = findViewById(R.id.switchInOut)
        radiusOverlay = findViewById(R.id.radiusOverlay)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        val dbFile = copyDatabaseFromAssets(this, "budapest_vector.mbtiles")

        mapView.getMapAsync { map ->
            this.mapboxMap = map
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(47.4979, 19.0402))
                .zoom(12.0)
                .build()

            map.setStyle(Style.Builder().fromUri("asset://awsStyle.json")) { style ->
                val sourceId = "transit-source"

                style.addSource(VectorSource(sourceId, "mbtiles://${dbFile.absolutePath}"))

                val lineLayer = LineLayer("line-layer", sourceId).apply {
                    sourceLayer = "transit_data"
                    setProperties(
                        lineWidth(4f),
                        lineCap("butt"),
                        lineJoin("miter"),
                        lineColor(get("color"))
                    )
                }
                style.addLayer(lineLayer)

                val stopLayer = CircleLayer("stop-layer", sourceId).apply {
                    sourceLayer = "transit_data"
                    setProperties(
                        circleRadius(3f),
                        circleColor(Color.WHITE),
                        circleStrokeWidth(1.5f),
                        circleStrokeColor(Color.BLACK)
                    )
                    setFilter(gt(zoom(), literal(13.5)))
                }
                style.addLayer(stopLayer)

                style.addImage("stripe-id", createStripePattern())

                style.addSource(GeoJsonSource("exclusion-source"))
                style.addLayer(FillLayer("exclusion-layer", "exclusion-source").withProperties(
                    fillPattern("stripe-id"), // Red shading
                    fillOpacity(0.3f), // Semi-transparent so you can still see the map
                ))

                style.addLayer(LineLayer("exclusion-outline-layer", "exclusion-source").withProperties(
                    lineColor("#000000".toColorInt()), // Solid Black edge
                    lineWidth(3f),                          // Make it thick and visible
                    lineJoin("round"),                      // Smooths out the corners of the polygon
                    lineCap("round")
                ))

                setupEditModeListeners()

                map.addOnMapClickListener { latLng ->
                    val tool = activeTool ?: return@addOnMapClickListener false

                    val clickedPoint = Point.fromLngLat(latLng.longitude, latLng.latitude)

                    // Pass the click down to whatever tool is currently active
                    val generatedFeature = tool.processClick(clickedPoint)

                    // If the tool is finished (e.g., tapped twice), it will return the Polygon
                    if (generatedFeature != null) {
                        // Keep track of the raw shape the user just drew
                        allExclusions.add(generatedFeature)

                        // Merge all raw shapes into one unified flat polygon
                        val mergedFeature = UnionHelper.mergeFeatures(allExclusions)

                        // Tell the map to draw ONLY the merged flat result
                        val displayList = if (mergedFeature != null) listOf(mergedFeature) else emptyList()
                        val exclusionCollection = FeatureCollection.fromFeatures(displayList)

                        style.getSourceAs<GeoJsonSource>("exclusion-source")?.setGeoJson(exclusionCollection.toJson())

                        setTool(null)
                    }
                    true // We handled the click, don't pass it to anything else
                }
            }
        }
    }

    // --- HELPER TO SWITCH TOOLS SAFELY ---
    fun setTool(tool: MapTool?) {
        activeTool?.reset() // clear pins from the old tool if the user cancelled midway
        activeTool = tool

        // Clear the yellow pins from the map UI visually
        mapView.getMapAsync { map ->
            val emptyCollection = FeatureCollection.fromFeatures(emptyList())
            map.style?.getSourceAs<GeoJsonSource>("pin-source")?.setGeoJson(emptyCollection.toJson())
        }
    }
    private fun copyDatabaseFromAssets(context: Context, dbName: String): File {
        val dbPath = context.getDatabasePath(dbName)
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()
            context.assets.open(dbName).use { inputStream ->
                FileOutputStream(dbPath).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return dbPath
    }

    private fun createStripePattern(): Bitmap {
        val size = 64 // Size of the repeating tile
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = "#FF0000".toColorInt() // Red stripes
            strokeWidth = 8f // Thickness of the stripes
            isAntiAlias = true
        }

        // Draw diagonal lines that seamlessly tile
        canvas.drawLine(0f, 0f, size.toFloat(), size.toFloat(), paint)
        // Draw the corners so it repeats seamlessly across tile boundaries
        canvas.drawLine(0f, -size.toFloat(), size * 2f, size.toFloat(), paint)
        canvas.drawLine(-size.toFloat(), 0f, size.toFloat(), size * 2f, paint)

        return bitmap
    }

    private fun setupEditModeListeners() {
        val map = mapboxMap ?: return

        // 1. Enter Edit Mode
        findViewById<Button>(R.id.btnStartCircle).setOnClickListener {
            mainToolbar.visibility = View.GONE
            editPanel.visibility = View.VISIBLE
            reticle.visibility = View.VISIBLE
            radiusOverlay.visibility = View.VISIBLE // SHOW OVERLAY
            updateLivePreview()
        }

        // 2. Listen to Map Movement (Updates live as they pan)
        map.addOnCameraMoveListener {
            if (editPanel.isVisible) {
                updateLivePreview()
            }
        }

        // 3. Listen to Slider (Updates live as they drag)
        inputRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Fire the update every time a number is typed or deleted
                updateLivePreview()
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed
            }
        })

        // 4. Listen to In/Out Switch
        switchInOut.setOnCheckedChangeListener { _, _ -> updateLivePreview() }

        // 5. Cancel Action
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            closeEditMode()
        }

        // 6. Confirm Action
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val center = map.cameraPosition.target

            // Read the text. If it's empty or invalid, default to 0.0
            val radiusKm = inputRadius.text.toString().toDoubleOrNull() ?: 0.0

            // Don't let them confirm an invisible/invalid circle
            if (radiusKm <= 0.0) return@setOnClickListener

            val isInside = switchInOut.isChecked

            val finalFeature = CircleTool.generateShape(
                Point.fromLngLat(center!!.longitude, center.latitude),
                radiusKm,
                isInside
            )

            allExclusions.add(finalFeature)
            val mergedFeature = UnionHelper.mergeFeatures(allExclusions)

            val displayList = if (mergedFeature != null) listOf(mergedFeature) else emptyList()
            map.style?.getSourceAs<GeoJsonSource>("exclusion-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(displayList).toJson())

            closeEditMode()
        }
    }

    private fun updateLivePreview() {
        val map = mapboxMap ?: return

        val radiusKm = inputRadius.text.toString().toDoubleOrNull() ?: 0.0

        if (radiusKm <= 0.0) {
            radiusOverlay.radiusPixels = 0f
            return
        }

        // 1. Get the real-world center coordinate
        val centerLatLng = map.cameraPosition.target
        val centerPoint = Point.fromLngLat(centerLatLng!!.longitude, centerLatLng.latitude)

        // 2. Calculate a real-world coordinate exactly 'radiusKm' away (e.g., moving directly East)
        val edgePoint = TurfMeasurement.destination(centerPoint, radiusKm, 90.0, TurfConstants.UNIT_KILOMETERS)
        val edgeLatLng = LatLng(edgePoint.latitude(), edgePoint.longitude())

        // 3. Ask MapLibre to translate those GPS coordinates into literal Screen Pixels
        val centerPixel = map.projection.toScreenLocation(centerLatLng)
        val edgePixel = map.projection.toScreenLocation(edgeLatLng)

        // 4. Calculate the pixel distance between the center and the edge
        // Using standard Pythagorean theorem: a^2 + b^2 = c^2
        val deltaX = edgePixel.x - centerPixel.x
        val deltaY = edgePixel.y - centerPixel.y
        val pixelDistance = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()

        // 5. Instantly update the Android view
        radiusOverlay.radiusPixels = pixelDistance
    }

    private fun closeEditMode() {
        editPanel.visibility = View.GONE
        reticle.visibility = View.GONE
        mainToolbar.visibility = View.VISIBLE
        radiusOverlay.visibility = View.GONE // HIDE OVERLAY
        radiusOverlay.radiusPixels = 0f
    }

    // MapLibre requires lifecycle management to prevent memory leaks[cite: 1]
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