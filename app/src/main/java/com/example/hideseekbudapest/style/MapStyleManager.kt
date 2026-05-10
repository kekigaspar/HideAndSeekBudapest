package com.example.hideseekbudapest.style

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import java.io.File

object MapStyleManager {
    const val EXCLUSION_SOURCE_ID = "exclusion-source"

    fun setupTransitAndExclusionStyle(style: Style, dbFile: File) {
        val sourceId = "transit-source"
        style.addSource(VectorSource(sourceId, "mbtiles://${dbFile.absolutePath}"))

        // 1. Transit Lines
        style.addLayer(LineLayer("line-layer", sourceId).apply {
            sourceLayer = "transit_data"
            setProperties(
                lineWidth(4f),
                lineCap("butt"),
                lineJoin("miter"),
                lineColor(get("color")) // Assuming your tiles have a color property
            )
        })

        // 2. Transit Stops
        style.addLayer(CircleLayer("stop-layer", sourceId).apply {
            sourceLayer = "transit_data"
            setProperties(
                circleRadius(3f),
                circleColor(Color.WHITE),
                circleStrokeWidth(1.5f),
                circleStrokeColor(Color.BLACK)
            )
            setFilter(gt(zoom(), literal(13.5)))
        })

        // 3. NEW: Transit Line Labels
        style.addLayer(SymbolLayer("line-label-layer", sourceId).apply {
            sourceLayer = "transit_data"

            // Only show labels when zoomed in fairly close so it doesn't clutter the map
            minZoom = 13.0f

            setProperties(
                // Grab the text from the vector tile metadata (Make sure "line_name" matches your actual JSON key!)
                textField(get("line_name")),

                // Force the text to curve along the physical line
                symbolPlacement("line"),

                // Make the text bold and appropriately sized
                textSize(15f),
                textAllowOverlap(false),
                textColor(Color.BLACK),

                // Add a white halo around the text so it's readable over dark transit lines
                textHaloColor(Color.WHITE),
                textHaloWidth(2.0f),

                // Space out the repeated labels so it's not a solid wall of text
                symbolSpacing(150f)
            )
        })

        // 4. Exclusion Zones
        style.addImage("stripe-id", createStripePattern())
        style.addSource(GeoJsonSource(EXCLUSION_SOURCE_ID))

        style.addLayer(FillLayer("exclusion-layer", EXCLUSION_SOURCE_ID).withProperties(
            fillPattern("stripe-id"),
            fillOpacity(0.3f),
        ))

        style.addLayer(LineLayer("exclusion-outline-layer", EXCLUSION_SOURCE_ID).withProperties(
            lineColor(Color.BLACK),
            lineWidth(3f),
            lineJoin("round"),
            lineCap("round")
        ))
    }

    private fun createStripePattern(): Bitmap {
        val size = 64
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 8f
            isAntiAlias = true
        }

        canvas.drawLine(0f, 0f, size.toFloat(), size.toFloat(), paint)
        canvas.drawLine(0f, -size.toFloat(), size * 2f, size.toFloat(), paint)
        canvas.drawLine(-size.toFloat(), 0f, size.toFloat(), size * 2f, paint)

        return bitmap
    }
}