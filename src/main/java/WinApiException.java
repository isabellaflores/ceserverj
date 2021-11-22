/*
 * This file is part of ceserverj by Isabella Flores
 *
 * Copyright © 2021 Isabella Flores
 *
 * It is licensed to you under the terms of the
 * Apache License, Version 2.0. Please see the
 * file LICENSE for more information.
 */

public class WinApiException extends Throwable {

    public WinApiException(String message, int errno) {
        super(message + " (errno=" + errno + ")");
    }

}
