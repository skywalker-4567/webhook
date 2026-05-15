CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_num  BIGSERIAL NOT NULL,
    entity_type   VARCHAR NOT NULL,
    entity_id     VARCHAR NOT NULL,
    action        VARCHAR NOT NULL,
    actor         VARCHAR NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    previous_hash VARCHAR NOT NULL,
    current_hash  VARCHAR NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id UUID
);

CREATE INDEX ON audit_log(entity_id, entity_type);
CREATE INDEX ON audit_log(sequence_num);