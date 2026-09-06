-- Imported channel listings never went through PublishWorker, so they have no
-- price_guard row. Evaluation used to skip those listings entirely.
INSERT INTO price_guard (id, channel_listing_id, enabled, high_percent, low_percent, created_at, updated_at)
SELECT gen_random_uuid(),
       cl.id,
       TRUE,
       COALESCE((SELECT value::numeric FROM app_setting WHERE key = 'price_guard.high_percent'), 15),
       COALESCE((SELECT value::numeric FROM app_setting WHERE key = 'price_guard.low_percent'), 15),
       now(),
       now()
FROM channel_listing cl
WHERE cl.status = 'PUBLISHED'
  AND NOT EXISTS (
      SELECT 1 FROM price_guard pg WHERE pg.channel_listing_id = cl.id
  );
