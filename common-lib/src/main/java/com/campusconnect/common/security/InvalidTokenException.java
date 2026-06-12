package com.campusconnect.common.security;

/** Thrown when a presented JWT is missing-claims, malformed, tampered, wrong-signed, or expired. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
