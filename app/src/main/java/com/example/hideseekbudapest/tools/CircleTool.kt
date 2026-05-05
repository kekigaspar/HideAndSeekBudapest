package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfTransformation

object CircleTool {

    // Generates the geometry based on the exact parameters passed in
    fun generateShape(center: Point, radiusKm: Double, isInside: Boolean): Feature {
        val circle = TurfTransformation.circle(center, radiusKm, 64, TurfConstants.UNIT_KILOMETERS)

        return if (isInside) {
            Feature.fromGeometry(circle)
        } else {
            val worldCoords = listOf(
                Point.fromLngLat(-180.0, 90.0), Point.fromLngLat(180.0, 90.0),
                Point.fromLngLat(180.0, -90.0), Point.fromLngLat(-180.0, -90.0),
                Point.fromLngLat(-180.0, 90.0)
            )
            Feature.fromGeometry(Polygon.fromLngLats(listOf(worldCoords, circle.coordinates()[0])))
        }
    }
}