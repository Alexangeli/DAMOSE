package TestUser.Controller.View;

import View.User.Account.AppShellView;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

public class AppShellViewTest {

    @Test
    public void shell_exposesAuthButtonBounds() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        SwingTestUtils.runOnEdt(() -> {
            JPanel content = new JPanel();
            AppShellView shell = new AppShellView(content, () -> {});

            JFrame frame = new JFrame();
            frame.setSize(800, 600);
            frame.setContentPane(shell);
            frame.setVisible(true);

            Rectangle b = shell.getAuthButtonBoundsOnLayer();
            assertNotNull(b);

            // deve stare nell'area top-right (x > 0, y >= 0)
            assertTrue(b.x >= 0);
            assertTrue(b.y >= 0);

            frame.dispose();
        });
    }
}