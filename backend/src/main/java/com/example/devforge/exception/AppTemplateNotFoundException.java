package com.example.devforge.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AppTemplateNotFoundException extends ResourceNotFoundException {
  public AppTemplateNotFoundException(String message) {
    super(message);
  }

  public AppTemplateNotFoundException(UUID id) {
    super("Template with ID " + id + " not found");
  }
}
