package View;

import Model.User.Session;

import javax.swing.*;
import java.awt.*;

public class AppShellView extends JPanel {

    private final JLabel statusLabel = new JLabel("Guest");
    private final JButton userButton = new JButton("👤");

    public AppShellView(JComponent centerContent, Runnable onUserClick) {
        setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        topBar.setBackground(new Color(245, 245, 245));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        userButton.setFocusable(false);
        userButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userButton.addActionListener(e -> onUserClick.run());

        right.add(statusLabel);
        right.add(userButton);

        topBar.add(right, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
        add(centerContent, BorderLayout.CENTER);

        refreshUserStatus();
    }

    public void refreshUserStatus() {
        if (Session.isLoggedIn()) {
            statusLabel.setText("Ciao, " + Session.getCurrentUser().getUsername());
        } else {
            statusLabel.setText("Guest");
        }
    }
}