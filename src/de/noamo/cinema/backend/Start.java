/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Einstieg in das Programm. Hier werden alle Dienste (die zu dem Backend gehören) gestartet.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 05.09.2020
 */
public abstract class Start {
    private final static int WEBSOCKET_PORT = 56953;
    private static String certificatePath;
    private static String host;
    private static int restApiPort = 4567;

    /**
     * Fragt den KeyStorePath ab. Dieser gibt an, wo die Schlüssel für die Zertifikate der Domain liegen.
     */
    static String getCertificatePath() {
        return certificatePath;
    }

    /**
     * Fragt den Host ab, auf dem der Server läuft.
     */
    static String getHost() {
        return host;
    }

    /**
     * Fragt den Port ab, auf der die REST API arbeitet.
     */
    static int getRestApiPort() {
        return restApiPort;
    }

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
            System.exit(1);
        }
    }

    private static String interpretKeyStorePath(String arg) throws IOException {
        // Prüfen, ob Datei existiert
        File temp = new File(arg);
        if (!temp.exists()) {
            log(2, "Zertifikat konnte nicht geladen werden (Datei wurde nicht gefunden)");
            System.exit(1);
        }

        // Prüfen, ob Datei den dirketen Pfad enthält
        if (!temp.getName().equals("last_nginx.conf")) return arg;

        // nginx interpretieren (nur Linux)
        log(0, "Zertifikat-Argument als nginx Konigurationsdatei erkannt");
        Scanner scanner = new Scanner(temp);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains("ssl_certificate")) {
                line = line.split("/", 2)[1];
                line = "/" + line.substring(0, line.length() - 1);
                log(0, "Erkannter Pfad des Zertifikates: " + line);
                return line;
            }
        }
        log(2, "nginx Datei konnte nicht interpretiert werden. Es wird daher kein Zertifikat verwendet!");
        return null;
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
        try {
            for (String s : args) {
                if (s.toUpperCase().startsWith("DB=")) dbUrl = s.substring(3);
                else if (s.toUpperCase().startsWith("MAIL=")) setupMail(s.substring(5));
                else if (s.toUpperCase().startsWith("RESTPORT=")) restApiPort = Integer.parseInt(s.substring(9));
                else if (s.toUpperCase().startsWith("HOST=")) host = s.substring(5);
                else if (s.toUpperCase().startsWith("ZERTIFIKAT="))
                    certificatePath = interpretKeyStorePath(s.substring(11));
            }
        } catch (Exception e) {
            log(2, "Argumente konnten nicht gelesen werden (" + e.getClass().toString() + ": " + e.getMessage() + ")");
            System.exit(1);
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
                RestServer.start(restApiPort);
                log(1, "Die REST API wurde gestartet");
                break;
            } catch (Exception e) {
                log(2, "REST API (" + e.getMessage() + ")");
                Thread.sleep(10000);
            }
        }
    }
}
