SET @watch_progress_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.referential_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'watch_progress'
             AND constraint_name = 'fk_watch_progress_asset'),
    'ALTER TABLE watch_progress DROP FOREIGN KEY fk_watch_progress_asset',
    'SELECT 1'
);
PREPARE watch_progress_ddl FROM @watch_progress_ddl;
EXECUTE watch_progress_ddl;
DEALLOCATE PREPARE watch_progress_ddl;
