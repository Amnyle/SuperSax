package app.supersax.domain.pitch;

/** ASCII spelling of alterations ("#", "b", "##", "bb"), used as stable ids in data files. */
final class Accidentals {
    private Accidentals() {}

    static String ascii(int alter) {
        switch (alter) {
            case 0: return "";
            case 1: return "#";
            case 2: return "##";
            case -1: return "b";
            case -2: return "bb";
            default: return (alter > 0 ? "#" : "b").repeat(Math.abs(alter));
        }
    }

    static int parse(String s) {
        switch (s) {
            case "": case "n": return 0;
            case "#": return 1;
            case "##": case "x": return 2;
            case "b": return -1;
            case "bb": return -2;
            default: throw new IllegalArgumentException("Bad accidental: " + s);
        }
    }
}
