package com.evsct.app.ui.stats

import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.util.Format
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Render [ui] as a single, self-contained HTML document and stream it to
 * [out] as UTF-8. Companion to [writeYearRecapPdf]: same [YearRecapUi] data
 * model, but the output is a richer, responsive page than the hand-laid-out
 * single-page PDF — a full monthly table (cost *and* energy, where the PDF
 * only charts cost) plus a Cost/Energy toggle on the chart.
 *
 * Deliberately dependency-free:
 *  - No `android.*` imports, so the renderer is plain JVM and unit-testable
 *    (unlike the Canvas-based PDF renderer).
 *  - No external resources (no CDN scripts, fonts, or images): everything is
 *    inline so the file opens offline in any browser. This also keeps the
 *    app's "nothing leaves the phone" promise — a CDN-loaded chart library
 *    would quietly phone home the moment the report was opened.
 *
 * The caller owns [out] and is responsible for closing it.
 */
internal fun writeYearRecapHtml(
    out: OutputStream,
    ui: YearRecapUi,
    units: UserUnits,
) {
    val genStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val titleSuffix = ui.vehicleName?.takeIf { it.isNotBlank() }?.let { " · ${esc(it)}" } ?: ""

    val html = buildString {
        append("<!DOCTYPE html>\n")
        append("<html lang=\"en\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>EVSCT — ${ui.selectedYear} Recap</title>\n")
        append("<style>\n").append(STYLE).append("\n</style>\n")
        append("</head>\n<body>\n")

        // --- Header ----------------------------------------------------------
        append("<header>\n")
        append("<h1>EVSCT — ${ui.selectedYear} Recap$titleSuffix</h1>\n")
        append("<p class=\"sub\">Generated ${esc(genStamp)}</p>\n")
        append("</header>\n")

        // --- Headline grid ---------------------------------------------------
        append("<section class=\"headline\">\n")
        appendStat("Sessions", ui.sessionCount.toString())
        appendStat("Total cost", Format.money(ui.totalCost, ui.costCurrency))
        appendStat("Total energy", Format.kwh(ui.totalKwh))
        appendStat("Distance", Format.distance(ui.totalDistanceKm, units.useMiles))
        append("</section>\n")

        if (ui.excludedByCurrency > 0) {
            val plural = if (ui.excludedByCurrency == 1) "" else "s"
            append(
                "<p class=\"note\">Cost totals are in ${esc(ui.costCurrency)}. " +
                    "${ui.excludedByCurrency} session$plural in another currency excluded.</p>\n"
            )
        }

        // --- Monthly chart + table ------------------------------------------
        append("<section class=\"card\">\n")
        append("<div class=\"card-head\">\n")
        append("<h2>Monthly</h2>\n")
        append("<div class=\"toggle\" role=\"group\" aria-label=\"Chart metric\">\n")
        append("<button type=\"button\" class=\"active\" data-metric=\"cost\">Cost</button>\n")
        append("<button type=\"button\" data-metric=\"energy\">Energy</button>\n")
        append("</div>\n</div>\n")

        append(monthlyChartSvg(ui))

        append("<table>\n<thead><tr><th>Month</th>")
        append("<th class=\"num\">Cost (${esc(ui.costCurrency)})</th>")
        append("<th class=\"num\">Energy</th></tr></thead>\n<tbody>\n")
        for (i in ui.monthlyCost.indices) {
            val label = ui.monthlyCost[i].first
            val cost = ui.monthlyCost[i].second
            val kwh = ui.monthlyKwh.getOrNull(i)?.second ?: 0.0
            append("<tr><td>${esc(label)}</td>")
            append("<td class=\"num\">${esc(Format.money(cost, ui.costCurrency))}</td>")
            append("<td class=\"num\">${esc(Format.kwh(kwh))}</td></tr>\n")
        }
        append("</tbody>\n</table>\n")
        append("</section>\n")

        // --- Top brands ------------------------------------------------------
        if (ui.topBrands.isNotEmpty()) {
            append("<section class=\"card\">\n")
            append("<h2>Top brands by spend (${esc(ui.costCurrency)})</h2>\n")
            val brandMax = ui.topBrands.maxOf { it.second }.coerceAtLeast(0.01)
            append("<div class=\"bars\">\n")
            ui.topBrands.forEach { (brand, amount) ->
                val pct = (amount / brandMax * 100.0).coerceIn(0.0, 100.0)
                append("<div class=\"bar-row\">")
                append("<span class=\"bar-label\">${esc(brand)}</span>")
                append("<span class=\"bar-track\"><span class=\"bar-fill\" style=\"width:${fmt(pct)}%\"></span></span>")
                append("<span class=\"bar-value\">${esc(Format.money(amount, ui.costCurrency))}</span>")
                append("</div>\n")
            }
            append("</div>\n</section>\n")
        }

        // --- Longest trip ----------------------------------------------------
        ui.longestTrip?.let { trip ->
            append("<section class=\"card\">\n")
            append("<h2>Longest trip</h2>\n")
            append("<p class=\"trip-name\">${esc(trip.name)}</p>\n")
            val parts = buildList {
                add(Format.distance(trip.distanceKm, units.useMiles))
                add("${trip.sessionCount} session" + if (trip.sessionCount == 1) "" else "s")
                if (trip.totalCost != null && trip.currency != null) {
                    add(Format.money(trip.totalCost, trip.currency))
                }
            }
            append("<p class=\"sub\">${esc(parts.joinToString(" · "))}</p>\n")
            append("</section>\n")
        }

        // --- Footer ----------------------------------------------------------
        val footerExtra = if (ui.excludedByCurrency > 0) {
            val plural = if (ui.excludedByCurrency == 1) "" else "s"
            " (${ui.excludedByCurrency} session$plural in another currency excluded from cost.)"
        } else ""
        append("<footer>Generated by EVSCT. ")
        append("${ui.sessionCount} sessions in ${ui.selectedYear}.${esc(footerExtra)}")
        append("</footer>\n")

        append("<script>\n").append(SCRIPT).append("\n</script>\n")
        append("</body>\n</html>\n")
    }

    out.write(html.toByteArray(Charsets.UTF_8))
}

