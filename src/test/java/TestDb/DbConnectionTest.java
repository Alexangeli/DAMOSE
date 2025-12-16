package TestDb;

import java.sql.Connection;
import java.sql.SQLException;
import db.util.DB;

public class DbConnectionTest {
    public static void main(String[] args) {
        try (Connection conn = DB.getConnection()) {
            if (conn != null) {
                System.out.println("Connessione al DB avvenuta con successo!");
            }
        } catch (SQLException e) {
            System.out.println("Errore durante la connessione:");
            e.printStackTrace();
        }
    }
}

