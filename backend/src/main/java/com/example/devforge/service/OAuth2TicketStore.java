package com.example.devforge.service;

import com.example.devforge.dto.AuthResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class OAuth2TicketStore {

  private static final Duration TICKET_TTL = Duration.ofSeconds(60);
  private static final Duration STATE_TTL = Duration.ofMinutes(10);
  private static final Duration SIGNUP_TTL = Duration.ofMinutes(15);

  private final Map<String, SignupEntry> signups = new ConcurrentHashMap<>();
  private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();
  private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

  private record SignupEntry(PendingSignup signup, Instant expiresAt) {}

  public record PendingSignup(
      UUID keycloakId,
      String email,
      String username,
      String firstName,
      String lastName,
      AuthResponse tokens) {}

  private record TicketEntry(AuthResponse response, Instant expiresAt) {}

  private record StateEntry(String codeVerifier, Instant expiresAt) {}

  public record PendingAuth(String state, String codeVerifier) {}

  public String storeTicket(AuthResponse response) {
    cleanExpired();
    String ticket = UUID.randomUUID().toString();
    tickets.put(ticket, new TicketEntry(response, Instant.now().plus(TICKET_TTL)));
    return ticket;
  }

  public Optional<AuthResponse> consumeTicket(String ticket) {
    if (ticket == null || ticket.isBlank()) return Optional.empty();
    TicketEntry entry = tickets.remove(ticket);
    if (entry == null || Instant.now().isAfter(entry.expiresAt())) return Optional.empty();
    return Optional.of(entry.response());
  }

  public PendingAuth createState() {
    cleanExpired();
    String state = UUID.randomUUID().toString();
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    states.put(state, new StateEntry(verifier, Instant.now().plus(STATE_TTL)));
    return new PendingAuth(state, verifier);
  }

  public Optional<String> consumeState(String state) {
    if (state == null || state.isBlank()) return Optional.empty();
    StateEntry entry = states.remove(state);
    if (entry == null || Instant.now().isAfter(entry.expiresAt())) return Optional.empty();
    return Optional.of(entry.codeVerifier());
  }

  public String storePendingSignup(PendingSignup signup) {
    cleanExpired();
    String token = UUID.randomUUID().toString();
    signups.put(token, new SignupEntry(signup, Instant.now().plus(SIGNUP_TTL)));
    return token;
  }

  public Optional<PendingSignup> getPendingSignup(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    SignupEntry e = signups.get(token);
    if (e == null || Instant.now().isAfter(e.expiresAt())) return Optional.empty();
    return Optional.of(e.signup());
  }

  public void consumePendingSignup(String token) {
    if (token != null) signups.remove(token);
  }

  private void cleanExpired() {
    Instant now = Instant.now();
    tickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    states.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    signups.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
  }
}
