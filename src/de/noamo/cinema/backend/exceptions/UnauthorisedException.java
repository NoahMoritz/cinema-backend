/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Aktionen, bei denen der Nutzer unautorisiert ist
 *
 * @author Noah Hoelterhoff
 * @version 25.09.2020
 * @since 25.09.2020
 */
public class UnauthorisedException extends Exception {
    /**
     * Erstellt eine neue {@link UnauthorisedException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public UnauthorisedException(String pMessage) {
        super(pMessage);
    }
}
