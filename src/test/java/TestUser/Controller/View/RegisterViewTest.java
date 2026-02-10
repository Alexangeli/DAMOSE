package TestUser.Controller.View;

import View.User.Account.RegisterView;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RegisterViewTest {

    @Test
    public void registerView_buildsComponents() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        AtomicBoolean goLoginCalled = new AtomicBoolean(false);

        SwingTestUtils.runOnEdt(() -> {
            RegisterView view = new RegisterView(() -> goLoginCalled.set(true));

            assertNotNull(SwingTestUtils.findLabelByText(view, "Username"));
            assertNotNull(SwingTestUtils.findLabelByText(view, "Email"));
            assertNotNull(SwingTestUtils.findLabelByText(view, "Password"));
            assertNotNull(SwingTestUtils.findLabelByText(view, "Ripeti password"));

            JButton createBtn = SwingTestUtils.findButtonByText(view, "Crea account");
            assertNotNull(createBtn);

            JButton backBtn = SwingTestUtils.findButtonByText(view, "Torna al login");
            assertNotNull(backBtn);

            backBtn.doClick();
            assertTrue(goLoginCalled.get());
        });
    }

    @Test
    public void resetForm_clearsFields() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        SwingTestUtils.runOnEdt(() -> {
            RegisterView view = new RegisterView(() -> {});

            // riempi i campi
            JTextField[] textFields = view.getComponents() == null ? new JTextField[0] : new JTextField[0];
            // cerchiamo manualmente i 2 JTextField
            JTextField first = (JTextField) SwingTestUtils.findComponent(view, c -> c instanceof JTextField);
            assertNotNull(first);
            first.setText("u");

            // trova un secondo JTextField (email)
            JTextField email = (JTextField) SwingTestUtils.findComponent(view, c ->
                    c instanceof JTextField && c != first
            );
            assertNotNull(email);
            email.setText("a@b.com");

            // trova password fields
            JPasswordField p1 = (JPasswordField) SwingTestUtils.findComponent(view, c -> c instanceof JPasswordField);
            assertNotNull(p1);
            p1.setText("x");

            JPasswordField p2 = (JPasswordField) SwingTestUtils.findComponent(view, c ->
                    c instanceof JPasswordField && c != p1
            );
            assertNotNull(p2);
            p2.setText("y");

            view.resetForm();

            assertEquals("", first.getText());
            assertEquals("", email.getText());
            assertEquals("", new String(p1.getPassword()));
            assertEquals("", new String(p2.getPassword()));
        });
    }
}