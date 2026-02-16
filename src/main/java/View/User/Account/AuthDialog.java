package View.User.Account;

import Model.User.User;

import javax.swing.*;
import java.awt.*;

public class AuthDialog extends JDialog {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    private LoginView loginView;
    private RegisterView registerView;

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
        // Evita qualsiasi bordo/grigio del root pane
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

    private void resizeToCurrentCard() {
        SwingUtilities.invokeLater(() -> {
            Component card = getVisibleCard();
            if (card != null) {
                Dimension pd = card.getPreferredSize();
                if (pd != null) {
                    // Riduciamo la larghezza per eliminare i bordi laterali (aumenta/diminuisci se serve)
                    int reducedWidth = pd.width - 80;

                    // Evita valori troppo piccoli su schermi/font diversi
                    int minW = 380;
                    if (reducedWidth < minW) reducedWidth = minW;

                    Dimension adjusted = new Dimension(reducedWidth, pd.height);

                    setSize(adjusted);
                    content.setPreferredSize(adjusted);
                }
            }

            // Nessun pack(): evitiamo che Swing aggiunga spazio extra
            setLocationRelativeTo(getOwner());
        });
    }

    /** Ritorna la card attualmente visibile dentro il CardLayout. */
    private Component getVisibleCard() {
        for (Component c : content.getComponents()) {
            if (c != null && c.isVisible()) return c;
        }
        return loginView;
    }
}