package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation

class CircleTool(private val isInside: Boolean) : MapTool {
    private val pins = mutableListOf<Point>()

    override fun processClick(point: Point): Feature? {
        pins.add(point)

        if (pins.size == 2) {
            val p1 = pins[0]
            val p2 = pins[1]
            val radius = TurfMeasurement.distance(p1, p2, TurfConstants.UNIT_KILOMETERS)
            val circle = TurfTransformation.circle(p1, radius, 64, TurfConstants.UNIT_KILOMETERS)

            val feature = if (isInside) {
                Feature.fromGeometry(circle)
            } else {
                // "Donut hole" logic for outside shading
                val worldCoords = listOf(
                    Point.fromLngLat(-180.0, 90.0), Point.fromLngLat(180.0, 90.0),
                    Point.fromLngLat(180.0, -90.0), Point.fromLngLat(-180.0, -90.0),
                    Point.fromLngLat(-180.0, 90.0)
                )
                Feature.fromGeometry(Polygon.fromLngLats(listOf(worldCoords, circle.coordinates()[0])))
            }

            reset() // clear pins for the next drawing
            return feature
        }

        return null // Still waiting for the second tap
    }

    override fun getTempPins(): List<Point> = pins

    override fun reset() {
        pins.clear()
    }
}