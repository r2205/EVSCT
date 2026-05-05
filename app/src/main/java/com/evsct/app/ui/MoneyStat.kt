package com.evsct.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.Format

/**
 * Stat-style cell for a money total that may span multiple currencies.
 * - Single currency renders like a regular Stat (titleMedium value).
 * - Mixed currencies stack each "$xxx CCC" on its own line at titleSmall,
 *   so the column width and surrounding row layout stay stable instead of
 *   getting visibly crowded by a long single-line breakdown.
 */
@Composable
fun MoneyStat(
    label: String,
    totals: CurrencyTotals,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            totals.isEmpty -> {
                Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            !totals.isMixed -> {
                val (currency, value) = totals.byCurrency.entries.first()
                Text(
                    Format.money(value, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            else -> {
                totals.byCurrency.entries
                    .sortedByDescending { it.value }
                    .forEach { (currency, value) ->
                        Text(
                            Format.money(value, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
