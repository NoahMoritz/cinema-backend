/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import de.noamo.cinema.backend.exceptions.*;
import spark.Request;
import spark.Response;

import java.io.File;
import java.sql.SQLException;

import static spark.Spark.*;

/**
 * Richtet die REST API ein und steuert sie.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 15.09.2020
 */
abstract class RestServer {
    private final static String APPLICATION_JSON = "application/json; charset=utf-8";
    private final static String TEXT_PLAIN = "text/plain; charset=utf-8";
    private final static Gson gson = new Gson();

    /**
     * Aktivert einen Account mit der {@link DataBase#activateAccount(String)}-Methode. Nach dem Ausführen der Methode
     * wird auf die Startseite der Webseite weitergeleitet (mit dem Parameter "activated=true/false")
     *
     * @param request  Reqeust der Anfrage
     * @param response Response der Anfrage
     * @return "Sie werden weitergeleitet..."
     * @throws SQLException Falls ein Fehler in der Verbindung zu der Datenbank auftritt
     */
    private static String activateAccount(Request request, Response response) throws SQLException {
        response.type(TEXT_PLAIN);
        String preparedResponse = "http" + (Start.getCertificatePath() == null ? "" : "s") + "://" + Start.getHost() + "?activated=";
        try {
            DataBase.activateAccount(request.params("key"));
            response.redirect(preparedResponse + "true", 303);
        } catch (BadRequestException | NotFoundException e) {
            response.redirect(preparedResponse + "false", 303);
        }
        return "Sie werden weitergeleitet...";
    }

    /**
     * Erstellt einen Account mit der {@link DataBase#createUser(String, String, String, boolean)}-Methode. Der dabei
     * erstellte Aktiverungscode wird dann per {@link Mail#sendActivationMail(String, String, String)} versendet.
     *
     * @param request  Reqeust der Anfrage
     * @param response Response der Anfrage
     * @return "Ihr Konto wurde erstellt. Bitte aktivieren Sie das Konto nun mit Aktivierungslink, den Sie per Mail
     * erhalten haben"
     * @throws BadRequestException Fehlerhafte Anfrage (z.B. nicht alles mitgegeben)
     * @throws ConflictException   Email-Adresse bereits vorhanden
     * @throws SQLException        Fehler in der Verbindung zur Datenbank
     */
    private static String createAccount(Request request, Response response) throws BadRequestException, ConflictException, SQLException {
        response.type(TEXT_PLAIN);
        JsonObject json = gson.fromJson(request.body(), JsonObject.class);
        String email = json.get("email").getAsString(), name = json.get("name").getAsString();
        String aktiverungsCode = DataBase.createUser(json.get("passwort").getAsString(), email, name, false);
        Mail.sendActivationMail(email, name, aktiverungsCode);
        return "Ihr Konto wurde erstellt. Bitte aktivieren Sie das Konto nun mit Aktivierungslink, den Sie per Mail erhalten haben";
    }

    /**
     * Aktiviert CORS (Cross-Origin Resource Sharing). Dies wird z.B. für den Flutter Client benötigt um einwandfrei zu
     * funktionieren. CORS erlaubt, dass Resourcen (wie Bilder) von anderen Servern abgefragt wird.
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
     * Richtet die Fehlerbehanldung ein. Hier werden Fehler zu Statuscodes zugeordent und die passenden Fehlermeldungen
     * an den Nutzer weiter gegeben. Ebenfalls werden alle Fehler in der Konsole ausgegeben
     */
    private static void exceptionHandeling() {
        // Logging
        initExceptionHandler((e) -> Start.log(2, e.getMessage()));

        // Fehlerhafte Anfragen
        exception(BadRequestException.class, (e, request, response) -> {
            response.status(400);
            response.type(TEXT_PLAIN);
            response.body(e.getMessage());
        });
        exception(NumberFormatException.class, (e, request, response) -> {
            response.status(400);
            response.type(TEXT_PLAIN);
            response.body("In der Anfrage wurde eine Zahl erwartet, aber eine andere Zeichenfolge mitgegeben");
        });
        exception(JsonParseException.class, (e, request, response) -> {
            response.status(400);
            response.type(TEXT_PLAIN);
            response.body("Die Anfrage enthält nicht ein dem vorgegebenen Format entsprechendes Json-Objekt");
        });

        // Keine Berechtigung oder falsche Anmeldedaten
        exception(UnauthorisedException.class, (e, request, response) -> {
            response.status(401);
            response.type(TEXT_PLAIN);
            response.body(e.getMessage());
        });

        // Angeforderte Resource nicht gefunden
        exception(NotFoundException.class, (e, request, response) -> {
            response.status(404);
            response.type(TEXT_PLAIN);
            response.body(e.getMessage());
        });

        // Konto nicht aktiv
        exception(NotActiveException.class, (e, request, response) -> {
            response.status(423);
            response.type(TEXT_PLAIN);
            response.body(e.getMessage());
        });

        // Conflict
        exception(ConflictException.class, (e, request, response) -> {
            response.status(409);
            response.type(TEXT_PLAIN);
            response.body(e.getMessage());
        });
    }

