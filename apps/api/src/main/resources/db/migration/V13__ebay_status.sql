ALTER TABLE channel_listing
    ADD COLUMN ebay_status VARCHAR(32);

UPDATE channel_listing
SET ebay_status = 'ACTIVE'
WHERE platform = 'EBAY'
  AND status = 'PUBLISHED';
