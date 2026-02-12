package Controller.Status;

import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;

import javax.swing.*;

public class OnlineDotController {
    private final ConnectionStatusProvider status;

    public OnlineDotController(ConnectionStatusProvider status) {
        this.status = status;

        status.addListener(s -> SwingUtilities.invokeLater(() -> {
            boolean online = (s == ConnectionState.ONLINE);
            // set pallino verde/arancione
        }));
    }
}
