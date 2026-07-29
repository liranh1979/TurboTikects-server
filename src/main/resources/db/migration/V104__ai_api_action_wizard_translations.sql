-- External API branch of the AI Workflow Builder rebuilt around a guided flow: Documentation ->
-- Select Endpoint -> Field Mapping -> Test -> Response Mapping -> Review & Save (6 steps total).
-- One continuing AI session (sessionType "api_action_builder") spans the whole visit. MCP tool
-- calls are unaffected — this migration only adds strings for the new/changed external_api steps.
INSERT IGNORE INTO dynamic_translations (lang_code, translation_key, translated_text, type) VALUES
    -- Step bar
    ('en', 'awb_step_documentation',        'Documentation',                                             'system'),
    ('en', 'awb_step_select_endpoint',      'Select Endpoint',                                            'system'),
    ('en', 'awb_step_test',                 'Test',                                                       'system'),
    ('en', 'awb_step_response_mapping',     'Response Mapping',                                           'system'),

    -- Step 1 — Documentation & Discover
    ('en', 'awb_discover_endpoints_btn',    'Discover Endpoints with AI',                                 'system'),
    ('en', 'awb_discovering_ellipsis',      'Discovering…',                                                'system'),
    ('en', 'awb_discover_failed',           'Could not discover endpoints — check that an AI provider is configured and active.', 'system'),
    ('en', 'awb_no_endpoints_found',        'No endpoints found in this documentation yet.',              'system'),

    -- Step 2 — Select Endpoint
    ('en', 'awb_select_endpoint_hint',      'Pick the endpoint this action should call.',                 'system'),
    ('en', 'awb_endpoint_recommended_badge','Recommended',                                                 'system'),
    ('en', 'awb_draft_skeleton_btn',        'Use This Endpoint',                                          'system'),
    ('en', 'awb_drafting_skeleton_ellipsis','Drafting…',                                                   'system'),
    ('en', 'awb_draft_skeleton_failed',     'Could not draft this call — try a different endpoint or AI provider/model.', 'system'),

    -- Step 3 — Field Mapping preview + shared AI agent panel
    ('en', 'awb_url_preview_label',         'Live URL Preview',                                           'system'),
    ('en', 'awb_body_preview_label',        'Request Body Preview',                                       'system'),
    ('en', 'awb_map_fields_btn',            'Map Fields with AI',                                         'system'),
    ('en', 'awb_mapping_fields_ellipsis',   'Mapping…',                                                    'system'),
    ('en', 'awb_map_fields_failed',         'Could not map fields — try a different AI provider/model.',  'system'),
    ('en', 'awb_agent_panel_label',         'ASK AI TO ADJUST THIS CALL',                                 'system'),
    ('en', 'awb_agent_instructions_placeholder', 'e.g. Use the customer''s work email instead of their personal one', 'system'),
    ('en', 'awb_agent_ask_btn',             'Ask AI',                                                     'system'),
    ('en', 'awb_agent_asking_ellipsis',     'Asking…',                                                     'system'),
    ('en', 'awb_agent_failed',              'Could not adjust this call.',                                'system'),
    ('en', 'awb_agent_proposed_heading',    'PROPOSED CHANGE',                                             'system'),
    ('en', 'awb_agent_apply_btn',           'Apply',                                                       'system'),

    -- Step 5 — Response Mapping (promoted from a Test-step panel to its own step)
    ('en', 'awb_response_mapping_hint',     'The AI proposes which parts of the real response are worth capturing — grounded in the actual response and what you originally asked for, not everything the API returns.', 'system'),
    ('en', 'awb_map_response_btn',          'Map Response with AI',                                       'system');
