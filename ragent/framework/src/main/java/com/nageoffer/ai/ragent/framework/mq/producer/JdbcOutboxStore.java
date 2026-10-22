package com.nageoffer.ai.ragent.framework.mq.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox rows live in the business database. status: 0 pending, 1 sending, 2 sent.
 */
@Component
@RequiredArgsConstructor
public class JdbcOutboxStore implements OutboxStore {

    static final int PENDING = 0;
    static final int SENDING = 1;
    static final int SENT = 2;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(String topic, String messageKey, String bizDesc, String payload, String traceHeaders) {
        jdbcTemplate.update("""
                INSERT INTO t_mq_outbox (topic, message_key, biz_desc, payload, trace_headers, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, topic, messageKey, bizDesc, payload, traceHeaders, PENDING);
    }

    @Override
    public List<OutboxMessage> claim(int limit) {
        return jdbcTemplate.query("""
                WITH picked AS (
                    SELECT id FROM t_mq_outbox
                    WHERE status = ?
                       OR (status = ? AND locked_at < CURRENT_TIMESTAMP - INTERVAL '2 minutes')
                    ORDER BY id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE t_mq_outbox AS outbox
                SET status = ?, locked_at = CURRENT_TIMESTAMP, attempts = attempts + 1
                FROM picked
                WHERE outbox.id = picked.id
                RETURNING outbox.id, outbox.topic, outbox.message_key, outbox.payload, outbox.trace_headers
                """, (rs, rowNum) -> new OutboxMessage(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("message_key"),
                rs.getString("payload"),
                rs.getString("trace_headers")
        ), PENDING, SENDING, limit, SENDING);
    }

    @Override
    public void markSent(long id) {
        jdbcTemplate.update("""
                UPDATE t_mq_outbox
                SET status = ?, sent_at = CURRENT_TIMESTAMP, locked_at = NULL
                WHERE id = ?
                """, SENT, id);
    }

    @Override
    public void release(long id) {
        jdbcTemplate.update("""
                UPDATE t_mq_outbox
                SET status = ?, locked_at = NULL
                WHERE id = ?
                """, PENDING, id);
    }
}
