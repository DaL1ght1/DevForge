package com.example.devforge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AppTemplateVersionNotFoundException extends ResourceNotFoundException {
    public AppTemplateVersionNotFoundException(String message) {
        super(message);
    }
    public AppTemplateVersionNotFoundException(UUID id) {
        super("Template version with ID " + id + " not found");
    }

}
