package com.example.devforge.exception;

public class GithubNotConnectedException extends RuntimeException {
  public GithubNotConnectedException(String message) {
    super(message);
  }
}
