package View.User;

import Model.User.Session;
import Model.User.User;

import javax.swing.*;
import java.awt.*;

public class AuthDialog extends JDialog {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    public AuthDialog(Window parent, Runnable onAuthChanged) {
        super(parent, "Account", ModalityType.APPLICATION_MODAL);

        LoginView loginView = new LoginView(new LoginView.Navigation() {
            @Override public void goToRegister() {
                cards.show(content, "register");
            }

            @Override public void onLoginSuccess(User user) {
                Session.login(user);     // ✅ usa la vostra Session
                onAuthChanged.run();
                dispose();
            }
        });

        RegisterView registerView = new RegisterView(new RegisterView.Navigation() {
            @Override public void goToLogin() {
                cards.show(content, "login");
            }
        });

        content.add(loginView, "login");
        content.add(registerView, "register");

        setContentPane(content);
        cards.show(content, "login");

        setSize(430, 360);
        setLocationRelativeTo(parent);
    }
}