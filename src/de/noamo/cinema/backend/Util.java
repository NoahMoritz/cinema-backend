/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import de.noamo.cinema.backend.exceptions.BadRequestException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Klasse für verschiedene Hilfsmethoden
 *
 * @author Noah Holeterhoff
 * @version 24.09.2020
 * @since 15.04.2020
 */
public class Util {
    /**
     * Liest eine Textdatei und kopiert Sie in einen String
     *
     * @param pFile Die Datei
     * @return Ein String mit dem Inhalt der Textdatei. Line-Seperatoren sind Systemstandarts
     * @throws IOException Falls auf die Datei nicht zugegriffen werden konnte
     */
    public static String loadFileIntoString(File pFile) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(pFile)) {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine()).append(System.lineSeparator());
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Speichert einen String in einer Datei ab. Falls diese Datei bereits existiert, wird sie überschrieben. Sollte der
     * Pfad zu der Datei nicht existieren, wird er erstellt. Sollte ein Parameter null sein, wird die Methode direkt
     * abgebrochen.
     *
     * @param file    Zieldatei
     * @param content String, der in die Datei geschrieben werden soll
     * @throws IOException Falls ein Zugriffsfehler mit der Datei auftritt
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void saveStringToFile(File file, String content) throws IOException {
        if (content == null || file == null) return;
        if (file.exists()) file.delete();
        file.getParentFile().mkdirs();
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(content);
        }
    }

    public static Timestamp stringToSQLTimestamp(String pTime) throws BadRequestException {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
            Date parsedDate = dateFormat.parse(pTime);
            return new java.sql.Timestamp(parsedDate.getTime());
        } catch (Exception e) {
            throw new BadRequestException("Der Zeitstempel hat ein ungültiges Format");
        }
    }

    /**
     * Analysiert den Link aus einem YouTube-Video und findend die Video-Id heraus
     *
     * @param pLink Link zu dem YouTube Video (entweder youtu.be, ein link mit watch?v=, oder die ID)
     * @return Die Video ID
     */
    public static String youtubeLinkToVideoId(String pLink) {
        if (pLink.length() == 11) return pLink;
        else if (pLink.contains("youtu.be")) return pLink.split("/", 4)[3];
        else if (pLink.contains("watch?v=")) return pLink.split(Pattern.quote("watch?v="), 2)[1].substring(0, 11);
        else return "-";
    }
}

