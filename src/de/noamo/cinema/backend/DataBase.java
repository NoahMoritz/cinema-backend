/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import de.noamo.cinema.backend.exceptions.InvalidException;
import de.noamo.cinema.backend.exceptions.ParameterException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.util.UUID;

/**
 * @author Noah Hoelterhoff
 * @version 15.09.2020
 * @since 05.09.2020
 */
public class DataBase {
    private static BasicDataSource basicDataSource;

    /**
     * Die Methode aktiviert einen Account mit einem Aktivierungsschlüssel, der per Mail verschickt wurde.
     *
     * @param pAktivierungsSchluessel Der Aktivierungsschlüssel
     * @throws ParameterException Falls der Schlüssel ein ungültiges Format hat
     * @throws SQLException       Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws InvalidException   Falls der Schlüssel nicht existiert (oder bereits aktiviert wurde)
     */
    static void activateAccount(String pAktivierungsSchluessel) throws ParameterException, SQLException, InvalidException {
        // Parameterprüfung
        if (pAktivierungsSchluessel.length() != 36) throw new ParameterException("Ungültiges Format!");

        // Account aktivieren
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("UPDATE konten SET aktiv=1 WHERE benutzerid=" +
                     "(SELECT benutzerid FROM aktivierungsSchluessel WHERE aktivierungsSchluessel='" + pAktivierungsSchluessel + "')");
             PreparedStatement preparedStatement2 = connection.prepareStatement("DELETE FROM aktivierungsSchluessel WHERE " +
                     "aktivierungsSchluessel='" + pAktivierungsSchluessel + "';")) {
            if (preparedStatement1.executeUpdate() == 0) throw new InvalidException("Ungültiger Aktivierungscode!");
            else preparedStatement2.executeUpdate();
        }
    }

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
        basicDataSource.setValidationQuery("SELECT benutzerid FROM konten");
        basicDataSource.setMinIdle(1);
        basicDataSource.setMaxIdle(5);
        basicDataSource.setMaxOpenPreparedStatements(50);
    }

    /**
     * Mit dieser Methode kann ein Benutzerkonto erstellt werden. Ebenfalls wird dadurch einen Aktivierungsemail
     * gesendet.
     *
     * @param passwort Das Passwort im Klartext
     * @param email    Eine eindeutige Email-Adresse für dieses Konto
     * @param name     Der Name der Person, der dieses Konto gehört
     * @throws SQLException                             Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws SQLIntegrityConstraintViolationException Falls die Email bereits in der Datenbank vorhanden ist
     * @throws ParameterException                       Falls ein Parameter nicht den Vorgaben entspricht
     */
    static void createUser(String passwort, String email, String name) throws SQLException, ParameterException, SQLIntegrityConstraintViolationException {
        // Parameterprüfung
        if (passwort.length() <= 8) throw new ParameterException("Das Passwort muss mehr als 8 Zeichen haben!");
        if (!email.matches("^(.+)@(.+)$"))
            throw new ParameterException("Die eingegebene Email-Adresse ist ungültig!");
        if (name.length() <= 5)
            throw new ParameterException("Bitte geben Sie Ihren vollständigen Namen (Vor- und Nachname) ein!");

        // Konto hinzufügen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("INSERT INTO konten(passwort, email, name) " +
                     "VALUES ('" + DigestUtils.md5Hex(passwort) + "', '" + email + "', '" + name + "');")) {
            preparedStatement1.executeUpdate();
            String uuid = UUID.randomUUID().toString();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO aktivierungsSchluessel(" +
                    "benutzerid, aktivierungsSchluessel) VALUES ((SELECT benutzerid FROM konten WHERE email = '" + email +
                    "'), '" + uuid + "');")) {
                preparedStatement2.executeUpdate();
                Mail.sendActivationMail(email, name, uuid);
            }
        }
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
                "aktiv BIT NOT NULL DEFAULT 0," + // Ob das Konto aktiviert wurde
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Erstellungszeitpunkt des Accounts
                "passwort VARCHAR(50) NOT NULL, " + // Das Passwort (für die Anmeldung)
                "email VARCHAR(254) NOT NULL, " + // Eine Email-Adresse des Benutzers (für Infos über Probleme)
                "name VARCHAR(100) NOT NULL, " + // Der Name der Person
                "PRIMARY KEY (benutzerid), " + // Eindeutige ID des Benutzers als Key
                "UNIQUE (email));").executeUpdate();

        connection.prepareStatement("CREATE TABLE IF NOT EXISTS aktivierungsSchluessel(benutzerid INT NOT NULL, " + // Eindeutige ID des Benutzers
                "aktivierungsSchluessel VARCHAR(36) NOT NULL, " + // Aktivierungsschlüssel für das Konto
                "UNIQUE (aktivierungsSchluessel), " + // Ein Aktivierungsschlüssel muss eindeutig sein
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid))").executeUpdate();

        connection.prepareStatement("CREATE TABLE IF NOT EXISTS adressen(benutzerid INT NOT NULL, " + // Eindeutige ID des Benutzers, zu dem dise Adresse gehört
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Zeitpunkt des Hinzufügens der Adresse
                "anrede VARCHAR(30) NOT NULL, " + // Anrede ("Herr"/"Frau" + ggf. "Dr." oder "Prof.) der Person an der Rechnungsadresse
                "vorname VARCHAR(50) NOT NULL, " + // Der Vorname der Person an der Rechnungsadresse
                "nachname VARCHAR(50) NOT NULL, " + // Der Nachname der Person an der Rechnungsadresse
                "strasse VARCHAR(100) NOT NULL, " + // Straße inkl. Hausnummer
                "plz VARCHAR(5) NOT NULL, " + // PLZ der Adresse
                "telefon VARCHAR(20), " + // Telefonnummer der Rechnungsadresse
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        try (PreparedStatement ps_adminAccount = connection.prepareStatement("INSERT INTO konten(passwort, name, " +
                "email, rolle, aktiv) VALUES ('" + DigestUtils.md5Hex("Initial") + "', 'Admin', 'info@noamo.de', 999, 1);")) {
            ps_adminAccount.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException ignored) {} // Tritt immer auf, wenn der Admin Account schon existiert
    }
}
