/*
 *  Copyright (c) NOAMO Tech - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Noah Moritz Hölterhoff <noah.hoelterhoff@gmail.com>, 20.5.2020
 */

package de.noamo.cinema.backend;


import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

/**
 * Diese Klasse ist für alle Interaktionen mit Emails (senden, empfangen, etc.) zuständig
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 15.09.2020
 */
abstract class Mail {
    static String emailAdresse, emailPasswort, emailHost;
    static int emailPort;

    /**
     * Sendet eine Aktivierungs-Email an eine Person
     *
     * @param pEmail                  Email-Adresse der Person
     * @param pName                   Name der Person
     * @param pAktivierungsSchluessel Einzigartiger Schlüssel, mit dem das Konto aktiviert werden kann
     */
    static void sendActivationMail(final String pEmail, final String pName, final String pAktivierungsSchluessel) {
        // HTML vorbereiten
        String htmlText = Resources.getActivationMail((Start.getCertificatePath() == null ? "http://" : "https://")
                + Start.getHost() + ":" + Start.getRestApiPort() + "/activate/" + pAktivierungsSchluessel);

        sendMail(pName, pEmail, "DHBW Kino - Aktivierungscode", htmlText);
    }

    static void sendEmailChangeMail(final String pName, final String pOldEmail, final String pNewEmail, final int pOldEmailKey, final int pNewEmailKey) {
        String htmlText1 = Resources.getChangeEmailMail(pName, pOldEmailKey, true);
        sendMail(pName, pOldEmail, "DHBW Kino - Email ändern", htmlText1);

        String htmlText2 = Resources.getChangeEmailMail(pName, pNewEmailKey, false);
        sendMail(pName, pNewEmail, "DHBW Kino - Email ändern", htmlText2);
    }

    static void sendTicketMail(final String pName, final String pEmail, final String pCode) {
        String htmlText = Resources.getTicketMail(pName, pCode);
        sendMail(pName, pEmail, "DHBW Kino - Ihre Bestellung", htmlText);
    }

    /**
     * Sendet eine verschlüsselte Email über den angegeben SMTP-Server.
     *
     * @param pName     Name der Person, die die EMail empfangen soll
     * @param pEmail    Die Email-Adresse des Empfängers
     * @param pSubject  Der Betreff der Email
     * @param pHtmlText Der Inhalt der Email (als HTML-Code)
     */
    private static void sendMail(String pName, String pEmail, String pSubject, String pHtmlText) {
        new Thread(() -> {
            // Email vorbereiten
            Email mail = EmailBuilder.startingBlank().to(pName, pEmail).withSubject(pSubject).
                    withHTMLText(pHtmlText).from("DHBW Kino", emailAdresse).buildEmail();

            // Mailer vorbereiten
            Mailer mailer = MailerBuilder.withSMTPServer(emailHost, emailPort, emailAdresse, emailPasswort).
                    withTransportStrategy(TransportStrategy.SMTP_TLS).buildMailer();

            // Email send
            mailer.sendMail(mail);
        }).start();
    }
}
