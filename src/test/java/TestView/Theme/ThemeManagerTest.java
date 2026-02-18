package TestView.Theme;

import View.Theme.Theme;
import View.Theme.ThemeManager;
import View.Theme.Themes;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

public class ThemeManagerTest {

    /**
     * Test della classe ThemeManager.
     * 
     * In questo test abbiamo voluto valutare il corretto funzionamento della gestione dei temi 
     * all’interno dell’applicazione. In particolare, si verifica:
     * 1) l’inizializzazione del tema corrente tramite il metodo get();
     * 2) l’aggiornamento del tema con set(), inclusa la notifica ai listener registrati;
     * 3) l’aggiunta e rimozione dei listener;
     * 4) l’applicazione ricorsiva dei colori ai componenti Swing tramite applyTo().
     * 
     * L’obiettivo di questa verifica è garantire la consistenza visiva dell’interfaccia 
     * e il rispetto dei principi di modularità e separazione delle responsabilità nella GUI, 
     * come indicato nelle specifiche del progetto Damose. La gestione dei listener 
     * con CopyOnWriteArrayList garantisce inoltre thread safety durante le modifiche dinamiche del tema.
     */
    @Test
    public void testThemeManagerBasics() {
        // Verifica tema iniziale
        Theme defaultTheme = ThemeManager.get();
        assertEquals(Themes.DEFAULT_ARANCIONE, defaultTheme);

        // Listener di test
        final boolean[] notified = {false};
        ThemeManager.ThemeListener listener = t -> {
            notified[0] = true;
            assertEquals(new Color(0, 128, 255), t.primary); // controlla il colore principale del nuovo tema
        };
        ThemeManager.addListener(listener);

        // Setta un nuovo tema
        Theme newTheme = new Theme(
                "Blu",
                new Color(0,128,255), new Color(0,100,200), new Color(255,255,0), new Color(255,0,0),
                new Color(240,240,240), new Color(255,255,255), new Color(200,200,200),
                new Color(0,0,0), new Color(120,120,120), new Color(255,255,255),
                new Color(0,150,255), new Color(0,180,255)
        );
        ThemeManager.set(newTheme);

        // Controlla aggiornamento tema
        assertEquals(newTheme, ThemeManager.get());
        assertTrue("Listener should have been notified", notified[0]);

        // Rimuovi listener e verifica che non venga più notificato
        notified[0] = false;
        ThemeManager.removeListener(listener);
        ThemeManager.set(Themes.DEFAULT_ARANCIONE);
        assertFalse("Listener should not be notified after removal", notified[0]);

        // Test applicazione su componenti Swing
        JPanel panel = new JPanel();
        JLabel label = new JLabel();
        JButton button = new JButton();
        JTextField textField = new JTextField();
        JList<String> list = new JList<>(new String[]{"a","b"});

        JPanel container = new JPanel();
        container.add(panel);
        container.add(label);
        container.add(button);
        container.add(textField);
        container.add(list);

        ThemeManager.set(newTheme);
        ThemeManager.applyTo(container);

        // Verifica colori dei componenti
        assertEquals(newTheme.bg, panel.getBackground());
        assertEquals(newTheme.text, label.getForeground());
        assertEquals(newTheme.primary, button.getBackground());
        assertEquals(newTheme.onPrimary, button.getForeground());
        assertEquals(newTheme.card, textField.getBackground());
        assertEquals(newTheme.text, textField.getForeground());
        assertEquals(newTheme.card, list.getBackground());
        assertEquals(newTheme.text, list.getForeground());
        assertEquals(newTheme.selected, list.getSelectionBackground());
    }
}