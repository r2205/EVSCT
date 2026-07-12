package com.evsct.app.ui.stats

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Web Mercator fit of a recap's stops + trip paths into a normalized
 * viewport, shared by the HTML export's SVG map and the on-screen Compose
 * preview so both frame the map identically. The longer viewport side is
 * [VIEW] units; [width] × [height] is the viewport, and [x]/[y] project a
 * coordinate into it (north up, origin top-left).
 */
internal class RecapMapProjection private constructor(
    private val minX: Double,
    private val maxX: Double,
    private val minY: Double,
    private val maxY: Double,
    private val scale: Double,
    val width: Double,
    val height: Double,
) {
    fun x(lng: Double): Double = (wx(lng) - minX) * scale
    fun y(lat: Double): Double = (wy(lat) - minY) * scale

    /** Whether [ring]'s bounding box overlaps the viewport — used to cull
     *  basemap rings that would draw entirely off-map. */
    fun overlaps(ring: List<Pair<Double, Double>>): Boolean {
        var rMinX = Double.MAX_VALUE
        var rMaxX = -Double.MAX_VALUE
        var rMinY = Double.MAX_VALUE
        var rMaxY = -Double.MAX_VALUE
        ring.forEach { (lat, lng) ->
            val x = wx(lng)
            val y = wy(lat)
            if (x < rMinX) rMinX = x
            if (x > rMaxX) rMaxX = x
            if (y < rMinY) rMinY = y
            if (y > rMaxY) rMaxY = y
        }
        return !(rMaxX < minX || rMinX > maxX || rMaxY < minY || rMinY > maxY)
    }

    companion object {
        /** The longer viewport dimension, so pin/stroke sizes can be
         *  expressed as simple fractions of it. */
        const val VIEW = 1000.0

        /** Minimum world-space span (≈ degrees) so a lone pin (or a tight
         *  cluster) isn't infinitely zoomed. */
        private const val MIN_SPAN = 0.6
        private const val PAD = 0.15
        private const val MIN_RATIO = 0.5
        private const val MAX_RATIO = 2.2

        /** World coordinates: x = longitude, y = -mercator(latitude) so
         *  north is up. */
        private fun wx(lng: Double): Double = lng

        private fun wy(lat: Double): Double {
            val l = lat.coerceIn(-85.0, 85.0)
            return -Math.toDegrees(ln(tan(PI / 4 + Math.toRadians(l) / 2)))
        }

        /** Fit a viewport around every stop and route vertex: pad the
         *  bounding box, floor the span (keeping the true center), and
         *  clamp the aspect ratio so the panel never gets absurdly thin in
         *  one dimension. Null when there's nothing to plot. */
        fun fit(
            stops: List<RecapMapStop>,
            paths: List<RecapTripPath>,
        ): RecapMapProjection? {
            if (stops.isEmpty()) return null
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            fun include(lat: Double, lng: Double) {
                val x = wx(lng)
                val y = wy(lat)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            stops.forEach { include(it.lat, it.lng) }
            paths.forEach { p -> p.points.forEach { include(it.first, it.second) } }

            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            var spanX = (maxX - minX).coerceAtLeast(MIN_SPAN) * (1.0 + PAD)
            var spanY = (maxY - minY).coerceAtLeast(MIN_SPAN) * (1.0 + PAD)
            val ratio = spanX / spanY
            if (ratio < MIN_RATIO) spanX = spanY * MIN_RATIO
            else if (ratio > MAX_RATIO) spanY = spanX / MAX_RATIO

            val scale = VIEW / maxOf(spanX, spanY)
            return RecapMapProjection(
                minX = cx - spanX / 2,
                maxX = cx + spanX / 2,
                minY = cy - spanY / 2,
                maxY = cy + spanY / 2,
                scale = scale,
                width = spanX * scale,
                height = spanY * scale,
            )
        }
    }
}