    /**
     * Richte alle Routen auf dem Server ein
     */
    private static void setupRoutes() {
        post("/create-account", RestServer::createAccount);
        post("/login", (req, res) -> DataBase.login(gson.fromJson(req.body(), JsonObject.class)));
        post("/update-user", (req, res) -> DataBase.updateUser(req.headers("Auth"), gson.fromJson(req.body(), JsonObject.class)));
        post("/delete-adress/:id", (req, res) -> DataBase.deleteAdress(req.headers("Auth"), Integer.parseInt(req.params("id"))));
        post("/add-adress", (req, res) -> DataBase.addAdress(req.headers("Auth"), gson.fromJson(req.body(), JsonObject.class)));
        get("/activate/:key", RestServer::activateAccount);
        post("/deactivateAccount", (req, res) -> DataBase.kontoDeaktivieren(req.headers("Auth"), req.body()));
        get("/get-movies", ((req, res) -> DataBase.getAktiveFilmeCached()));
        get("/get-kategorien", (req, res) -> DataBase.getKategorienCached().toString());
        get("/get-saalplan/:id", ((req, res) -> DataBase.getSaalPlan(Integer.parseInt(req.params("id"))).toString()));
        get("/vorstellungen/:filmid", (req, res) -> DataBase.getVorstellungen(Integer.parseInt(req.params("filmid"))));
        get("/vorstellungen", (req, res) -> DataBase.getVorstellungen(0));
        get("/vorstellung-details/:id", (req, res) -> DataBase.getVorstellungsDetails(Integer.parseInt(req.params("id"))));
        get("/get-userinfos", (req, res) -> DataBase.getUserInfos(req.headers("Auth")));

        // Admin Commands
        path("/admin", () -> {
            before("/*", (req, res) -> Start.log(0, "Es wird von " + req.ip() + " auf den Admin-Bereich zugegriffen"));
            post("/neue-vorstellung", (req, res) -> {
                res.type(TEXT_PLAIN);
                return DataBase.insertVorstellung(req.headers("Auth"), gson.fromJson(req.body(), JsonObject.class));
            });
        });
    }

    /*private static void setupUploadSaalplan() {
        post("admin/upload-saalplan", ((request, response) -> {
            try {
                String authCode = request.headers("Auth");
                DataBase.uploadSaalplan(authCode, gson.fromJson(request.body(), JsonObject.class));
                return "";
            } catch (BadRequestException pe) {
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
    }*/

    /**
     * Startet den Server auf einem bestimmten Port.
     *
     * @param pPort Der Port, auf dem der Server gestartet werden soll
     */
    public static void start(int pPort) {
        // Fehlerverarbeitung aktivieren
        exceptionHandeling();
        // Port einstellen
        port(pPort);
        // HTTPS aktivieren
        if (Start.getCertificatePath() != null) secure(System.getProperty("user.home") + File.separator + "cinema" +
                File.separator + "cinema.jks", "temppw", Start.getHost(), null, null);
        // Cross-Origin Resource Sharing aktivieren
        enableCORS();
        // Standart Typ einstellen
        before("/*", (request, response) -> response.type(APPLICATION_JSON));
        // Pfade einstellen
        setupRoutes();
    }
}
