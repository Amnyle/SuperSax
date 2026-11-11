package app.supersax.domain.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Pure rules of the in-app updater. */
public final class Releases {
    private Releases() {}

    private static String stripV(String v) {
        String t = v.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /** "v1.0.12" → [1, 0, 12]; non-digit suffixes are ignored. */
    public static List<Integer> parts(String v) {
        List<Integer> out = new ArrayList<>();
        for (String p : stripV(v).split("[.+-]", -1)) {
            int i = 0;
            while (i < p.length() && Character.isDigit(p.charAt(i))) i++;
            int n = 0;
            if (i > 0) {
                try { n = Integer.parseInt(p.substring(0, i)); } catch (NumberFormatException e) { n = 0; }
            }
            out.add(n);
        }
        return out;
    }

    /** Numeric comparison part by part: 1.0.10 is newer than 1.0.9. */
    public static boolean isNewer(String remote, String local) {
        List<Integer> a = parts(remote), b = parts(local);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int d = (i < a.size() ? a.get(i) : 0) - (i < b.size() ? b.get(i) : 0);
            if (d != 0) return d > 0;
        }
        return false;
    }

    /** The release APK (never a debug build). */
    public static @Nullable GhAsset pickApk(List<GhAsset> assets) {
        GhAsset best = null;
        for (GhAsset a : assets) {
            if (!a.getName().endsWith(".apk") || a.getName().toLowerCase(Locale.ROOT).contains("debug")) continue;
            if (best == null || a.getSize() > best.getSize()) best = a;
        }
        return best;
    }

    /** The update to offer, or null when the release is a draft, a pre-release, has no APK, or is not newer. */
    public static @Nullable AvailableUpdate available(GhRelease release, String installedVersion) {
        if (release.getDraft() || release.getPrerelease()) return null;
        GhAsset apk = pickApk(release.getAssets());
        if (apk == null) return null;
        String version = stripV(release.getTag_name());
        if (!isNewer(version, installedVersion)) return null;
        String notes = release.getBody() == null ? "" : release.getBody().trim();
        return new AvailableUpdate(version, notes, apk, release.getHtml_url());
    }
}
