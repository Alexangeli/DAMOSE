package TestUser.Controller.View;

import View.User.Account.LoginView;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class LoginViewTest {

    @Test
    public void loginView_buildsComponents() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        AtomicBoolean goRegisterCalled = new AtomicBoolean(false);

        SwingTestUtils.runOnEdt(() -> {
            LoginView view = new LoginView(new LoginView.Navigation() {
                @Override public void goToRegister() { goRegisterCalled.set(true); }
                @Override public void onLoginSuccess(Model.User.User user) {}
            });

            assertNotNull(SwingTestUtils.findLabelByText(view, "Username"));
            assertNotNull(SwingTestUtils.findLabelByText(view, "Password"));

            JButton loginBtn = SwingTestUtils.findButtonByText(view, "Accedi");
            assertNotNull(loginBtn);

            JButton regBtn = SwingTestUtils.findButtonByText(view, "Registrati");
            assertNotNull(regBtn);

            // click su "Registrati"
            regBtn.doClick();
            assertTrue(goRegisterCalled.get());
        });
    }

    @Test
    public void resetForm_clearsFields() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        SwingTestUtils.runOnEdt(() -> {
            LoginView view = new LoginView(new LoginView.Navigation() {
                @Override public void goToRegister() {}
                @Override public void onLoginSuccess(Model.User.User user) {}
            });

            // trova campi e scrivi qualcosa
            JTextField tf = (JTextField) SwingTestUtils.findComponent(view, c -> c instanceof JTextField);
            assertNotNull(tf);
            tf.setText("abc");

            JPasswordField pf = (JPasswordField) SwingTestUtils.findComponent(view, c -> c instanceof JPasswordField);
            assertNotNull(pf);
            pf.setText("pass");

            view.resetForm();

            assertEquals("", tf.getText());
            assertEquals("", new String(pf.getPassword()));
        });
    }
}