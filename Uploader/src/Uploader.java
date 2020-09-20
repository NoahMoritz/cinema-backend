/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.File;
import java.io.IOException;

/**
 * @author Noah Hoelterhoff
 * @version 20.09.2020
 * @since 20.09.2020
 */
public abstract class Uploader {
    private static String hostname, username, password, pathExternal, pathLocal;
    private static SSHClient ssh;

    private static void chnageService(String function) throws ConnectionException, TransportException {
        ssh.startSession().exec("sudo systemctl " + function + " cinema");
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

    public static void main(String[] args) throws IOException {
        // read args
        for (String s : args) {
            if (s.startsWith("HOSTNAME=")) hostname = s.substring(9);
            if (s.startsWith("USERNAME=")) username = s.substring(9);
            if (s.startsWith("PASSWORD=")) password = s.substring(9);
            if (s.startsWith("PATHE=")) pathExternal = s.substring(6);
            if (s.startsWith("PATHL=")) pathLocal = s.substring(6);
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
