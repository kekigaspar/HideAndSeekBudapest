package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon as MapboxPolygon
import com.mapbox.geojson.MultiPolygon as MapboxMultiPolygon
import com.mapbox.geojson.Geometry as MapboxGeometry
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.geom.MultiPolygon as JtsMultiPolygon

object UnionHelper {
    private val factory = GeometryFactory()

    // Takes a list of overlapping features and returns one unified Feature
    fun mergeFeatures(features: List<Feature>): Feature? {
        if (features.isEmpty()) return null

        var combinedGeometry: Geometry? = null

        for (feature in features) {
            val geom = feature.geometry()

            // We only process Polygons (which both our Circle and Line tools create)
            if (geom is MapboxPolygon) {
                val jtsGeom = toJtsPolygon(geom)

                // This is the JTS magic that physically merges intersecting shapes
                combinedGeometry = if (combinedGeometry == null) {
                    jtsGeom
                } else {
                    combinedGeometry.union(jtsGeom)
                }
            }
        }

        return combinedGeometry?.let { fromJts(it) }?.let { Feature.fromGeometry(it) }
    }

    // --- TRANSLATORS BELOW ---

    private fun toJtsPolygon(mapboxPolygon: MapboxPolygon): JtsPolygon {
        val rings = mapboxPolygon.coordinates()
        if (rings.isEmpty()) return factory.createPolygon()

        // 1. Create the outer boundary
        val exterior = factory.createLinearRing(rings[0].map { Coordinate(it.longitude(), it.latitude()) }.toTypedArray())

        // 2. Create any interior holes (Needed for your 'Circle Outside' tool)
        val holes = rings.drop(1).map { ring ->
            factory.createLinearRing(ring.map { Coordinate(it.longitude(), it.latitude()) }.toTypedArray())
        }.toTypedArray()

        return factory.createPolygon(exterior, holes)
    }

    private fun fromJts(jtsGeom: Geometry): MapboxGeometry? {
        return when (jtsGeom) {
            is JtsPolygon -> toMapboxPolygon(jtsGeom)
            // If the user draws two shapes that DON'T touch, JTS creates a MultiPolygon
            is JtsMultiPolygon -> {
                val polys = mutableListOf<MapboxPolygon>()
                for (i in 0 until jtsGeom.numGeometries) {
                    polys.add(toMapboxPolygon(jtsGeom.getGeometryN(i) as JtsPolygon))
                }
                MapboxMultiPolygon.fromPolygons(polys)
            }
            else -> null
        }
    }

    private fun toMapboxPolygon(jtsPolygon: JtsPolygon): MapboxPolygon {
        val rings = mutableListOf<List<Point>>()

        // Convert exterior
        val extCoords = jtsPolygon.exteriorRing.coordinates
        rings.add(extCoords.map { Point.fromLngLat(it.x, it.y) })

        // Convert holes
        for (i in 0 until jtsPolygon.numInteriorRing) {
            val intCoords = jtsPolygon.getInteriorRingN(i).coordinates
            rings.add(intCoords.map { Point.fromLngLat(it.x, it.y) })
        }

        return MapboxPolygon.fromLngLats(rings)
    }
}