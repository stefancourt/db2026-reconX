package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.DlqMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * TICKET-ADV136 — DLQ inbox. Lookup is by eventId, never by primary key:
 * the operator replaying a message knows the business event id from the logs,
 * not the row id.
 */
public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

    Optional<DlqMessage> findByEventId(String eventId);

    List<DlqMessage> findAllByOrderByFirstSeenDesc();
}
