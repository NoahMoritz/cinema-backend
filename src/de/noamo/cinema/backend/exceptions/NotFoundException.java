/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Eingaben, zu denen nichts gefunden wurde
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 15.09.2020
 */
public class NotFoundException extends Exception {
    /**
     * Erstellt eine neue {@link NotFoundException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public NotFoundException(String pMessage) {
        super(pMessage);
    }
}
