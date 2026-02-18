package View.User.Account;

import Model.User.User;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog modale per autenticazione utente.
 *
 * Funzione:
 * - contiene due schermate (login e registrazione) gestite tramite CardLayout
 * - permette di passare da una schermata all'altra tramite callback di navigazione
 * - notifica l'applicazione quando l'utente effettua il login con successo
 *
 * Note di progetto:
 * - la dialog è volutamente "pulita" (sfondo bianco e senza bordi del root pane)
 * - non usa pack() durante i cambi card per evitare padding extra e layout instabili
 * - la dimensione viene adattata alla card attualmente visibile
 */
public class AuthDialog extends JDialog {

    /** Gestione delle schermate (login/register) tramite CardLayout. */
    private final CardLayout cards = new CardLayout();

    /** Pannello root che contiene le card. */
    private final JPanel content = new JPanel(cards);

    /** Schermata di login. */
    private LoginView loginView;

    /** Schermata di registrazione. */
    private RegisterView registerView;

    /**
     * Crea la dialog di autenticazione.
     *
     * @param parent finestra owner (usata per centratura e modality)
     * @param onAuthChanged callback invocata dopo un login riuscito
     */
    public AuthDialog(Window parent, Runnable onAuthChanged) {
        super(parent, "Account", ModalityType.APPLICATION_MODAL);

        loginView = new LoginView(new LoginView.Navigation() {
            @Override public void goToRegister() {
                registerView.resetForm();
                cards.show(content, "register");
                resizeToCurrentCard();
            }

            @Override public void onLoginSuccess(User user) {
                onAuthChanged.run();
                dispose();
            }
        });

        registerView = new RegisterView(new RegisterView.Navigation() {
            @Override public void goToLogin() {
                loginView.resetForm();
                cards.show(content, "login");
                resizeToCurrentCard();
            }
        });

        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        content.add(loginView, "login");
        content.add(registerView, "register");

        setContentPane(content);

        // Impostazioni per ottenere una finestra completamente bianca e senza bordi extra.
        getRootPane().setBorder(null);

        setBackground(Color.WHITE);
        getContentPane().setBackground(Color.WHITE);
        content.setBackground(Color.WHITE);
        content.setOpaque(true);
        getRootPane().setOpaque(true);
        getRootPane().setBackground(Color.WHITE);

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        cards.show(content, "login");
        resizeToCurrentCard();
        setLocationRelativeTo(parent);
    }

    /**
     * Ridimensiona la dialog in base alla card visibile.
     * La logica usa la preferredSize della card e riduce leggermente la larghezza
     * per evitare margini laterali indesiderati.
     */
    private void resizeToCurrentCard() {
        SwingUtilities.invokeLater(() -> {
            Component card = getVisibleCard();
            if (card != null) {
                Dimension pd = card.getPreferredSize();
                if (pd != null) {
                    int reducedWidth = pd.width - 80;

                    // Protezione per schermi/font diversi: evita una finestra troppo stretta.
                    int minW = 380;
                    if (reducedWidth < minW) reducedWidth = minW;

                    Dimension adjusted = new Dimension(reducedWidth, pd.height);

                    setSize(adjusted);
                    content.setPreferredSize(adjusted);
                }
            }

            // Evitiamo pack(): così non viene aggiunto spazio extra dal layout di Swing.
            setLocationRelativeTo(getOwner());
        });
    }

    /**
     * Restituisce la card attualmente visibile.
     * Se per qualche motivo non viene trovata, ritorna un fallback (loginView).
     *
     * @return componente visibile nel CardLayout
     */
    private Component getVisibleCard() {
        for (Component c : content.getComponents()) {
            if (c != null && c.isVisible()) return c;
        }
        return loginView;
    }
}
