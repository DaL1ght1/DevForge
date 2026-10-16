package com.example.devforge.service;

import com.example.devforge.dto.*;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(RefreshTokenRequest request);

  void logout(String refreshToken, Jwt jwt);

  String buildAuthorizationUrl(String provider);

  String handleOAuth2Callback(String code, String state, String error, String errorDescription);

  AuthResponse exchangeTicket(OAuth2TicketRequest request);

  SignupPendingResponse getPendingSignup(OAuth2TicketRequest request);

  AuthResponse completeSignup(CompleteSignupRequest request);
}
