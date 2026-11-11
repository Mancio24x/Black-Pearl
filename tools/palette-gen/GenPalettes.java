// Generates the Material 3 palettes of Black Pearl from tools/palette-gen (run.sh):
//   app/src/main/res/values{,-night}/palettes.xml   static themes (+ Palette.Custom, bound to color resources)
//   app/src/main/res/values/colors_custom_theme.xml default "My theme" colors (overridden at runtime)
//   app/src/main/java/org/qosp/notes/ui/theme/CustomThemeResources.kt  attribute -> color resource map
// Role math lives in app/src/main/java/org/qosp/notes/ui/theme/ThemeRoles.java (shared with the app).
// Edit the PALETTES table, re-run, commit the generated files.

import org.qosp.notes.ui.theme.ThemeRoles;
import org.qosp.notes.ui.theme.ThemeRoles.Kind;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class GenPalettes {

    /** paper, surface, ink, muted, accent, line */
    record D(int paper, int surface, int ink, int muted, int accent, int line) {}

    /**
     * style: suffix of Palette.<style> (referenced by ColorScheme in PreferenceEnums.kt: keep names stable).
     * light: designed light colors (null = generated from seed). alwaysDark: designed dark theme used in both modes.
     */
    record P(String style, int seed, Kind kind, Integer tertiary, D light, D alwaysDark) {}

    static D d(int... c) { return new D(c[0] | 0xFF000000, c[1] | 0xFF000000, c[2] | 0xFF000000, c[3] | 0xFF000000, c[4] | 0xFF000000, c[5] | 0xFF000000); }

    static final List<P> PALETTES = List.of(
            // Designed (Atlante-style) light themes; dark mode is generated from the accent.
            new P("BlackPearl", 0xFF1B4F72, Kind.TONAL_SPOT, 0xFFC9A227, d(0xF6F3EC, 0xFFFDF8, 0x16212B, 0x5B6670, 0x1B4F72, 0xE3DED3), null),
            new P("Parchment", 0xFF8E2F25, Kind.TONAL_SPOT, null, d(0xF4ECDD, 0xFFFBF3, 0x2E2418, 0x6E5E48, 0x8E2F25, 0xE5D9C3), null),
            new P("OpenSea", 0xFF006A8E, Kind.TONAL_SPOT, null, d(0xEAF3F7, 0xFBFEFF, 0x12303F, 0x55707D, 0x006A8E, 0xD2E3EA), null),
            new P("Sage", 0xFF416D53, Kind.TONAL_SPOT, null, d(0xEFF5EF, 0xFBFEFA, 0x2B4439, 0x5B7065, 0x416D53, 0xD7E4D9), null),
            new P("Rose", 0xFF99496C, Kind.TONAL_SPOT, null, d(0xFFF3F5, 0xFFFCFD, 0x4C2939, 0x775662, 0x99496C, 0xF0D9E2), null),
            new P("Lavender", 0xFF76539C, Kind.TONAL_SPOT, null, d(0xF5F1FC, 0xFDFCFF, 0x362C50, 0x6F6182, 0x76539C, 0xE4DCEF), null),
            new P("Coral", 0xFFB5472C, Kind.TONAL_SPOT, null, d(0xFFF1EC, 0xFFFBF9, 0x4A2418, 0x7B5649, 0xB5472C, 0xF3D8CE), null),
            new P("Sand", 0xFF8A5035, Kind.TONAL_SPOT, null, d(0xF8F0E5, 0xFFFCF6, 0x4B382A, 0x786451, 0x8A5035, 0xE8DBCA), null),
            new P("DustyBlue", 0xFF365F8C, Kind.TONAL_SPOT, null, d(0xEDF3F9, 0xFBFDFF, 0x24394F, 0x5D6E80, 0x365F8C, 0xD5E1EE), null),
            // Always-dark designed themes.
            new P("Abyss", 0xFFE6C068, Kind.TONAL_SPOT, null, null, d(0x0E1720, 0x18232E, 0xE8EEF3, 0xA9B6C2, 0xE6C068, 0x2B3946)),
            new P("Graphite", 0xFFADCFFF, Kind.TONAL_SPOT, null, null, d(0x171C24, 0x232C38, 0xEDF3FA, 0xB0BFD0, 0xADCFFF, 0x3A485A)),
            new P("NightViolet", 0xFFD3B6FF, Kind.TONAL_SPOT, null, null, d(0x191724, 0x282337, 0xF3ECFF, 0xBFB1D2, 0xD3B6FF, 0x493D5B)),
            // Vivid themes: designed light colors (clear paper, saturated accent); dark mode generated from the seed.
            new P("Blue", 0xFF2196F3, Kind.TONAL_SPOT, null, d(0xEEF4FC, 0xFFFFFF, 0x10233D, 0x52627A, 0x1565C0, 0xD3E0F0), null),
            new P("Red", 0xFFEB4132, Kind.TONAL_SPOT, null, d(0xFDF0EF, 0xFFFCFB, 0x3B1513, 0x7A5350, 0xC62828, 0xF1D6D3), null),
            new P("Yellow", 0xFFFFBA00, Kind.TONAL_SPOT, null, d(0xFFF7DC, 0xFFFDF4, 0x3A2E05, 0x6E5F2E, 0x8F6300, 0xF0E2B3), null),
            new P("Green", 0xFF7CB342, Kind.TONAL_SPOT, null, d(0xF0F7EA, 0xFCFFF9, 0x1D2E17, 0x56664F, 0x3A7A22, 0xD8E6CF), null),
            new P("Pink", 0xFFC2185B, Kind.TONAL_SPOT, null, d(0xFCEFF5, 0xFFFBFD, 0x3D1428, 0x7A5266, 0xC2185B, 0xF1D4E2), null),
            new P("Orange", 0xFFFF8800, Kind.TONAL_SPOT, null, d(0xFFF3E8, 0xFFFCF8, 0x3D2210, 0x7B5A45, 0xB84F00, 0xF3DCC8), null),
            new P("Purple", 0xFF8249E8, Kind.TONAL_SPOT, null, d(0xF4F0FD, 0xFDFBFF, 0x261A45, 0x625A7D, 0x6A3FD0, 0xE0D7F5), null),
            new P("Teal", 0xFF00897B, Kind.TONAL_SPOT, null, d(0xE9F6F4, 0xFAFFFE, 0x0F2E2A, 0x4D6965, 0x00796B, 0xCDE6E2), null),
            new P("Monochrome", 0xFF000000, Kind.MONOCHROME, null, null, null)
    );

    /** Default "My theme" (paper, surface, accent) until the user picks colors. */
    static final int[] CUSTOM_DEFAULT = {0xFFF4ECDD, 0xFFFFFBF3, 0xFF8E2F25};

    static LinkedHashMap<String, Integer> roles(P p, boolean night) {
        if (p.alwaysDark != null) return designed(p.alwaysDark, true);
        if (!night && p.light != null) return designed(p.light, false);
        return ThemeRoles.fromSeed(p.seed, p.kind, p.tertiary, night);
    }

    static LinkedHashMap<String, Integer> designed(D d, boolean dark) {
        return ThemeRoles.designed(d.paper, d.surface, d.ink, d.muted, d.accent, d.line, dark);
    }

    static String items(Map<String, Integer> roles, String indent) {
        StringBuilder b = new StringBuilder();
        roles.forEach((k, v) -> b.append(indent).append(String.format("<item name=\"%s\">#%06X</item>%n", k, v & 0xFFFFFF)));
        return b.toString();
    }

    static String resName(String attr) { return "bp_custom_" + attr.replace("android:", ""); }

    static String palettes(boolean night) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<!-- GENERATED by tools/palette-gen - do not edit by hand. -->\n<resources>\n");
        b.append("    <style name=\"Palette.Default\" parent=\"Theme.Material3.DayNight.NoActionBar\">\n")
                .append(items(roles(PALETTES.get(0), night), "        ")).append("    </style>\n\n");
        for (P p : PALETTES) {
            String what = p.alwaysDark != null ? "designed, always dark" : p.light != null ? "designed light, generated dark" : p.kind + " from seed";
            b.append(String.format("    <!-- seed #%06X, %s -->%n", p.seed & 0xFFFFFF, what));
            b.append("    <style name=\"Palette.").append(p.style).append("\" parent=\"\">\n")
                    .append(items(roles(p, night), "        ")).append("    </style>\n\n");
        }
        // "My theme": every role points to a color resource that the app overrides at runtime (Android 11+).
        b.append("    <style name=\"Palette.Custom\" parent=\"\">\n");
        for (String attr : ThemeRoles.custom(CUSTOM_DEFAULT[0], CUSTOM_DEFAULT[1], CUSTOM_DEFAULT[2]).keySet())
            b.append(String.format("        <item name=\"%s\">@color/%s</item>%n", attr, resName(attr)));
        b.append("    </style>\n");
        return b.append("</resources>\n").toString();
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        Path res = root.resolve("app/src/main/res");
        Files.writeString(res.resolve("values/palettes.xml"), palettes(false));
        Files.writeString(res.resolve("values-night/palettes.xml"), palettes(true));

        LinkedHashMap<String, Integer> custom = ThemeRoles.custom(CUSTOM_DEFAULT[0], CUSTOM_DEFAULT[1], CUSTOM_DEFAULT[2]);
        StringBuilder colors = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<!-- GENERATED by tools/palette-gen. Default \"My theme\"; replaced at runtime by CustomTheme. -->\n<resources>\n");
        custom.forEach((k, v) -> colors.append(String.format("    <color name=\"%s\">#%06X</color>%n", resName(k), v & 0xFFFFFF)));
        Files.writeString(res.resolve("values/colors_custom_theme.xml"), colors.append("</resources>\n").toString());

        StringBuilder kt = new StringBuilder("// GENERATED by tools/palette-gen - do not edit by hand.\n")
                .append("package org.qosp.notes.ui.theme\n\nimport org.qosp.notes.R\n\n")
                .append("/** Theme attribute name (as in ThemeRoles) -> color resource used by Palette.Custom. */\n")
                .append("object CustomThemeResources {\n    val byAttribute: Map<String, Int> = mapOf(\n");
        custom.keySet().forEach(k -> kt.append(String.format("        \"%s\" to R.color.%s,%n", k, resName(k))));
        kt.append("    )\n}\n");
        Files.writeString(root.resolve("app/src/main/java/org/qosp/notes/ui/theme/CustomThemeResources.kt"), kt.toString());
        System.out.println("Wrote " + PALETTES.size() + " palettes + custom theme resources");
    }
}
