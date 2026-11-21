package com.evsct.app.ui.vehicles

import android.os.Bundle

/**
 * The vehicle form's unsaved edits, as a [Bundle] the screen's
 * SavedStateHandle carries across process death.
 *
 * Android kills a backgrounded app without warning, and the likeliest moment
 * is the one the form sends the user away for: the photo picker, choosing a
 * profile photo. The recreated screen used to reload the row from the
 * database, silently dropping everything typed since it opened.
 *
 * Only fields that differ from the form as loaded are captured, and
 * [restore] lays them over a fresh load, so a field the user never touched
 * takes its freshly loaded value and a field they did keeps their edit.
 * Clearing a value (removing the photo, emptying the notes) is an edit like
 * any other and survives too.
 *
 * Only what the user edits travels. [VehicleEditUi.isNew] and
 * [VehicleEditUi.useMiles] come from the load, and the snackbar text is
 * dropped. Every editable field must be listed in [FIELDS]: one left out is
 * silently dropped on restore (VehicleFormDraftTest round-trips them all).
 * They are the fields VehicleEditViewModel's formOnly() keeps.
 *
 * Mirrors the session form's SessionFormDraft.
 */
internal object VehicleFormDraft {

    /** The fields of [form] that differ from [baseline]. Empty when the
     *  form is unedited. */
    fun capture(form: VehicleEditUi, baseline: VehicleEditUi): Bundle =
        Bundle().also { out -> FIELDS.forEach { it.capture(form, baseline, out) } }

    /** [onto] with every field [draft] carries replaced by its value. */
    fun restore(draft: Bundle, onto: VehicleEditUi): VehicleEditUi =
        FIELDS.fold(onto) { form, field -> field.restore(draft, form) }

    private class Field(
        val capture: (form: VehicleEditUi, baseline: VehicleEditUi, out: Bundle) -> Unit,
        val restore: (draft: Bundle, onto: VehicleEditUi) -> VehicleEditUi,
    )

    /** A field stored under [key] with [put] when it differs from the
     *  baseline. [read] gets the restoring form's current value as a
     *  fallback for anything it can't decode. */
    private fun <T> field(
        key: String,
        get: (VehicleEditUi) -> T,
        set: VehicleEditUi.(T) -> VehicleEditUi,
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

    private fun text(key: String, get: (VehicleEditUi) -> String, set: VehicleEditUi.(String) -> VehicleEditUi) =
        field(key, get, set, { k, v -> putString(k, v) }, { k, fallback -> getString(k) ?: fallback })

    private val FIELDS: List<Field> = listOf(
        text("name", { it.name }) { copy(name = it) },
        text("year", { it.year }) { copy(year = it) },
        text("make", { it.make }) { copy(make = it) },
        text("model", { it.model }) { copy(model = it) },
        text("trim", { it.trim }) { copy(trim = it) },
        text("batteryKwh", { it.batteryKwh }) { copy(batteryKwh = it) },
        text("rangeText", { it.rangeText }) { copy(rangeText = it) },
        text("vin", { it.vin }) { copy(vin = it) },
        text("notes", { it.notes }) { copy(notes = it) },
        field(
            "isDefault", { it.isDefault }, { copy(isDefault = it) },
            { k, v -> putBoolean(k, v) }, { k, fallback -> getBoolean(k, fallback) },
        ),
        // "" is no photo: VehicleImageStore paths are never empty.
        field(
            "imagePath", { it.imagePath }, { copy(imagePath = it) },
            { k, v -> putString(k, v.orEmpty()) },
            { k, fallback ->
                when (val path = getString(k)) {
                    null -> fallback
                    "" -> null
                    else -> path
                }
            },
        ),
    )
}
