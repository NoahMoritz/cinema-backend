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
import de.noamo.cinema.backend.exceptions.InvalidException;
import de.noamo.cinema.backend.exceptions.NotActiveException;
import de.noamo.cinema.backend.exceptions.ParameterException;
import de.noamo.cinema.backend.exceptions.UnauthorisedException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.text.Normalizer;
import java.util.UUID;

/**
 * Ist zuständig für die Verbindung zur Datenbank und für Aktionen, die dort ausgeführt werden. Die Verbindungen werden
 * mit in einem Connection-Pool verwaltet ({@link BasicDataSource}).
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 05.09.2020
 */
abstract class DataBase {
    private final static int DPCP2_MAX_CON_IDLE = 6;
    private final static int DPCP2_MAX_OPEN_STATEMENTS = 50;
    private final static int DPCP2_MIN_CON_IDLE = 1;
    private final static int TTL_MOVIE_LIST = 1800000;
    private static BasicDataSource basicDataSource;
    private static CacheObject movies;

    /**
     * Die Methode aktiviert einen Account mit einem Aktivierungsschlüssel. Dieser Aktiverungsschlüssel befindet sich in
     * der Datenbank in der Tabelle "aktiverungsSchluessel".
     *
     * @param pAktivierungsSchluessel Der Aktivierungsschlüssel
     * @throws ParameterException Falls der Schlüssel ein ungültiges Format hat (kein 36 Zeichen)
     * @throws SQLException       Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws InvalidException   Falls der Schlüssel nicht existiert (oder bereits aktiviert wurde)
     */
    static void activateAccount(String pAktivierungsSchluessel) throws ParameterException, SQLException, InvalidException {
        // Parameterprüfung
        if (pAktivierungsSchluessel.length() != 36) throw new ParameterException("Ungültiges Format!");

        // Account aktivieren
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("UPDATE konten SET aktiv=1 WHERE benutzerid=" +
                     "(SELECT benutzerid FROM aktivierungsSchluessel WHERE aktivierungs_schluessel='" + pAktivierungsSchluessel + "')");
             PreparedStatement preparedStatement2 = connection.prepareStatement("DELETE FROM aktivierungsSchluessel WHERE " +
                     "aktivierungs_schluessel='" + pAktivierungsSchluessel + "';")) {
            if (preparedStatement1.executeUpdate() == 0) throw new InvalidException("Ungültiger Aktivierungscode!");
            else preparedStatement2.executeUpdate();
        }
    }

    /**
     * Prüft, ob ein Account autorisiert ist, eine bestimmte Aktion auszuführen
     *
     * @param pJson  {@link JsonObject}, dass die Propertys {@code email} und {@code passwort} enthält
     * @param pLevel Das Level, dass für diese Aktion mindestens benötigt wird
     * @throws SQLException          Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws ParameterException    Falls die Attribute {@code email} und {@code passwort} nicht existieren
     * @throws UnauthorisedException Falls der Nutzer für diese Aktion nicht autorisiert ist
     */
    private static void authorize(JsonObject pJson, int pLevel) throws SQLException, ParameterException, UnauthorisedException {
        // Parameter auslesen
        if (!pJson.has("email") || !pJson.has("passwort")) throw
                new ParameterException("Es fehlen die Popertys 'email' und/oder 'passwort'");
        String email = pJson.get("email").getAsString();
        String passwort = pJson.get("passwort").getAsString();

        // Abfrage starten
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT rolle, aktiv FROM konten " +
                     "WHERE email='" + email + "' AND passwort='" + DigestUtils.md5Hex(passwort) + "';");
             ResultSet rs = preparedStatement.executeQuery()) {
            if (rs.next()) {
                if (!rs.getBoolean("aktiv")) throw new UnauthorisedException("Konto nicht aktiv");
                if (rs.getInt("rolle") < pLevel)
                    throw new UnauthorisedException("Keine ausreichenden Rechte! Benoetigt wird Stufe " +
                            pLevel + ". Sie haben: " + rs.getInt("rolle"));
            } else throw new UnauthorisedException("Account mit diesen Anmeldedaten nicht gefunden");
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

        basicDataSource = new BasicDataSource();
        basicDataSource.setUrl(pUrl);
        basicDataSource.setValidationQuery("SELECT benutzerid FROM konten");
        basicDataSource.setMinIdle(DPCP2_MIN_CON_IDLE);
        basicDataSource.setMaxIdle(DPCP2_MAX_CON_IDLE);
        basicDataSource.setMaxOpenPreparedStatements(DPCP2_MAX_OPEN_STATEMENTS);
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
     * @return Der Aktivierungscode, mit dem dieses Konto aktiviert werden kann
     * @throws SQLException                             Falls ein Problem in der Verbindung zu der Datenbank vorliegt
     * @throws SQLIntegrityConstraintViolationException Falls die Email bereits in der Datenbank vorhanden ist
     * @throws ParameterException                       Falls ein Parameter nicht den Vorgaben entspricht
     */
    static String createUser(String pPasswort, String pEmail, String pName) throws SQLException, ParameterException, SQLIntegrityConstraintViolationException {
        // Parameterprüfung
        if (pPasswort.length() <= 8) throw new ParameterException("Das Passwort muss mehr als 8 Zeichen haben!");
        if (!pEmail.matches("^(.+)@(.+)$"))
            throw new ParameterException("Die eingegebene Email-Adresse ist ungültig!");
        if (pName.length() <= 5)
            throw new ParameterException("Bitte geben Sie Ihren vollständigen Namen (Vor- und Nachname) ein!");

        // Konto hinzufügen
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("INSERT INTO konten(passwort, email, name) " +
                     "VALUES ('" + DigestUtils.md5Hex(pPasswort) + "', '" + pEmail + "', '" + pName + "');")) {
            preparedStatement1.executeUpdate();
            String uuid = UUID.randomUUID().toString();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO aktivierungsSchluessel(" +
                    "benutzerid, aktivierungs_schluessel) VALUES ((SELECT benutzerid FROM konten WHERE email = '" + pEmail +
                    "'), '" + uuid + "');")) {
                preparedStatement2.executeUpdate();
                return uuid;
            }
        }
    }

    /**
     * Erstellt die benötigen Tabellen, falls diese noch nicht existieren. Ebenfalls wird ein initialer Admin Account
     * erstellt, der dafür da ist, das Admin Panl zu öffnen und die Starteinstellungen vor zu nehmen.
     *
     * @param pConnection Die Verbindung zu der Datenbank
     */
    private static void dataBaseSetup(Connection pConnection) throws SQLException {
        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS konten(" +
                "benutzerid INT UNSIGNED NOT NULL AUTO_INCREMENT, " + // Eindeutige ID des Benutzers
                "rolle INT(3) UNSIGNED NOT NULL DEFAULT 0, " + // Rolle des Nutzers (spielt für den Zugriff eine Rolle)
                "aktiv BIT NOT NULL DEFAULT 0," + // Ob das Konto aktiviert wurde
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Erstellungszeitpunkt des Accounts
                "passwort VARCHAR(50) NOT NULL, " + // Das Passwort (für die Anmeldung)
                "email VARCHAR(254) NOT NULL, " + // Eine Email-Adresse des Benutzers (für Infos über Probleme)
                "name VARCHAR(60) NOT NULL, " + // Der Name der Person
                "PRIMARY KEY (benutzerid), " + // Eindeutige ID des Benutzers als Key
                "UNIQUE (email));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS authCodes(" +
                "benutzerid INT UNSIGNED NOT NULL," +
                "authCode VARCHAR(36) NOT NULL," +
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS aktivierungsSchluessel(benutzerid INT UNSIGNED NOT NULL, " + // Eindeutige ID des Benutzers
                "aktivierungs_schluessel VARCHAR(36) NOT NULL, " +
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Aktivierungsschlüssel für das Konto
                "UNIQUE (aktivierungs_schluessel), " + // Ein Aktivierungsschlüssel muss eindeutig sein
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS adressen(" +
                "benutzerid INT UNSIGNED NOT NULL, " + // Eindeutige ID des Benutzers, zu dem dise Adresse gehört
                "erstellt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " + // Zeitpunkt des Hinzufügens der Adresse
                "anrede VARCHAR(30) NOT NULL, " + // Anrede ("Herr"/"Frau" + ggf. "Dr." oder "Prof.) der Person an der Rechnungsadresse
                "vorname VARCHAR(50) NOT NULL, " + // Der Vorname der Person an der Rechnungsadresse
                "nachname VARCHAR(50) NOT NULL, " + // Der Nachname der Person an der Rechnungsadresse
                "strasse VARCHAR(100) NOT NULL, " + // Straße inkl. Hausnummer
                "plz INT(5) NOT NULL, " + // PLZ der Adresse
                "telefon VARCHAR(20), " + // Telefonnummer der Rechnungsadresse
                "FOREIGN KEY (benutzerid) REFERENCES konten(benutzerid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS filme(" +
                "filmid INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
                "name VARCHAR(100) NOT NULL, " +
                "bild_link TEXT NOT NULL, " +
                "hintergrund_bild_link TEXT NOT NULL, " +
                "trailer_youtube TEXT NOT NULL, " +
                "kurze_beschreibung TEXT NOT NULL, " +
                "beschreibung TEXT NOT NULL, " +
                "fsk TINYINT UNSIGNED NOT NULL, " +
                "dauer TINYINT UNSIGNED NOT NULL," +
                "land VARCHAR(20) NOT NULL, " +
                "filmstart DATE NOT NULL, " +
                "empfohlen BIT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (filmid));").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS kinosaele(" +
                "saalid INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
                "name VARCHAR(20) NOT NULL, " +
                "width INT NOT NULL, " +
                "height INT NOT NULL, " +
                "PRIMARY KEY (saalid)," +
                "UNIQUE (name)" +
                ");").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS kategorien(" +
                "kategorieid INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
                "name VARCHAR(20) NOT NULL, " +
                "aufpreis DECIMAL(4,2) NOT NULL, " +
                "faktor DOUBLE UNSIGNED NOT NULL, " +
                "width INT UNSIGNED NOT NULL, " +
                "height INT UNSIGNED NOT NULL ," +
                "color_hex VARCHAR(6) NOT NULL," +
                "PRIMARY KEY (kategorieid)," +
                "UNIQUE (name)" +
                ");").executeUpdate();

        pConnection.prepareStatement("CREATE TABLE IF NOT EXISTS saalPlaetze(" +
                "saalid INT UNSIGNED NOT NULL, " +
                "kategorieid INT UNSIGNED NOT NULL," +
                "reihe VARCHAR(1) NOT NULL, " +
                "platz INT(2) NOT NULL, " +
                "x INT UNSIGNED NOT NULL, " +
                "y INT UNSIGNED NOT NULL, " +
                "UNIQUE (saalid,reihe,platz), " +
                "FOREIGN KEY (saalid) REFERENCES kinosaele(saalid), " +
                "FOREIGN KEY (kategorieid) REFERENCES kategorien(kategorieid)" +
                ");").executeUpdate();
        try (PreparedStatement ps_adminAccount = pConnection.prepareStatement("INSERT INTO konten(passwort, name, " +
                "email, rolle, aktiv) VALUES ('" + DigestUtils.md5Hex("Initial") + "', 'Admin', 'info@noamo.de', 999, 1);")) {
            ps_adminAccount.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException ignored) {} // Tritt immer auf, wenn der Admin Account schon existiert
    }

    /**
     * Fragt eine Filmübersicht ab
     *
     * @return Die Filmübersicht als String im Json-Format
     */
    synchronized static String getAllMovies() {
        // ggf. aus dem Cache laden
        if (movies != null && movies.isAlive()) return movies.json;

        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM filme ORDER BY filmstart DESC");
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
            movies = new CacheObject(json, TTL_MOVIE_LIST);
        } catch (SQLException e) {
            Start.log(2, "Die Filmliste konnte nicht abgefragt werden! (" + e.getMessage() + ")");
        }
        return movies.json;
    }

    static JsonArray getKateogorien() throws SQLException {
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
                kategorien.add(temp);
            }
            return kategorien;
        }
    }

    static JsonObject getSaalPlan(int saalid) throws SQLException, InvalidException {
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT * FROM kinosaele WHERE saalid=" + saalid + ";");
             PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM saalPlaetze WHERE saalid=" + saalid + ";");
             ResultSet r1 = preparedStatement1.executeQuery();
             ResultSet r2 = preparedStatement2.executeQuery()) {
            JsonObject saal = new JsonObject();

            if (!r1.next()) throw new InvalidException("Unbekannter Saal");
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
     * Ermöglicht es einem Nutzer seine eigenen Nutzerinfos abzufragen.
     *
     * @param pAuthCode Ein AuthCode, mit dem der Benutzer sich identifizieren kann.
     * @return {@link JsonObject}, dass die Nutzerinfos enthält
     * @throws ParameterException    Falls das Format des AuthCodes ungültig ist
     * @throws SQLException          Falls es Problem mit der Verbindung zur Datenbank gibt
     * @throws UnauthorisedException Falls der AuthCode ungültig ist
     */
    static JsonObject getUserInfos(String pAuthCode) throws ParameterException, SQLException, UnauthorisedException {
        if (pAuthCode == null || pAuthCode.length() != 36)
            throw new ParameterException("AuthCode hat ein falsches Format!");
        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT * FROM konten " +
                     "WHERE benutzerid=(SELECT benutzerid FROM authCodes WHERE authCode='" + DigestUtils.md5Hex(pAuthCode) + "');");
             ResultSet resultSet1 = preparedStatement1.executeQuery()) {
            if (!resultSet1.next()) throw new UnauthorisedException("AuthCode ungültig!");
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", resultSet1.getString("name"));
            jsonObject.addProperty("email", resultSet1.getString("email"));
            jsonObject.addProperty("erstellt", resultSet1.getTimestamp("erstellt").toString());
            JsonArray adressen = new JsonArray();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("SELECT * FROM adressen " +
                    "WHERE benutzerid=" + resultSet1.getInt("benutzerid") + ";");
                 ResultSet resultSet2 = preparedStatement2.executeQuery()) {
                while (resultSet2.next()) {
                    JsonObject temp = new JsonObject();
                    temp.addProperty("anrede", resultSet2.getString("anrede"));
                    temp.addProperty("vorname", resultSet2.getString("vorname"));
                    temp.addProperty("nachname", resultSet2.getString("nachname"));
                    temp.addProperty("strasse", resultSet2.getString("strasse"));
                    temp.addProperty("plz", resultSet2.getString("plz"));
                    String tel = resultSet2.getString("telefon");
                    if (tel != null) temp.addProperty("telefon", tel);
                    adressen.add(temp);
                }
            }
            if (adressen.size() > 0) jsonObject.add("adressen", adressen);
            return jsonObject;
        }
    }

    /**
     * Führt einen Login durch und gibt sowohl den Namen als auch den Auth Code zurück.
     *
     * @param pJson {@link JsonObject} mit 'email' und 'passwort'
     * @return {@link JsonObject} mit 'authToken' und 'name'
     * @throws ParameterException    Falls 'email' oder 'passwort' im JsonObjekt nicht vorhanden ist
     * @throws SQLException          Falls ein Fehler mit der Verbindung zur Datenbank auftritt
     * @throws UnauthorisedException Falls die Kombination aus Email und Passwort falsch ist
     * @throws NotActiveException    Falls das Konto noch nicht aktiviert ist
     */
    static JsonObject login(JsonObject pJson) throws ParameterException, SQLException, UnauthorisedException, NotActiveException {
        if (!pJson.has("email") || !pJson.has("passwort"))
            throw new ParameterException("Es fehlen die Popertys 'email' und/oder 'passwort'");

        String email = pJson.get("email").getAsString(), passwort = pJson.get("passwort").getAsString();

        try (Connection connection = basicDataSource.getConnection();
             PreparedStatement preparedStatement1 = connection.prepareStatement("SELECT benutzerid, name, aktiv FROM konten " +
                     "WHERE email='" + email + "' AND passwort='" + DigestUtils.md5Hex(passwort) + "';");
             ResultSet resultSet1 = preparedStatement1.executeQuery()) {
            if (!resultSet1.next()) throw new UnauthorisedException("Email/Passwort Kombination falsch");
            if (resultSet1.getBoolean("aktiv")) throw new NotActiveException("Das Konto ist nicht aktiviert");
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", resultSet1.getString("name"));
            String authCode = UUID.randomUUID().toString();
            try (PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO authCodes(benutzerid, " +
                    "authCode) VALUES (" + resultSet1.getInt("benutzerid") + ", '" + DigestUtils.md5Hex(authCode) + "');")) {
                preparedStatement2.executeUpdate();
            }
            jsonObject.addProperty("authToken", authCode);
            return jsonObject;
        }
    }

    static void uploadSaalplan(JsonObject jsonObject) throws SQLException, ParameterException, UnauthorisedException {
        authorize(jsonObject, 700);

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
     * Ein Cache-Objekt beinhaltet
     */
    private static class CacheObject {
        private final long aliveUntil;
        private final String json;

        /**
         * Erstellt ein Cache-Objekt.
         *
         * @param jsonObject Das Objekt, dass in dem Cache gespeichert werden soll
         * @param ttl        Time To Live (Wie lange das JsonObject verwendet werden kann (in ms))
         */
        private CacheObject(JsonObject jsonObject, int ttl) {
            json = Normalizer.normalize(jsonObject.toString(), Normalizer.Form.NFKC);
            aliveUntil = ttl + System.currentTimeMillis();
        }

        private boolean isAlive() {
            return System.currentTimeMillis() < movies.aliveUntil;
        }
    }
}
