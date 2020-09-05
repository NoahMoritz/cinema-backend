/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;

/**
 * @author Noah Hoelterhoff
 * @version 05.09.2020
 * @since 05.09.2020
 */
public class DataBase {
    private static BasicDataSource basicDataSource;

    /**
     * <b>!!Wichtig: Aktuell werden nur MYSQL und MariaDB Datenbanken unterstützt!</b><br><br>
     * Stellt eine Verbindung zu der Datenbank her und erstellt ggf. fehlende Tabellen in dieser (über die Methode
     * {@link DataBase#dataBaseSetup(Connection)})
     *
     * @param url Die vollständige JDBC-URL der Datenbank (inkl. Passwort, Username, etc.)
     * @throws SQLException Falls keine Verbindung hergestellt werden kann oder beim Setup Probleme auftreten
     */
    static void connect(String url) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            dataBaseSetup(connection);
        }

        basicDataSource = new BasicDataSource();
        basicDataSource.setUrl(url);
        basicDataSource.setValidationQuery("SELECT userid FROM accounts");
        basicDataSource.setMinIdle(1);
        basicDataSource.setMaxIdle(5);
        basicDataSource.setMaxOpenPreparedStatements(50);
    }

    /**
     * Erstellt die benötigen Tabellen, falls diese noch nicht existieren. Ebenfalls wird ein initialer Admin Account
     * erstellt, der dafür da ist, das Admin Panl zu öffnen und die Starteinstellungen vor zu nehmen
     *
     * @param connection Die Verbindung zu der Datenbank
     */
    private static void dataBaseSetup(Connection connection) throws SQLException {
        connection.prepareStatement("CREATE TABLE IF NOT EXISTS konten(benutzerid INT NOT NULL AUTO_INCREMENT, " + // Eindeutige ID des Benutzers
                "rolle INT NOT NULL DEFAULT 0, " + // Rolle des Nutzers (spielt für den Zugriff eine Rolle)
                "aktiv BIT NOT NULL DEFAULT 0, " + // Gibt an, ob ein Account aktiviert wurde
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Erstellungszeitpunkt des Accounts
                "benutzername VARCHAR(20) NOT NULL, " + // Der Benutzername (zum Anmelden)
                "passwort VARCHAR(50) NOT NULL, " + // Das Passwort (für die Anmeldung)
                "email VARCHAR(254) NOT NULL, " + // Eine Email-Adresse des Benutzers (für Infos über Probleme)
                "name VARCHAR(100) NOT NULL, " + // Der Name der Person
                "PRIMARY KEY (benutzerid), " + // Eindeutige ID des Benutzers als Key
                "UNIQUE (benutzername), UNIQUE (email));").executeUpdate();

        connection.prepareStatement("CREATE TABLE IF NOT EXISTS adressen(benutzerid INT NOT NULL, " + // Eindeutige ID des Benutzers, zu dem dise Adresse gehört
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Zeitpunkt des Hinzufügens der Adresse
                "anrede VARCHAR(30) NOT NULL, " + // Anrede ("Herr"/"Frau" + ggf. "Dr." oder "Prof.) der Person an der Rechnungsadresse
                "vorname VARCHAR(50) NOT NULL, " + // Der Vorname der Person an der Rechnungsadresse
                "nachname VARCHAR(50) NOT NULL, " + // Der Nachname der Person an der Rechnungsadresse
                "strasse VARCHAR(100) NOT NULL, " + // Straße inkl. Hausnummer
                "plz VARCHAR(5) NOT NULL, " + // PLZ der Adresse
                "telefon VARCHAR(20), " + // Telefonnummer der Rechnungsadresse
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        try (PreparedStatement ps_adminAccount = connection.prepareStatement("INSERT INTO konten(benutzername, passwort, name, email, rolle, aktiv) VALUES ('admin', '" + DigestUtils.md5Hex("Initial") + "', 'Admin', 'info@noamo.de', 999, 1);")) {
            ps_adminAccount.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException ignored) {} // Tritt immer auf, wenn der Admin Account schon existiert
    }
}
