package app.saxpractice.domain.rank

/** Tier and division names in English and French (used by the weekly review text and the UI). */
object RankNames {
    private val EN = listOf("Unranked", "Iron", "Bronze", "Silver", "Gold", "Platinum", "Emerald", "Diamond", "Master")
    private val FR = listOf("Non classé", "Fer", "Bronze", "Argent", "Or", "Platine", "Émeraude", "Diamant", "Maître")
    private val DIVISIONS = listOf("IV", "III", "II", "I")

    fun tier(tier: Int, language: String): String = (if (language.startsWith("fr")) FR else EN)[tier.coerceIn(0, 8)]

    /** Division 0..3 from lowest (IV) to highest (I). */
    fun division(division: Int): String = DIVISIONS[division.coerceIn(0, 3)]

    fun full(tier: Int, division: Int, language: String): String =
        if (tier in 1..7) "${tier(tier, language)} ${division(division)}" else tier(tier, language)
}
