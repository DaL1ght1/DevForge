package com.example.devforge.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class WorkspaceNotFoundException extends ResourceNotFoundException {
  public WorkspaceNotFoundException(UUID id) {
    super("Workspace with ID " + id + " not found");
  }
}
