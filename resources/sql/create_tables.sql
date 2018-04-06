-- name: sql-create-user-table!
CREATE TABLE reddit_user (
  id    IDENTITY PRIMARY KEY,
  name  VARCHAR(20) UNIQUE,
  total INT DEFAULT 0
);

-- name: sql-create-reply-table!
CREATE TABLE reddit_reply (
  user_to   BIGINT NOT NULL,
  user_from BIGINT NOT NULL,
  total     INT DEFAULT 0,
  FOREIGN KEY (user_to) REFERENCES reddit_user (id),
  FOREIGN KEY (user_from) REFERENCES reddit_user (id)
);

-- name: sql-add-replies-constraint!
ALTER TABLE reddit_reply
ADD CONSTRAINT unique_replies_restraint UNIQUE(user_to, user_from);

-- name: sql-create-post-table!
CREATE TABLE reddit_post (
  id        IDENTITY PRIMARY KEY,
  reddit_id VARCHAR(32) NOT NULL UNIQUE,
  active    BOOLEAN DEFAULT TRUE
);
