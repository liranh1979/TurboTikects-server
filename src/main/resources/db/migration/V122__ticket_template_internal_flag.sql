-- Problem Management: a reusable "internal-only" flag on ticket_templates. Today ANY
-- authenticated user (including one with zero permissions) can create/view a ticket on ANY
-- template — there is no per-template permission check anywhere in TicketController. That's
-- fine for end-user-facing templates (Incident, Service Request), but Problem records (and
-- later Change records) are internal IT-process records that must never be creatable or
-- visible to a plain end user. Defaults to 0 so no existing template's behavior changes.
-- See V2/Problem Management/04-permissions-end-users.html for the full design writeup.
ALTER TABLE ticket_templates ADD COLUMN is_internal TINYINT(1) NOT NULL DEFAULT 0;
