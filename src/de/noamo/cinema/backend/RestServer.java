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
     * Regelt GET-Anfragen für die Aktivierungsseite.
     */
    private static void setupActivate() {
        get("/activate/:key", ((request, response) -> {
            try {
                DataBase.activateAccount(request.params("key"));
                response.redirect("https://" + Start.getHost() + "?activated=true", 303);
                return "Redirect";
            } catch (ParameterException | InvalidException e) {
                response.redirect("https://" + Start.getHost() + "?activated=false", 303);
                return "Redirect";
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
        // Fehlerausgabe aktivieren
        initExceptionHandler((e) -> Start.log(2, e.getMessage()));
        // Port einstellen
        port(pPort);
        // HTTPS aktivieren
        if (Start.getCertificatePath() != null) secure(System.getProperty("user.home") + File.separator + "cinema" +
                File.separator + "cinema.jks", "temppw", Start.getHost(), null, null);
        // Cross-Origin Resource Sharing aktivieren
        enableCORS();
        // Pfade einstellen
        setupCreateAccount();
        setupActivate();
        setupGetMovies();
    }
}
