package View.User.Account;

import Controller.User.account.RegisterController;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

public class RegisterView extends JPanel {

    public interface Navigation {
        void goToLogin();
    }

    private final Navigation nav;
    private final RegisterController registerController = new RegisterController();

    // ✅ campi come membri, così possiamo resettarli
    private JTextField username;
    private JTextField email;
    private JPasswordField pass1;
    private JPasswordField pass2;
    private JLabel msg;

    public RegisterView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

    /** ✅ reset completo della form */
    public void resetForm() {
        if (username != null) username.setText("");
        if (email != null) email.setText("");
        if (pass1 != null) pass1.setText("");
        if (pass2 != null) pass2.setText("");
        if (msg != null) {
            msg.setForeground(new Color(176, 0, 32));
            msg.setText(" ");
        }
    }

    private void buildUI() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);

        username = new JTextField(18);
        email = new JTextField(18);
        pass1 = new JPasswordField(18);
        pass2 = new JPasswordField(18);

        JButton registerBtn = new JButton("Crea account");
        JButton goLoginBtn = new JButton("Torna al login");

        msg = new JLabel(" ");
        msg.setForeground(new Color(176, 0, 32));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Registrazione");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, c);

        c.gridy++; c.gridwidth = 1;
        add(new JLabel("Username"), c);
        c.gridx = 1; add(username, c);

        c.gridx = 0; c.gridy++;
        add(new JLabel("Email"), c);
        c.gridx = 1; add(email, c);

        c.gridx = 0; c.gridy++;
        add(new JLabel("Password"), c);
        c.gridx = 1; add(pass1, c);

        c.gridx = 0; c.gridy++;
        add(new JLabel("Ripeti password"), c);
        c.gridx = 1; add(pass2, c);

        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        add(msg, c);

        c.gridy++;
        add(registerBtn, c);

        c.gridy++;
        goLoginBtn.setBorderPainted(false);
        goLoginBtn.setContentAreaFilled(false);
        goLoginBtn.setForeground(new Color(25, 118, 210));
        goLoginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        add(goLoginBtn, c);

        registerBtn.addActionListener(e -> {
            msg.setText(" ");
            msg.setForeground(new Color(176, 0, 32));

            String u = username.getText() == null ? "" : username.getText().trim();
            String em = email.getText() == null ? "" : email.getText().trim();
            String p1 = new String(pass1.getPassword());
            String p2 = new String(pass2.getPassword());

            if (u.isEmpty() || em.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
                msg.setText("Compila tutti i campi.");
                return;
            }
            if (!Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                    .matcher(em).matches()) {
                msg.setText("Email non valida.");
                return;
            }
            if (!p1.equals(p2)) {
                msg.setText("Le password non coincidono.");
                return;
            }

            boolean ok = registerController.register(u, em, p1);

            if (ok) {
                msg.setForeground(new Color(27, 94, 32));
                msg.setText("Registrazione completata! Ora fai login.");

                // ✅ IMPORTANTISSIMO: reset prima di cambiare schermata
                resetForm();
                nav.goToLogin();
            } else {
                msg.setText("Registrazione fallita: username già usato o errore DB.");
            }
        });

        goLoginBtn.addActionListener(e -> {
            // ✅ se torni al login, pulisci questa form
            resetForm();
            nav.goToLogin();
        });

        pass2.addActionListener(e -> registerBtn.doClick());
    }
}