package com.example.devforge.exception;

public class InvalidTemplateManifestException extends IllegalArgumentException {
  public InvalidTemplateManifestException(String message) {
    super("Template manifest is invalid: " + message);
  }
}
