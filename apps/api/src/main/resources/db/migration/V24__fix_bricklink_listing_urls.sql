UPDATE channel_listing
SET live_url = bricklink_photo_upload_url
WHERE platform = 'BRICKLINK'
  AND live_url LIKE '%store.page?p=#%'
  AND coalesce(bricklink_photo_upload_url, '') <> '';

UPDATE channel_listing
SET live_url = 'https://www.bricklink.com/v2/inventory_detail.page?invID='
    || regexp_replace(live_url, '.*id=', '')
WHERE platform = 'BRICKLINK'
  AND live_url LIKE '%store.page?p=#/item?id=%';

UPDATE alert_event
SET url = replace(
        url,
        'https://www.bricklink.com/v2/store.page?p=#/item?id=',
        'https://www.bricklink.com/v2/inventory_detail.page?invID='
    )
WHERE url LIKE '%store.page?p=#/item?id=%';
