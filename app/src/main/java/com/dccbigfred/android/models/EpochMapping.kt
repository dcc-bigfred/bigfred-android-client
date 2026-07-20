package com.dccbigfred.android.models

/**
 * Maps hydrus catalog epoch codes (e.g. III_A, VI_B, VI) to Polish
 * modelling-epoch codes used by BigFred vehicles (IIIa, VIb, VI).
 */
object EpochMapping {
    private val VALID = setOf(
        "I", "Ia", "Ib",
        "II", "IIa", "IIb", "IIc",
        "III", "IIIa", "IIIb", "IIIc",
        "IV", "IVa", "IVb", "IVc",
        "V", "Va", "Vb", "Vc",
        "VI", "VIa", "VIb",
    )

    fun toPolishEpoch(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed in VALID) return trimmed

        val parts = trimmed.split("_", limit = 2)
        val roman = parts[0].uppercase()
        val letter = parts.getOrNull(1)?.firstOrNull()?.lowercaseChar()?.toString().orEmpty()
        val mapped = roman + letter
        return mapped.takeIf { it in VALID }
    }

    fun mapAll(codes: List<String>): List<String> {
        return codes.mapNotNull { toPolishEpoch(it) }.distinct()
    }
}
