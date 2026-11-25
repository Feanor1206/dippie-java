INSERT INTO users(id, username, email, password, full_name, role, enabled)
VALUES (1, 'admin', 'admin@dippie.test', 'unused', 'Quản trị', 'ROLE_ADMIN', TRUE),
       (2, 'viewer', 'viewer@dippie.test', 'unused', 'Người xem', 'ROLE_USER', TRUE),
       (3, 'locked', 'locked@dippie.test', 'unused', 'Tài khoản khóa', 'ROLE_ADMIN', FALSE);

INSERT INTO catalog_series(id, title, original_title, synopsis)
VALUES ('s1', 'Bộ Một', 'First Series', 'Tóm tắt bộ một'), ('s2', 'Bộ Hai', NULL, NULL);

INSERT INTO catalog_seasons(id, series_id, number, title, synopsis)
VALUES ('m1', 's1', 1, 'Mùa đầu', 'Tóm tắt mùa'), ('m2', 's1', 2, 'Mùa sau', NULL);

INSERT INTO catalog_episodes(id, series_id, season_id, number, title, kind, synopsis, duration_ms, raw_json)
VALUES ('e1', 's1', 'm1', 1, 'Bí mật mùa hè', 'regular', 'Tóm tắt tập', 125000,
        '{"external_ids":[{"value":"abcdefghijk","namespace":"youtube_watch"},{"namespace":"youtube_playback","value":"lmnopqrstuv"}]}'),
       ('e2', 's1', 'm2', 1, 'Hành trình', 'regular', NULL, 190000, NULL),
       ('e3', 's2', NULL, NULL, 'Tập đặc biệt', 'special', NULL, NULL,
        '{"external_ids":[{"namespace":"youtube_video_id","value":"ABCDEFGHIJK"}]}');

INSERT INTO catalog_media_assets(id, storage_provider, storage_key, relative_path, drive_file_id, enabled)
VALUES ('a1', 'drive', 'drive-key', 'phim.mp4', 'abc_123', TRUE),
       ('a2', 'drive', 'ignored-key', 'phim2.mp4', 'disabled', FALSE);

INSERT INTO catalog_episode_assets(episode_id, media_asset_id) VALUES ('e1', 'a1'), ('e1', 'a2');

INSERT INTO movies(id, title, rating)
VALUES ('s1', 'Bộ Một', 0), ('e1', 'Bí mật mùa hè', 0), ('e2', 'Hành trình', 0),
       ('e3', 'Tập đặc biệt', 0), ('legacy', 'Phim cũ', 0);

INSERT INTO movie_ratings(id, user_id, movie_id, rating)
VALUES (1, 2, 'e1', 10), (2, 1, 'e1', 7), (3, 2, 's1', 7), (4, 2, 'e2', 9),
       (5, 2, 'legacy', 4);

INSERT INTO watch_progress(user_id, episode_id, asset_id, position_ms)
VALUES (1, 'e1', 'a1', 1350), (2, 'e1', 'youtube:lmnopqrstuv', 2000),
       (1, 'e2', 'a3', 3000), (2, 'e3', 'a4', 0);
