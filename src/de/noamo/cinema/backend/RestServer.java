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
 * @author Noah Hoelterhoff
 * @version 15.09.2020
 * @since 15.09.2020
 */
public class RestServer {
    private final static int BAD_REQUEST = 400;
    private final static int CONFLICT = 409;
    private final static int FORBIDDEN = 403;
    private final static int SERVER_ERROR = 500;
    private final static Gson gson = new Gson();

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

    private static void enableHTTPS() {
        // Prüfen, ob HTTPS verwendet werden soll
        if (Start.getCertificatePath() == null) return;

        // Alten PKCS12 KeyStore löschen
        File oldPkcs12 = new File(System.getProperty("user.home") + File.separator + "cinema.pkcs12");
        if (oldPkcs12.delete())
            Start.log(0, "Es wurde eine alter PKCS12 Keystore gefunden. Dieser wird nun geloescht....");

        try {
            // Zertifikat von PEM zu DER umwandeln
            Process p1 = Runtime.getRuntime().exec("openssl pkcs12 -export -in " + Start.getCertificatePath() + " -out "
                    + System.getProperty("user.home") + File.separator + "cinema.pkcs12 -passout pass:temppw -name " + Start.getHost());
            int exitVal1 = p1.waitFor();
            if (exitVal1 != 0) throw new Exception("Error PEM to PKCS12");

            // Alten Key aus dem Keystore ggf. löschen
            Process p2 = Runtime.getRuntime().exec("keytool -delete -noprompt -alias " + Start.getHost() + " -keystore " +
                    System.getProperty("user.home") + File.separator + "cinema.jks -storepass temppw");
            p2.waitFor();

            // Zertifikat zum Keystore hinzufügen
            Process p3 = Runtime.getRuntime().exec("keytool -v -importkeystore -alias " + Start.getHost() +
                    " -srckeystore " + System.getProperty("user.home") + File.separator + "cinema.pkcs12 -keystore " +
                    System.getProperty("user.home") + File.separator + "cinema.jks -noprompt -storepass temppw " +
                    "-srcstorepass temppw -deststoretype JKS");
            int exitVal3 = p3.waitFor();
            if (exitVal3 != 0) throw new Exception("Error PKCS23 to JKS");
        } catch (Exception e) {
            Start.log(2, "HTTPS konnte fuer den REST-Server nicht aktiviert werden (" + e.getMessage() + ")");
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
        get("/cinema-backend/activate/:key", ((request, response) -> {
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
        post("/cinema-backend/create-account", ((request, response) -> {
            response.type("text/plain; charset=utf-8");
            try {
                JsonObject json = gson.fromJson(request.body(), JsonObject.class);
                DataBase.createUser(json.get("passwort").getAsString(), json.get("email").getAsString(), json.get("name").getAsString());
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
                return "Internal Server Error";
            }
        }));
    }

    private static void setupGetMovies() {
        get("/cinema-backend/get-movies", ((request, response) -> {
            response.type("text/json; charset=utf-8");
            return DataBase.getAllMovies();
        }));
    }

    /**
     * Startet den Server auf einem bestimmten Port.
     *
     * @param port Der Port, auf dem der Server gestartet werden soll
     */
    public static void start(int port) {
        port(port);
        enableHTTPS();
        enableCORS();
        setupCreateAccount();
        setupActivate();
        setupGetMovies();
    }
}
