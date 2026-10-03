CREATE TABLE agent_session (
    id VARCHAR(128) PRIMARY KEY,
    owner_key VARCHAR(256) NOT NULL,
    app_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    state_json CLOB NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_agent_session_owner ON agent_session(owner_key, app_id, updated_at);

CREATE TABLE agent_message (
    id VARCHAR(256) NOT NULL,
    session_id VARCHAR(128) NOT NULL REFERENCES agent_session(id),
    seq BIGINT NOT NULL,
    run_id VARCHAR(128),
    role VARCHAR(32) NOT NULL,
    body_json CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(session_id, seq),
    UNIQUE(session_id, id)
);

CREATE TABLE agent_run (
    id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL REFERENCES agent_session(id),
    owner_key VARCHAR(256) NOT NULL,
    app_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(256) NOT NULL,
    parent_run_id VARCHAR(128),
    root_run_id VARCHAR(128),
    resumed_from_run_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    prompt_hash VARCHAR(128) NOT NULL,
    request_json CLOB NOT NULL,
    result_json CLOB,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(owner_key, app_id, request_id)
);
CREATE INDEX idx_agent_run_status ON agent_run(status, started_at);
CREATE INDEX idx_agent_run_session ON agent_run(session_id, started_at);

CREATE TABLE agent_run_event (
    run_id VARCHAR(128) NOT NULL REFERENCES agent_run(id),
    seq BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload_json CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(run_id, seq)
);

CREATE TABLE agent_tool_execution (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL REFERENCES agent_run(id),
    call_id VARCHAR(256) NOT NULL,
    name VARCHAR(128) NOT NULL,
    args_json CLOB NOT NULL,
    args_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json CLOB,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(run_id, call_id)
);

CREATE TABLE agent_memory_summary (
    id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL REFERENCES agent_session(id),
    through_message_seq BIGINT NOT NULL,
    summary CLOB NOT NULL,
    source_hash VARCHAR(64),
    template_hash VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(session_id, through_message_seq)
);

CREATE TABLE agent_plan (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL REFERENCES agent_run(id),
    name VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE agent_plan_step (
    plan_id VARCHAR(128) NOT NULL REFERENCES agent_plan(id),
    step_index INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result CLOB,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(plan_id, step_index)
);

CREATE TABLE codegen_artifact (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL REFERENCES agent_run(id),
    path VARCHAR(2048) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    before_hash VARCHAR(64),
    after_hash VARCHAR(64),
    backup_reference VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
