package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.DlqMessage;
import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.security.JwtAuthenticationFilter;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-ADV136 — replay endpoint contract: ADMIN only, one event at a time,
 * and a dry run that touches nothing.
 *
 * <p>SecurityConfig and JwtAuthenticationFilter are imported explicitly —
 * without them the slice falls back to Boot's default chain and the RBAC
 * assertion would be vacuous.
 */
@WebMvcTest(DlqAdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class DlqAdminControllerTest {

    private static final String EVENT_ID = "11111111-2222-3333-4444-555555555555";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DlqMessageRepository repo;
    @MockitoBean private TradeEventProducer producer;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    private static DlqMessage quarantined() {
        return DlqMessage.builder()
                .id(7L)
                .eventId(EVENT_ID)
                .tradeRef("TRD-20260315-0001")
                .originalTopic("trade-events")
                .partition(1)
                .offset(42L)
                .payload("{\"eventId\":\"" + EVENT_ID + "\",\"tradeRef\":\"TRD-20260315-0001\","
                        + "\"eventType\":\"TRADE_CREATED\",\"timestamp\":\"2026-03-15T10:15:30Z\","
                        + "\"actor\":\"trader@reconx.local\",\"before\":null,\"after\":null}")
                .reason("boom")
                .firstSeen(Instant.parse("2026-03-15T10:15:31Z"))
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replay_dryRun_previewsWithoutPublishingOrDeleting() throws Exception {
        when(repo.findByEventId(EVENT_ID)).thenReturn(Optional.of(quarantined()));

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", EVENT_ID)
                        .param("dryRun", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.wouldReplayTo").value("trade-events"))
                .andExpect(jsonPath("$.tradeRef").value("TRD-20260315-0001"));

        // A dry run that publishes is not a dry run.
        verifyNoInteractions(producer);
        verify(repo).findByEventId(EVENT_ID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replay_republishesOriginalEventAndClearsTheRow() throws Exception {
        DlqMessage msg = quarantined();
        when(repo.findByEventId(EVENT_ID)).thenReturn(Optional.of(msg));

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", EVENT_ID)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.topic").value("trade-events"));

        ArgumentCaptor<TradeEvent> published = ArgumentCaptor.forClass(TradeEvent.class);
        verify(producer).publish(published.capture());
        assertThat(published.getValue().eventId()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(published.getValue().tradeRef()).isEqualTo("TRD-20260315-0001");
        assertThat(published.getValue().eventType()).isEqualTo(TradeEvent.EventType.TRADE_CREATED);

        verify(repo).delete(msg);
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void replay_asNonAdmin_isForbidden() throws Exception {
        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", EVENT_ID)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(producer);
        verify(repo, org.mockito.Mockito.never()).findByEventId(any());
    }
}
