ALTER TABLE watch_rule
    ADD COLUMN ebay_search_query VARCHAR(500),
    ADD COLUMN ebay_feedback_min INTEGER NOT NULL DEFAULT 1;
