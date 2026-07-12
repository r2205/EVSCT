package com.evsct.app.ui

import com.evsct.app.data.entity.ChargingType
import com.evsct.app.ui.theme.EvAccentPalette
import com.evsct.app.ui.theme.TypeAccent

/** The palette trio for a charging type. Lives outside the theme package so
 *  ui.theme stays free of entity imports. */
fun EvAccentPalette.forType(type: ChargingType): TypeAccent = when (type) {
    ChargingType.DC_FAST -> dcFast
    ChargingType.AC_L2 -> acL2
    ChargingType.AC_L1 -> acL1
}
