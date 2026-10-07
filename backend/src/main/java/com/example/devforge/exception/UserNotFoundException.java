package com.example.devforge.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class UserNotFoundException extends ResourceNotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }

  public UserNotFoundException(UUID id) {
    super("User with ID " + id + " not found");
  }
}
