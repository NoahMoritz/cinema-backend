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
import de.noamo.cinema.backend.exceptions.NotActiveException;
import de.noamo.cinema.backend.exceptions.ParameterException;
import de.noamo.cinema.backend.exceptions.UnauthorisedException;

import java.io.File;
import java.sql.SQLException;
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
    private final static int NOT_FOUND = 404;
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

    private static void setupGetKategorien() {
        get("/get-kategorien", (request, response) -> {
            try {
                response.type("text/json; charset=utf-8");
                return DataBase.getKateogorien().toString();
            } catch (SQLException sqlException) {
                response.status(SERVER_ERROR);
                return "Interner Serverfehler!";
            }
        });
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

    private static void setupGetSaalplan() {
        get("/get-saalplan/:id", ((request, response) -> {
            try {
                response.type("text/json; charset=utf-8");
                return DataBase.getSaalPlan(Integer.parseInt(request.params("id"))).toString();
            } catch (InvalidException e1) {
                response.status(NOT_FOUND);
                return "Kein Saal mit dieser Id gefunden";
            } catch (SQLException e2) {
                e2.printStackTrace();
                response.status(SERVER_ERROR);
                return "Interner Serverfehler!";
            }
        }));
    }

    private static void setupGetUserInfos() {
        get("/get-userinfos", ((request, response) -> {
            try {
                String authCode = request.headers("Auth");
                JsonObject jsonObject = DataBase.getUserInfos(authCode);
                response.type("text/json; charset=utf-8");
                return jsonObject;
            } catch (ParameterException e1) {
                response.status(BAD_REQUEST);
                return e1.getMessage();
            } catch (UnauthorisedException e2) {
                response.status(FORBIDDEN);
                return e2.getMessage();
            } catch (Exception e3) {
                response.status(SERVER_ERROR);
                e3.printStackTrace();
                return "Interner Server Fehler";
            }
        }));
    }

    private static void setupLogin() {
        post("/login", ((request, response) -> {
            try {
                return DataBase.login(gson.fromJson(request.body(), JsonObject.class));
            } catch (ParameterException e1) {
                response.status(BAD_REQUEST);
                return e1.getMessage();
            } catch (UnauthorisedException e2) {
                response.status(FORBIDDEN);
                return e2.getMessage();
            } catch (NotActiveException e3) {
                response.status(CONFLICT);
                return e3.getMessage();
            } catch (Exception e4) {
                response.status(SERVER_ERROR);
                e4.printStackTrace();
                return "Interner Serverfehler!";
            }
        }));
    }

    private static void setupUploadSaalplan() {
        post("admin/upload-saalplan", ((request, response) -> {
            try {
                DataBase.uploadSaalplan(gson.fromJson(request.body(), JsonObject.class));
                return "";
            } catch (ParameterException pe) {
                response.status(BAD_REQUEST);
                return pe.getMessage();
            } catch (SQLIntegrityConstraintViolationException e2) {
                response.status(CONFLICT);
                return "Es existiert bereits Kinosaal mit diesem Namen!";
            } catch (UnauthorisedException e3) {
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
        setupUploadSaalplan();
        setupGetSaalplan();
        setupGetKategorien();
        setupGetUserInfos();
        setupLogin();
    }
}