/* --- private helpers --- */

private fun StringBuilder.appendStat(label: String, value: String) {
    append("<div class=\"stat\"><div class=\"stat-value\">")
    append(esc(value))
    append("</div><div class=\"stat-label\">")
    append(esc(label))
    append("</div></div>\n")
}

/**
 * Inline SVG bar chart with two stacked series — cost and energy — each in
 * its own `<g>`. Only one is visible at a time; the toggle button flips a
 * class on the chart container (see [SCRIPT]) so no values are recomputed in
 * the browser. Month labels live in a third, always-visible group. With
 * JavaScript disabled the cost series shows and the table below still carries
 * every number, so the report degrades gracefully.
 */
private fun monthlyChartSvg(ui: YearRecapUi): String {
    val labels = ui.monthlyCost.map { it.first }
    val costs = ui.monthlyCost.map { it.second }
    val kwh = ui.monthlyCost.indices.map { ui.monthlyKwh.getOrNull(it)?.second ?: 0.0 }
    val n = labels.size
    if (n == 0) return ""

    val gap = 6.0
    val barW = ((CHART_W - (n - 1) * gap) / n).coerceAtLeast(1.0)

    fun seriesGroup(values: List<Double>, cssClass: String): String {
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
        return buildString {
            append("<g class=\"$cssClass\">")
            values.forEachIndexed { i, v ->
                if (v > 0) {
                    val x = i * (barW + gap)
                    val h = (v / max * CHART_PLOT_H)
                    val y = CHART_PLOT_H - h
                    append(
                        "<rect x=\"${fmt(x)}\" y=\"${fmt(y)}\" " +
                            "width=\"${fmt(barW)}\" height=\"${fmt(h)}\" rx=\"2\"/>"
                    )
                }
            }
            append("</g>")
        }
    }

    val axis = buildString {
        append("<g class=\"axis\">")
        labels.forEachIndexed { i, label ->
            val cx = i * (barW + gap) + barW / 2
            append("<text x=\"${fmt(cx)}\" y=\"${fmt(CHART_PLOT_H + 14)}\">${esc(label)}</text>")
        }
        append("</g>")
    }

    val viewH = CHART_PLOT_H + 20
    return buildString {
        append("<svg class=\"chart\" viewBox=\"0 0 ${fmt(CHART_W)} ${fmt(viewH)}\" ")
        append("preserveAspectRatio=\"none\" role=\"img\" aria-label=\"Monthly chart\">")
        append("<line class=\"baseline\" x1=\"0\" y1=\"${fmt(CHART_PLOT_H)}\" ")
        append("x2=\"${fmt(CHART_W)}\" y2=\"${fmt(CHART_PLOT_H)}\"/>")
        append(seriesGroup(costs, "bars bars-cost"))
        append(seriesGroup(kwh, "bars bars-energy"))
        append(axis)
        append("</svg>\n")
    }
}

