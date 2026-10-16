package com.example.devforge.controller;

import com.example.devforge.dto.*;
import com.example.devforge.exception.InvalidCredentialsException;
import com.example.devforge.security.AuthCookies;
import com.example.devforge.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user login and token refresh")
public class AuthController {

  private final AuthService authService;
  private final AuthCookies cookies;

  @SecurityRequirements
  @Operation(
      summary = "Authenticate user",
      description = "Authenticates user against Keycloak and returns JWT tokens")
  @PostMapping("/login")
  public SessionResponse login(@RequestBody @Valid LoginRequest request, HttpServletResponse res) {
    AuthResponse a = authService.login(request);
    cookies.write(res, a);
    return SessionResponse.from(a);
  }

  @SecurityRequirements
  @Operation(
      summary = "Refresh access token",
      description = "Exchanges a valid refresh token for a new access token")
  @PostMapping("/refresh")
  public SessionResponse refresh(
      @CookieValue(name = AuthCookies.REFRESH, required = false) String rt,
      HttpServletResponse res) {
    if (rt == null || rt.isBlank()) throw new InvalidCredentialsException("No session");
    try {
      AuthResponse a = authService.refresh(new RefreshTokenRequest(rt));
      cookies.write(res, a);
      return SessionResponse.from(a);
    } catch (InvalidCredentialsException e) {
      cookies.clear(res);
      throw e;
    }
  }

  @SecurityRequirements
  @Operation(
      summary = "Logout user",
      description = "Logs out the user and invalidates the refresh token")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @CookieValue(name = AuthCookies.REFRESH, required = false) String rt,
      HttpServletResponse res) {
    authService.logout(rt, null);
    cookies.clear(res);
  }

  @SecurityRequirements
  @Operation(summary = "Start OAuth2 login with a social provider")
  @GetMapping("/oauth2/authorize/{provider}")
  public ResponseEntity<Void> authorize(@PathVariable String provider) {
    String url = authService.buildAuthorizationUrl(provider);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  @SecurityRequirements
  @Operation(summary = "OAuth2 callback (called by Keycloak)")
  @GetMapping("/oauth2/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(name = "error_description", required = false) String errorDescription) {
    String redirect = authService.handleOAuth2Callback(code, state, error, errorDescription);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
  }

  @PostMapping("/oauth2/ticket")
  public SessionResponse exchangeTicket(
      @RequestBody @Valid OAuth2TicketRequest request, HttpServletResponse res) {
    AuthResponse a = authService.exchangeTicket(request);
    cookies.write(res, a);
    return SessionResponse.from(a);
  }

  @SecurityRequirements
  @PostMapping("/oauth2/signup/pending")
  public SignupPendingResponse pendingSignup(@RequestBody @Valid OAuth2TicketRequest request) {
    return authService.getPendingSignup(request);
  }

  @SecurityRequirements
  @PostMapping("/oauth2/signup/complete")
  public SessionResponse completeSignup(
      @RequestBody @Valid CompleteSignupRequest request, HttpServletResponse res) {
    AuthResponse a = authService.completeSignup(request);
    cookies.write(res, a);
    return SessionResponse.from(a);
  }
}
