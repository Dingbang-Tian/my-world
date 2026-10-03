CREATE TABLE agent_run_checkpoint (
    run_id VARCHAR(128) PRIMARY KEY REFERENCES agent_run(id),
    session_version BIGINT NOT NULL,
    next_model_turn INT NOT NULL,
    exchange_json CLOB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
