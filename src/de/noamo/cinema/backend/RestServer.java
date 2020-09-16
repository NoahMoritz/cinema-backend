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

    /**
     * Startet den Server auf einem bestimmten Port.
     *
     * @param port Der Port, auf dem der Server gestartet werden soll
     */
    public static void start(int port) {
        port(port);
        setupCreateAccount();
        setupActivate();
    }
}
