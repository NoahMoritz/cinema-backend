# Cinema-Backend
Folgende Argumente können/müssen dem Backend übergeben werden:

| Prefix | Verpflichtend | Aufbau                                                  | Beispiel                                                                                              |
|--------|---------------|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| DB     |       Ja      | DB=<JDBC_STRING>                                        | DB=jdbc:mysql://myserver.de:3306/cinema?user=testuser&password=123456789&serverTimezone=Europe/Berlin |
| MAIL   |       Ja      | MAIL=<SMTP_HOST>:<SMTP_PORT>:<EMAIL_ADRESSE>:<PASSWORT> | MAIL=myserver.de:25:test@myserver.de:123456789                                                        |
|        |               |                                                         |                                                                                                       |
