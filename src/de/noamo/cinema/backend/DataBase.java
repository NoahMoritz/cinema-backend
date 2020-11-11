/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.noamo.cinema.backend.exceptions.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.util.Random;
import java.util.UUID;

/**
 * Ist zuständig für die Verbindung zur Datenbank und für Aktionen, die dort ausgeführt werden. Die Verbindungen werden
 * mit in einem Connection-Pool verwaltet ({@link BasicDataSource}).
 *
 * @author Noah Hoelterhoff
 * @version 04.10.2020
 * @since 05.09.2020
 */
abstract class DataBase {
    private final static int DPCP2_MAX_CON_IDLE = 6;
    private final static int DPCP2_MAX_OPEN_STATEMENTS = 50;
    private final static int DPCP2_MIN_CON_IDLE = 1;
    private final static int TTL_KATEGORIEN = 21600000; // 6 Stunden
    private final static int TTL_MOVIE_LIST = 1800000; // 30 Minuten
    private final static int TTL_SAELE = 43200000; // 12 Stunden
    private static BasicDataSource basicDataSource;
    private static CacheObject<JsonArray> categories;
    private static CacheObject<String> movies;
    private static CacheObject<String> saele;

    /**
     * Die Methode aktiviert einen Account mit einem Aktivierungsschlüssel. Dieser Aktiverungsschlüssel befindet sich in
     * der Datenbank in der Tabelle "aktiverungsSchluessel".
     *
     * @param pAktivierungsSchluessel Der Aktivierungsschlüssel
     * @throws BadRequestException Falls der Schlüssel ein ungültiges Format hat (kein 36 Zeichen)
     * @throws SQLException        Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws NotFoundException   Falls der Schlüssel nicht existiert (oder bereits aktiviert wurde)
     */
    static void activateAccount(String pAktivierungsSchluessel) throws BadRequestException, SQLException, NotFoundException {
        // Parameterprüfung
        if (pAktivierungsSchluessel.length() != 36) throw new BadRequestException("Ungültiges Format!");

        // Account aktivieren
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("UPDATE konten SET aktiv=1 WHERE benutzerid=" +
                     "(SELECT benutzerid FROM aktivierungsSchluessel WHERE aktivierungs_schluessel='" + pAktivierungsSchluessel + "')");
             PreparedStatement preparedStatement2 = connection.prepareStatement("DELETE FROM aktivierungsSchluessel WHERE " +
                     "aktivierungs_schluessel='" + pAktivierungsSchluessel + "';")) {
            if (preparedStatement1.executeUpdate() == 0) throw new NotFoundException("Ungültiger Aktivierungscode!");
            else preparedStatement2.executeUpdate();
        }
    }

    /**
     * Blockt alle Anfragen, bei denen ein Konto nicht aktiv ist.
     *
     * @param resultSet Ein Resultset mit einer ausgewählten Reihe und der Spalte 'aktiv'
     * @throws SQLException       Falls ein Fehler mit dem ResultSet auftritt
     * @throws NotActiveException Falls das Konto nicht aktiv ist
     */
    private static void aktivBarriere(ResultSet resultSet) throws SQLException, NotActiveException {
        int aktivCode = resultSet.getInt("aktiv");
        if (aktivCode != 1)
            throw new NotActiveException(
                    "Das Konto wurde " + (aktivCode == 0 ? "noch nicht aktiviert!" : "deaktiviert!"));
    }

    /**
     * Prüft, ob ein Account autorisiert ist, eine bestimmte Aktion auszuführen
     *
     * @param authCode Ein Autorisierungscode für ein Konto
     * @param pLevel   Das Level, dass für diese Aktion mindestens benötigt wird
     * @throws SQLException          Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws BadRequestException   Falls die Attribute {@code email} und {@code passwort} nicht existieren
     * @throws UnauthorisedException Falls der Nutzer für diese Aktion nicht autorisiert ist
     */
    private static void authorizationBarriere(String authCode, int pLevel) throws SQLException, BadRequestException, UnauthorisedException, NotActiveException {
        // Parameterprüfung
        if (authCode == null) throw new BadRequestException("Es wurde kein AuthCode bereigestellt");
        if (authCode.length() != 36)
            throw new BadRequestException("Ungültiger AuthCode (ein gültiger Auth-Code hat 36 Zeichen)");

        // Zugehöriges Konto finden
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT rolle, aktiv FROM konten " +
                     "WHERE benutzerid=(SELECT benutzerid FROM authCodes WHERE auth_code='" + DigestUtils.md5Hex(authCode) + "');");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            // Konto prüfen
            if (!resultSet.next()) throw new UnauthorisedException("Account mit diesen Anmeldedaten nicht gefunden");
            aktivBarriere(resultSet);

            // Prüfen, ob die Autorisierungsstufe des Kontos hoch genug ist
            if (resultSet.getInt("rolle") < pLevel)
                throw new UnauthorisedException("Keine ausreichenden Rechte! Benoetigt wird Stufe " +
                        pLevel + ". Sie haben: " + resultSet.getInt("rolle"));
        }
    }

    /**
     * <b>!!Wichtig: Aktuell werden nur MySQL und MariaDB Datenbanken unterstützt!</b><br><br>
     * Stellt eine Verbindung zu der Datenbank her und erstellt ggf. fehlende Tabellen in dieser (über die Methode
     * {@link DataBase#dataBaseSetup(Connection)})
     *
     * @param pUrl Die vollständige JDBC-URL der Datenbank (inkl. Passwort, Username, etc.)
     * @throws SQLException Falls keine Verbindung hergestellt werden kann oder beim Setup Probleme auftreten
     */
    static void connect(String pUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(pUrl)) {
            dataBaseSetup(connection);
        }

        basicDataSource = new BasicDataSource(); // Connection Pool
        basicDataSource.setUrl(pUrl); // URL für die Verbindung
        basicDataSource.setValidationQuery("SELECT benutzerid FROM konten");
        basicDataSource.setMinIdle(DPCP2_MIN_CON_IDLE);
        basicDataSource.setMaxIdle(DPCP2_MAX_CON_IDLE);
        basicDataSource.setMaxOpenPreparedStatements(DPCP2_MAX_OPEN_STATEMENTS);
    }

    /**
     * Fragt alle vorhandenen Kinosäle ab.
     *
     * @return Ein {@link String} im Json-Format
     * @throws SQLException Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     */
    static String getAllSaele() throws SQLException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT saalid, name FROM kinosaele");
             ResultSet rs = preparedStatement.executeQuery()) {
            JsonArray json = new JsonArray();
            while (rs.next()) {
                JsonObject temp = new JsonObject();
                temp.addProperty("saalid", rs.getString("saalid"));
                temp.addProperty("name", rs.getString("name"));
                json.add(temp);
            }
            saele = new CacheObject<>(json.toString(), TTL_SAELE);
            return json.toString();
        }
    }

    /**
     * Hat die selbe Funktion wie {@link DataBase#getAllSaele()}, fragt die Daten aber nicht gewzungenermaßen aus der
     * Datenbank ab, sondern versucht ein gecachtes Objekt zu verwenden.
     */
    static String getAllSaeleCached() throws SQLException {
        if (saele == null || saele.isNotAlive()) return getAllSaele();
        return saele.cache;
    }

    /**
     * Mit dieser Methode kann ein Benutzerkonto erstellt werden. Das Konto ist nach der Erstellung noch nicht
     * aktiviert. Es wird allerdings ein Aktiverungscode hinterlegt, der mit der {@link
     * DataBase#activateAccount(String)}-Methode zur Aktiverung verwendet werden kann. Dieser Code wird als String
     * zurück gegeben.
     *
     * @param pPasswort Das Passwort im Klartext
     * @param pEmail    Eine eindeutige Email-Adresse für dieses Konto
     * @param pName     Der Name der Person, der dieses Konto gehört
     * @param aktiv     Ob das Konto von anfang an aktiviert sein soll
     * @return Der Aktivierungscode, mit dem dieses Konto aktiviert werden kann
     * @throws SQLException        Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws ConflictException   Falls die Email bereits in der Datenbank vorhanden ist
     * @throws BadRequestException Falls ein Parameter nicht den Vorgaben entspricht
     */
    static String createUser(String pPasswort, String pEmail, String pName, boolean aktiv) throws SQLException, BadRequestException, ConflictException {
        // Parameterprüfung
        if (pPasswort.length() <= 8) throw new BadRequestException("Das Passwort muss mehr als 8 Zeichen haben!");
        if (!pEmail.matches("^(.+)@(.+)$"))
            throw new BadRequestException("Die eingegebene Email-Adresse ist ungültig!");
        if (pName.length() <= 5)
            throw new BadRequestException("Bitte geben Sie Ihren vollständigen Namen (Vor- und Nachname) ein!");

        // Konto hinzufügen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("INSERT INTO konten(passwort, email, name, aktiv) " +
                     "VALUES ('" + DigestUtils.md5Hex(pPasswort) + "', '" + pEmail + "', '" + pName + "', " + (aktiv ? 1 : 0) + ");")) {
            preparedStatement1.executeUpdate();

            // Aktiverungscode generieren
            String uuid = UUID.randomUUID().toString();

            // Aktiverungscode hochladen und zurück geben
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO aktivierungsSchluessel(" +
                    "benutzerid, aktivierungs_schluessel) VALUES ((SELECT benutzerid FROM konten WHERE email = '" + pEmail +
                    "'), '" + uuid + "');")) {
                preparedStatement2.executeUpdate();
                return uuid;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException("Zu der angegeben Email-Adresse existiert bereits ein Konto. Falls Sie dieses " +
                    "Konto besitzen und es deaktivert haben/das Passwort nicht mehr wissen, kontaktieren Sie den Kundenservice!");
        }
    }

    /**
     * Erstellt die benötigen Tabellen, falls diese noch nicht existieren. Ebenfalls wird ein initialer Admin Account
     * erstellt, der dafür da ist, das Admin Panl zu öffnen und die Starteinstellungen vor zu nehmen.
     *
     * @param pConnection Die Verbindung zu der Datenbank
     */
    private static void dataBaseSetup(Connection pConnection) throws SQLException {
        // --- Kinosäle ---
        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS kinosaele(" +
                "saalid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID eines Kinosaals
                "name VARCHAR(20) NOT NULL, " + // Der Name des Kinosaals
                "width INT NOT NULL, " + // Die Breite des Saals in Pixeln
                "height INT NOT NULL, " + // Die Höhe des Saals in Pixeln
                "PRIMARY KEY (saalid)," + // SaalId als eindeutiger Schlüssel
                "UNIQUE (name));").executeUpdate(); // Saalname als eindeutige Bezeichnung

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS kategorien(" +
                "kategorieid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID für die Kategorie
                "name VARCHAR(20) NOT NULL, " + // Name der Kategorie
                "aufpreis DECIMAL(4,2) NOT NULL, " + // Aufpreis für dies Kategorie (z.B. +0.5 für 50ct teurer; negativ möglich)
                "faktor DOUBLE UNSIGNED NOT NULL, " + // Faktor für einen Sitzplatz (z.B. 2 für doppelt so teuer wie normal)
                "width INT UNSIGNED NOT NULL, " + // Breite eines Sitzes in Pixeln
                "height INT UNSIGNED NOT NULL ," + // Höhe eines Sitzes in Pixeln
                "color_hex VARCHAR(6) NOT NULL," + // HEX-Farbcode des Sitzes
                "icon TEXT, " + // Icon für die Sitzplatzkategorie
                "PRIMARY KEY (kategorieid), " + // kategorieid als eindeutiger Schlüssel
                "UNIQUE (name));").executeUpdate(); // Name als eindeutige Bezeichnung (da es sonst zu Verwirrungen kommt)

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS saalPlaetze(" +
                "platzid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID für jeden Platz
                "saalid INT UNSIGNED NOT NULL, " + // Referenz auf den Saal, in dem der Platz ist
                "kategorieid INT UNSIGNED NOT NULL," + // Referenz auf die Kategorie, zu der der Saal gehört
                "reihe VARCHAR(1) NOT NULL, " + // Reihe des Platzes (für die Verständlichkeit bei Menschen)
                "platz INT(2) NOT NULL, " + // Sitz des Platzes (für die Verständlichkeit bei Menschen)
                "x INT UNSIGNED NOT NULL, " + // x-Koordinate des Platzes (ausgeend von der linken oberen Ecke)
                "y INT UNSIGNED NOT NULL, " + // y-Koordinate des Platzes (ausgeend von der linken oberen Ecke)
                "PRIMARY KEY (platzid), " + // Platz ID des eindeutiger Schlüssel
                "UNIQUE (saalid,reihe,platz), " + // Eindeutige Kombination aus Saal Reihe und Platz (um Verwechlungen zu vermeiden)
                "FOREIGN KEY (saalid) REFERENCES kinosaele(saalid), " +
                "FOREIGN KEY (kategorieid) REFERENCES kategorien(kategorieid));").executeUpdate();

        // --- Konten ---
        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS konten(" +
                "benutzerid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID des Benutzers
                "rolle INT(3) UNSIGNED NOT NULL DEFAULT 0, " + // Rolle des Nutzers (spielt für den Zugriff eine Rolle)
                "aktiv TINYINT(1) UNSIGNED NOT NULL DEFAULT 0, " + // Ob das Konto inaktiv(0), aktiv(1), deaktiviert(2) ist
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Erstellungszeitpunkt des Accounts
                "passwort VARCHAR(50) NOT NULL, " + // Das Passwort (für die Anmeldung)
                "email VARCHAR(254) NOT NULL, " + // Eine Email-Adresse des Benutzers (für Infos über Probleme)
                "name VARCHAR(60) NOT NULL, " + // Der Name der Person
                "PRIMARY KEY (benutzerid), " + // Eindeutige ID des Benutzers als Key
                "UNIQUE (email));").executeUpdate(); // Email einzigartig (da sie zur Anmeldung dient)

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS authCodes(" +
                "benutzerid INT UNSIGNED NOT NULL, " + // Referenz auf die eindeutige ID des Benutzers
                "auth_code VARCHAR(36) NOT NULL, " + // Der Autorisierungscode des Benutzers
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Erstellungszeitpunkt des Codes
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid), " +
                "UNIQUE (auth_code));").executeUpdate(); // Der AuthCode muss eindeutig sein um Fehler zu verhindern

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS aktivierungsSchluessel(" +
                "benutzerid INT UNSIGNED NOT NULL, " + // Referenz auf die eindeutige ID des Benutzers
                "aktivierungs_schluessel VARCHAR(36) NOT NULL, " + // Aktivierungsschlüssel für das Konto
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Zeitpunkt der Erstellung des Schlüssels
                "UNIQUE (aktivierungs_schluessel), " + // Der Schlüssel muss eindeutig sein
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS adressen(" +
                "adressenid INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
                "benutzerid INT UNSIGNED NOT NULL, " + // Eindeutige ID des Benutzers, zu dem dise Adresse gehört
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Zeitpunkt des Hinzufügens der Adresse
                "anrede VARCHAR(30) NOT NULL, " + // Anrede ("Herr"/"Frau" + ggf. "Dr." oder "Prof.) der Person an der Rechnungsadresse
                "name VARCHAR(50) NOT NULL, " + // Der Name der Person an der Rechnungsadresse
                "strasse VARCHAR(100) NOT NULL, " + // Straße inkl. Hausnummer
                "plz INT(5) NOT NULL, " + // PLZ der Adresse
                "stadt VARCHAR(30) NOT NULL, " + // Stadt der Adresse
                "telefon VARCHAR(20), " +
                "PRIMARY KEY (adressenid), " + // Telefonnummer der Rechnungsadresse
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS changeEmail(" +
                "benutzerid INT UNSIGNED NOT NULL, " +
                "neue_email VARCHAR(254) NOT NULL, " +
                "alte_email_key INT(5) UNSIGNED NOT NULL, " +
                "neue_email_key INT(5) UNSIGNED NOT NULL, " +
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE (benutzerid), " + // Zeitpunkt des Hinzufügens der Keys
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        // --- Filme ---
        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS filme(" +
                "filmid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID des Films
                "name VARCHAR(100) NOT NULL, " + // Name des Films
                "bild_link TEXT NOT NULL, " + // Link zu dem Cover des Films
                "hintergrund_bild_link TEXT NOT NULL, " + // Link zu einem Hintergundbild für den Film
                "trailer_youtube VARCHAR(11) NOT NULL, " + // YouTube-ID des Trailers
                "kurze_beschreibung TEXT NOT NULL, " + // Kurzbeschreibung des Films
                "beschreibung TEXT NOT NULL, " + // Lange Beschreibung des Films
                "fsk TINYINT(2) UNSIGNED NOT NULL, " + // FSK Angabe des Films
                "dauer TINYINT UNSIGNED NOT NULL," + // Dauer des Films in Minuten
                "land VARCHAR(20) NOT NULL, " + // Produktionsland des Films
                "filmstart DATE NOT NULL, " + // Startzeitpunkt des Films
                "empfohlen BIT NOT NULL DEFAULT 0, " + // Ob der Film empfohlen wird, oder nicht
                "aktiv BIT NOT NULL DEFAULT 1, " + // Ob der Film aktuell aktiv ist (oder nur noch für ehmalige Bestellungen)
                "PRIMARY KEY (filmid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS vorstellungen(" +
                "vorstellungsid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID der Vorstellung
                "filmid INT UNSIGNED NOT NULL, " + // Referenz auf dem Film
                "saalid INT UNSIGNED NOT NULL, " + // Referenz auf den Saal
                "basis_preis DOUBLE UNSIGNED NOT NULL, " + // Preis für den günstigsten Platz
                "vorstellungsbeginn DATETIME NOT NULL, " + // Zeitpunkt, zu dem die Vorstellung beginnt
                "3d bit NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (vorstellungsid), " +
                "FOREIGN KEY (filmid) REFERENCES filme(filmid), " +
                "FOREIGN KEY (saalid) REFERENCES kinosaele(saalid)" +
                ");").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS bestellungen(" +
                "bestellnummer INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
                "vorstellungsid INT UNSIGNED NOT NULL, " +
                "benutzerid INT UNSIGNED NOT NULL, " +
                "anrede VARCHAR(30) NOT NULL, " + // Anrede ("Herr"/"Frau" + ggf. "Dr." oder "Prof.) der Person an der Rechnungsadresse
                "name VARCHAR(50) NOT NULL, " + // Der Name der Person an der Rechnungsadresse
                "strasse VARCHAR(100) NOT NULL, " + // Straße inkl. Hausnummer
                "plz INT(5) NOT NULL, " + // PLZ der Adresse
                "stadt VARCHAR(30) NOT NULL, " + // Stadt der Adresse
                "telefon VARCHAR(20), " + // Telefonnummer der Rechnungsadresse
                "PRIMARY KEY (bestellnummer), " +
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid), " +
                "FOREIGN KEY (vorstellungsid) REFERENCES vorstellungen(vorstellungsid)" +
                ");").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS bestellungePlaetze(" +
                "bestellnummer INT UNSIGNED NOT NULL, " +
                "platzid INT UNSIGNED NOT NULL, " +
                "preis DOUBLE UNSIGNED NOT NULL, " +
                "UNIQUE (platzid, bestellnummer)" +
                ");").executeUpdate();

        try (PreparedStatement ps_adminAccount = pConnection.prepareStatement("INSERT INTO konten(passwort, name, " +
                "email, rolle, aktiv) VALUES ('" + DigestUtils.md5Hex("Initial123") + "', 'Admin', 'info@noamo.de', 999, 1);")) {
            ps_adminAccount.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException ignored) {
        } // Tritt immer auf, wenn der Admin Account schon existiert
    }

    /**
     * Fragt eine Filmübersicht ab. Ein Beispiel für eine Rückgabe ist:<br>
     * <pre>{@code {
     *   "erstellt": 1602494662519,
     *   "filme": [
     *      {
     *       "filmid": 7,
     *       "name": "Hello Again – Ein Tag für immer",
     *       "bild_link": "https://download.noamo.de/images/cinema/hello_again.jpg",
     *       "hintergrund_bild_link": "https://download.noamo.de/images/cinema/bg_hello_again.jpg",
     *       "trailer_youtube_id": "G7nEpa04oDc",
     *       "kurze_beschreibung": "Lorem ipsum dolor sit amet, consetetur sadipscing",
     *       "beschreibung": "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor ",
     *       "fsk": 6,
     *       "dauer": 92,
     *       "land": "Deutschland",
     *       "filmstart": "2020-09-24",
     *       "empfohlen": false
     *     }
     *   ]
     * }}
     *
     * @return Die Filmübersicht als String im Json-Format
     * @throws SQLException Bei Fehlern bei der Verbindung zu der Datenbank
     */
    static String getAktiveFilme() throws SQLException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM filme WHERE aktiv=1" +
                     " ORDER BY filmstart DESC");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            // Json Objekt erstellen
            JsonObject json = new JsonObject();
            JsonArray list = new JsonArray();
            json.addProperty("erstellt", System.currentTimeMillis());
            json.add("filme", list);

            // Liste mit Daten füllen
            while (resultSet.next()) {
                JsonObject temp = new JsonObject();
                temp.addProperty("filmid", resultSet.getInt("filmid"));
                temp.addProperty("name", resultSet.getString("name"));
                temp.addProperty("bild_link", resultSet.getString("bild_link"));
                temp.addProperty("hintergrund_bild_link", resultSet.getString("hintergrund_bild_link"));
                temp.addProperty("trailer_youtube_id", resultSet.getString("trailer_youtube"));
                temp.addProperty("kurze_beschreibung", resultSet.getString("kurze_beschreibung"));
                temp.addProperty("beschreibung", resultSet.getString("beschreibung"));
                temp.addProperty("fsk", resultSet.getInt("fsk"));
                temp.addProperty("dauer", resultSet.getInt("dauer"));
                temp.addProperty("land", resultSet.getString("land"));
                temp.addProperty("filmstart", resultSet.getDate("filmstart").toString());
                temp.addProperty("empfohlen", resultSet.getBoolean("empfohlen"));
                list.add(temp);
            }

            // Neues JsonObjekt in den Cache speichern
            movies = new CacheObject<>(json.toString(), TTL_MOVIE_LIST);
            return movies.cache;
        }
    }

    /**
     * Fragt eine aktuelle ggf. gecachde Variante von {@link DataBase#getAktiveFilme()} ab.
     */
    static String getAktiveFilmeCached() throws SQLException {
        if (movies == null || movies.isNotAlive()) return getAktiveFilme();
        return movies.cache;
    }

    static String changeEmailConfirm(String pAuthCode, JsonObject pJsonObject) throws SQLException, BadRequestException, UnauthorisedException, ConflictException {
        try {
            int newEmailKey = pJsonObject.get("newEmailKey").getAsInt();
            int oldEmailKey = pJsonObject.get("oldEmailKey").getAsInt();
            try (Connection connection = basicDataSource.getConnection();
                 PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT benutzerid, neue_email, neue_email_key, alte_email_key FROM changeEmail " +
                         "WHERE benutzerid=(SELECT benutzerid FROM authCodes WHERE auth_code = '" + DigestUtils.md5Hex(pAuthCode) + "');");
                 ResultSet resultSet = preparedStatement1.executeQuery()) {

                if (!resultSet.next()) throw new UnauthorisedException("AuthCode ungültig");
                if (newEmailKey != resultSet.getInt("neue_email_key"))
                    throw new ConflictException("Key für die neue Email ungültig!");
                if (oldEmailKey != resultSet.getInt("alte_email_key"))
                    throw new ConflictException("Key für die alte Email ungültig!");
                String newEmail = resultSet.getString("neue_email"); // Neue Email abfragen
                int benutzerid = resultSet.getInt("benutzerid"); // Benutzerid auslesen

                // Neue Email einfügen
                try (PreparedStatement preparedStatement2 = connection.prepareStatement("UPDATE konten SET email='" +
                        newEmail + "' WHERE benutzerid=" + benutzerid + ";")) {
                    preparedStatement2.executeUpdate();
                }

                // Änderungseintrag löschen
                try (PreparedStatement preparedStatement2 = connection.prepareStatement("DELETE FROM changeEmail WHERE benutzerid = " + benutzerid + ";")) {
                    preparedStatement2.executeUpdate();
                }
            }
            return "OK Email wurde geändert";
        } catch (NullPointerException | ClassCastException exception) {
            throw new BadRequestException("Es wurden nicht alle Attribute erfolgreich mitgegeben. Notwendig sind (als Integer)" +
                    " 'newEmailKey' und 'oldEmailKey'");
        }
    }

    static String changeEmailRequest(String pAuthCode, JsonObject pJsonObject) throws BadRequestException, SQLException, UnauthorisedException {
        try {
            String passwort = pJsonObject.get("passwort").getAsString();
            String newEmail = pJsonObject.get("newEmail").getAsString();
            try (Connection connection = basicDataSource.getConnection();
                 PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT benutzerid, email, name " +
                         "FROM konten WHERE benutzerid = (SELECT benutzerid FROM authCodes WHERE auth_code = '" + DigestUtils.md5Hex(pAuthCode) +
                         "') AND passwort='" + DigestUtils.md5Hex(passwort) + "';");
                 ResultSet resultSet = preparedStatement1.executeQuery()) {
                if (!resultSet.next()) throw new UnauthorisedException("AuthCode oder Passwort ungültig");

                int benutzerid = resultSet.getInt("benutzerid");
                String oldEmail = resultSet.getString("email");
                String name = resultSet.getString("name");
                int oldEmailKey = new Random().nextInt(89999) + 10000;
                int newEmailKey = new Random().nextInt(89999) + 10000;

                // Alten Datensätz ggf. löschen
                try (PreparedStatement preparedStatement2 = connection.prepareStatement("DELETE FROM changeEmail WHERE benutzerid = " + benutzerid + ";")) {
                    preparedStatement2.executeUpdate();
                }

                // Neuen Datensatz einfügen
                try (PreparedStatement preparedStatement3 = connection.prepareStatement("INSERT INTO changeEmail(benutzerid, alte_email, neue_email, alte_email_key, neue_email_key) VALUES(" + benutzerid + ", '" + oldEmail +
                        "', '" + newEmail + "', " + oldEmailKey + ", " + newEmailKey + ");");) {
                    preparedStatement3.executeUpdate();
                }

                // Mails senden
                Mail.sendEmailChangeMail(name, oldEmail, newEmail, oldEmailKey, newEmailKey);
            }
            return "OK Bitte senden bestätigen Sie die Änderung über /changeEmail/confirm";
        } catch (NullPointerException | ClassCastException exception) {
            throw new BadRequestException("Es wurden nicht alle Attribute erfolgreich mitgegeben. Notwendig sind (als Strings)" +
                    " 'passwort' und 'newEmail'");
        }
    }

    /**
     * Fragt alle Sitzkategorien ab, die existieren. Ein Rückgabe kann z.B. sein:<br>
     * <pre>{@code [
     *  {
     *   "kategorieid": 1,
     *   "name": "Parkett",
     *   "aufpreis": 0.0,
     *   "faktor": 1.0,
     *   "width": 20,
     *   "height": 20,
     *   "color_hex": "#FF00FF"
     *  },
     *  {
     *   "kategorieid": 2,
     *   "name": "Loveseat",
     *   "aufpreis": 1.0,
     *   "faktor": 2.0,
     *   "width": 45,
     *   "height": 20,
     *   "color_hex": "#FF00FF",
     *   "icon": "",
     *  },
     * ]}
     *
     * @return Ein {@link JsonArray}, das alle Sitze enthält
     * @throws SQLException Bei Fehlern bei der Verbindung zu der Datenbank
     */
    static JsonArray getKategorien() throws SQLException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM kategorien");
             ResultSet rs = preparedStatement.executeQuery()) {
            JsonArray kategorien = new JsonArray();
            while (rs.next()) {
                JsonObject temp = new JsonObject();
                temp.addProperty("kategorieid", rs.getInt("kategorieid"));
                temp.addProperty("name", rs.getString("name"));
                temp.addProperty("aufpreis", rs.getDouble("aufpreis"));
                temp.addProperty("faktor", rs.getDouble("faktor"));
                temp.addProperty("width", rs.getInt("width"));
                temp.addProperty("height", rs.getInt("height"));
                temp.addProperty("color_hex", '#' + rs.getString("color_hex"));
                String iconLink = rs.getString("icon");
                if (iconLink != null) temp.addProperty("icon", iconLink);
                kategorien.add(temp);
            }
            categories = new CacheObject<>(kategorien, TTL_KATEGORIEN);
            return kategorien;
        }
    }

    /**
     * Fragt eine aktuelle ggf. gecachde Variante von {@link DataBase#getKategorien()} ()} ab.
     */
    static JsonArray getKategorienCached() throws SQLException {
        if (categories == null || categories.isNotAlive()) return getKategorien();
        else return categories.cache;
    }

    /**
     * Fragt alle Details zu einem Saal ab. Ein Beispiel für eine Rückgabe ist:<br>
     * <pre>{@code {
     *     "name": "Kino 1",
     *     "width": 368,
     *     "height": 184,
     *     "sitze": [
     *     {
     *       "kategorie": 1,
     *       "reihe": "A",
     *       "platz": 1,
     *       "x": 5,
     *       "y": 5
     *     },
     *     {
     *       "kategorie": 1,
     *       "reihe": "A",
     *       "platz": 2,
     *       "x": 30,
     *       "y": 5
     *     }
     * }}
     *
     * @param saalid Die ID des Saals, zu dem die Details abgefragt werden sollen
     * @return Ein {@link JsonObject}, das die Details enthält
     * @throws SQLException      Falls ein Fehler in der Verbindung zu der Datenbank auftritt
     * @throws NotFoundException Falls der Saal nicht gefunden wurde
     */
    static JsonObject getSaalPlan(int saalid) throws SQLException, NotFoundException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT * FROM kinosaele WHERE saalid=" + saalid + ";");
             PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM saalPlaetze WHERE saalid=" + saalid + ";");
             ResultSet r1 = preparedStatement1.executeQuery();
             ResultSet r2 = preparedStatement2.executeQuery()) {
            JsonObject saal = new JsonObject();

            if (!r1.next()) throw new NotFoundException("Kein Saal mit dieser Id gefunden");
            saal.addProperty("name", r1.getString("name"));
            saal.addProperty("width", r1.getInt("width"));
            saal.addProperty("height", r1.getInt("height"));

            JsonArray sitze = new JsonArray();
            saal.add("sitze", sitze);

            while (r2.next()) {
                JsonObject temp = new JsonObject();
                temp.addProperty("kategorie", r2.getInt("kategorieid"));
                temp.addProperty("reihe", r2.getString("reihe"));
                temp.addProperty("platz", r2.getInt("platz"));
                temp.addProperty("x", r2.getInt("x"));
                temp.addProperty("y", r2.getInt("y"));
                sitze.add(temp);
            }

            return saal;
        }
    }

    /**
     * Aktualiser die Daten von einem Nutzer. Die Eingabe ist ein {@link JsonObject} mit den möglichen Propertys "email"
     * , "name" und "passwort". Alle Attribute können vorhanden sein, müssen es aber nicht.
     *
     * @param authCode   Ein AuthCode, mit dem der Benutzer sich identifizieren kann.
     * @param jsonObject {@link JsonObject}, dass die Nutzerinfos, die aktualisiert werden sollen, enthält
     * @return "OK", wenn es geklappt hat.
     * @throws SQLException        Falls ein Fehler in der Vebrindung zu der Datenbank auftritt
     * @throws BadRequestException Falls das Passwort zu kurz, der Name zu kurz oder die Email-Adresse ungültig ist
     */
    static String updateUser(String authCode, JsonObject jsonObject) throws SQLException, BadRequestException, ConflictException, UnauthorisedException {
        // Werte auslesen
        String email = (jsonObject.has("email") ? jsonObject.get("email").getAsString() : null);
        String name = (jsonObject.has("name") ? jsonObject.get("name").getAsString() : null);
        String passwort = (jsonObject.has("passwort") ? jsonObject.get("passwort").getAsString() : null);

        // Werte prüfen
        if (email == null && name == null && passwort == null)
            throw new BadRequestException("Es wurden keine Parameter mitgegeben, die geändert werden sollen (Möglich ist \"email\", \"name\" und \"passwort\")");
        if (email != null && !email.matches("^(.+)@(.+)$"))
            throw new BadRequestException("Die neue Email-Adresse ist ungültig!");
        if (name != null && name.length() <= 5)
            throw new BadRequestException("Bitte geben Sie Ihren vollständigen Namen (Vor- und Nachname) ein!");
        if (passwort != null && passwort.length() <= 8)
            throw new BadRequestException("Das Passwort muss mehr als 8 Zeichen haben!");

        // In String formatieren
        String update = (email == null ? "" : "email='" + email + "'");
        update += (name == null ? "" : (update.length() == 0 ? "" : " AND ") + "name='" + name + "'");
        update += (passwort == null ? "" : (update.length() == 0 ? "" : " AND ") + "passwort='" + passwort + "'");

        // Ausführen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("UPDATE konten SET " + update +
                     " WHERE benutzerid = (SELECT benutzerid FROM authCodes WHERE auth_code = '" + DigestUtils.md5Hex(authCode) + "');")) {
            if (preparedStatement.executeUpdate() == 0) throw new UnauthorisedException("AuthCode ungültig!");
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException("Zu der angegeben Email-Adresse existiert bereits ein anderes Konto!");
        }
        return "Ok";
    }

    /**
     * Löscht eine Adresse aus dem System. Die ID lässt sich einer Adresse eindeutig zuordnen
     *
     * @param authCode Ein AuthCode, mit dem der Benutzer sich identifizieren kann.
     * @param pId      Die ID der Adresse, die gelöscht werden soll
     * @return "Ok", wenn es geklappt hat
     * @throws SQLException          Falls ein Fehler in der Verbindung zu der Datenbank auftritt
     * @throws UnauthorisedException Falls der AuthCode ungültig ist
     * @throws BadRequestException   Falls keine Adresse mit dieser ID gefunden wurde
     */
    static String deleteAdress(String authCode, int pId) throws SQLException, UnauthorisedException, BadRequestException {
        try (Connection connection = basicDataSource.getConnection()) {
            // Userid herausfinden
            int userid;
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT benutzerid FROM authCodes WHERE auth_code='" +
                    DigestUtils.md5Hex(authCode) + "';");
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) throw new UnauthorisedException("AuthCode ungültig!");
                userid = resultSet.getInt("benutzerid");
            }

            // Adresse löschen
            try (PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM adressen WHERE adressenid=" + pId + " AND benutzerid=" + userid + ";")) {
                if (preparedStatement.executeUpdate() == 0)
                    throw new BadRequestException("Es wurde keine Adresse mit dieser ID gefunden");
            }
        }
        return "Ok";
    }

    /**
     * Fügt einem Benutzer eine Adresse hinzu.
     *
     * @param authCode   Ein AuthCode, mit dem der Benutzer sich identifizieren kann.
     * @param jsonObject Ein {@link JsonObject} mit der neuen Adresse. Notwendig sind die Attribute (als Strings)
     *                   'anrede', 'name', 'strasse', 'plz', 'stadt' und 'stadt', während 'telefon' optional ist
     * @return "Ok" bei Erfolg
     * @throws BadRequestException   Falls die Anfrage ungültige oder fehlende Daten enthält
     * @throws SQLException          Falls ein Fehler in der VErbindung zu der Datenbank auftritt
     * @throws UnauthorisedException Falls Der AuthCode ungültig ist
     */
    static String addAdress(String authCode, JsonObject jsonObject) throws BadRequestException, SQLException, UnauthorisedException {
        try {
            // Attribute lesen
            String anrede = jsonObject.get("anrede").getAsString();
            String name = jsonObject.get("name").getAsString();
            String strasse = jsonObject.get("strasse").getAsString();
            String plz = jsonObject.get("plz").getAsString();
            String stadt = jsonObject.get("stadt").getAsString();
            String telefon = (jsonObject.has("telefon") ? jsonObject.get("telefon").getAsString().replaceAll(" ", "") : null);

            // Attribute prüfen
            if (anrede.length() < 4)
                throw new BadRequestException("Die Anrede muss min. 4 Zeichen haben (Herr, Frau) + ggf. Dr./Prof.");
            if (name.length() < 5) throw new BadRequestException("Bitte geben Sie den vollständigen Namen ein");
            if (strasse.length() < 2) throw new BadRequestException("Eine Straße muss mindestens 2 Zeichen haben");
            if (!plz.matches("^[0-9]{5}$")) throw new BadRequestException("Ungültige Postleitzahl!");
            if (stadt.length() < 2) throw new BadRequestException("Eine Stadt muss mindestens 2 Zeichen haben");
            if (telefon != null && !telefon.matches("^\\+(?:[0-9]⋅?){6,14}[0-9]$"))
                throw new BadRequestException("Telefonnummer ungültig (Internationales Format erforderlich)");

            // SQL
            try (Connection connection = basicDataSource.getConnection()) {
                //Userid herausfinden
                int userid;
                try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT benutzerid FROM authCodes WHERE auth_code='" +
                        DigestUtils.md5Hex(authCode) + "';");
                     ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) throw new UnauthorisedException("AuthCode ungültig!");
                    userid = resultSet.getInt("benutzerid");
                }

                try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO adressen(benutzerid, " +
                        "anrede, name, strasse, plz, stadt" + (telefon == null ? "" : ", telefon") +
                        ") VALUES ('" + userid + "', '" + anrede + "', '" + name + "', '" + strasse + "', '" +
                        plz + "', '" + stadt + "'" + (telefon == null ? "" : ", '" + telefon + "'") + ")")) {
                    preparedStatement.executeUpdate();
                }
            }
            return "OK";
        } catch (NullPointerException | ClassCastException exception) {
            throw new BadRequestException("Es wurden nicht alle Attribute erfolgreich mitgegeben. Notwendig sind (als Strings)" +
                    " 'anrede', 'name', 'strasse', 'plz' und 'stadt', während 'telefon' optional ist");
        }
    }

    /**
     * Ermöglicht es einem Nutzer seine eigenen Nutzerinfos abzufragen.
     *
     * @param pAuthCode Ein AuthCode, mit dem der Benutzer sich identifizieren kann.
     * @return {@link JsonObject}, dass die Nutzerinfos enthält
     * @throws BadRequestException   Falls das Format des AuthCodes ungültig ist
     * @throws SQLException          Falls es Problem mit der Verbindung zur Datenbank gibt
     * @throws UnauthorisedException Falls der AuthCode ungültig ist
     */
    static JsonObject getUserInfos(String pAuthCode) throws BadRequestException, SQLException, UnauthorisedException {
        // Parameterprüfung
        if (pAuthCode == null || pAuthCode.length() != 36)
            throw new BadRequestException("AuthCode hat ein falsches Format oder wurde nicht mitgegeben!");

        // Konto anhand des AuthCodes finden
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT * FROM konten " +
                     "WHERE benutzerid=(SELECT benutzerid FROM authCodes WHERE auth_code='" + DigestUtils.md5Hex(pAuthCode) + "');");
             ResultSet resultSet1 = preparedStatement1.executeQuery()) {

            // Falls kein AuthCode gefunden wurde
            if (!resultSet1.next()) throw new UnauthorisedException("AuthCode ungültig!");

            // Hauptattribute in Rückgabeobjekt einfügen
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", resultSet1.getString("name"));
            jsonObject.addProperty("email", resultSet1.getString("email"));
            jsonObject.addProperty("erstellt", resultSet1.getTimestamp("erstellt").toString());

            // Adressen abfragen und der Rückgabe hinzufügen
            JsonArray adressen = new JsonArray();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM adressen " +
                    "WHERE benutzerid=" + resultSet1.getInt("benutzerid") + ";");
                 ResultSet resultSet2 = preparedStatement2.executeQuery()) {
                while (resultSet2.next()) {
                    JsonObject temp = new JsonObject();
                    temp.addProperty("adressenid", resultSet2.getString("adressenid"));
                    temp.addProperty("anrede", resultSet2.getString("anrede"));
                    temp.addProperty("name", resultSet2.getString("name"));
                    temp.addProperty("strasse", resultSet2.getString("strasse"));
                    temp.addProperty("plz", resultSet2.getString("plz"));
                    String tel = resultSet2.getString("telefon");
                    if (tel != null) temp.addProperty("telefon", tel);
                    adressen.add(temp);
                }
            }
            if (adressen.size() > 0) jsonObject.add("adressen", adressen);

            // Zurückgeben
            return jsonObject;
        }
    }

    /**
     * Fragt alle Vorstellungen zu einem oder allen Filmen ab. Es werden nur zukünftige Vorstellungen zurück gegeben
     *
     * @param pFilmId Die ID des Filmes, von dem man die Vorstellung wissen will (0, wenn man alle Filme wissen will)
     * @return Ein JsonArray, dass die Vorstellungen enthält
     * @throws SQLException      Falls ein Fehler in der Vebrindung zu der Datenbank auftritt
     * @throws NotFoundException Falls keine Vorstellung gefunden wurde
     */
    static JsonArray getVorstellungen(int pFilmId) throws SQLException, NotFoundException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM vorstellungen " +
                     "INNER JOIN kinosaele on vorstellungen.saalid = kinosaele.saalid WHERE " +
                     (pFilmId == 0 ? "" : "filmid=" + pFilmId + " AND ") + "vorstellungsbeginn > CURRENT_TIMESTAMP " +
                     "ORDER BY vorstellungsbeginn;");
             ResultSet resultSet = preparedStatement.executeQuery()) {
            if (!resultSet.next()) throw new NotFoundException("Keine Vorstellung gefunden!");
            JsonArray array = new JsonArray();
            JsonObject temp;
            do {
                temp = new JsonObject();
                temp.addProperty("vorstellungsid", resultSet.getInt("vorstellungsid"));
                temp.addProperty("3d", resultSet.getBoolean("3d"));
                if (pFilmId == 0) temp.addProperty("filmid", resultSet.getInt("filmid"));
                temp.addProperty("saalName", resultSet.getString("name"));
                temp.addProperty("vorstellungsbeginn", resultSet.getTimestamp("vorstellungsbeginn").toString());
                temp.addProperty("basisPreis", resultSet.getDouble("basis_preis"));
                array.add(temp);
            } while (resultSet.next());
            return array;
        }
    }

    /**
     * Gibt Details zu einer Vorstellung zurück. Eine Rückgabe könnte z.B. sein:<br>
     * <pre>{@code {
     *   "saalName": "Kino 1",
     *   "3d": true,
     *   "filmid": 2,
     *   "basisPreis": 12.0,
     *   "width": 368,
     *   "height": 184,
     *   "kategorien": [
     *      {
     *       "kategorieid": 1,
     *       "name": "Parkett",
     *       "aufpreis": 0.0,
     *       "faktor": 1.0,
     *       "width": 20,
     *       "height": 20,
     *       "color_hex": "#FF00FF"
     *     },
     *     {
     *       "kategorieid": 2,
     *       "name": "Loge",
     *       "aufpreis": 1.5,
     *       "faktor": 1.0,
     *       "width": 20,
     *       "height": 20,
     *       "color_hex": "#FF0000"
     *     }
     *   ]
     * }}
     *
     * @param pVorstellungsId Die ID von der Vorstellung, zu der man Details haben möchte
     * @return Ein {@link JsonObject} mit dem oben beschriebene Inhalt
     * @throws SQLException Falls ein Fehler in der Verbindung zu der Datenbank auftritt
     * @throws NotFoundException Falls die Vorstellung nicht gefunden wurde
     * @throws BadRequestException Falls ID ungültig ist (d.h falls es eine Zahl < 0 ist)
     */
    static JsonObject getVorstellungsDetails(int pVorstellungsId) throws SQLException, NotFoundException, BadRequestException {
        // Parameterprüfung
        if (pVorstellungsId < 1) throw new BadRequestException("Ungültige Vorstellungsid");

        // Alle Infos zu der bestimmte Vorstellung abfragen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT * FROM vorstellungen INNER " +
                     "JOIN kinosaele on vorstellungen.saalid = kinosaele.saalid WHERE vorstellungsid=" + pVorstellungsId + ";");
             ResultSet resultSet1 = preparedStatement1.executeQuery()) {

            // Filtern, falls nichts gefunden wurde
            if (!resultSet1.next()) throw new NotFoundException("Die Vorstellung wurde nicht gefunden!");

            // Metadaten zu der Vorstellung abfragen
            JsonObject reVal = new JsonObject();
            reVal.addProperty("saalName", resultSet1.getString("name"));
            reVal.addProperty("3d", resultSet1.getBoolean("3d"));
            reVal.addProperty("filmid", resultSet1.getInt("filmid"));
            reVal.addProperty("basisPreis", resultSet1.getDouble("basis_preis"));
            reVal.addProperty("width", resultSet1.getInt("width"));
            reVal.addProperty("height", resultSet1.getInt("height"));
            reVal.add("kategorien", getKategorienCached());

            // Sitze abfragen
            JsonArray sitze = new JsonArray();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM saalPlaetze LEFT " +
                    "JOIN (SELECT platzid from bestellungePlaetze p INNER JOIN bestellungen b ON p.bestellnummer = " +
                    "b.bestellnummer WHERE vorstellungsid = " + pVorstellungsId + ") AS b ON saalPlaetze.platzid = b.platzid;");
                 ResultSet resultSet2 = preparedStatement2.executeQuery()) {

                // Alle Sitze in Array einfügen
                JsonObject temp;
                while (resultSet2.next()) {
                    temp = new JsonObject();
                    temp.addProperty("id", resultSet2.getInt("platzid"));
                    temp.addProperty("reihe", resultSet2.getString("reihe"));
                    temp.addProperty("platz", resultSet2.getInt("platz"));
                    temp.addProperty("kategorie", resultSet2.getInt("kategorieid"));
                    temp.addProperty("x", resultSet2.getInt("x"));
                    temp.addProperty("y", resultSet2.getInt("y"));
                    temp.addProperty("belegt", resultSet2.getInt("b.platzid") != 0);
                    sitze.add(temp);
                }
            }
            reVal.add("sitze", sitze);

            // JsonObjekt zurück geben
            return reVal;
        }
    }

    static String insertVorstellung(String pAuthCode, JsonObject pJson) throws BadRequestException, SQLException, UnauthorisedException, NotFoundException, NotActiveException {
        // Zugangberechtigung prüfen
        authorizationBarriere(pAuthCode, 700);

        // Parameterprüfung
        if (pJson == null) throw new BadRequestException("Kein Json-Objekt vorhanden");
        if (!pJson.has("filmid")) throw new BadRequestException("In dem Json-Objekt fehlt das Attribut 'filmid'");
        if (!pJson.has("saalid")) throw new BadRequestException("In dem Json-Objekt fehlt das Attribut 'saalid'");
        if (!pJson.has("basis_preis"))
            throw new BadRequestException("In dem Json-Objekt fehlt das Attribut 'basis_preis'");
        if (!pJson.has("vorstellungsbeginn"))
            throw new BadRequestException("In dem Json-Objekt fehlt das Attribut 'vorstellungsbeginn'");

        // Film einfügen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement p = connection.prepareStatement("INSERT INTO vorstellungen(filmid, " +
                     "saalid, basis_preis, vorstellungsbeginn) VALUES (?,?,?,?);")) {
            p.setInt(1, pJson.get("filmid").getAsInt());
            p.setInt(2, pJson.get("saalid").getAsInt());
            p.setDouble(3, pJson.get("basis_preis").getAsDouble());
            p.setTimestamp(4, Util.stringToSQLTimestamp(pJson.get("vorstellungsbeginn").getAsString()));
            p.executeUpdate();
            return "Vorstellung erstellt";
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new NotFoundException("'saalid' oder 'filmid' konnte nicht zugeordnet werden!");
        }
    }

    /**
     * Diese Methode deaktiviert ein Konto. Dadurch kann auf das Konto nicht mehr zugegriffen werden. Der AktivCode
     * eines deaktiviereten Kontos ist dabei 3.
     *
     * @param pAuthCode Ein AuthCode um den Benutzer zu identifizieren
     * @param pPasswort Das Passwort zu zusätzlichen Bestätigung
     * @throws SQLException      Falls ein Fehler in der Verbindung zur Datenbank auftritt
     * @throws NotFoundException Falls ein Konto ungültig ist
     */
    static String kontoDeaktivieren(String pAuthCode, String pPasswort) throws SQLException, NotFoundException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("UPDATE konten SET aktiv=2 WHERE passwort='" +
                     DigestUtils.md5Hex(pPasswort) + "' AND benutzerid=(SELECT benutzerid FROM authCodes WHERE auth_code='" +
                     DigestUtils.md5Hex(pAuthCode) + "');")) {
            if (preparedStatement.executeUpdate() == 0) throw new NotFoundException("AuthCode oder Passwort falsch!");
            return "Ihr Konto wurde erfolgreich deaktiviert";
        }
    }

    /**
     * Führt einen Login durch und gibt sowohl den Namen als auch den Auth Code zurück.
     *
     * @param pJson {@link JsonObject} mit 'email' und 'passwort'
     * @return {@link JsonObject} mit 'authToken' und 'name'
     * @throws BadRequestException   Falls 'email' oder 'passwort' im JsonObjekt nicht vorhanden ist
     * @throws SQLException          Falls ein Fehler mit der Verbindung zur Datenbank auftritt
     * @throws UnauthorisedException Falls die Kombination aus Email und Passwort falsch ist
     * @throws NotActiveException    Falls das Konto noch nicht aktiviert ist
     */
    static JsonObject login(JsonObject pJson) throws BadRequestException, SQLException, UnauthorisedException, NotActiveException {
        // Parameterprüfung & Parameter lesen
        if (!pJson.has("email") || !pJson.has("passwort"))
            throw new BadRequestException("Es fehlen die Popertys 'email' und/oder 'passwort'");
        String email = pJson.get("email").getAsString(), passwort = pJson.get("passwort").getAsString();

        // Zugehöriges Konto finden
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT benutzerid, name, aktiv FROM konten " +
                     "WHERE email='" + email + "' AND passwort='" + DigestUtils.md5Hex(passwort) + "';");
             ResultSet resultSet1 = preparedStatement1.executeQuery()) {

            // Falsche Anmeldedaten filtern & Prüfen, ob das Konto aktiv ist
            if (!resultSet1.next()) throw new UnauthorisedException("Email/Passwort Kombination falsch");
            aktivBarriere(resultSet1);

            // Rückgabe Json-Objekt erstellen
            JsonObject jsonObject = new JsonObject();
            String authCode = UUID.randomUUID().toString(); // AuthCode generieren
            jsonObject.addProperty("name", resultSet1.getString("name"));
            jsonObject.addProperty("authToken", authCode);

            // AuthCode in Datenbank hochladen
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO authCodes(benutzerid, " +
                    "auth_code) VALUES (" + resultSet1.getInt("benutzerid") + ", '" + DigestUtils.md5Hex(authCode) + "');")) {
                preparedStatement2.executeUpdate();
            }

            // Objekt zurückgeben
            return jsonObject;
        }
    }

    static void uploadSaalplan(String pAuthCode, JsonObject jsonObject) throws SQLException, BadRequestException, UnauthorisedException, NotActiveException {
        authorizationBarriere(pAuthCode, 700);

        String name = jsonObject.get("name").getAsString();
        int width = jsonObject.get("width").getAsInt();
        int height = jsonObject.get("height").getAsInt();
        JsonArray sitze = jsonObject.get("sitze").getAsJsonArray();

        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("INSERT INTO kinosaele(name, " +
                     "width, height) VALUES ('" + name + "', " + width + ", " + height + ")", Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement1.executeUpdate();

            // Automatisch generierte ID abfragen
            ResultSet rs = preparedStatement1.getGeneratedKeys();
            rs.next();
            int saalid = rs.getInt(1);

            // Jeden Sitz hochladen
            PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO saalPlaetze(saalid, " +
                    "kategorieid, reihe, platz, x, y) VALUES (?, ?, ?, ?, ?, ?)");
            for (JsonElement s : sitze) {
                JsonObject temp = s.getAsJsonObject();
                preparedStatement2.setInt(1, saalid);
                preparedStatement2.setInt(2, temp.get("kategorie").getAsInt());
                preparedStatement2.setString(3, temp.get("reihe").getAsString());
                preparedStatement2.setInt(4, temp.get("platz").getAsInt());
                preparedStatement2.setInt(5, temp.get("x").getAsInt());
                preparedStatement2.setInt(6, temp.get("y").getAsInt());
                preparedStatement2.addBatch();
            }
            preparedStatement2.executeBatch();
        }
    }

    /**
     * Ein Cache-Objekt, dass Daten zwischenspeichert
     *
     * @param <T> Typ des Objektes, was geachet werden soll
     */
    private static class CacheObject<T> {
        private final long aliveUntil;
        private final T cache;

        /**
         * Erstellt ein Cache-Objekt.
         *
         * @param pCache Das Objekt, dass in dem Cache gespeichert werden soll
         * @param ttl    Time To Live (Wie lange das JsonObject verwendet werden kann (in ms))
         */
        private CacheObject(T pCache, int ttl) {
            cache = pCache;
            aliveUntil = ttl + System.currentTimeMillis();
        }

        /**
         * Fragt ab, ob das Objekt noch valide ({@code true}) ist oder erneutert ({@code false}) werden muss
         */
        private boolean isNotAlive() {
            return System.currentTimeMillis() >= aliveUntil;
        }
    }
}
