/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Werte, die ungültig sind.
 *
 * @author Noah Hoelterhoff
 * @version 21.09.2020
 * @since 15.09.2020
 */
public class InvalidException extends Exception {
    /**
     * Erstellt eine neue {@link InvalidException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public InvalidException(String pMessage) {
        super(pMessage);
    }
}
