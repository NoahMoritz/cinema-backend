# Capitol Cinema Backend

JavaDoc des Backends: https://noahmoritz.github.io/cinema-backend/

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
- <b>PAYPALCLIENTID</b>
    - Verpflichtend: Ja
    - Aufbau: PAYPALCLIENTID=(CLIENT ID VON PAYPAL)
    - Beispiel: PAYPALCLIENTID=askjfasfjkhsfhewsaf
- <b>PAYPALCLIENTSECRET</b>
    - Verpflichtend: Ja
    - Aufbau: PAYPALCLIENTSECRET=(CLIENT SECRET VON PAYPAL)
    - Beispiel: PAYPALCLIENTSECRET=asf4as56f7saf48asfsafa
- <b>WEBHOOK</b>
    - Verpflichtend: Nein
    - Info: Immer POST mit application/json ({"content": "<log>"})
    - Aufbau: WEBHOOK=(URL DES WEBHOOKS)
    - Beispiel: WEBHOOK=https://discord.com/api/webhooks/79437525933/WlUTk5DJO2FBfDqlUybvQayAahkr2