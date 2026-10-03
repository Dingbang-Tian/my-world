CREATE TABLE agent_session (
    id VARCHAR(128) PRIMARY KEY,
    owner_key VARCHAR(256) NOT NULL,
    app_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    state_json LONGTEXT NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_agent_session_owner (owner_key, app_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_message (
    id VARCHAR(256) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL,
    run_id VARCHAR(128) NULL,
    role VARCHAR(32) NOT NULL,
    body_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id, seq),
    UNIQUE KEY uk_agent_message_id (session_id, id),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_run (
    id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    owner_key VARCHAR(256) NOT NULL,
    app_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(256) NOT NULL,
    parent_run_id VARCHAR(128) NULL,
    root_run_id VARCHAR(128) NULL,
    resumed_from_run_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    prompt_hash VARCHAR(128) NOT NULL,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    UNIQUE KEY uk_agent_run_request (owner_key, app_id, request_id),
    KEY idx_agent_run_status (status, started_at),
    KEY idx_agent_run_session (session_id, started_at),
    CONSTRAINT fk_agent_run_session FOREIGN KEY (session_id) REFERENCES agent_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_run_event (
    run_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id, seq),
    CONSTRAINT fk_agent_event_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_tool_execution (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    call_id VARCHAR(256) NOT NULL,
    name VARCHAR(128) NOT NULL,
    args_json LONGTEXT NOT NULL,
    args_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json LONGTEXT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    UNIQUE KEY uk_agent_tool_call (run_id, call_id),
    CONSTRAINT fk_agent_tool_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_memory_summary (
    id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    through_message_seq BIGINT NOT NULL,
    summary LONGTEXT NOT NULL,
    source_hash VARCHAR(64) NULL,
    template_hash VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_summary_position (session_id, through_message_seq),
    CONSTRAINT fk_agent_summary_session FOREIGN KEY (session_id) REFERENCES agent_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_plan (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    CONSTRAINT fk_agent_plan_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_plan_step (
    plan_id VARCHAR(128) NOT NULL,
    step_index INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result LONGTEXT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (plan_id, step_index),
    CONSTRAINT fk_agent_step_plan FOREIGN KEY (plan_id) REFERENCES agent_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE codegen_artifact (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    before_hash VARCHAR(64) NULL,
    after_hash VARCHAR(64) NULL,
    backup_reference VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_codegen_artifact_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_run_checkpoint (
    run_id VARCHAR(128) PRIMARY KEY,
    session_version BIGINT NOT NULL,
    next_model_turn INT NOT NULL,
    exchange_json LONGTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_agent_checkpoint_run FOREIGN KEY (run_id) REFERENCES agent_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
