package TestUser.Controller.View;

import View.User.Account.AuthDialog;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

public class AuthDialogTest {

    @Test
    public void initialCard_isLogin() {
        // se in headless (CI), salta
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        SwingTestUtils.runOnEdt(() -> {
            JFrame owner = new JFrame();
            owner.setSize(300, 200);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);

            AuthDialog dlg = new AuthDialog(owner, () -> {});
            dlg.setVisible(false);

            // Deve contenere la label "Login" nella view iniziale
            JLabel loginTitle = SwingTestUtils.findLabelByText((Container) dlg.getContentPane(), "Login");
            assertNotNull("Dovrei vedere la view di Login come card iniziale", loginTitle);

            dlg.dispose();
            owner.dispose();
        });
    }
}