/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 11 2020
 */

package de.noamo.cinema.backend;

import de.noamo.cinema.backend.exceptions.BadRequestException;
import de.noamo.cinema.backend.exceptions.ConflictException;
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
        connection.prepareStatement("DELETE FROM aktivierungsSchluessel").executeUpdate();
        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();

        String code = DataBase.createUser("password1", "test1@noamo.de", "Test User", false);
        Assertions.assertEquals(code.length(), 36);
        ResultSet rs = connection.prepareStatement("SELECT * FROM konten WHERE email='test1@noamo.de';").executeQuery();
        Assertions.assertTrue(rs.next());
        Assertions.assertEquals(rs.getInt("aktiv"), 0);
        ResultSet rs2 = connection.prepareStatement("SELECT * FROM aktivierungsSchluessel WHERE benutzerid=" + rs.getInt("benutzerid")).executeQuery();
        Assertions.assertTrue(rs2.next());

        connection.prepareStatement("DELETE FROM aktivierungsSchluessel").executeUpdate();
        connection.prepareStatement("DELETE FROM konten WHERE email='" + "test1@noamo.de" + "'").executeUpdate();
    }
}