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
import java.io.InputStreamReader
import com.google.android.gms.maps.model.PolylineOptions
import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.google.maps.android.ui.IconGenerator
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var googleMap: GoogleMap

    private val transitStops = mutableListOf<com.google.android.gms.maps.model.Circle>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            enableUserLocation()
        }
    }

    private fun loadAllTransitLines() {
        try {
            // Read all file names located specifically in the "lines" subfolder
            val lineFiles = assets.list("lines")

            if (lineFiles != null) {
                for (fileName in lineFiles) {
                    // Pass the full relative path to the drawing function
                    drawTransitLine("lines/$fileName")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drawTransitLine(filePath: String) {
        try {
            val inputStream = assets.open(filePath)
            val jsonString = InputStreamReader(inputStream).readText()
            val jsonObject = JSONObject(jsonString)

            val lineName = jsonObject.getString("line_name")
            val hexColor = jsonObject.getString("color")
            val parsedColor = hexColor.toColorInt()
            val zIndex = jsonObject.getDouble("z_index").toFloat()

            // 1. Draw the Line
            val coordinatesArray = jsonObject.getJSONArray("coordinates")
            val polylineOptions = PolylineOptions()
                .color(parsedColor)
                .width(12f)
                .zIndex(zIndex)

            for (i in 0 until coordinatesArray.length()) {
                val coordinatePair = coordinatesArray.getJSONArray(i)
                polylineOptions.add(LatLng(coordinatePair.getDouble(0), coordinatePair.getDouble(1)))
            }
            googleMap.addPolyline(polylineOptions)

            // 2. Draw the Label (Placed roughly in the middle of the line)
            val midIndex = coordinatesArray.length() / 2
            val midCoord = coordinatesArray.getJSONArray(midIndex)
            val midLatLng = LatLng(midCoord.getDouble(0), midCoord.getDouble(1))

            val iconGenerator = IconGenerator(this)
            iconGenerator.setColor(parsedColor)
            iconGenerator.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Inverse)
            val iconBitmap = iconGenerator.makeIcon(lineName)

            googleMap.addMarker(
                MarkerOptions()
                    .position(midLatLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
                    .zIndex(zIndex + 0.5f) // Keep label slightly above the line
            )

            // 3. Draw the Stops
            val stopsArray = jsonObject.getJSONArray("stops")
            for (i in 0 until stopsArray.length()) {
                val stopObj = stopsArray.getJSONObject(i)
                val stopLatLng = LatLng(stopObj.getDouble("lat"), stopObj.getDouble("lng"))

                val circle = googleMap.addCircle(
                    CircleOptions()
                        .center(stopLatLng)
                        .radius(15.0) // 15 meters wide
                        .fillColor(Color.WHITE)
                        .strokeColor(Color.BLACK)
                        .strokeWidth(3f)
                        .zIndex(zIndex + 0.1f)
                        .visible(false) // Hidden by default
                )
                transitStops.add(circle)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment

        mapFragment.getMapAsync { map ->
            googleMap = map

            val budapestBounds = LatLngBounds(
                LatLng(47.4200, 18.9500), // Southwest corner (Kelenföld / Buda Hills edge)
                LatLng(47.5700, 19.1700)  // Northeast corner (Újpest / Örs vezér tere edge)
            )

            googleMap.setLatLngBoundsForCameraTarget(budapestBounds)
            googleMap.setMinZoomPreference(11.5f)
            val budapestCenter = LatLng(47.4979, 19.0402)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(budapestCenter, 12f))

            val topPadding = (60 * resources.displayMetrics.density).toInt()
            googleMap.setPadding(0, topPadding, 0, 0)

            loadAllTransitLines()

            checkLocationPermission()

            googleMap.setOnCameraMoveListener {
                val zoomLevel = googleMap.cameraPosition.zoom
                val shouldShowStops = zoomLevel >= 13.5f // Adjust this float to change when stops appear

                // Only loop through and update if the visibility state actually needs to change
                if (transitStops.isNotEmpty() && transitStops[0].isVisible != shouldShowStops) {
                    transitStops.forEach { it.isVisible = shouldShowStops }
                }
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