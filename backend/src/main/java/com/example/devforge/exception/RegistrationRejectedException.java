package com.example.devforge.exception;

/**
 * Keycloak refused the registration (e.g. password policy). Message is safe to show to the user.
 */
public class RegistrationRejectedException extends RuntimeException {
  public RegistrationRejectedException(String message) {
    super(message);
  }
}
