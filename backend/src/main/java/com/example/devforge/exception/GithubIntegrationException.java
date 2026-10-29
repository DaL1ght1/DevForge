package com.example.devforge.exception;

public class GithubIntegrationException extends RuntimeException {
  public GithubIntegrationException(String message) {
    super(message);
  }

  public GithubIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
