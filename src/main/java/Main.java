import Controller.AppController;
import config.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.ImageIcon;

/**
 * Entry point dell'applicazione DAMOSE.
 *
 * Avvia l'applicazione su Event Dispatch Thread (EDT) e delega l'inizializzazione
 * all'{@link AppController}, che si occupa di costruire la UI e collegare controller,
 * view e servizi.
 */
public class Main {

    /**
     * Punto di ingresso del programma.
     * L'avvio viene eseguito su EDT per rispettare le regole di Swing.
     *
     * @param args argomenti da linea di comando (non utilizzati)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppController().start());
    }

    /**
     * Crea e configura la finestra principale dell'applicazione.
     *
     * Imposta:
     * - titolo e dimensioni minime
     * - colore di background
     * - icona dell'applicazione (barra titolo e, se supportato, taskbar)
     *
     * @return frame principale configurato
     */
    private static JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

        try {
            java.net.URL iconUrl = Main.class.getResource("/icons/logo.png");
            if (iconUrl != null) {
                Image icon = new ImageIcon(iconUrl).getImage();
                myFrame.setIconImage(icon);

                // Su alcune piattaforme è possibile impostare anche l'icona della taskbar.
                try {
                    Taskbar.getTaskbar().setIconImage(icon);
                } catch (Exception ignored) {
                    // Non tutte le piattaforme supportano Taskbar o consentono l'operazione.
                }
            } else {
                System.err.println("[Main] Icon not found: /icons/logo.png");
            }
        } catch (Exception ex) {
            System.err.println("[Main] Failed to set app icon: " + ex.getMessage());
        }

        return myFrame;
    }
}
