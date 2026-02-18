package TestView.Theme;

import View.Theme.Theme;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.*;

public class ThemeTest {

    /**
     * Test della classe Theme.
     * 
     * In questo test abbiamo voluto verificare la corretta assegnazione dei valori 
     * durante l'inizializzazione dell'oggetto Theme. L'intento principale è 
     * confermare che ciascun campo della classe mantenga coerentemente i valori 
     * passati al costruttore, in accordo con il principio di immutabilità degli 
     * oggetti final. Tale verifica è fondamentale per garantire l'integrità della 
     * palette cromatica e la consistenza dell'interfaccia utente all'interno 
     * dell'applicazione Damose, come richiesto dalle specifiche del progetto.
     */
    @Test
    public void testThemeInitialization() {
        Color primary = new Color(255, 122, 0);
        Color primaryDark = new Color(200, 100, 0);
        Color accent = new Color(0, 122, 255);
        Color danger = new Color(255, 0, 0);
        Color bg = new Color(240, 240, 240);
        Color card = new Color(255, 255, 255);
        Color border = new Color(200, 200, 200);
        Color text = new Color(0, 0, 0);
        Color textMuted = new Color(120, 120, 120);
        Color onPrimary = new Color(255, 255, 255);
        Color hover = new Color(255, 140, 0);
        Color selected = new Color(255, 180, 0);

        Theme theme = new Theme(
                "TestTheme",
                primary, primaryDark, accent, danger,
                bg, card, border,
                text, textMuted, onPrimary,
                hover, selected
        );

        assertEquals("TestTheme", theme.name);
        assertEquals(primary, theme.primary);
        assertEquals(primaryDark, theme.primaryDark);
        assertEquals(accent, theme.accent);
        assertEquals(danger, theme.danger);
        assertEquals(bg, theme.bg);
        assertEquals(card, theme.card);
        assertEquals(border, theme.border);
        assertEquals(text, theme.text);
        assertEquals(textMuted, theme.textMuted);
        assertEquals(onPrimary, theme.onPrimary);
        assertEquals(hover, theme.hover);
        assertEquals(selected, theme.selected);
    }
}