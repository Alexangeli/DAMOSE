package Model.Net;

public interface ConnectionStatusProvider {
    ConnectionState getState();
    void addListener(ConnectionListener listener);
    void removeListener(ConnectionListener listener);
}