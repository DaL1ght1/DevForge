package com.example.devforge.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GithubWebhookDeliveryRepository {

  private final JdbcTemplate jdbc;

  public boolean markReceived(String deliveryId, String event) {
    return jdbc.update(
            "INSERT INTO github_webhook_delivery (delivery_id, event) VALUES (?, ?)"
                + " ON CONFLICT (delivery_id) DO NOTHING",
            deliveryId,
            event)
        == 1;
  }
}
