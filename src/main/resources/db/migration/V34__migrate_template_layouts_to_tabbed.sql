-- Wrap any old flat-array layouts into the new tabbed format.
-- Old format: [{"fieldKey":"title",...}, ...]
-- New format: {"tabs":[{"tabKey":"main","label":"Main","fields":[...]}]}
UPDATE ticket_template_versions
SET layout = JSON_OBJECT(
    'tabs', JSON_ARRAY(
        JSON_OBJECT(
            'tabKey', 'main',
            'label',  'Main',
            'fields', layout
        )
    )
)
WHERE JSON_TYPE(layout) = 'ARRAY';
