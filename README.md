# Cinema-Backend
Folgende Argumente können/müssen dem Backend übergeben werden:

| Prefix   | Verpflichtend         | Aufbau                                                  | Beispiel                                                                                              |
|----------|-----------------------|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| DB       | Ja                    | DB=(JDBC_STRING)                                        | DB=jdbc:mysql://myserver.de:3306/cinema?user=testuser&password=123456789&serverTimezone=Europe/Berlin |
| MAIL     | Ja                    | MAIL=(SMTP HOST):(SMTP PORT):(EMAIL ADRESSE):(PASSWORT) | MAIL=myserver.de:25:test@myserver.de:123456789                                                        |
| RESTPORT | Nein (Standart: 4567) | RESTPORT=(REST API PORT)                                | RESTPORT=4568                                                                                         |
