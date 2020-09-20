/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
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

    public static void main(String[] args) throws IOException {
        for (String s : args) {
            if (s.startsWith("HOSTNAME=")) hostname = s.substring(9);
            if (s.startsWith("USERNAME=")) username = s.substring(9);
            if (s.startsWith("PASSWORD=")) password = s.substring(9);
            if (s.startsWith("PATHE=")) pathExternal = s.substring(6);
            if (s.startsWith("PATHL=")) pathLocal = s.substring(6);
        }
        upload();
    }

    public static void upload() throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(hostname);
        System.out.println("Connected");
        try {
            ssh.authPassword(username, password);
            System.out.println("Authorized");
            SFTPClient sftp = ssh.newSFTPClient();
            System.out.println("Starting upload...");
            sftp.put(new FileSystemFile(pathLocal), (pathExternal.endsWith("/") ? pathExternal +
                    new File(pathLocal).getName() : pathExternal + "/" + new File(pathLocal).getName()));
            System.out.println("Upload finished");
            sftp.close();
        } finally {
            ssh.disconnect();
        }
    }
}
