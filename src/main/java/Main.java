import Controller.AppController;
import config.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.ImageIcon;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::startApp);
    }

    private static void startApp() {
        JFrame myFrame = createFrame();

        AppController app = new AppController(myFrame);

        myFrame.setContentPane(app.getRoot());
        myFrame.setLocationRelativeTo(null);
        myFrame.setVisible(true);

        // avvia dopo che la UI è pronta
        app.start();
    }

    private static JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

        try {
            java.net.URL iconUrl = Main.class.getResource("/icons/logo.png");
            if (iconUrl != null) {
                Image icon = new ImageIcon(iconUrl).getImage();
                myFrame.setIconImage(icon);
                try {
                    Taskbar.getTaskbar().setIconImage(icon);
                } catch (Exception ignored) {}
            } else {
                System.err.println("[Main] Icon not found: /icons/logo.png");
            }
        } catch (Exception ex) {
            System.err.println("[Main] Failed to set app icon: " + ex.getMessage());
        }

        return myFrame;
    }
}