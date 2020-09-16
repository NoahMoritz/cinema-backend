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
 * @version 16.09.2020
 * @since 15.09.2020
 */
abstract class Mail {
    static String emailAdresse, emailPasswort, emailHost;
    static int emailPort;

    static void sendActivationMail(final String pEmail, final String pName, final String pAktivierungsSchluessel) {
        Email mail = EmailBuilder.startingBlank().to(pName, pEmail).withSubject("Capitol Cinema - Aktivierungscode").
                withHTMLText(Resources.getActivationMail("http://" + Start.getHost() + ":" + Start.getRestApiPort() +
                        "/cinema-backend/activate/" + pAktivierungsSchluessel)).
                from("Capitol Cinema", emailAdresse).buildEmail();
        Mailer mailer = MailerBuilder.withSMTPServer(emailHost, emailPort, emailAdresse, emailPasswort).
                withTransportStrategy(TransportStrategy.SMTP_TLS).buildMailer();
        mailer.sendMail(mail);
    }
}
