# Capitol Cinema Backend
<b><u>Folgende Argumente können/müssen dem Backend übergeben werden:</u></b>

- <b>DB</b>
    - Verpflichtend: Ja
    - Aufbau: DB=(JDBC_STRING)
    - Beispiel: DB=jdbc:mysql://myserver.de:3306/cinema?user=testuser&password=123456789&serverTimezone=Europe/Berlin
- <b>MAIL</b>
    - Verpflichtend: Ja
    - Aufbau MAIL=(SMTP HOST):(SMTP PORT):(EMAIL ADRESSE):(PASSWORT)
    - Beispiel: MAIL=myserver.de:25:test@myserver.de:123456789
- <b>HOST</b>
    - Verpflichtend: Ja
    - Aufbau: HOST=(DOMAIN)
    - Beispiel: HOST=myserver.de
- <b>ZERTIFIKAT</b>
    - Verpflichtend: Nein (aber empfohlen)
    - Aufbau: ZERTIFIKAT=(PFAD ZU ZERTIFIKAT)
    - Alternativer Aufbau: ZERTIFIKAT=(PFAD ZU last_nginx.conf)
    - Beispiel: ZERTIFIKAT=/var/www/vhosts/system/myserver.de/conf/last_nginx.conf
- <b>RESTPORT</b>
    - Verpflichtend: Nein
    - Standartwert: 4567
    - Aufbau: RESTPORT=(REST API PORT)
    - Beispiel: RESTPORT=4568
- <b>WEBSOCKETPORT</b>
    - Verpflichtend: Nein
    - Standartwert: 56953
    - Aufbau: WEBSOCKETPORT=(WEBSOCKET PORT)
    - Beispiel: WEBSOCKETPORT=4569

<br><br>
<b><u>Folgende Routen hat die REST API:</u></b>

- <b>/get-movies</b>
    - GET
    - Fragt alle Filme ab
    - Rückgabe z.B.:
        ```json
        {
          "erstellt": 1600787832012,
          "filme": [{
            "filmid": 7,
            "name": "Hello Again – Ein Tag für immer",
            "bild_link": "https://download.noamo.de/images/cinema/hello_again.jpg",
            "hintergrund_bild_link": "https://download.noamo.de/images/cinema/bg_hello_again.jpg",
            "trailer_youtube_id": "G7nEpa04oDc",
            "kurze_beschreibung": "Romantische Komödie, die \"Und täglich grüßt das Murmeltier\" mit \"Die Hochzeit meines besten Freundes\" kreuzt.",
            "beschreibung": "Zazie (Alicia von Rittberg) lebt gemeinsam mit ihren Freunden [für dieses Beispiel gekürzt]",
            "fsk": 6,
            "dauer": 92,
            "land": "Deutschland",
            "filmstart": "2020-09-24",
            "empfohlen": false
          },
          {
            "filmid": 1,
            "name": "The Outpost - Überleben ist Alles",
            "bild_link": "https://download.noamo.de/images/cinema/the_outpost.jpg",
            "hintergrund_bild_link": "https://download.noamo.de/images/cinema/bg_the_outpost.jpg",
            "trailer_youtube_id": "_9Lkxfx-Rxs",
            "kurze_beschreibung": "Auf einer wahren Begebenheit basierender Kriegs-Actioner mit Starbesetzung",
            "beschreibung": "Camp Keating ist ein Außenposten des US-Militärs, [für dieses Beispiel gekürzt]",
            "fsk": 12,
            "dauer": 123,
            "land": "USA",
            "filmstart": "2020-09-17",
            "empfohlen": true
          }]
        }
        ```
        <br>
- <b>/create-account</b>
    - POST
    - Erstellt ein neues Konto und versendet eine Aktivierungsmail
    - Fehlercodes:
        - 400 (Bad Request): Fehlerhafte Anfrage
        - 403 (Forbidden): Account mit diesen Parametern nicht erlaubt (Erklärung in der Antwort)
        - 409 (Conflict): EMail-Adresse existiert bereits
        - 500 (Server Error): Interner Serverfehler
    - Eingabe z.B.:
        ```json
        {
          "name": "Max Mustermann",
          "email": "max.mustermann@gmail.com",
          "passwort": "testpassword"
        }
        ```
        <br>
- <b>/activate/:key</b>
    - GET
    - Aktiviert ein Konto
    - Die Rückgabe ist immer eine anzeigbare HTML Seite
    - Fehlercodes:
        - 400 (Bad Request): Fehlerhafte Anfrage
        - 403 (Forbidden): Der Account wird bereits verwendet
        - 500 (Server Error): Interner Serverfehler<br><br>
 - <b>/get-userinfos</b>
    - GET
    - Fragt die Details eines Benutzers ab
    - Erfodert im Header das Attribut "Auth" mit einem 36-Stelligem Auth-Code
    - Fehlercodes:
        - 400 (Bad Request): Fehlerhafte Anfrage
        - 403 (Forbidden): Ungültiger Code
        - 500 (Server Error): Interner Serverfehler
    - Rückgabe minimal:
        ```json
        {
          "name": "Max Mustermann",
          "email": "max.mustermann@gmail.com",
          "erstellt": "2020-09-23 15:43:00.0"
        }
        ```
    - Rückgabe maximal:
        ```json
        {
          "name": "Max Mustermann",
          "email": "noah.hoelterhoff@gmail.com",
          "erstellt": "2020-09-27 23:34:52.0",
          "adressen": [
            {
              "anrede": "Herr",
              "vorname": "Max",
              "nachname": "Mustermann",
              "strasse": "Musterstr. 4",
              "plz": "12345",
              "telefon": "01520 9574320"
            },
            {
              "anrede": "Frau",
              "vorname": "Maxine",
              "nachname": "Musterfrau",
              "strasse": "Musterstr. 3",
              "plz": "12345"
            }
          ]
        }
        ```
        <br>
- <b>/login</b>
    - GET
    - Generiert einen Auth Key und gibt den Namen zurück
    - Eingabe:
        ```json
        {
          "passwort": "123456789",
          "email": "max.mustermann@gmail.com"
        }
        ```
    - Rückgabe:
        ```json
        {
          "name":"Max Mustermann",
          "authToken":"d8c5a09f-2c99-4ed0-bb11-4b8393938cf3"
        }
        ```