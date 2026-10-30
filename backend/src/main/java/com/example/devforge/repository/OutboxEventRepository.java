package com.example.devforge.repository;

import com.example.devforge.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      value =
          "SELECT * FROM outbox_event WHERE sent_at IS NULL ORDER BY created_at"
              + " LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> lockUnsent(@Param("limit") int limit);
}
