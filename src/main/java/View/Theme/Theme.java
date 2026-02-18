package View.Theme;

import java.awt.Color;

/**
 * Rappresenta un tema dell’interfaccia grafica.
 *
 * Contiene i colori principali utilizzati nell’app, organizzati per categorie:
 * - Core: colori principali del tema
 * - Surfaces: sfondi, card e bordi
 * - Text: colori del testo
 * - States: colori per hover, selezioni, ecc.
 *
 * Questa classe è immutabile: i valori sono final e settati solo tramite costruttore.
 */
public final class Theme {

    /** Nome del tema (es. "Light", "Dark") */
    public final String name;

    // ===================== COLORI CORE =====================
    public final Color primary;      // colore principale
    public final Color primaryDark;  // variante scura del colore principale
    public final Color accent;       // colore accentuato (per evidenziare elementi)
    public final Color danger;       // colore di avviso/errore

    // ===================== COLORI SURFACES =====================
    public final Color bg;           // colore di sfondo principale
    public final Color card;         // colore sfondo card/pannelli
    public final Color border;       // colore bordi

    // ===================== COLORI TESTO =====================
    public final Color text;         // colore testo principale
    public final Color textMuted;    // colore testo secondario / muted
    public final Color onPrimary;    // colore del testo su elementi primary (es. button)

    // ===================== COLORI STATI =====================
    public final Color hover;        // colore per hover
    public final Color selected;     // colore per elementi selezionati

    /**
     * Costruttore completo per inizializzare tutti i colori del tema.
     *
     * @param name nome del tema
     * @param primary colore principale
     * @param primaryDark variante scura del colore principale
     * @param accent colore accentuato
     * @param danger colore di avviso/errore
     * @param bg colore di sfondo
     * @param card colore di card/pannelli
     * @param border colore dei bordi
     * @param text colore del testo principale
     * @param textMuted colore del testo secondario/muted
     * @param onPrimary colore del testo sopra primary
     * @param hover colore hover
     * @param selected colore selezione
     */
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
