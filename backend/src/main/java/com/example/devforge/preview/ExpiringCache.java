package com.example.devforge.preview;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ExpiringCache<K, V> {

  private record Entry<V>(V value, Instant expiresAt) {}

  private final Duration ttl;
  private final int maxEntries;
  private final Clock clock;
  private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

  public ExpiringCache(Duration ttl, int maxEntries, Clock clock) {
    this.ttl = ttl;
    this.maxEntries = maxEntries;
    this.clock = clock;
  }

  public synchronized Optional<V> get(K key) {
    Entry<V> entry = entries.get(key);
    if (entry == null) return Optional.empty();
    if (!entry.expiresAt().isAfter(clock.instant())) {
      entries.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  public synchronized void put(K key, V value) {
    Instant now = clock.instant();
    entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
    entries.put(key, new Entry<>(value, now.plus(ttl)));
    Iterator<Map.Entry<K, Entry<V>>> oldestFirst = entries.entrySet().iterator();
    while (entries.size() > maxEntries && oldestFirst.hasNext()) {
      oldestFirst.next();
      oldestFirst.remove();
    }
  }

  public synchronized int size() {
    return entries.size();
  }
}
