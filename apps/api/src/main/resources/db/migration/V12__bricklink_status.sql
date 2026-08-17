ALTER TABLE channel_listing
    ADD COLUMN bricklink_status VARCHAR(32);

UPDATE channel_listing
SET bricklink_status = 'ACTIVE'
WHERE platform = 'BRICKLINK'
  AND status = 'PUBLISHED';
