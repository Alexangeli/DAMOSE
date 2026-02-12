package Model.Net;

public interface ConnectionListener {
    void onConnectionStateChanged(ConnectionState newState);
}