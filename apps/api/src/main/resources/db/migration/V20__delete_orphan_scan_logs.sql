DELETE FROM market_scan_log l
WHERE NOT EXISTS (
    SELECT 1
    FROM set_watch w
    WHERE w.catalog_item_id = l.catalog_item_id
       OR lower(w.set_number) = lower(l.set_number)
);
