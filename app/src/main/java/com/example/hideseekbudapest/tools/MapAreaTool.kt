package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature

// 1. A blank interface that all specific parameter classes will implement.
// This allows us to pass different inputs (radii, coordinates, booleans) dynamically.
interface ToolParams

// 2. The blueprint for every tool.
interface MapAreaTool<T : ToolParams> {
    val toolName: String

    // Every tool takes its specific parameters and spits out a GeoJSON Feature
    fun generateFeature(params: T): Feature
}