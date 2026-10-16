package com.example.devforge.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record OAuth2TicketRequest(@NotBlank(message = "Ticket is required") String ticket)
    implements Serializable {}
