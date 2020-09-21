/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.util.Scanner;

/**
 * Ist für die Verwaltung vor Resourcen (Mails, Webseiten, Bildern, etc.) zuständig.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 16.09.2020
 */
abstract class Resources {
    private static String activationMail;
    private static String activationSite;

    /**
     * Gibt den HTML-Code der Aktiverungsmail zurück. Dabei wird ein Platzhalter für den Aktivierungslink durch den
     * mitgegebenen ersetzt.
     *
     * @param pLink Der individuelle Aktiverungslink für die Person
     * @return Ein String mit dem HTML Code für die Email
     */
    public static String getActivationMail(String pLink) {
        return activationMail.replaceAll("REPLACE_WITH_LINK", pLink);
    }

    /**
     * Gibt den HTMl-Code für die Aktiverungswebseite zurück. Dabei werden die Platzhalter für Title und Untertitel
     * durch die mitgegben Attribute ersetzt.
     *
     * @param pTitle    Titel (in großen Buchstaben über dem Strich)
     * @param pSubTitle Untertitel (in kleinen Buchstaben unter dem Strich)
     * @return Ein String mit dem HTML Code für die Webseite
     */
    public static String getActivationSite(String pTitle, String pSubTitle) {
        return activationSite.replace("REPLACE_TITLE", pTitle).replace("REPLACE_SUBTITLE", pSubTitle);
    }

    /**
     * Liest eine Datei aus den Resourcen und kopiert Sie in einen String
     *
     * @param pFileName Der Pfad innerhalb des res Ordners zu der Datei
     * @return Ein String mit dem Inhalt der Datei. Line-Seperatoren sind Systemstandarts
     */
    private static String loadResourceIntoString(String pFileName) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(Resources.class.getResourceAsStream(pFileName))) {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine()).append(System.lineSeparator());
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Lädt alle Resourcen, die verwendet werden in diese Klasse.
     *
     * @throws Exception Falls Fehler bei dem Laden auftreten
     */
    static void loadResources() throws Exception {
        activationMail = loadResourceIntoString("/mails/ActivationMail.html");
        activationSite = loadResourceIntoString("/sites/ActivationSite.html");
    }
}
