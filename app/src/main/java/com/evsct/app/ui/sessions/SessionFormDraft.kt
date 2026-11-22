package com.evsct.app.ui.sessions

import android.os.Bundle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PaymentMethod
import com.evsct.app.data.entity.PricingModel

/**
 * The session form's unsaved edits, as a [Bundle] the screen's
 * SavedStateHandle carries across process death.
 *
 * Android kills a backgrounded app without warning, and the likeliest moment
 * is the one the form sends the user away for: the camera or photo picker,
 * attaching a receipt. The recreated screen used to reload the row from the
 * database, silently dropping everything typed since it opened.
 *
 * Only fields that differ from the form as loaded are captured, and
 * [restore] lays them over a fresh load. A field the user never touched takes
 * its freshly loaded value — a tracked charge's elapsed-time seed is the
 * current elapsed time, not the one from before the process died — and a
 * field they did keeps their edit. Clearing a value (removing the trip,
 * dropping the coordinates) is an edit like any other and survives too.
 *
 * Only what the user edits travels. The data lists and transient flags are
 * reloaded, and [SessionEditUi.isNew] and [SessionEditUi.isTracking] come
 * from the load. Every editable field must be listed in [FIELDS]: one left
 * out is silently dropped on restore (SessionFormDraftTest round-trips them
 * all). They are the fields SessionEditViewModel's formOnly() keeps.
 */
internal object SessionFormDraft {

    /** The fields of [form] that differ from [baseline]. Empty when the
     *  form is unedited. */
    fun capture(form: SessionEditUi, baseline: SessionEditUi): Bundle =
        Bundle().also { out -> FIELDS.forEach { it.capture(form, baseline, out) } }

    /** [onto] with every field [draft] carries replaced by its value. */
    fun restore(draft: Bundle, onto: SessionEditUi): SessionEditUi =
        FIELDS.fold(onto) { form, field -> field.restore(draft, form) }

    private class Field(
        val capture: (form: SessionEditUi, baseline: SessionEditUi, out: Bundle) -> Unit,
        val restore: (draft: Bundle, onto: SessionEditUi) -> SessionEditUi,
    )

    /** A field stored under [key] with [put] when it differs from the
     *  baseline. [read] gets the restoring form's current value as a
     *  fallback for anything it can't decode. */
    private fun <T> field(
        key: String,
        get: (SessionEditUi) -> T,
        set: SessionEditUi.(T) -> SessionEditUi,
        put: Bundle.(String, T) -> Unit,
        read: Bundle.(String, T) -> T,
    ) = Field(
        capture = { form, baseline, out ->
            val value = get(form)
            if (value != get(baseline)) out.put(key, value)
        },
        restore = { draft, onto ->
            if (draft.containsKey(key)) onto.set(draft.read(key, get(onto))) else onto
        },
    )

    private fun text(key: String, get: (SessionEditUi) -> String, set: SessionEditUi.(String) -> SessionEditUi) =
        field(key, get, set, { k, v -> putString(k, v) }, { k, fallback -> getString(k) ?: fallback })

    /** A row id, where null (no trip, no vehicle) is [NO_ID]. */
    private fun id(key: String, get: (SessionEditUi) -> Long?, set: SessionEditUi.(Long?) -> SessionEditUi) =
        field(
            key, get, set,
            { k, v -> putLong(k, v ?: NO_ID) },
            { k, _ -> getLong(k, NO_ID).takeIf { it != NO_ID } },
        )

    /** An enum by name. A name this build doesn't know keeps the loaded
     *  value rather than failing the restore. */
    private inline fun <reified E : Enum<E>> enumField(
        key: String,
        noinline get: (SessionEditUi) -> E,
        noinline set: SessionEditUi.(E) -> SessionEditUi,
    ) = field(
        key, get, set,
        { k, v -> putString(k, v.name) },
        { k, fallback -> enumValues<E>().firstOrNull { it.name == getString(k) } ?: fallback },
    )

