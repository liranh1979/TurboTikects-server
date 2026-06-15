-- Soft-delete flag for users.
-- Deleting a user that owns tickets (FK fk_ticket_request_user) is no longer a hard delete:
-- instead the row is flagged is_deleted=1 and hidden from all user lists/pickers.
-- Creating a new user with the same user_name as a soft-deleted row reactivates that row.
ALTER TABLE users
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
