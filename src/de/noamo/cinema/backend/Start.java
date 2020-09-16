/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

/**
 * Einstieg in das Programm. Hier werden alle Dienste (die zu dem Backend gehören) gestartet.
 *
 * @author Noah Hoelterhoff
 * @version 05.09.2020
 * @since 05.09.2020
 */
public abstract class Start {
    private final static String HOST = "localhost";
    private final static int REST_PORT = 4567;
    private final static int WEBSOCKET_PORT = 56953;

    /**
     * Lädt alle Resourcen, die während der Laufzeit benötigt werden
     */
    private static void loadResources() {
        try {
            Resources.loadResources();
            log(1, "Alle Resourcen erfolgreich geladen");
        } catch (Exception e) {
            log(2, "Es konnten nicht alle Resourcen geladen werden (" + e.getMessage() + ")");
            log(0, "Das Programm wird nun beendet...");
        }
    }

    /**
     * Loggt einen Text in der Konsole
     *
     * @param type 0=plain, 1=ok prefix, 2=fehler prefix
     */
    static void log(int type, String message) {
        String pre = (type == 2 ? "[\033[0;31mFEHLER\033[0m] " : (type == 1 ? "[\033[0;32mOK\033[0m] " : ""));
        System.out.println(pre + message);
    }

    /**
     * Einstieg in das Programm
     *
     * @param args Aktuell: DB&lt;JDBC-URL&gt;, (...)
     */
    public static void main(String[] args) throws InterruptedException {
        // args lesen
        String dbUrl = null;
        for (String s : args) {
            if (s.toUpperCase().startsWith("DB=")) dbUrl = s.substring(3);
            if (s.toUpperCase().startsWith("MAIL=")) setupMail(s.substring(5));
            // Platz für mögliche Erweiterungen der args
        }

        // Laden & Starten
        loadResources();
        waitForDataBase(dbUrl);
        waitForRestApi();
        //new Server(new InetSocketAddress(HOST, WEBSOCKET_PORT)).run();
    }

    /**
     * Interpretiert einen EMail-Konfigurations-String. Dieser muss folgendes Fotmat haben: {@code
     * HOST:PORT:EMAILADRESSE:PASSWORT}
     *
     * @param emailString String im oben beschriebene Format
     */
    private static void setupMail(String emailString) {
        String[] emailSplit = emailString.split(":", 4);
        Mail.emailHost = emailSplit[0];
        Mail.emailPort = Integer.parseInt(emailSplit[1]);
        Mail.emailAdresse = emailSplit[2];
        Mail.emailPasswort = emailSplit[3];
    }

    /**
     * Wartet darauf, dass eine Verbindung zu der Datenbank hergestellt wird. Diese Methode probiert dabei alle 10
     * Sekunden erneut, die Vebrindung herzustellen.
     *
     * @param dbUrl Die JDBC-URL für die Datenbank (inkl. Benutzername und Passwort)
     * @throws InterruptedException Falls die Methode beim 10 Sekunden warten unterbrochen wird
     */
    @SuppressWarnings("BusyWait")
    private static void waitForDataBase(String dbUrl) throws InterruptedException {
        while (true) {
            try {
                DataBase.connect(dbUrl);
                log(1, "Die Verbindung zu der Datenbank wurde erfolgreich hergestellt");
                break;
            } catch (Exception e) {
                log(2, "Datenbankverbindung (" + e.getMessage() + ")");
                Thread.sleep(10000);
            }
        }
    }

    /**
     * Wartet darauf, dass die REST API online geht. Diese Methode probiert dies dabei alle 10 Sekunden erneut.
     *
     * @throws InterruptedException Falls die Methode beim 10 Sekunden warten unterbrochen wird
     */
    @SuppressWarnings("BusyWait")
    private static void waitForRestApi() throws InterruptedException {
        while (true) {
            try {
                RestServer.start(REST_PORT);
                log(1, "Die REST API wurde gestartet");
                break;
            } catch (Exception e) {
                log(2, "REST API (" + e.getMessage() + ")");
                Thread.sleep(10000);
            }
        }
    }
}
