-- name: sql-all-users
SELECT * FROM reddit_user;

-- name: sql-add-user<!
INSERT INTO reddit_user (name)
VALUES (:name);

-- name: sql-user-by-name
SELECT * FROM reddit_user
WHERE name = :name;

-- name: sql-user-by-id
SELECT * FROM reddit_user
WHERE id = :id;

-- name: sql-update-user-total!
UPDATE reddit_user
SET total = total + :total
WHERE id = :user_to;

