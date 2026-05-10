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

        // Idiomatic Kotlin functional chain to merge geometries
        val combinedGeometry = features
            .mapNotNull { it.geometry() as? MapboxPolygon }
            .map { toJtsPolygon(it) }
            .reduceOrNull { acc: Geometry, jtsPolygon: JtsPolygon -> acc.union(jtsPolygon) }

        return combinedGeometry?.let { fromJts(it) }?.let { Feature.fromGeometry(it) }
    }

    // --- TRANSLATORS BELOW ---

    private fun toJtsPolygon(mapboxPolygon: MapboxPolygon): JtsPolygon {
        val rings = mapboxPolygon.coordinates()
        if (rings.isEmpty()) return factory.createPolygon()

        val exterior = factory.createLinearRing(rings[0].map { Coordinate(it.longitude(), it.latitude()) }.toTypedArray())

        val holes = rings.drop(1).map { ring ->
            factory.createLinearRing(ring.map { Coordinate(it.longitude(), it.latitude()) }.toTypedArray())
        }.toTypedArray()

        return factory.createPolygon(exterior, holes)
    }

    private fun fromJts(jtsGeom: Geometry): MapboxGeometry? {
        return when (jtsGeom) {
            is JtsPolygon -> toMapboxPolygon(jtsGeom)
            is JtsMultiPolygon -> {
                val polys = (0 until jtsGeom.numGeometries).map { i ->
                    toMapboxPolygon(jtsGeom.getGeometryN(i) as JtsPolygon)
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