package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement

data class HotterColderParams(
    val previousLocation: Point,
    val currentLocation: Point,
    val isHotter: Boolean
) : ToolParams

object HotterColderTool : MapAreaTool<HotterColderParams> {
    override val toolName = "Hotter / Colder"

    // How far out to draw the "half plane" so it covers all of Budapest (and Europe)
    private const val MASSIVE_DISTANCE_KM = 500.0

    override fun generateFeature(params: HotterColderParams): Feature {
        // 1. Find the midpoint between the two checks
        val midpoint = TurfMeasurement.midpoint(params.previousLocation, params.currentLocation)

        // 2. Find the direction the seeker moved
        val bearingMoved = TurfMeasurement.bearing(params.previousLocation, params.currentLocation)

        // 3. Determine which direction the "target zone" is in.
        // If hotter, the target zone is in front of the midpoint. If colder, it's behind.
        val targetBearing = if (params.isHotter) bearingMoved else (bearingMoved - 180.0)

        // 4. Calculate the perpendicular bisector line (left and right of the midpoint)
        val leftBearing = bearingMoved - 90.0
        val rightBearing = bearingMoved + 90.0

        val leftEdge = TurfMeasurement.destination(midpoint, MASSIVE_DISTANCE_KM, leftBearing, TurfConstants.UNIT_KILOMETERS)
        val rightEdge = TurfMeasurement.destination(midpoint, MASSIVE_DISTANCE_KM, rightBearing, TurfConstants.UNIT_KILOMETERS)

        // 5. Project those edges forward to create a massive box covering the half-plane
        val farLeft = TurfMeasurement.destination(leftEdge, MASSIVE_DISTANCE_KM, targetBearing, TurfConstants.UNIT_KILOMETERS)
        val farRight = TurfMeasurement.destination(rightEdge, MASSIVE_DISTANCE_KM, targetBearing, TurfConstants.UNIT_KILOMETERS)

        // 6. Connect the dots into a Polygon
        // Order matters! Must be a closed loop: Right -> Left -> Far Left -> Far Right -> Right
        val coordinates = listOf(
            rightEdge,
            leftEdge,
            farLeft,
            farRight,
            rightEdge
        )

        return Feature.fromGeometry(Polygon.fromLngLats(listOf(coordinates)))
    }
}