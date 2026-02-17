package View.Theme;

import java.awt.Color;

public final class Theme {
    public final String name;

    // Core
    public final Color primary;
    public final Color primaryDark;
    public final Color accent;
    public final Color danger;

    // Surfaces
    public final Color bg;
    public final Color card;
    public final Color border;

    // Text
    public final Color text;
    public final Color textMuted;
    public final Color onPrimary;

    // States
    public final Color hover;
    public final Color selected;

    public Theme(String name,
                 Color primary, Color primaryDark, Color accent, Color danger,
                 Color bg, Color card, Color border,
                 Color text, Color textMuted, Color onPrimary,
                 Color hover, Color selected) {

        this.name = name;
        this.primary = primary;
        this.primaryDark = primaryDark;
        this.accent = accent;
        this.danger = danger;
        this.bg = bg;
        this.card = card;
        this.border = border;
        this.text = text;
        this.textMuted = textMuted;
        this.onPrimary = onPrimary;
        this.hover = hover;
        this.selected = selected;
    }
}