package com.example.devforge.exception;


public class RegistrationRejectedException extends RuntimeException {
  public RegistrationRejectedException(String message) {
    super(message);
  }
}
