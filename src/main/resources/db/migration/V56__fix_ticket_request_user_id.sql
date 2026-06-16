-- Backfill request_user_id from ticketData JSON for all existing tickets.
-- When admins created tickets via the UI, the actual requester was stored in
-- ticketData['request_user'] as a string, but request_user_id was always set
-- to the admin's own ID. This corrects the column to match the intended value.
UPDATE tickets t
JOIN users u ON u.red_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(t.ticket_data, '$.request_user')) AS UNSIGNED)
SET
    t.request_user_id = u.red_id,
    t.company_id      = u.company_id
WHERE
    JSON_EXTRACT(t.ticket_data, '$.request_user') IS NOT NULL
    AND JSON_UNQUOTE(JSON_EXTRACT(t.ticket_data, '$.request_user')) REGEXP '^[0-9]+$'
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(t.ticket_data, '$.request_user')) AS UNSIGNED) <> t.request_user_id;
