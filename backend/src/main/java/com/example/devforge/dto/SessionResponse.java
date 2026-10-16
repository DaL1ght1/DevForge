package com.example.devforge.dto;

import java.io.Serializable;

public record SessionResponse(UserResponseDto user, long expiresIn) implements Serializable {

  public static SessionResponse from(AuthResponse a) {
    return new SessionResponse(a.user(), a.expiresIn());
  }
}
