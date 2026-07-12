package com.evsct.app.ui

/**
 * Outcome of a finished long-running operation (backup, restore, import,
 * export). Routine successes surface as a snackbar; failures — and the
 * results of destructive replaces, which deserve acknowledgement — surface
 * as a titled dialog. Shared by the Settings and Year-recap screens.
 */
data class OpFeedback(
    val title: String,
    val body: String,
    val isError: Boolean = false,
    val asDialog: Boolean = isError,
)
