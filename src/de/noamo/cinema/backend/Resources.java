/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.util.Scanner;

/**
 * @author Noah Hoelterhoff
 * @version 16.09.2020
 * @since 16.09.2020
 */
public class Resources {
    private static String activationMail;
    private static String activationSite;

    public static String getActivationMail(String link) {
        return activationMail.replaceAll("REPLACE_WITH_LINK", link);
    }

    public static String getActivationSite(String title, String subtitle) {
        return activationSite.replace("REPLACE_TITLE", title).replace("REPLACE_SUBTITLE", subtitle);
    }

    /**
     * Liest eine Datei aus den Resourcen und kopiert Sie in einen String
     *
     * @param fileName Die Datei
     * @return Ein String mit dem Inhalt der Datei. Line-Seperatoren sind Systemstandarts
     */
    private static String loadResourceIntoString(String fileName) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(Mail.class.getResourceAsStream(fileName))) {
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
