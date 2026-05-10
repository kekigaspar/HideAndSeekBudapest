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

    // THE FIX 1: 40km easily covers Budapest, but is small enough
    // that Earth's curvature won't warp the straight line on the map.
    private const val ZONE_DISTANCE_KM = 40.0

    override fun generateFeature(params: HotterColderParams): Feature {
        // 1. Find the exact midpoint
        val midpoint = TurfMeasurement.midpoint(params.previousLocation, params.currentLocation)

        // 2. Find the direction the seeker moved
        val bearingMoved = TurfMeasurement.bearing(params.previousLocation, params.currentLocation)

        // 3. Determine the direction of the "Hotter" or "Colder" zone
        val targetBearing = if (params.isHotter) bearingMoved else (bearingMoved - 180.0)

        // 4. Calculate the perpendicular bisector line
        val leftBearing = bearingMoved - 90.0
        val rightBearing = bearingMoved + 90.0

        val leftEdge = TurfMeasurement.destination(midpoint, ZONE_DISTANCE_KM, leftBearing, TurfConstants.UNIT_KILOMETERS)
        val rightEdge = TurfMeasurement.destination(midpoint, ZONE_DISTANCE_KM, rightBearing, TurfConstants.UNIT_KILOMETERS)

        // 5. Project the edges forward to create the massive box
        val farLeft = TurfMeasurement.destination(leftEdge, ZONE_DISTANCE_KM, targetBearing, TurfConstants.UNIT_KILOMETERS)
        val farRight = TurfMeasurement.destination(rightEdge, ZONE_DISTANCE_KM, targetBearing, TurfConstants.UNIT_KILOMETERS)

        // THE FIX 2: Counter-Clockwise winding order prevents MapLibre rendering glitches.
        // Order: Right -> Far Right -> Far Left -> Left -> Back to Right
        val coordinates = listOf(
            rightEdge,
            farRight,
            farLeft,
            leftEdge,
            rightEdge
        )

        return Feature.fromGeometry(Polygon.fromLngLats(listOf(coordinates)))
    }
}