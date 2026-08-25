CREATE TABLE api_call_daily (
    day DATE NOT NULL,
    platform VARCHAR(32) NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day, platform)
);
