package com.example.devforge.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExpiringCacheTest {

  private static final class StepClock extends Clock {
    private Instant now = Instant.parse("2026-10-30T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void entriesExpireAfterTtl() {
    StepClock clock = new StepClock();
    ExpiringCache<String, String> cache = new ExpiringCache<>(Duration.ofMinutes(1), 10, clock);
    cache.put("a", "1");

    clock.now = clock.now.plusSeconds(59);
    assertThat(cache.get("a")).contains("1");
    clock.now = clock.now.plusSeconds(1);
    assertThat(cache.get("a")).isEmpty();
  }

  @Test
  void leastRecentlyUsedEntryIsEvictedWhenFull() {
    ExpiringCache<String, String> cache =
        new ExpiringCache<>(Duration.ofMinutes(1), 2, new StepClock());
    cache.put("a", "1");
    cache.put("b", "2");
    cache.get("a");
    cache.put("c", "3");

    assertThat(cache.get("a")).contains("1");
    assertThat(cache.get("b")).isEmpty();
    assertThat(cache.get("c")).contains("3");
    assertThat(cache.size()).isEqualTo(2);
  }
}
