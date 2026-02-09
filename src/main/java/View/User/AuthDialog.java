package View.User;

import Model.User.User;

import javax.swing.*;
import java.awt.*;

public class AuthDialog extends JDialog {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    public AuthDialog(Window parent, Runnable onAuthChanged) {
        super(parent, "Account", ModalityType.APPLICATION_MODAL);

        final LoginView[] loginRef = new LoginView[1];
        final RegisterView[] registerRef = new RegisterView[1];

        LoginView loginView = new LoginView(new LoginView.Navigation() {
            @Override public void goToRegister() {
                registerRef[0].resetForm();
                cards.show(content, "register");
            }

            @Override public void onLoginSuccess(User user) {
                // Session.login(user) NON serve qui: lo fa già LoginController
                onAuthChanged.run();
                dispose();
            }
        });

        RegisterView registerView = new RegisterView(new RegisterView.Navigation() {
            @Override public void goToLogin() {
                loginRef[0].resetForm();
                cards.show(content, "login");
            }
        });

        loginRef[0] = loginView;
        registerRef[0] = registerView;

        content.add(loginView, "login");
        content.add(registerView, "register");

        setContentPane(content);
        cards.show(content, "login");

        setSize(430, 360);
        setLocationRelativeTo(parent);
    }
}