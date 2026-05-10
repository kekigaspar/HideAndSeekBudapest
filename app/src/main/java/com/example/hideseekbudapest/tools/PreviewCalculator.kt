package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Projection
import kotlin.math.hypot

object PreviewCalculator {

    /**
     * Translates a geographic radius into a screen pixel radius.
     */
    fun calculateRadiusInPixels(
        centerLatLng: LatLng,
        radiusKm: Double,
        projection: Projection
    ): Float {
        if (radiusKm <= 0.0) return 0f

        val centerPoint = Point.fromLngLat(centerLatLng.longitude, centerLatLng.latitude)

        // Find a point exactly 'radiusKm' away directly to the East (90 degrees)
        val edgePoint = TurfMeasurement.destination(
            centerPoint,
            radiusKm,
            90.0,
            TurfConstants.UNIT_KILOMETERS
        )
        val edgeLatLng = LatLng(edgePoint.latitude(), edgePoint.longitude())

        // Convert both geographic points to screen pixels
        val centerPixel = projection.toScreenLocation(centerLatLng)
        val edgePixel = projection.toScreenLocation(edgeLatLng)

        val deltaX = edgePixel.x - centerPixel.x
        val deltaY = edgePixel.y - centerPixel.y

        // Pythagorean theorem for the distance on screen
        return hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
    }
}