package TestUser.Controller.View;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

public final class SwingTestUtils {

    private SwingTestUtils() {}

    /** Esegue una runnable sull'EDT e aspetta che finisca. */
    public static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Esegue una callable sull'EDT e ritorna il risultato. */
    public static <T> T callOnEdt(Callable<T> c) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        FutureTask<T> task = new FutureTask<>(c);
        runOnEdt(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Cerca un componente nel subtree che matcha un predicato. */
    public static Component findComponent(Container root, Predicate<Component> pred) {
        if (root == null) return null;

        for (Component c : root.getComponents()) {
            if (pred.test(c)) return c;
            if (c instanceof Container) {
                Component found = findComponent((Container) c, pred);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static JLabel findLabelByText(Container root, String text) {
        Component c = findComponent(root, comp ->
                comp instanceof JLabel && text.equals(((JLabel) comp).getText())
        );
        return (JLabel) c;
    }

    public static JButton findButtonByText(Container root, String text) {
        Component c = findComponent(root, comp ->
                comp instanceof JButton && text.equals(((JButton) comp).getText())
        );
        return (JButton) c;
    }

    public static JTextField findTextField(Container root) {
        Component c = findComponent(root, comp -> comp instanceof JTextField);
        return (JTextField) c;
    }

    public static JPasswordField findPasswordField(Container root) {
        Component c = findComponent(root, comp -> comp instanceof JPasswordField);
        return (JPasswordField) c;
    }
}