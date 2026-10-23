-- Run against the selected Node web database AFTER applying database/web-schema.sql.
-- Adds the column used by mysql-store.cjs when upgrading an older installation.
-- Re-running the migration does not change an existing column or erase data.
SET @ttcs_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'web_meeting'
    AND column_name = 'participant_groups'
);
SET @ttcs_migration_sql = IF(
  @ttcs_column_exists = 0,
  'ALTER TABLE web_meeting ADD COLUMN participant_groups JSON NOT NULL DEFAULT (JSON_ARRAY())',
  'SELECT ''participant_groups already exists'' AS migration_status'
);
PREPARE ttcs_migration FROM @ttcs_migration_sql;
EXECUTE ttcs_migration;
DEALLOCATE PREPARE ttcs_migration;
