package com.example.devforge.exception;

public class InvalidWebhookSignatureException extends RuntimeException {
  public InvalidWebhookSignatureException(String message) {
    super(message);
  }
}
