package TestView.Theme;

import View.Theme.Theme;
import View.Theme.Themes;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.*;

public class ThemesTest {

    /**
     * Test della classe Themes.
     * 
     * Questo test ha l'obiettivo di verificare che i temi predefiniti siano correttamente inizializzati
     * e che i colori specificati in esadecimale vengano tradotti correttamente in oggetti Color.
     * Tale verifica è fondamentale per garantire la coerenza visiva dell'applicazione Damose e
     * il corretto funzionamento della gestione dei temi tramite ThemeManager.
     */
    @Test
    public void testDefaultArancioneTheme() {
        Theme t = Themes.DEFAULT_ARANCIONE;

        assertEquals("Default (Arancione)", t.name);
        assertEquals(new Color(0xF5,0x7C,0x00), t.primary);
        assertEquals(new Color(0xE6,0x51,0x00), t.primaryDark);
        assertEquals(new Color(0xFF,0xA0,0x00), t.accent);
        assertEquals(new Color(0xD3,0x2F,0x2F), t.danger);
        assertEquals(new Color(0xF6,0xF7,0xFB), t.bg);
        assertEquals(new Color(0xFF,0xFF,0xFF), t.card);
        assertEquals(new Color(0xE6,0xE8,0xEF), t.border);
        assertEquals(new Color(0x11,0x18,0x27), t.text);
        assertEquals(new Color(0x6B,0x72,0x80), t.textMuted);
        assertEquals(new Color(0xFF,0xFF,0xFF), t.onPrimary);
        assertEquals(new Color(0xFF,0xF3,0xE0), t.hover);
        assertEquals(new Color(0xFF,0xE0,0xB2), t.selected);
    }

    @Test
    public void testRossoPompeianoTheme() {
        Theme t = Themes.ROSSO_POMPEIANO;

        assertEquals("Rosso Pompeiano", t.name);
        assertEquals(new Color(0xC0,0x39,0x2B), t.primary);
        assertEquals(new Color(0x92,0x2B,0x21), t.primaryDark);
        assertEquals(new Color(0xE7,0x4C,0x3C), t.accent);
        assertEquals(new Color(0xB7,0x1C,0x1C), t.danger);
        assertEquals(new Color(0xF6,0xF7,0xFB), t.bg);
        assertEquals(new Color(0xFF,0xFF,0xFF), t.card);
        assertEquals(new Color(0xE6,0xE8,0xEF), t.border);
        assertEquals(new Color(0x11,0x18,0x27), t.text);
        assertEquals(new Color(0x6B,0x72,0x80), t.textMuted);
        assertEquals(new Color(0xFF,0xFF,0xFF), t.onPrimary);
        assertEquals(new Color(0xFD,0xED,0xEC), t.hover);
        assertEquals(new Color(0xF5,0xB7,0xB1), t.selected);
    }
}