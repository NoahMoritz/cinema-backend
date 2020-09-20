/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.util;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.File;
import java.io.IOException;

/**
 * Die Klasse kümmert sich um das Hochladen von Änderungen und Neustarten eines Services. Für alle verpflichtenden
 * Argumente siehe {@link Uploader#main(String[])}.<br> Der Service wird über {@code systemctl} neu gestartet. Stellen
 * Sie daher sicher, dass der Service damit läuft.
 *
 * @author Noah Hoelterhoff
 * @version 20.09.2020
 * @since 20.09.2020
 */
public abstract class Uploader {
    private static String hostname, username, password, pathExternal, pathLocal, serviceName;
    private static SSHClient ssh;

    private static void chnageService(String function) throws ConnectionException, TransportException {
        ssh.startSession().exec("sudo systemctl " + function + " " + serviceName);
        System.out.println("Service: " + function);
    }

    private static void connect() throws IOException {
        // Connect
        ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(hostname);
        System.out.println("Connected");

        // Authorize
        ssh.authPassword(username, password);
        System.out.println("Authorized");
    }

    /**
     * Stoppt den Service auf dem Remote Host, lädt die Änderung hoch und startet ihn dann wieder.<br> Folgende
     * Argumente existieren:<br>
     * <table border="1">
     *  <tr><td>Befehl</td><td>Beispiel</td><td>Erklärung</td></tr>
     *  <tr><td>HOSTNAME</td><td>HOSTNAME=test.de</td><td>Host des Linux-Systems</td></tr>
     *  <tr><td>USERNAME</td><td>USERNAME=root</td><td>Username für das System</td></tr>
     *  <tr><td>PASSWORD</td><td>PASSWORD=testpw</td><td>Passwort für das Benutezrkonto</td></tr>
     *  <tr><td>PATHE</td><td>PATHE=/test/</td><td>Pfad auf dem Server, an dem die Datei abgelegt werden soll</td></tr>
     *  <tr><td>PATHL</td><td>PATHL=C:/test/test.jar</td><td>Pfad auf dem eigenen System, an dem die Datei liegt</td></tr>
     *  <tr><td>SERVICE</td><td>SERVICE=testservice</td><td>Name des Services auf dem Server</td></tr>
     * </table>
     *
     * @param args Die oben erklärten Argumente
     * @throws IOException Falls ein Fehler bei der Verbindung oder mit den Dateien auftritt
     */
    public static void main(String[] args) throws IOException {
        // read args
        for (String s : args) {
            if (s.startsWith("HOSTNAME=")) hostname = s.substring(9);
            else if (s.startsWith("USERNAME=")) username = s.substring(9);
            else if (s.startsWith("PASSWORD=")) password = s.substring(9);
            else if (s.startsWith("PATHE=")) pathExternal = s.substring(6);
            else if (s.startsWith("PATHL=")) pathLocal = s.substring(6);
            else if (s.startsWith("SERVICE=")) serviceName = s.substring(8);
        }
        if (hostname == null || username == null || password == null || pathLocal == null || pathExternal == null || serviceName == null) {
            System.out.println("HOSTNAME, USERNAME, PASSWORD, PATHE, PATHL and SERVICE are mandatory arguments!");
            System.exit(1);
        }

        // execute
        try {
            connect();
            chnageService("stop");
            upload();
        } finally {
            chnageService("start");
        }
    }

    private static void upload() throws IOException {
        SFTPClient sftp = ssh.newSFTPClient();
        System.out.println("Starting upload...");
        sftp.put(new FileSystemFile(pathLocal), (pathExternal.endsWith("/") ? pathExternal +
                new File(pathLocal).getName() : pathExternal + "/" + new File(pathLocal).getName()));
        System.out.println("Upload finished");
        sftp.close();
    }
}
