package TestUser.Controller.View;

import View.User.Account.AccountDropdown;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class AccountDropdownTest {

    @Test
    public void dropdown_show_hide_works() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());

        AtomicBoolean profileCalled = new AtomicBoolean(false);
        AtomicBoolean logoutCalled = new AtomicBoolean(false);

        SwingTestUtils.runOnEdt(() -> {
            JFrame owner = new JFrame();
            owner.setSize(400, 300);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);

            AccountDropdown dd = new AccountDropdown(owner,
                    () -> profileCalled.set(true),
                    () -> logoutCalled.set(true)
            );

            dd.setUiScale(1.0);
            dd.showAtScreen(200, 200);
            assertTrue(dd.isVisible());

            dd.hide();
            assertFalse(dd.isVisible());

            owner.dispose();
        });
    }
}