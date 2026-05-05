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

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var activeTool: MapTool? = null
    private val allExclusions = mutableListOf<Feature>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapLibre MUST be initialized before setting the content view[cite: 1]
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCircleInside).setOnClickListener {
            setTool(CircleTool(isInside = true))
        }
        findViewById<Button>(R.id.btnCircleOutside).setOnClickListener {
            setTool(CircleTool(isInside = false))
        }

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        val dbFile = copyDatabaseFromAssets(this, "budapest_vector.mbtiles")

        mapView.getMapAsync { map ->
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

                style.addSource(GeoJsonSource("pin-source"))
                style.addLayer(CircleLayer("pin-layer", "pin-source").withProperties(
                    circleRadius(6f),
                    circleColor(Color.YELLOW),
                    circleStrokeWidth(2f),
                    circleStrokeColor(Color.BLACK)
                ))

                map.addOnMapClickListener { latLng ->
                    val tool = activeTool ?: return@addOnMapClickListener false

                    val clickedPoint = Point.fromLngLat(latLng.longitude, latLng.latitude)

                    // Pass the click down to whatever tool is currently active
                    val generatedFeature = tool.processClick(clickedPoint)

                    // Update the UI to show the temporary yellow pins
                    val pinCollection = FeatureCollection.fromFeatures(tool.getTempPins().map { Feature.fromGeometry(it) })
                    style.getSourceAs<GeoJsonSource>("pin-source")?.setGeoJson(pinCollection.toJson())

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