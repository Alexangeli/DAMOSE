package View.Theme;

import java.awt.Color;

public final class Themes {

    private static Color c(String hex) {
        return new Color(
                Integer.valueOf(hex.substring(1, 3), 16),
                Integer.valueOf(hex.substring(3, 5), 16),
                Integer.valueOf(hex.substring(5, 7), 16)
        );
    }

    // ✅ Tema attuale: arancione principale
    public static final Theme DEFAULT_ARANCIONE = new Theme(
            "Default (Arancione)",
            c("#F57C00"), // primary
            c("#E65100"), // primaryDark
            c("#FFA000"), // accent
            c("#D32F2F"), // danger (rosso)
            c("#F6F7FB"), // bg
            c("#FFFFFF"), // card
            c("#E6E8EF"), // border
            c("#111827"), // text
            c("#6B7280"), // textMuted
            c("#FFFFFF"), // onPrimary
            c("#FFF3E0"), // hover
            c("#FFE0B2")  // selected
    );

    // ✅ Tema nuovo: rosso pompeiano principale
    public static final Theme ROSSO_POMPEIANO = new Theme(
            "Rosso Pompeiano",
            c("#C0392B"), // primary
            c("#922B21"), // primaryDark
            c("#E74C3C"), // accent
            c("#B71C1C"), // danger
            c("#F6F7FB"), // bg
            c("#FFFFFF"), // card
            c("#E6E8EF"), // border
            c("#111827"), // text
            c("#6B7280"), // textMuted
            c("#FFFFFF"), // onPrimary
            c("#FDEDEC"), // hover
            c("#F5B7B1")  // selected
    );

    private Themes() {}
}