package com.example.devforge.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class AppServiceNotFoundException extends ResourceNotFoundException {
  public AppServiceNotFoundException(String message) {
    super(message);
  }

  public AppServiceNotFoundException(UUID id) {
    super("Service with ID " + id + " not found");
  }
}
