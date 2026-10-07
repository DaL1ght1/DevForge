package com.example.devforge.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AppTemplateVersionNotFoundException extends ResourceNotFoundException {
  public AppTemplateVersionNotFoundException(String message) {
    super(message);
  }

  public AppTemplateVersionNotFoundException(UUID id) {
    super("Template version with ID " + id + " not found");
  }
}
