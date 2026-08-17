ALTER TABLE channel_listing
    ADD COLUMN shopify_status VARCHAR(32);

UPDATE channel_listing
SET shopify_status = 'ACTIVE'
WHERE platform = 'SHOPIFY'
  AND status = 'PUBLISHED';
