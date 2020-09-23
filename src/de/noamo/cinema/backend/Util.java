/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author Noah Holeterhoff
 * @version 31.07.2020
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
}
