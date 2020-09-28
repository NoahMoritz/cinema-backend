/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Aktionen, bei denen der Nutzer seinen Account nicht aktivier hat
 *
 * @author Noah Hoelterhoff
 * @version 28.09.2020
 * @since 28.09.2020
 */
public class NotActiveException extends Exception {
    /**
     * Erstellt eine neue {@link NotActiveException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public NotActiveException(String pMessage) {
        super(pMessage);
    }
}
