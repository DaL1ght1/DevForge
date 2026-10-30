package com.example.devforge.service.implementation;

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
public class GithubLinkStateStore {

  private static final Duration STATE_TTL = Duration.ofMinutes(15);

  private final Map<String, Entry> states = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public record PendingLink(UUID keycloakId, UUID workspaceId) {}

  private record Entry(PendingLink link, Instant expiresAt) {}

  public String create(UUID keycloakId, UUID workspaceId) {
    Instant now = Instant.now();
    states.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    states.put(state, new Entry(new PendingLink(keycloakId, workspaceId), now.plus(STATE_TTL)));
    return state;
  }

  public Optional<PendingLink> consume(String state) {
    if (state == null || state.isBlank()) return Optional.empty();
    Entry entry = states.remove(state);
    if (entry == null || Instant.now().isAfter(entry.expiresAt())) return Optional.empty();
    return Optional.of(entry.link());
  }
}
