/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import de.noamo.cinema.backend.exceptions.InvalidException;
import de.noamo.cinema.backend.exceptions.ParameterException;

import java.io.File;
import java.sql.SQLIntegrityConstraintViolationException;

import static spark.Spark.*;

/**
 * Richtet die REST API ein und steuert sie.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 15.09.2020
 */
abstract class RestServer {
    private final static int BAD_REQUEST = 400;
    private final static int CONFLICT = 409;
    private final static int FORBIDDEN = 403;
    private final static int SERVER_ERROR = 500;
    private final static Gson gson = new Gson();

    /**
     * Aktiviert CORS (Cross-Origin Resource Sharing). Dies wird z.B. für den Flutter Client benötigt um einwandfrei zu
     * funktionieren
     */
    private static void enableCORS() {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers",
                        accessControlRequestHeaders);
            }
            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });
        before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));
    }

    /**
     * Aktiviert HTTPS auf der REST API. Dazu wird das übergeben Zertifikat zuerst in einen PKCS12-Keystore übertragen
     * und dann in einen JKS-Keystore. Diesen JKS-Keystore kann dann Spark mit der {@link spark.Spark#secure(String,
     * String, String, String)} verwenden, um die Verbindung zu verschlüsseln.<br><br> Sollte die {@link
     * Start#getCertificatePath()} {@code null} zurück geben, wird diese Methode abgebrochen, da davon ausgegangen
     * werden kann, dass kein HTTPS verwendet werden soll
     */
    private static void enableHTTPS() {
        // Prüfen, ob HTTPS verwendet werden soll
        if (Start.getCertificatePath() == null) return;

        // ggf. alten PKCS12 KeyStore löschen
        File oldPkcs12 = new File(System.getProperty("user.home") + File.separator + "cinema.pkcs12");
        if (oldPkcs12.delete())
            Start.log(0, "Es wurde eine alter PKCS12 Keystore gefunden. Dieser wird nun geloescht....");

        // Zertifikat zu JKS-Keystore hinzufügen
        try {
            // PEM-Zertifikat in einen PKCS12-Keystore speichern
            Process p1 = Runtime.getRuntime().exec("openssl pkcs12 -export -in " + Start.getCertificatePath() + " -out "
                    + System.getProperty("user.home") + File.separator + "cinema.pkcs12 -passout pass:temppw -name " + Start.getHost());
            int exitVal1 = p1.waitFor();
            if (exitVal1 != 0) throw new Exception("Error PEM to PKCS12");

            // ggf. alten Key aus dem JKS-Keystore löschen
            Process p2 = Runtime.getRuntime().exec("keytool -delete -noprompt -alias " + Start.getHost() + " -keystore " +
                    System.getProperty("user.home") + File.separator + "cinema.jks -storepass temppw");
            p2.waitFor();

            // Zertifikat aus PKCS12-Keystore zum JKS-Keystore hinzufügen
            Process p3 = Runtime.getRuntime().exec("keytool -v -importkeystore -alias " + Start.getHost() +
                    " -srckeystore " + System.getProperty("user.home") + File.separator + "cinema.pkcs12 -keystore " +
                    System.getProperty("user.home") + File.separator + "cinema.jks -noprompt -storepass temppw " +
                    "-srcstorepass temppw -deststoretype JKS");
            int exitVal3 = p3.waitFor();
            if (exitVal3 != 0) throw new Exception("Error PKCS23 to JKS");
        } catch (Exception e) {
            Start.log(2, "HTTPS konnte fuer die REST API nicht aktiviert werden (" + e.getMessage() + ")");
            return;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            new File(System.getProperty("user.home") + File.separator + "cinema.pkcs12").delete();
        }

        // HTTPS aktivieren
        secure(System.getProperty("user.home") + File.separator + "cinema.jks", "temppw", null, null);
        Start.log(1, "HTTPS wurde fuer die REST API erfolgreich aktiviert");
    }

    /**
     * Regelt GET-Anfragen für die Aktivierungsseite.
     */
    private static void setupActivate() {
        get("/activate/:key", ((request, response) -> {
            response.type("text/html; charset=utf-8");
            try {
                DataBase.activateAccount(request.params("key"));
                return Resources.getActivationSite("WILLKOMMEN", "Ihr Konto ist nun aktiviert");
            } catch (ParameterException ex) {
                response.status(BAD_REQUEST);
                return Resources.getActivationSite("UNGÜLTIG", "Der Aktiverungslink hat ein ungültiges Format");
            } catch (InvalidException ex) {
                response.status(FORBIDDEN);
                return Resources.getActivationSite("UNGÜLTIG", "Der Aktivierungslink ist ungültig oder wurde bereits verwendet");
            }
        }));
    }

    /**
     * Regelt POST-Anfragen für das Erstellen eines Kontos
     */
    private static void setupCreateAccount() {
        post("/create-account", ((request, response) -> {
            response.type("text/plain; charset=utf-8");
            try {
                JsonObject json = gson.fromJson(request.body(), JsonObject.class);
                String email = json.get("email").getAsString(), name = json.get("name").getAsString();
                String aktiverungsCode = DataBase.createUser(json.get("passwort").getAsString(), email, name);
                Mail.sendActivationMail(email, name, aktiverungsCode);
                return "Das Konto wurde erfolgreich erstellt!";
            } catch (NullPointerException | JsonSyntaxException e1) {
                response.status(BAD_REQUEST);
                return "Die Anfrage hat kein gültiges Format!";
            } catch (SQLIntegrityConstraintViolationException e2) {
                response.status(CONFLICT);
                return "Zu der angegeben Email-Adresse existiert bereits ein Konto!";
            } catch (ParameterException e3) {
                response.status(FORBIDDEN);
                return e3.getMessage();
            } catch (Exception e4) {
                response.status(SERVER_ERROR);
                e4.printStackTrace();
                return "Interner Server Fehler";
            }
        }));
    }

    /**
     * Regelt GET-Anfragen für Filmübersichten
     */
    private static void setupGetMovies() {
        get("/get-movies", ((request, response) -> {
            response.type("text/json; charset=utf-8");
            return DataBase.getAllMovies();
        }));
    }

    /**
     * Startet den Server auf einem bestimmten Port.
     *
     * @param pPort Der Port, auf dem der Server gestartet werden soll
     */
    public static void start(int pPort) {
        port(pPort);
        enableHTTPS();
        enableCORS();
        setupCreateAccount();
        setupActivate();
        setupGetMovies();
    }
}
