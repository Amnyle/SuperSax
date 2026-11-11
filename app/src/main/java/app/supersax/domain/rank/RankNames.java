package app.supersax.domain.rank;

/** Tier and division names in English and French (used by the weekly review text and the UI). */
public final class RankNames {
    private static final String[] EN = {"Unranked", "Iron", "Bronze", "Silver", "Gold", "Platinum", "Emerald", "Diamond", "Master"};
    private static final String[] FR = {"Non classé", "Fer", "Bronze", "Argent", "Or", "Platine", "Émeraude", "Diamant", "Maître"};
    private static final String[] DIVISIONS = {"IV", "III", "II", "I"};

    private RankNames() {}

    public static String tier(int tier, String language) {
        return (language.startsWith("fr") ? FR : EN)[Math.max(0, Math.min(8, tier))];
    }

    /** Division 0..3 from lowest (IV) to highest (I). */
    public static String division(int division) { return DIVISIONS[Math.max(0, Math.min(3, division))]; }

    public static String full(int tier, int division, String language) {
        return tier >= 1 && tier <= 7 ? tier(tier, language) + " " + division(division) : tier(tier, language);
    }
}
