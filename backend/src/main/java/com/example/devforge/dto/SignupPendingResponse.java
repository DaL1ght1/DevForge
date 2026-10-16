package com.example.devforge.dto;

import java.io.Serializable;

public record SignupPendingResponse(
    String email, String username, String firstName, String lastName) implements Serializable {}
