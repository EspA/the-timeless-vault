-- Inventory created as 75159 (no suffix) must share the 75159-1 catalog used by
-- item watches and market snapshots, or price guards never see a median.
-- Leave bases that also have a -2 (or other non-1) variant for a later pass.

UPDATE inventory_item i
SET catalog_item_id = target.id,
    updated_at = now()
FROM catalog_item src
JOIN catalog_item target
  ON lower(target.set_number) = lower(src.set_number || '-1')
WHERE i.catalog_item_id = src.id
  AND position('-' in src.set_number) = 0
  AND src.set_number <> 'UNKNOWN'
  AND NOT EXISTS (
      SELECT 1
      FROM catalog_item other
      WHERE other.set_number ~ ('^' || src.set_number || '-[0-9]+$')
        AND other.set_number <> src.set_number || '-1'
  );

UPDATE inventory_item i
SET catalog_item_id = target.id,
    updated_at = now()
FROM catalog_item target
WHERE i.sku = 'TTV-7261-A34E'
  AND target.set_number = '7261-1';

UPDATE inventory_item i
SET catalog_item_id = target.id,
    updated_at = now()
FROM catalog_item target
WHERE i.sku = 'TTV-7261-C98E'
  AND target.set_number = '7261-2';
