package config;

import java.awt.Color;

/*
Questa classe conntiene tutte le costati necessarie nell'applicazione per diminuire la ridondanza
 */
public class AppConfig {
    // Colori principali
    public static final Color BACKGROUND_COLOR = new Color(240, 240, 240);
    public static final Color PRIMARY_COLOR = new Color(0, 120, 215);
    public static final Color SECONDARY_COLOR = new Color(32, 32, 32);

    // Finestre
    public static final int DEFAULT_WIDTH = 800;
    public static final int DEFAULT_HEIGHT = 600;

    // Aggiornamento dati
    public static final int REFRESH_INTERVAL_SECONDS = 30;

    // App
    public static final String APP_TITLE = "Damose - Rome Transit Tracker";
}
