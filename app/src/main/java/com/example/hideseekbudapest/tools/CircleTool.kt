package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfTransformation

data class CircleParams(
    val center: Point,
    val radiusKm: Double,
    val isInside: Boolean
) : ToolParams

object CircleTool : MapAreaTool<CircleParams> {
    override val toolName = "Circle Exclusion"

    override fun generateFeature(params: CircleParams): Feature {
        val circle = TurfTransformation.circle(
            params.center,
            params.radiusKm,
            64,
            TurfConstants.UNIT_KILOMETERS
        )

        return if (params.isInside) {
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