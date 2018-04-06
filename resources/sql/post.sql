-- name: sql-get-post
SELECT * FROM reddit_post
WHERE reddit_id = :reddit_id;

-- name: sql-add-post<!
INSERT INTO reddit_post (reddit_id)
    VALUES (:reddit_id);

-- name: sql-remove-post!
DELETE FROM reddit_post
WHERE reddit_id = :reddit_id;
