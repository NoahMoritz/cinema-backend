/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 11 2020
 */

package de.noamo.cinema.backend;

import com.google.gson.JsonObject;
import de.noamo.cinema.backend.exceptions.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

class DataBaseTest {
    private final static String host = "noamo.de";
    private final static int port = 3306;
    private final static String dbName = "cinema_test";
    private final static String username = "test";
    private final static String password = "pZsb4$69";

    @BeforeAll
    static void connect() throws SQLException {
        DataBase.connect("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?user=" + username +
                "&password=" + password + "&serverTimezone=Europe/Berlin");
    }

    @Test
    void createUser() throws BadRequestException, ConflictException, SQLException {
        Connection connection = DataBase.getConnection();
        reset(connection);

        String code = DataBase.createUser("password1", "test1@noamo.de", "Test User", false);
        Assertions.assertEquals(36, code.length());
        ResultSet rs = connection.prepareStatement("SELECT * FROM konten WHERE email='test1@noamo.de';").executeQuery();
        Assertions.assertTrue(rs.next());
        Assertions.assertEquals(0, rs.getInt("aktiv"));
        ResultSet rs2 = connection.prepareStatement("SELECT * FROM aktivierungsSchluessel WHERE benutzerid=" + rs.getInt("benutzerid")).executeQuery();
        Assertions.assertTrue(rs2.next());

        connection.prepareStatement("DELETE FROM aktivierungsSchluessel").executeUpdate();
        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();
    }

    @Test
    void activateAccount() throws SQLException, BadRequestException, NotFoundException, ConflictException {
        Connection connection = DataBase.getConnection();
        reset(connection);

        String code = DataBase.createUser("password1", "test1@noamo.de", "Test User", false);
        DataBase.activateAccount(code);

        ResultSet rs1 = connection.prepareStatement("SELECT * FROM aktivierungsSchluessel;").executeQuery();
        Assertions.assertFalse(rs1.next());

        ResultSet rs2 = connection.prepareStatement("SELECT * FROM konten WHERE email='" + "test1@noamo.de" + "'").executeQuery();
        Assertions.assertTrue(rs2.next());
        Assertions.assertEquals(1, rs2.getInt("aktiv"));

        connection.prepareStatement("DELETE FROM aktivierungsSchluessel").executeUpdate();
        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();
    }

    @Test
    void kontoDeaktivieren() throws SQLException, BadRequestException, ConflictException, NotActiveException, UnauthorisedException, NotFoundException {
        Connection connection = DataBase.getConnection();
        reset(connection);

        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();
        DataBase.createUser("password1", "test1@noamo.de", "Test User", true);

        ResultSet rs1 = connection.prepareStatement("SELECT * FROM konten WHERE email='test1@noamo.de';").executeQuery();
        Assertions.assertTrue(rs1.next());

        JsonObject json = new JsonObject();
        json.addProperty("email", "test1@noamo.de");
        json.addProperty("passwort", "password1");
        JsonObject authJson = DataBase.login(json);
        String auth = authJson.get("authToken").getAsString();

        DataBase.kontoDeaktivieren(auth, "password1");
        ResultSet rs2 = connection.prepareStatement("SELECT * FROM konten WHERE email='test1@noamo.de';").executeQuery();
        Assertions.assertTrue(rs2.next());
        Assertions.assertEquals(2, rs2.getInt("aktiv"));
    }

    private void reset(Connection connection) throws SQLException {
        connection.prepareStatement("DELETE FROM aktivierungsSchluessel").executeUpdate();
        connection.prepareStatement("DELETE FROM authCodes").executeUpdate();
        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();
    }
}