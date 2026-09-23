package com.evsct.app.ui.sessions

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.evsct.app.R
import com.evsct.app.data.entity.PaymentMethod
import com.evsct.app.util.PaymentHistory
import com.evsct.app.util.PaymentUse
import com.evsct.app.util.TagSuggestions

/**
 * The session form's Payment group: which kind of payment, then — once a
 * kind is picked — which card, app or account.
 *
 * Two layers rather than one free-text field because they answer different
 * questions. The method is a fixed set, so it's a tap and it stays
 * consistent; the detail is whatever the user calls their card, so it's
 * text, with the details already used under that method offered as chips.
 *
 * Before a method is picked, the payments used most recently are offered
 * whole: most charges are paid the way the last few were, and one tap on
 * "TD Visa" beats picking Credit card and then typing it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaymentFields(
    method: PaymentMethod?,
    detail: String,
    // Every payment used on any session, most recent first.
    history: List<PaymentUse>,
    onMethodChange: (PaymentMethod?) -> Unit,
    onDetailChange: (String) -> Unit,
    onUseAgain: (PaymentUse) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (method == null && history.isNotEmpty()) {
            RecentPayments(history.take(PaymentHistory.MAX_RECENT), onUseAgain)
        }
        // FlowRow for the same reason as the pricing chips: seven options
        // overflow any phone width.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaymentMethod.entries.forEach { m ->
                FilterChip(
                    selected = m == method,
                    // Unlike charging type and pricing, payment is optional —
                    // tapping the picked method again takes it back off.
                    onClick = { onMethodChange(if (m == method) null else m) },
                    label = { Text(stringResource(m.labelRes())) },
                    leadingIcon = selectedCheck(m == method),
                )
            }
        }
        if (method != null) {
            PaymentDetailField(
                method = method,
                detail = detail,
                usedBefore = remember(history, method) { PaymentHistory.details(history, method) },
                onDetailChange = onDetailChange,
            )
        }
    }
}

@Composable
private fun RecentPayments(uses: List<PaymentUse>, onUse: (PaymentUse) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.form_payment_recently_used),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // Scrolls rather than wraps, like the tag suggestions: these are
        // shortcuts, not the field itself, and shouldn't dominate the group.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uses.forEach { use ->
                val methodLabel = stringResource(use.method.labelRes())
                // The chip shows only the detail, with the icon standing in
                // for the method — which a screen reader can't see, so the
                // spoken label names both.
                val spoken = stringResource(
                    R.string.form_payment_use,
                    use.detail?.let { "$methodLabel, $it" } ?: methodLabel,
                )
                AssistChip(
                    onClick = { onUse(use) },
                    label = { Text(use.detail ?: methodLabel) },
                    leadingIcon = {
                        Icon(
                            use.method.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
    }
}

/** Which card, app or account — free text, with what was used before under
 *  this method offered underneath. The chips narrow as the user types, the
 *  same matching the tags field uses, so a half-typed "td" finds "TD Visa". */
@Composable
private fun PaymentDetailField(
    method: PaymentMethod,
    detail: String,
    usedBefore: List<String>,
    onDetailChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Local TextFieldValue, as in DurationField, so text arriving from
    // outside — a tapped chip — lands with the cursor after it. The plain
    // String overload keeps the old cursor, and typing on after tapping
    // "TD Visa" off a half-typed "td" would insert mid-word.
    var fieldValue by remember { mutableStateOf(TextFieldValue(detail, TextRange(detail.length))) }
    LaunchedEffect(detail) {
        if (fieldValue.text != detail) fieldValue = TextFieldValue(detail, TextRange(detail.length))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { fv ->
                fieldValue = fv
                if (fv.text != detail) onDetailChange(fv.text)
            },
            label = { Text(stringResource(method.detailLabelRes())) },
            placeholder = { Text(stringResource(method.detailHintRes())) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                // Card and network names are proper nouns.
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // Excluding the current text hides a chip that would do nothing:
        // once the field reads "TD Visa", offering "TD Visa" is noise.
        val suggestions = remember(usedBefore, detail) {
            TagSuggestions.match(usedBefore, detail, exclude = listOf(detail))
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    if (detail.isBlank()) R.string.form_payment_recently_used
                    else R.string.form_payment_matching,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { suggestion ->
                    val spoken = stringResource(R.string.form_payment_use, suggestion)
                    AssistChip(
                        onClick = {
                            fieldValue = TextFieldValue(suggestion, TextRange(suggestion.length))
                            onDetailChange(suggestion)
                        },
                        label = { Text(suggestion) },
                        modifier = Modifier.semantics { contentDescription = spoken },
                    )
                }
            }
        }
    }
}

/** The payment on a log row, behind the method's icon. [label] is the
 *  detail when there is one ("TD Visa"), else the method's name — resolved
 *  by the caller, which speaks the same words. */
@Composable
internal fun PaymentPill(method: PaymentMethod, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = method.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
internal fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.CREDIT_CARD -> R.string.form_payment_credit_card
    PaymentMethod.DEBIT_CARD -> R.string.form_payment_debit_card
    PaymentMethod.MOBILE_WALLET -> R.string.form_payment_mobile_wallet
    PaymentMethod.APP -> R.string.form_payment_app
    PaymentMethod.RFID_CARD -> R.string.form_payment_rfid_card
    PaymentMethod.PLUG_AND_CHARGE -> R.string.form_payment_plug_and_charge
    PaymentMethod.OTHER -> R.string.form_payment_other
}

internal fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD -> Icons.Default.CreditCard
    PaymentMethod.MOBILE_WALLET -> Icons.Default.Contactless
    PaymentMethod.APP -> Icons.Default.PhoneAndroid
    PaymentMethod.RFID_CARD -> Icons.Default.Nfc
    PaymentMethod.PLUG_AND_CHARGE -> Icons.Default.EvStation
    PaymentMethod.OTHER -> Icons.Default.Payments
}

@StringRes
private fun PaymentMethod.detailLabelRes(): Int = when (this) {
    PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD -> R.string.form_payment_which_card
    PaymentMethod.MOBILE_WALLET -> R.string.form_payment_which_wallet
    PaymentMethod.APP -> R.string.form_payment_which_app
    PaymentMethod.RFID_CARD -> R.string.form_payment_which_rfid
    PaymentMethod.PLUG_AND_CHARGE -> R.string.form_payment_which_account
    PaymentMethod.OTHER -> R.string.form_payment_details
}

@StringRes
private fun PaymentMethod.detailHintRes(): Int = when (this) {
    PaymentMethod.CREDIT_CARD -> R.string.form_payment_hint_credit_card
    PaymentMethod.DEBIT_CARD -> R.string.form_payment_hint_debit_card
    PaymentMethod.MOBILE_WALLET -> R.string.form_payment_hint_mobile_wallet
    PaymentMethod.APP -> R.string.form_payment_hint_app
    PaymentMethod.RFID_CARD -> R.string.form_payment_hint_rfid_card
    PaymentMethod.PLUG_AND_CHARGE -> R.string.form_payment_hint_plug_and_charge
    PaymentMethod.OTHER -> R.string.form_payment_hint_other
}
