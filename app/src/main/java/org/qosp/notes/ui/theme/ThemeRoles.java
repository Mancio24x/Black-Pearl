package org.qosp.notes.ui.theme;

import com.google.android.material.color.utilities.DynamicColor;
import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.MaterialDynamicColors;
import com.google.android.material.color.utilities.SchemeMonochrome;
import com.google.android.material.color.utilities.SchemeNeutral;
import com.google.android.material.color.utilities.SchemeTonalSpot;
import com.google.android.material.color.utilities.SchemeVibrant;
import com.google.android.material.color.utilities.TonalPalette;
import com.google.android.material.color.utilities.Variant;

import java.util.LinkedHashMap;

/**
 * Material 3 color roles (theme attribute name -> ARGB) for Black Pearl themes.
 * Single source of truth for the generated static palettes (tools/palette-gen) and for "My theme" at runtime.
 * Plain Java on purpose: the generator compiles this file without Android.
 */
@SuppressWarnings("RestrictedApi")
public final class ThemeRoles {
    private ThemeRoles() {}

    public enum Kind { TONAL_SPOT, VIBRANT, NEUTRAL, MONOCHROME }

    /** Full M3 scheme generated from a seed color (Material's own algorithm). */
    public static LinkedHashMap<String, Integer> fromSeed(int seed, Kind kind, Integer tertiarySeed, boolean dark) {
        Hct src = Hct.fromInt(seed);
        DynamicScheme s;
        switch (kind) {
            case VIBRANT: s = new SchemeVibrant(src, dark, 0.0); break;
            case NEUTRAL: s = new SchemeNeutral(src, dark, 0.0); break;
            case MONOCHROME: s = new SchemeMonochrome(src, dark, 0.0); break;
            default: s = new SchemeTonalSpot(src, dark, 0.0);
        }
        if (tertiarySeed != null) {
            Hct t = Hct.fromInt(tertiarySeed);
            s = new DynamicScheme(src, Variant.TONAL_SPOT, dark, 0.0, s.primaryPalette, s.secondaryPalette,
                    TonalPalette.fromHueAndChroma(t.getHue(), Math.max(t.getChroma(), 40.0)), s.neutralPalette, s.neutralVariantPalette);
        }
        return roles(s);
    }

    /**
     * A designed theme (Atlante style): exact background ("paper"), cards ("surface"), text ("ink"),
     * secondary text ("muted"), accent and divider colors; the other roles come from the accent's M3 scheme.
     */
    public static LinkedHashMap<String, Integer> designed(int paper, int surface, int ink, int muted, int accent, int line, boolean dark) {
        LinkedHashMap<String, Integer> r = fromSeed(accent, Kind.TONAL_SPOT, null, dark);
        int deep = isDark(paper) ? paper : ink; // the darkest of the theme's own colors
        int onAccent = contrast(accent, 0xFFFFFFFF) >= contrast(accent, deep) ? 0xFFFFFFFF : deep;
        r.put("colorPrimary", accent);
        r.put("colorOnPrimary", onAccent);
        r.put("android:colorBackground", paper);
        r.put("colorOnBackground", ink);
        r.put("colorSurface", paper);
        r.put("colorOnSurface", ink);
        r.put("colorSurfaceVariant", blend(paper, ink, 0.08));
        r.put("colorOnSurfaceVariant", muted);
        r.put("colorSurfaceInverse", ink);
        r.put("colorOnSurfaceInverse", paper);
        r.put("colorSurfaceDim", blend(paper, ink, dark ? 0.02 : 0.07));
        r.put("colorSurfaceBright", dark ? blend(paper, ink, 0.12) : paper);
        r.put("colorSurfaceContainerLowest", surface);
        r.put("colorSurfaceContainerLow", surface);
        r.put("colorSurfaceContainer", blend(paper, ink, 0.05));
        r.put("colorSurfaceContainerHigh", blend(paper, ink, 0.08));
        r.put("colorSurfaceContainerHighest", blend(paper, ink, 0.11));
        r.put("colorOutline", blend(paper, ink, 0.45));
        r.put("colorOutlineVariant", line);
        return r;
    }

