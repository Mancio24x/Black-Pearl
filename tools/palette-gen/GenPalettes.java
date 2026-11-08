// Generates the Material 3 color palettes used by Black Pearl.
// Uses the official Material color utilities bundled in com.google.android.material:material.
// Run: tools/palette-gen/run.sh   (writes app/src/main/res/values{,-night}/palettes.xml)
// Edit the PALETTES table below, re-run, commit the generated XML.

import com.google.android.material.color.utilities.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class GenPalettes {

    record P(String style, int seed, Variant variant, Integer tertiarySeed) {}

    // style name suffix -> seed color. Keep names stable: they are referenced by ColorScheme in PreferenceEnums.kt.
    static final List<P> PALETTES = List.of(
            new P("BlackPearl", 0xFF1B4F72, Variant.TONAL_SPOT, 0xFFC9A227),
            new P("Blue", 0xFF2196F3, Variant.TONAL_SPOT, null),
            new P("Red", 0xFFEB4132, Variant.TONAL_SPOT, null),
            new P("Yellow", 0xFFFFBA00, Variant.TONAL_SPOT, null),
            new P("Green", 0xFF7CB342, Variant.TONAL_SPOT, null),
            new P("Pink", 0xFFC2185B, Variant.TONAL_SPOT, null),
            new P("Orange", 0xFFFF8800, Variant.TONAL_SPOT, null),
            new P("Purple", 0xFF8249E8, Variant.TONAL_SPOT, null),
            new P("Teal", 0xFF00897B, Variant.TONAL_SPOT, null),
            new P("Rose", 0xFFE8A0B4, Variant.TONAL_SPOT, null),
            new P("Lavender", 0xFF9C8CD4, Variant.TONAL_SPOT, null),
            new P("Sage", 0xFF7D9A7E, Variant.TONAL_SPOT, null),
            new P("DustyBlue", 0xFF6F8FAF, Variant.TONAL_SPOT, null),
            new P("Sand", 0xFFC2A878, Variant.TONAL_SPOT, null),
            new P("NightViolet", 0xFF4B3B7A, Variant.VIBRANT, null),
            new P("Graphite", 0xFF5F6368, Variant.NEUTRAL, null),
            new P("Monochrome", 0xFF000000, Variant.MONOCHROME, null)
    );

    static DynamicScheme scheme(P p, boolean dark) {
        Hct src = Hct.fromInt(p.seed);
        DynamicScheme base = switch (p.variant) {
            case VIBRANT -> new SchemeVibrant(src, dark, 0.0);
            case NEUTRAL -> new SchemeNeutral(src, dark, 0.0);
            case MONOCHROME -> new SchemeMonochrome(src, dark, 0.0);
            default -> new SchemeTonalSpot(src, dark, 0.0);
        };
        if (p.tertiarySeed == null) return base;
        Hct t = Hct.fromInt(p.tertiarySeed);
        return new DynamicScheme(src, p.variant, dark, 0.0, base.primaryPalette, base.secondaryPalette,
                TonalPalette.fromHueAndChroma(t.getHue(), Math.max(t.getChroma(), 40.0)),
                base.neutralPalette, base.neutralVariantPalette);
    }

    static String roles(DynamicScheme s, String indent) {
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
        StringBuilder b = new StringBuilder();
        r.forEach((k, v) -> b.append(indent).append(String.format("<item name=\"%s\">#%06X</item>%n", k, v.getArgb(s) & 0xFFFFFF)));
        return b.toString();
    }

    static String file(boolean dark) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<!-- GENERATED by tools/palette-gen/GenPalettes.java - do not edit by hand. -->\n")
                .append("<resources>\n");
        // Default palette = first entry, used as parent of the app theme.
        b.append("    <style name=\"Palette.Default\" parent=\"Theme.Material3.DayNight.NoActionBar\">\n")
                .append(roles(scheme(PALETTES.get(0), dark), "        ")).append("    </style>\n\n");
        for (P p : PALETTES) {
            b.append("    <!-- seed #").append(String.format("%06X", p.seed & 0xFFFFFF)).append(" ").append(p.variant).append(" -->\n");
            b.append("    <style name=\"Palette.").append(p.style).append("\" parent=\"\">\n")
                    .append(roles(scheme(p, dark), "        ")).append("    </style>\n\n");
        }
        return b.append("</resources>\n").toString();
    }

    public static void main(String[] args) throws IOException {
        Path res = Paths.get(args.length > 0 ? args[0] : "app/src/main/res");
        Files.writeString(res.resolve("values/palettes.xml"), file(false));
        Files.writeString(res.resolve("values-night/palettes.xml"), file(true));
        System.out.println("Wrote " + PALETTES.size() + " palettes");
    }
}
