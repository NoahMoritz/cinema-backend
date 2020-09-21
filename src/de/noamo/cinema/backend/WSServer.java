/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * Klasse für Implementation eines Websockets, der für die Verteilung von Live-Daten zuständig ist.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 05.09.2020
 */
abstract class WSServer extends WebSocketServer {
    private final Gson GSON = new Gson();

    /**
     * Erstellt einen neuen Websocket. Siehe {@link WebSocketServer#WebSocketServer(InetSocketAddress)} für weitere
     * Infos.
     */
    public WSServer(InetSocketAddress adress) {
        super(adress);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {

    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        Start.log(2, "Fehler im Websocket: " + e.getMessage());
    }

    @Override
    public void onMessage(WebSocket conn, String stringMessage) {
        JsonObject jsonMessage = GSON.fromJson(stringMessage, JsonObject.class);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {

    }

    @Override
    public void onStart() {
        Start.log(1, "Socket wurde erfolgreich gestartet");
    }
}
