/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Aktionen, bei denen die Anfrage einen Konflikt auslöst
 *
 * @author Noah Hoelterhoff
 * @version 12.10.2020
 * @since 12.10.2020
 */
public class ConflictException extends Exception {
    /**
     * Erstellt eine neue {@link ConflictException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public ConflictException(String pMessage) {
        super(pMessage);
    }
}
