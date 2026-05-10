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
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import java.io.File

object MapStyleManager {
    const val EXCLUSION_SOURCE_ID = "exclusion-source"

    fun setupTransitAndExclusionStyle(style: Style, dbFile: File) {
        val sourceId = "transit-source"
        style.addSource(VectorSource(sourceId, "mbtiles://${dbFile.absolutePath}"))

        // Transit Lines
        style.addLayer(LineLayer("line-layer", sourceId).apply {
            sourceLayer = "transit_data"
            setProperties(
                lineWidth(4f),
                lineCap("butt"),
                lineJoin("miter"),
                lineColor(get("color"))
            )
        })

        // Transit Stops
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

        // Exclusion Zones
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