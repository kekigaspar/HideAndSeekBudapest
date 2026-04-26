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

class MainActivity : AppCompatActivity() {

    private lateinit var googleMap: GoogleMap

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            enableUserLocation()
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

            checkLocationPermission()
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