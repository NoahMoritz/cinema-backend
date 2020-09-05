/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.net.InetSocketAddress;
import java.sql.SQLException;

/**
 * @author Noah Hoelterhoff
 * @version 05.09.2020
 * @since 05.09.2020
 */
public class Start {
    private final static String HOST = "localhost";
    private final static int PORT = 56953;

    /**
     * Loggt einen Text in der Konsole
     *
     * @param type 0=plain, 1=ok prefix, 2=fehler prefix
     */
    public static void log(int type, String message) {
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
            if (s.toUpperCase().startsWith("DB")) dbUrl = s.substring(2);
            // Platz für mögliche Erweiterungen der args
        }

        waitForDataBase(dbUrl);
        new Server(new InetSocketAddress(HOST, PORT)).run();
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
                log(1, "Die Verbindung zu der Datenbank wurde erfoglreich hergestellt");
                break;
            } catch (SQLException e) {
                log(2, "Datenbankverbindung (" + e.getMessage() + ")");
                Thread.sleep(10000);
            }
        }
    }
}
