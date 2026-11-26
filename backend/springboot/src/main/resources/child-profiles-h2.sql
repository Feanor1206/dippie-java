ALTER TABLE IF EXISTS watchlist_items DROP CONSTRAINT IF EXISTS uq_user_movie_watchlist;
ALTER TABLE IF EXISTS favorite_items DROP CONSTRAINT IF EXISTS uq_user_movie_favorite;
ALTER TABLE IF EXISTS movie_ratings DROP CONSTRAINT IF EXISTS uq_user_movie_rating;
ALTER TABLE IF EXISTS watch_progress DROP CONSTRAINT IF EXISTS uq_watch_progress_user_episode;
