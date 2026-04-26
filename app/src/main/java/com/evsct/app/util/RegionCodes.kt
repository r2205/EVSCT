package com.evsct.app.util

import java.text.Normalizer

/**
 * Maps province/state names to their two-letter codes. Used to keep the
 * Prov/State field consistent whether the value comes from the geocoder
 * (full name) or the user typing (often abbreviated).
 *
 * Matching is case-insensitive and diacritic-insensitive, so "Québec" and
 * "quebec" both resolve to "QC".
 */
object RegionCodes {

    private val nameToCode: Map<String, String> = mapOf(
        // Canada – provinces and territories
        "Alberta" to "AB",
        "British Columbia" to "BC",
        "Manitoba" to "MB",
        "New Brunswick" to "NB",
        "Newfoundland and Labrador" to "NL",
        "Newfoundland" to "NL",
        "Nova Scotia" to "NS",
        "Northwest Territories" to "NT",
        "Nunavut" to "NU",
        "Ontario" to "ON",
        "Prince Edward Island" to "PE",
        "Quebec" to "QC",
        "Saskatchewan" to "SK",
        "Yukon" to "YT",
        "Yukon Territory" to "YT",
        // French variants the geocoder may surface in fr locales
        "Colombie-Britannique" to "BC",
        "Nouveau-Brunswick" to "NB",
        "Nouvelle-Écosse" to "NS",
        "Terre-Neuve-et-Labrador" to "NL",
        "Île-du-Prince-Édouard" to "PE",
        "Territoires du Nord-Ouest" to "NT",
        // US – 50 states + DC
        "Alabama" to "AL",
        "Alaska" to "AK",
        "Arizona" to "AZ",
        "Arkansas" to "AR",
        "California" to "CA",
        "Colorado" to "CO",
        "Connecticut" to "CT",
        "Delaware" to "DE",
        "District of Columbia" to "DC",
        "Washington, D.C." to "DC",
        "Florida" to "FL",
        "Georgia" to "GA",
        "Hawaii" to "HI",
        "Idaho" to "ID",
        "Illinois" to "IL",
        "Indiana" to "IN",
        "Iowa" to "IA",
        "Kansas" to "KS",
        "Kentucky" to "KY",
        "Louisiana" to "LA",
        "Maine" to "ME",
        "Maryland" to "MD",
        "Massachusetts" to "MA",
        "Michigan" to "MI",
        "Minnesota" to "MN",
        "Mississippi" to "MS",
        "Missouri" to "MO",
        "Montana" to "MT",
        "Nebraska" to "NE",
        "Nevada" to "NV",
        "New Hampshire" to "NH",
        "New Jersey" to "NJ",
        "New Mexico" to "NM",
        "New York" to "NY",
        "North Carolina" to "NC",
        "North Dakota" to "ND",
        "Ohio" to "OH",
        "Oklahoma" to "OK",
        "Oregon" to "OR",
        "Pennsylvania" to "PA",
        "Rhode Island" to "RI",
        "South Carolina" to "SC",
        "South Dakota" to "SD",
        "Tennessee" to "TN",
        "Texas" to "TX",
        "Utah" to "UT",
        "Vermont" to "VT",
        "Virginia" to "VA",
        "Washington" to "WA",
        "West Virginia" to "WV",
        "Wisconsin" to "WI",
        "Wyoming" to "WY",
    )

    private val foldedToCode: Map<String, String> =
        nameToCode.entries.associate { (name, code) -> fold(name) to code }

    private val knownCodes: Set<String> = nameToCode.values.toSet()

    /** Returns the canonical 2-letter code, or null if [input] isn't recognized. */
    fun toCode(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val upper = trimmed.uppercase()
        if (upper.length == 2 && upper in knownCodes) return upper
        return foldedToCode[fold(trimmed)]
    }

    /** Returns the canonical code if recognized, otherwise the original input untouched. */
    fun normalize(input: String): String = toCode(input) ?: input

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
}
