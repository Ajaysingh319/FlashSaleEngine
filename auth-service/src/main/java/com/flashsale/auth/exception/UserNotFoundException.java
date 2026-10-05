package com.flashsale.auth.exception;

/** The authenticated account no longer exists. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User not found");
    }
}
