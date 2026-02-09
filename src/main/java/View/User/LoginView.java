package View.User;

import Controller.User.LoginController;
import Model.User.User;

import javax.swing.*;
import java.awt.*;

public class LoginView extends JPanel {

    public interface Navigation {
        void goToRegister();
        void onLoginSuccess(User user);
    }

    private final Navigation nav;
    private final LoginController loginController = new LoginController();

    // ✅ campi come membri
    private JTextField username;
    private JPasswordField password;
    private JLabel msg;

    public LoginView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

    /** ✅ reset della form login */
    public void resetForm() {
        if (username != null) username.setText("");
        if (password != null) password.setText("");
        if (msg != null) msg.setText(" ");
    }

    private void buildUI() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);

        username = new JTextField(18);
        password = new JPasswordField(18);

        JButton loginBtn = new JButton("Accedi");
        JButton goRegisterBtn = new JButton("Registrati");

        msg = new JLabel(" ");
        msg.setForeground(new Color(176, 0, 32));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Login");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, c);

        c.gridy++; c.gridwidth = 1;
        add(new JLabel("Username"), c);
        c.gridx = 1;
        add(username, c);

        c.gridx = 0; c.gridy++;
        add(new JLabel("Password"), c);
        c.gridx = 1;
        add(password, c);

        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        add(msg, c);

        c.gridy++;
        add(loginBtn, c);

        c.gridy++;
        goRegisterBtn.setBorderPainted(false);
        goRegisterBtn.setContentAreaFilled(false);
        goRegisterBtn.setForeground(new Color(25, 118, 210));
        goRegisterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        add(goRegisterBtn, c);

        loginBtn.addActionListener(e -> {
            msg.setText(" ");

            String u = username.getText() == null ? "" : username.getText().trim();
            String p = new String(password.getPassword());

            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Inserisci username e password.");
                return;
            }

            User user = loginController.login(u, p);

            if (user == null) {
                msg.setText("Credenziali non valide oppure errore DB.");
                return;
            }

            nav.onLoginSuccess(user);
        });

        goRegisterBtn.addActionListener(e -> {
            resetForm(); // ✅ pulisci quando vai a register
            nav.goToRegister();
        });

        password.addActionListener(e -> loginBtn.doClick());
    }
}