/** Format a Double for SVG/CSS output with a '.' decimal separator,
 *  regardless of the device locale (a comma here would break the markup). */
private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)

/** HTML-escape user-controlled text. Brand, trip, and vehicle names flow
 *  straight from user input into the document, so this is the HTML analog of
 *  the CSV formula-injection defense in [com.evsct.app.data.csv.Csv]. */
private fun esc(s: String): String = buildString(s.length) {
    for (c in s) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}

private const val CHART_W = 600.0
private const val CHART_PLOT_H = 180.0

private val STYLE = """
:root {
  --green: #2E7D32;
  --green-soft: #C8E6C9;
  --ink: #1b1b1b;
  --muted: #5f6368;
  --line: #e0e0e0;
  --bg: #fafafa;
  --card: #ffffff;
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 16px;
  font-family: -apple-system, Roboto, "Segoe UI", system-ui, sans-serif;
  color: var(--ink); background: var(--bg);
  max-width: 760px; margin-inline: auto;
  -webkit-text-size-adjust: 100%;
}
header { margin-bottom: 16px; }
h1 { font-size: 1.5rem; margin: 0 0 2px; color: var(--green); }
h2 { font-size: 1.05rem; margin: 0 0 12px; }
.sub { color: var(--muted); margin: 0; font-size: 0.85rem; }
.note { color: var(--muted); font-size: 0.8rem; margin: 8px 0 0; }
.headline {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 12px 0;
}
.stat {
  background: var(--green-soft); border-radius: 10px; padding: 12px; text-align: center;
}
.stat-value { font-size: 1.05rem; font-weight: 700; color: var(--green); }
.stat-label { font-size: 0.72rem; color: var(--muted); margin-top: 2px; }
.card {
  background: var(--card); border: 1px solid var(--line); border-radius: 12px;
  padding: 16px; margin: 12px 0;
}
.card-head { display: flex; align-items: center; justify-content: space-between; }
.card-head h2 { margin: 0; }
.toggle { display: inline-flex; border: 1px solid var(--line); border-radius: 999px; overflow: hidden; }
.toggle button {
  border: 0; background: transparent; padding: 4px 14px; font-size: 0.8rem;
  color: var(--muted); cursor: pointer;
}
.toggle button.active { background: var(--green); color: #fff; }
.chart { width: 100%; height: 220px; display: block; margin: 14px 0; }
.chart .bars rect { fill: var(--green); }
.chart .bars-energy { display: none; }
.chart.show-energy .bars-cost { display: none; }
.chart.show-energy .bars-energy { display: inline; }
.chart .baseline { stroke: var(--line); stroke-width: 1; }
.chart .axis text { fill: var(--muted); font-size: 11px; text-anchor: middle; }
table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
th, td { padding: 6px 4px; border-bottom: 1px solid var(--line); text-align: left; }
th { color: var(--muted); font-weight: 600; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.bars { display: flex; flex-direction: column; gap: 8px; }
.bar-row { display: grid; grid-template-columns: 8rem 1fr auto; gap: 8px; align-items: center; }
.bar-label { font-size: 0.85rem; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { background: var(--line); border-radius: 4px; height: 14px; overflow: hidden; }
.bar-fill { display: block; height: 100%; background: var(--green); }
.bar-value { font-size: 0.85rem; font-variant-numeric: tabular-nums; }
.trip-name { font-weight: 600; margin: 0 0 2px; }
footer { color: var(--muted); font-size: 0.75rem; margin-top: 20px; text-align: center; }
@media (max-width: 480px) {
  .headline { grid-template-columns: repeat(2, 1fr); }
  .bar-row { grid-template-columns: 6rem 1fr auto; }
}
""".trim()

private val SCRIPT = """
document.querySelectorAll('.toggle button').forEach(function (btn) {
  btn.addEventListener('click', function () {
    var metric = btn.getAttribute('data-metric');
    document.querySelectorAll('.chart').forEach(function (c) {
      c.classList.toggle('show-energy', metric === 'energy');
    });
    document.querySelectorAll('.toggle button').forEach(function (b) {
      b.classList.toggle('active', b === btn);
    });
  });
});
""".trim()
