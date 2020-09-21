/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

package de.noamo.cinema.backend.exceptions;

/**
 * Fehlerklasse für Parameter, die unerwartete Werte enthalten.
 *
 * @author Noah Hoelterhoff
 * @version 15.09.2020
 * @since 15.09.2020
 */
public class ParameterException extends Exception {
    /**
     * Erstellt eine neue {@link ParameterException} mit konfigurierbarer Fehlermeldung.
     *
     * @param pMessage Fehlermeldung (genauen Grund angeben)
     */
    public ParameterException(String pMessage) {
        super(pMessage);
    }
}
