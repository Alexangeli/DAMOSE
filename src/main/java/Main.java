import config.AppConfig;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        JFrame myFrame = new JFrame();                                      //creo un nuovo frame
        myFrame.setTitle(AppConfig.APP_TITLE);                              //cambio il titolo
        myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);             //chiudo la pagina quando premo la x
        myFrame.setResizable(true);                                         //rendo le dimenzioni della pagina modificabili
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT); //dimensioni di default
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR); //colore pagina

        myFrame.setVisible(true);                                           //rendo la pagina isibile

    }
}