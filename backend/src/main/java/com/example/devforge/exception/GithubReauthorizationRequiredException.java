package com.example.devforge.exception;

public class GithubReauthorizationRequiredException extends RuntimeException {
  public GithubReauthorizationRequiredException(String message) {
    super(message);
  }
}
