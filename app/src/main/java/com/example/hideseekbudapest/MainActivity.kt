package com.example.hideseekbudapest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.json.JSONObject
import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.google.maps.android.ui.IconGenerator
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.data.geojson.GeoJsonLineStringStyle
import com.google.maps.android.data.geojson.GeoJsonPointStyle

class MainActivity : AppCompatActivity() {

    private lateinit var googleMap: GoogleMap

    private var currentZoomTier: Int = -1

    private val transitLayers = mutableListOf<GeoJsonLayer>()

    private val stopIcon: BitmapDescriptor by lazy {
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(3, Color.BLACK)
            setSize(24, 24)
        }
        val bitmap = createBitmap(24, 24)
        val canvas = Canvas(bitmap)
        dotDrawable.setBounds(0, 0, canvas.width, canvas.height)
        dotDrawable.draw(canvas)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            enableUserLocation()
        }
    }

    private fun getZoomTier(zoom: Float): Int {
        return when {
            zoom >= 15.0f -> 5 // Stops and everything else
            zoom >= 14.0f -> 4 // Buses
            zoom >= 13.5f -> 3 // Trolleys
            zoom >= 13.0f -> 2 // Trams
            zoom >= 12.5f -> 1 // HEVs
            else -> 0          // Metros only
        }
    }

    private fun loadAllTransitLines() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Assuming you put the new files in assets/geojson
                val lineFiles = assets.list("lines")
                if (lineFiles != null) {
                    for (fileName in lineFiles) {
                        val jsonString = assets.open("lines/$fileName").bufferedReader().use { it.readText() }
                        val jsonObject = JSONObject(jsonString)

                        withContext(Dispatchers.Main) {
                            drawGeoJsonLayer(jsonObject)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun drawGeoJsonLayer(geoJsonObject: JSONObject) {
        val layer = GeoJsonLayer(googleMap, geoJsonObject)

        // Cache your IconGenerators to save memory if multiple labels share a line
        val labelIcons = mutableMapOf<String, BitmapDescriptor>()

        for (feature in layer.features) {
            val type = feature.getProperty("type")
            val zIndex = feature.getProperty("z_index").toDouble().toFloat()

            when (type) {
                "path" -> {
                    val colorHex = feature.getProperty("color")
                    val lineStyle = GeoJsonLineStringStyle()
                    lineStyle.color = colorHex.toColorInt()
                    lineStyle.width = 12f
                    lineStyle.zIndex = zIndex
                    feature.lineStringStyle = lineStyle
                }
                "label" -> {
                    val lineName = feature.getProperty("line_name")
                    val colorHex = feature.getProperty("color")

                    // Renamed to 'labelBitmap' to avoid the naming collision
                    val labelBitmap = labelIcons.getOrPut(lineName) {
                        val iconGenerator = IconGenerator(this@MainActivity)
                        val ovalBackground = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 50f
                            setColor(colorHex.toColorInt())
                            setStroke(4, Color.WHITE)
                        }
                        iconGenerator.setBackground(ovalBackground)
                        iconGenerator.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Inverse)
                        BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(lineName))
                    }

                    val pointStyle = GeoJsonPointStyle()
                    pointStyle.setIcon(labelBitmap) // Use the explicit setter
                    pointStyle.setZIndex(zIndex)

                    feature.pointStyle = pointStyle
                }
                "stop" -> {
                    val pointStyle = GeoJsonPointStyle()
                    pointStyle.setIcon(stopIcon) // Use the explicit setter
                    pointStyle.setZIndex(zIndex)
                    pointStyle.setAnchor(0.5f, 0.5f) // Use the explicit dual-parameter setter
                    pointStyle.setVisible(false) // Default to hidden

                    feature.pointStyle = pointStyle
                }
            }
        }

        layer.addLayerToMap()
        transitLayers.add(layer)
    }

    private fun updateMapVisibility(zoom: Float) {
        // 1. Calculate the new tier
        val newTier = getZoomTier(zoom)

        // 2. The Magic: If we haven't crossed a threshold, STOP HERE.
        if (newTier == currentZoomTier) return

        // Update the tracker
        currentZoomTier = newTier

        // 3. Define the boolean flags based on the exact zoom level
        val showStops = zoom >= 15.0f
        val showBusLabels = zoom >= 14.0f
        val showTrolleyLabels = zoom >= 13.5f
        val showTramLabels = zoom >= 13.0f
        val showHevLabels = zoom >= 12.5f

        // 4. Run the expensive loop ONLY when necessary
        for (layer in transitLayers) {
            for (feature in layer.features) {
                val type = feature.getProperty("type")
                if (type == "path") continue

                val priorityStr = feature.getProperty("priorityLevel")
                val priorityLevel = priorityStr?.toDoubleOrNull()?.toInt() ?: 1

                val shouldBeVisible = if (type == "stop") {
                    showStops
                } else {
                    when (priorityLevel) {
                        4 -> true               // Metros
                        5 -> showHevLabels      // HEVs
                        3 -> showTramLabels     // Trams
                        2 -> showTrolleyLabels  // Trolleys
                        1 -> showBusLabels      // Buses
                        else -> true
                    }
                }

                val currentStyle = feature.pointStyle
                if (currentStyle.isVisible != shouldBeVisible) {
                    currentStyle.setVisible(shouldBeVisible)
                    feature.pointStyle = currentStyle
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment

        mapFragment.getMapAsync { map ->
            googleMap = map

            // 1. Clean the UI
            googleMap.isBuildingsEnabled = false
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))

            googleMap.setMinZoomPreference(11.25f)

            // Lock the maximum zoom IN (optional, prevents zooming into blank gray pixels)
            googleMap.setMaxZoomPreference(18.0f)

            // 1. Calculate a safe distance to push the buttons down (e.g., 60dp converted to pixels)
            val topPadding = (60 * resources.displayMetrics.density).toInt()

            // 2. Apply padding: left, top, right, bottom
            googleMap.setPadding(0, topPadding, 0, 0)

            // 2. Lock the map to Budapest and set the starting zoom
            val budapestBounds = LatLngBounds(
                LatLng(47.4200, 18.9500), // Southwest corner (Kelenföld / Buda Hills edge)
                LatLng(47.5700, 19.1700)  // Northeast corner (Újpest / Örs vezér tere edge)
            )
            googleMap.setLatLngBoundsForCameraTarget(budapestBounds)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(budapestBounds.center, 12f))

            // 3. Turn on the User Location Blue Dot
            // (Make sure your permission request logic still fires before or around this!)
            checkLocationPermission()

            // 4. Load the transit data
            loadAllTransitLines()

            // 5. Instantly clean the map based on the starting zoom level
            updateMapVisibility(googleMap.cameraPosition.zoom)

            // 6. Keep it clean when the user zooms in and out
            googleMap.setOnCameraIdleListener {
                updateMapVisibility(googleMap.cameraPosition.zoom)
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableUserLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableUserLocation() {
        try {
            googleMap.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}