    private val FIELDS: List<Field> = listOf(
        field(
            "sessionStart", { it.sessionStart }, { copy(sessionStart = it) },
            { k, v -> putLong(k, v) }, { k, fallback -> getLong(k, fallback) },
        ),
        text("durationText", { it.durationText }) { copy(durationText = it) },
        text("waitTimeText", { it.waitTimeText }) { copy(waitTimeText = it) },
        text("odometerText", { it.odometerText }) { copy(odometerText = it) },
        text("energyText", { it.energyText }) { copy(energyText = it) },
        text("costText", { it.costText }) { copy(costText = it) },
        text("currency", { it.currency }) { copy(currency = it) },
        // "" is no method: PaymentMethod names are never empty.
        field(
            "paymentMethod", { it.paymentMethod }, { copy(paymentMethod = it) },
            { k, v -> putString(k, v?.name.orEmpty()) },
            { k, fallback ->
                val name = getString(k).orEmpty()
                if (name.isEmpty()) null else PaymentMethod.entries.firstOrNull { it.name == name } ?: fallback
            },
        ),
        text("paymentDetail", { it.paymentDetail }) { copy(paymentDetail = it) },
        text("postedEnergyPriceText", { it.postedEnergyPriceText }) { copy(postedEnergyPriceText = it) },
        text("postedTimeRateText", { it.postedTimeRateText }) { copy(postedTimeRateText = it) },
        enumField<TimeRateUnit>("postedTimeRateUnit", { it.postedTimeRateUnit }) { copy(postedTimeRateUnit = it) },
        text("postedMaxPowerText", { it.postedMaxPowerText }) { copy(postedMaxPowerText = it) },
        text("batteryStartText", { it.batteryStartText }) { copy(batteryStartText = it) },
        text("batteryEndText", { it.batteryEndText }) { copy(batteryEndText = it) },
        enumField<ChargingType>("chargingType", { it.chargingType }) { copy(chargingType = it) },
        enumField<PricingModel>("pricingModel", { it.pricingModel }) { copy(pricingModel = it) },
        text("brand", { it.brand }) { copy(brand = it) },
        text("city", { it.city }) { copy(city = it) },
        text("province", { it.province }) { copy(province = it) },
        text("address", { it.address }) { copy(address = it) },
        text("stationName", { it.stationName }) { copy(stationName = it) },
        text("stallName", { it.stallName }) { copy(stallName = it) },
        id("tripId", { it.tripId }) { copy(tripId = it) },
        field(
            "continuesPrevious", { it.continuesPrevious }, { copy(continuesPrevious = it) },
            { k, v -> putBoolean(k, v) }, { k, fallback -> getBoolean(k, fallback) },
        ),
        id("vehicleId", { it.vehicleId }) { copy(vehicleId = it) },
        text("notes", { it.notes }) { copy(notes = it) },
        field(
            "tags", { it.tags }, { copy(tags = it) },
            { k, v -> putStringArrayList(k, ArrayList(v)) },
            { k, fallback -> getStringArrayList(k)?.toList() ?: fallback },
        ),
        text("tagDraft", { it.tagDraft }) { copy(tagDraft = it) },
        field(
            "receipts", { it.receipts }, { copy(receipts = it) },
            { k, v -> putBundle(k, encodeReceipts(v)) },
            { k, fallback -> getBundle(k)?.let(::decodeReceipts) ?: fallback },
        ),
        // One field for the pair: the form always sets both halves together.
        // NaN stands in for null, which no real coordinate is.
        field(
            "coordinates", { it.latitude to it.longitude },
            { copy(latitude = it.first, longitude = it.second) },
            { k, (lat, lng) -> putDoubleArray(k, doubleArrayOf(lat ?: Double.NaN, lng ?: Double.NaN)) },
            { k, fallback ->
                getDoubleArray(k)?.takeIf { it.size == 2 }
                    ?.let { (lat, lng) -> lat.takeUnless { it.isNaN() } to lng.takeUnless { it.isNaN() } }
                    ?: fallback
            },
        ),
    )

    /** Row ids use [NO_ID] for receipts attached since the form opened;
     *  original names use "" for none (renameReceipt never stores ""). */
    private fun encodeReceipts(receipts: List<UiReceipt>) = Bundle().apply {
        putLongArray(RECEIPT_IDS, LongArray(receipts.size) { receipts[it].id ?: NO_ID })
        putStringArrayList(RECEIPT_PATHS, ArrayList(receipts.map { it.filePath }))
        putStringArrayList(RECEIPT_NAMES, ArrayList(receipts.map { it.originalFileName.orEmpty() }))
    }

    private fun decodeReceipts(bundle: Bundle): List<UiReceipt>? {
        val ids = bundle.getLongArray(RECEIPT_IDS) ?: return null
        val paths = bundle.getStringArrayList(RECEIPT_PATHS) ?: return null
        val names = bundle.getStringArrayList(RECEIPT_NAMES) ?: return null
        if (ids.size != paths.size || ids.size != names.size) return null
        return ids.indices.map { i ->
            UiReceipt(
                id = ids[i].takeIf { it != NO_ID },
                filePath = paths[i],
                originalFileName = names[i].takeIf { it.isNotEmpty() },
            )
        }
    }

    /** Null for a row id. Real ids are positive autoincrement values. */
    private const val NO_ID = Long.MIN_VALUE
    private const val RECEIPT_IDS = "ids"
    private const val RECEIPT_PATHS = "paths"
    private const val RECEIPT_NAMES = "names"
}
