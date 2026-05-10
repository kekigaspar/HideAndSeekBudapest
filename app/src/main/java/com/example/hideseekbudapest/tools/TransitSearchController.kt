package com.example.hideseekbudapest.tools

import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiLineString
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.sources.VectorSource

object TransitSearchController {

    /**
     * Executes the "Satellite Bounce" animation to find and frame a transit line.
     * Marked as 'suspend' so it can handle the animation delays smoothly.
     */
    suspend fun findAndZoomToLine(map: MapLibreMap, lineName: String) {
        // 1. JUMP OUT SAFELY: Center on Budapest but stay at Zoom 12.0
        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(47.4979, 19.0402))
                    .zoom(12.0)
                    .build()
            ),
            800
        )

        // 2. WAIT: Give the map time to fly there and load the tiles
        delay(1200)

        val source = map.style?.getSourceAs<VectorSource>("transit-source") ?: return

        // 3. QUERY: Ask for the line data now that the tiles are loaded
        val features = source.querySourceFeatures(
            arrayOf("transit_data"),
            eq(toString(get("line_name")), literal(lineName))
        )

        if (features.isEmpty()) return

        val boundsBuilder = LatLngBounds.Builder()
        var pointsFound = 0

        // 4. PARSE GEOMETRY safely from JSON to avoid casting errors
        for (feature in features) {
            val geom = feature.geometry() ?: continue

            if (geom.type() == "LineString") {
                val line = LineString.fromJson(geom.toJson())
                for (point in line.coordinates()) {
                    boundsBuilder.include(LatLng(point.latitude(), point.longitude()))
                    pointsFound++
                }
            } else if (geom.type() == "MultiLineString") {
                val multiLine = MultiLineString.fromJson(geom.toJson())
                for (line in multiLine.coordinates()) {
                    for (point in line) {
                        boundsBuilder.include(LatLng(point.latitude(), point.longitude()))
                        pointsFound++
                    }
                }
            }
        }

        // 5. DIVE IN: Tightly frame the newly assembled route
        try {
            if (pointsFound > 1) {
                val bounds = boundsBuilder.build()
                map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}