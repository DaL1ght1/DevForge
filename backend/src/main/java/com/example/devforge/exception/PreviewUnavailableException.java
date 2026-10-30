package com.example.devforge.exception;

public class PreviewUnavailableException extends RuntimeException {
  public PreviewUnavailableException(String message) {
    super(message);
  }

  public PreviewUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
