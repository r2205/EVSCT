package com.evsct.app.ui.trips

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.evsct.app.R
import com.evsct.app.data.entity.Trip
import com.evsct.app.util.Format

/** "Jul 3, 2026 – Jul 12, 2026"-style label for a trip's dates: a range
 *  when both are set, a one-sided "From …"/"Until …" when only one is, or
 *  null when the trip has no dates. Shared by the trip list rows and the
 *  trip detail header. Composable so the one-sided prefixes localize; both
 *  callers are composables. */
@Composable
internal fun tripDateLabel(trip: Trip): String? {
    val start = trip.startDate
    val end = trip.endDate
    return when {
        start != null && end != null ->
            if (Format.date(start) == Format.date(end)) Format.date(start)
            else "${Format.date(start)} – ${Format.date(end)}"
        start != null -> stringResource(R.string.log_date_from, Format.date(start))
        end != null -> stringResource(R.string.log_date_until, Format.date(end))
        else -> null
    }
}
