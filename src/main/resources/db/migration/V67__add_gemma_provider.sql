-- Gemma 4 (local Ollama) built-in LLM provider.
-- api_key is no longer mandatory: local providers (Ollama) need no key.
ALTER TABLE ai_settings MODIFY COLUMN api_key TEXT NULL;
ALTER TABLE ai_settings ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT 0;

-- Fresh install (no existing rows) -> Gemma becomes the active default.
-- Upgrade (rows already exist, one likely active) -> Gemma is only added to
-- the list, leaving whatever is currently active untouched.
INSERT INTO ai_settings (provider_name, api_key, model_name, is_active, is_system, created_at, updated_at)
SELECT 'gemma', NULL, 'gemma4:e2b',
       CASE WHEN EXISTS (SELECT 1 FROM ai_settings) THEN 0 ELSE 1 END,
       1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_settings WHERE provider_name = 'gemma');
