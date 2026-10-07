package com.example.devforge.exception;

/** Keycloak is unreachable or returned an unexpected error. */
public class IdentityProviderException extends RuntimeException {
  public IdentityProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
