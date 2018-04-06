-- name: sql-all-replies
SELECT * FROM reddit_reply;

-- name: sql-has-reply-relationship
SELECT 1
FROM reddit_reply
WHERE user_to = :user_to AND
      user_from = :user_from;

-- name: sql-create-reply-relationship!
INSERT INTO reddit_reply (user_to, user_from)
VALUES (:user_to, :user_from);

-- name: sql-update-reply-totals!
UPDATE reddit_reply
SET total = total + :total
WHERE user_to = :user_to AND
      user_from = :user_from;

-- name: sql-reply-from-totals
SELECT rr.user_from, rr.total
FROM reddit_reply AS rr
  JOIN reddit_user AS ru
    ON rr.user_to = ru.id
WHERE ru.name = :user_to;

-- name: sql-reply-to-totals
SELECT rr.user_to, rr.total
FROM reddit_reply AS rr
  JOIN reddit_user AS ru
    ON rr.user_from = ru.id
WHERE ru.name = :user_from;
