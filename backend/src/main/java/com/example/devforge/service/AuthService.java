package com.example.devforge.service;

import com.example.devforge.dto.AuthResponse;
import com.example.devforge.dto.LoginRequest;
import com.example.devforge.dto.RefreshTokenRequest;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(RefreshTokenRequest request);

  void logout(String refreshToken, Jwt jwt);
}
