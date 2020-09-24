/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.io.File;
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
     * Liest das Zertifikat von dem Pfad ein, dass durch {@link Start#getCertificatePath()} zurückgegeben wird. Dieses
     * wird dann in einem PKCS12 KeyStore ziwschengespeichert und zum Schluss in einen JKS KeyStore exportiert. Dieser
     * befindet sich in dem "{@code (user.home)/cinema/cinema.jks}"
     */
    private static void importCertificateInJKS() {
        // Prüft, ob ein Zertifikat existiert
        if (Start.getCertificatePath() == null) return;

        // Path vorbereiten
        String home = System.getProperty("user.home") + File.separator + "cinema" + File.separator;

        // ggf. alten PKCS12 und JKS KeyStore löschen
        File oldPkcs12 = new File(home + "cinema.pkcs12");
        File oldJks = new File(home + "cinema.jks");
        if (oldPkcs12.delete()) Start.log(0, "Es wurde eine alter PKCS12 Keystore gefunden & geloescht");
        if (oldJks.delete()) Start.log(0, "Es wurde eine alter JKS Keystore gefunden & geloescht");

        try {
            // PEM-Zertifikat in einen PKCS12-Keystore speichern
            Process p1 = Runtime.getRuntime().exec("openssl pkcs12 " +
                    "-export " +
                    "-in " + Start.getCertificatePath() + " " +
                    "-out " + home + "cinema.pkcs12 " +
                    "-passout pass:temppw " +
                    "-name " + Start.getHost());
            int exitVal1 = p1.waitFor();
            if (exitVal1 != 0) throw new Exception("Error PEM to PKCS12");

            // Zertifikat aus PKCS12-Keystore zum JKS-Keystore hinzufügen
            Process p2 = Runtime.getRuntime().exec("keytool -v " +
                    "-importkeystore " +
                    "-srckeystore " + home + "cinema.pkcs12 " +
                    "-keystore " + home + "cinema.jks " +
                    "-noprompt " +
                    "-storepass temppw " +
                    "-srcstorepass temppw " +
                    "-deststoretype JKS " +
                    "-srcalias " + Start.getHost() + " " +
                    "-destalias " + Start.getHost());
            int exitVal2 = p2.waitFor();
            if (exitVal2 != 0) throw new Exception("Error PKCS23 to JKS");

            // Log ausgeben
            Start.log(1, "Zertifikat fuer " + Start.getHost() + " erfolgreich eingelesen");
        } catch (Exception e) {
            Start.log(2, "Zertifikat fuer " + Start.getHost() + "konnte nicht verarbeitet werden (" + e.getMessage() + ")");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            new File(home + "cinema.pkcs12").delete();
        }
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
        importCertificateInJKS();
    }
}
