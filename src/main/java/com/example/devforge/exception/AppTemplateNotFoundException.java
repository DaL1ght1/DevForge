package com.example.devforge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AppTemplateNotFoundException extends ResourceNotFoundException {
    public AppTemplateNotFoundException(String message) {
        super(message);
    }
    public AppTemplateNotFoundException(UUID id) {
        super("Template with ID " + id + " not found");
    }

}
