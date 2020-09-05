/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * @author Noah Hoelterhoff
 * @version 05.09.2020
 * @since 05.09.2020
 */
public class Server extends WebSocketServer {

    public Server(InetSocketAddress adress) {

    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {

    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        Start.log(2, "Fehler im Websocket: " + e.getMessage());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {

    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {

    }

    @Override
    public void onStart() {
        Start.log(1, "Socket wurde erfolgreich gestartet");
    }
}
