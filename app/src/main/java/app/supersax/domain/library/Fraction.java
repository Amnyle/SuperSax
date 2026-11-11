package app.supersax.domain.library;

/** An exact fraction of a whole note (1/4 = quarter note), always reduced. */
public final class Fraction implements Comparable<Fraction> {
    public static final Fraction ZERO = new Fraction(0, 1);

    private final int num;
    private final int den;

    public Fraction(int num, int den) {
        if (den <= 0) throw new IllegalArgumentException("denominator must be positive");
        int g = Math.max(1, gcd(Math.abs(num), den));
        this.num = num / g;
        this.den = den / g;
    }

    public int getNum() { return num; }
    public int getDen() { return den; }
    public double getValue() { return (double) num / den; }

    public Fraction plus(Fraction o) { return new Fraction(num * o.den + o.num * den, den * o.den); }

    public Fraction minus(Fraction o) { return new Fraction(num * o.den - o.num * den, den * o.den); }

    /** Kept for callers that reduce explicitly; fractions are always reduced already. */
    public Fraction reduced() { return this; }

    @Override public int compareTo(Fraction o) { return Long.compare((long) num * o.den, (long) o.num * den); }

    @Override public boolean equals(Object o) { return o instanceof Fraction && ((Fraction) o).num == num && ((Fraction) o).den == den; }

    @Override public int hashCode() { return 31 * num + den; }

    @Override public String toString() { return num + "/" + den; }

    public static Fraction parse(String s) {
        String[] p = s.split("/");
        return new Fraction(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }
}
