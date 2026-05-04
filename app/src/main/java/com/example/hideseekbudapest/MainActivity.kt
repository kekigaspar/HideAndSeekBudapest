package com.example.hideseekbudapest

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.VectorSource
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private fun debugMBTilesMetadata(dbFile: File) {
        try {
            val database = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = database.rawQuery("SELECT name, value FROM metadata", null)

            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(0)
                    val value = cursor.getString(1)
                    android.util.Log.d("MBTILES_DEBUG", "Row -> $name: $value")
                } while (cursor.moveToNext())
            }
            cursor.close()
            database.close()
        } catch (e: Exception) {
            android.util.Log.e("MBTILES_DEBUG", "Failed to read database", e)
        }
    }

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapLibre MUST be initialized before setting the content view
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        // 1. Ensure the MBTiles file is copied from assets to internal storage
        val dbFile = copyDatabaseFromAssets(this, "budapest_vector.mbtiles")
        debugMBTilesMetadata(dbFile)

        // 2. Load the map
        mapView.getMapAsync { map ->

            // Set initial camera position to Budapest
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(47.4979, 19.0402))
                .zoom(12.0)
                .build()

            // Inside your setStyle block
            map.setStyle(Style.Builder().fromUri("asset://awsStyle.json")) { style ->
                val sourceId = "transit-source"
                val dbFile = copyDatabaseFromAssets(this, "budapest_vector.mbtiles")

                style.addSource(VectorSource(sourceId, "mbtiles://${dbFile.absolutePath}"))

                // 1. THE LINE BRUSH: Automatically picks only the LineStrings from the layer
                val lineLayer = LineLayer("line-layer", sourceId).apply {
                    sourceLayer = "transit_data" // The name from your Tippecanoe command
                    setProperties(
                        lineWidth(4f),
                        lineCap("butt"), // Keeps edges flush to prevent the disappearing line bug
                        lineJoin("miter"),
                        lineColor(get("color")) // Uses the color property from your GeoJSON
                    )
                }
                style.addLayer(lineLayer)

                // 2. THE CIRCLE BRUSH: Automatically picks only the Points from the same layer
                val stopLayer = CircleLayer("stop-layer", sourceId).apply {
                    sourceLayer = "transit_data" // Same sourceLayer as above!
                    setProperties(
                        circleRadius(3f),
                        circleColor(Color.WHITE),
                        circleStrokeWidth(1.5f),
                        circleStrokeColor(Color.BLACK)
                    )
                    // Optimization: Don't draw the dots until the user zooms in closer
                    setFilter(org.maplibre.android.style.expressions.Expression.gt(
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.literal(13.5)
                    ))
                }
                style.addLayer(stopLayer)
            }
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

    // MapLibre requires lifecycle management to prevent memory leaks
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