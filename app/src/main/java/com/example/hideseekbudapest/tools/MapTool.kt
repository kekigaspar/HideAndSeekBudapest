package com.example.hideseekbudapest.tools

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point

interface MapTool {
    // Called when the user taps the map.
    // Returns a Feature if the shape is finished, or null if it needs more taps.
    fun processClick(point: Point): Feature?

    // Returns the current temporary pins so the map can draw them
    fun getTempPins(): List<Point>

    // Resets the tool if the user cancels
    fun reset()
}