-- Seeds a built-in "Getting Started" KB category + article walking new users/agents/admins
-- through TurboTikects' main menus. Text-only for now — screenshot placeholders are marked
-- inline so anyone with KB edit rights can drop real screenshots in later via the KB editor's
-- image upload, without touching this migration. A normal, admin-editable/deletable article
-- (no is_system/undeletable protection requested for this one — that's an alert_types-specific
-- concept, not extended to kb_articles here).

INSERT IGNORE INTO kb_categories (name, icon, display_order) VALUES
    ('Getting Started', '🚀', 0);

INSERT IGNORE INTO kb_articles (title, body, category_id, tags, visibility, author_id)
SELECT
    'How to Use TurboTikects — Main Menus Overview',
    '<p>Welcome to TurboTikects! This guide walks through the main menus you will see, depending on whether you are an end user submitting requests or an agent/admin managing them.</p>

<h3>If you are an end user (the Portal)</h3>
<p>When you log in as an end user, you land on the <strong>Portal</strong> home screen. The top bar is always visible and has four buttons:</p>
<ul>
  <li><strong>Chat with AI</strong> — talk through an issue with the AI assistant before opening a ticket; it can often resolve simple problems on the spot.</li>
  <li><strong>My Tickets</strong> — see every ticket you have submitted and its current status.</li>
  <li><strong>My Tasks</strong> — any workflow steps assigned to you that need your input.</li>
  <li><strong>Knowledge Base</strong> — search self-service articles like this one before submitting a new ticket.</li>
</ul>
<div style="border:1px dashed #94a3b8;border-radius:8px;padding:14px 16px;color:#64748b;font-size:13px;margin:10px 0">📷 Add screenshot here: Portal top bar with the four nav buttons</div>
<p>From the home screen, use <strong>New Ticket</strong> to open a request. As you type a title, the Knowledge Base will automatically suggest articles that might solve your problem before you even submit.</p>
<div style="border:1px dashed #94a3b8;border-radius:8px;padding:14px 16px;color:#64748b;font-size:13px;margin:10px 0">📷 Add screenshot here: New Ticket form with the "these articles might help" suggestion panel</div>

<h3>If you are an agent or admin (the main app)</h3>
<p>Agents and admins see a sidebar on the left instead of the portal top bar, with:</p>
<ul>
  <li><strong>Dashboard</strong> — an overview of ticket volume, SLA performance, and AI-generated insights.</li>
  <li><strong>Service Desk</strong> — the full ticket queue: filter, search, bulk-update, and open any ticket.</li>
  <li><strong>My Tasks</strong> — workflow items assigned to you.</li>
  <li><strong>Settings</strong> — everything administrators configure (see below).</li>
</ul>
<div style="border:1px dashed #94a3b8;border-radius:8px;padding:14px 16px;color:#64748b;font-size:13px;margin:10px 0">📷 Add screenshot here: Main sidebar navigation (Dashboard / Service Desk / My Tasks / Settings)</div>
<p>The top bar shows real-time notifications, the active AI provider status (for super admins), and quick access to your profile and logout.</p>

<h3>The Settings area</h3>
<p>Settings is organized into cards, each gated by permission — you will only see the ones you have access to:</p>
<ul>
  <li><strong>Fields</strong> — custom fields for tickets, users, groups, workflow items, labels, and alert types.</li>
  <li><strong>Users &amp; Groups</strong> — manage accounts, permissions, LDAP/Azure sync.</li>
  <li><strong>Tickets &amp; Templates</strong> — build the forms and workflows tickets are created from.</li>
  <li><strong>Knowledge Base</strong> — manage articles and categories (you are looking at one right now!).</li>
  <li><strong>SLA</strong>, <strong>Recurring Tickets</strong>, <strong>Announcements</strong>, <strong>Acceleration Rules</strong> — automation and operational tooling.</li>
  <li><strong>AI Manager</strong>, <strong>Email</strong>, <strong>Notifications</strong> — integrations.</li>
</ul>
<div style="border:1px dashed #94a3b8;border-radius:8px;padding:14px 16px;color:#64748b;font-size:13px;margin:10px 0">📷 Add screenshot here: Settings overview grid of cards</div>
<p>That is the whole map. If you get lost, this article is always one click away from the Knowledge Base menu.</p>',
    (SELECT id FROM kb_categories WHERE name = 'Getting Started' LIMIT 1),
    JSON_ARRAY('getting-started', 'navigation', 'main-menu'),
    'public',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM kb_articles WHERE title = 'How to Use TurboTikects — Main Menus Overview'
);