    /** "My theme": only three colors chosen by the user; text colors and dark/light are derived. */
    public static LinkedHashMap<String, Integer> custom(int paper, int surface, int accent) {
        boolean dark = isDark(paper);
        Hct p = Hct.fromInt(paper);
        TonalPalette neutral = TonalPalette.fromHueAndChroma(p.getHue(), Math.min(p.getChroma(), 10.0));
        int ink = neutral.tone(dark ? 92 : 12);
        int muted = neutral.tone(dark ? 76 : 38);
        int line = blend(paper, ink, 0.14);
        return designed(paper, surface, ink, muted, accent, line, dark);
    }

    public static boolean isDark(int color) { return luminance(color) < 0.18; }

    public static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static double luminance(int c) {
        double[] v = {((c >> 16) & 0xFF) / 255.0, ((c >> 8) & 0xFF) / 255.0, (c & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** WCAG contrast ratio (1..21). */
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static LinkedHashMap<String, Integer> roles(DynamicScheme s) {
        MaterialDynamicColors m = new MaterialDynamicColors();
        LinkedHashMap<String, DynamicColor> r = new LinkedHashMap<>();
        r.put("colorPrimary", m.primary()); r.put("colorOnPrimary", m.onPrimary());
        r.put("colorPrimaryContainer", m.primaryContainer()); r.put("colorOnPrimaryContainer", m.onPrimaryContainer());
        r.put("colorPrimaryInverse", m.inversePrimary());
        r.put("colorPrimaryFixed", m.primaryFixed()); r.put("colorPrimaryFixedDim", m.primaryFixedDim());
        r.put("colorOnPrimaryFixed", m.onPrimaryFixed()); r.put("colorOnPrimaryFixedVariant", m.onPrimaryFixedVariant());
        r.put("colorSecondary", m.secondary()); r.put("colorOnSecondary", m.onSecondary());
        r.put("colorSecondaryContainer", m.secondaryContainer()); r.put("colorOnSecondaryContainer", m.onSecondaryContainer());
        r.put("colorSecondaryFixed", m.secondaryFixed()); r.put("colorSecondaryFixedDim", m.secondaryFixedDim());
        r.put("colorOnSecondaryFixed", m.onSecondaryFixed()); r.put("colorOnSecondaryFixedVariant", m.onSecondaryFixedVariant());
        r.put("colorTertiary", m.tertiary()); r.put("colorOnTertiary", m.onTertiary());
        r.put("colorTertiaryContainer", m.tertiaryContainer()); r.put("colorOnTertiaryContainer", m.onTertiaryContainer());
        r.put("colorTertiaryFixed", m.tertiaryFixed()); r.put("colorTertiaryFixedDim", m.tertiaryFixedDim());
        r.put("colorOnTertiaryFixed", m.onTertiaryFixed()); r.put("colorOnTertiaryFixedVariant", m.onTertiaryFixedVariant());
        r.put("colorError", m.error()); r.put("colorOnError", m.onError());
        r.put("colorErrorContainer", m.errorContainer()); r.put("colorOnErrorContainer", m.onErrorContainer());
        r.put("android:colorBackground", m.background()); r.put("colorOnBackground", m.onBackground());
        r.put("colorSurface", m.surface()); r.put("colorOnSurface", m.onSurface());
        r.put("colorSurfaceVariant", m.surfaceVariant()); r.put("colorOnSurfaceVariant", m.onSurfaceVariant());
        r.put("colorSurfaceInverse", m.inverseSurface()); r.put("colorOnSurfaceInverse", m.inverseOnSurface());
        r.put("colorSurfaceDim", m.surfaceDim()); r.put("colorSurfaceBright", m.surfaceBright());
        r.put("colorSurfaceContainerLowest", m.surfaceContainerLowest());
        r.put("colorSurfaceContainerLow", m.surfaceContainerLow());
        r.put("colorSurfaceContainer", m.surfaceContainer());
        r.put("colorSurfaceContainerHigh", m.surfaceContainerHigh());
        r.put("colorSurfaceContainerHighest", m.surfaceContainerHighest());
        r.put("colorOutline", m.outline()); r.put("colorOutlineVariant", m.outlineVariant());
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        r.forEach((k, v) -> out.put(k, v.getArgb(s)));
        return out;
    }
}
