-- ============================================================
-- V5: BỔ SUNG CỘT QUẢN LÝ NHẬP LÔ VÀ ĐƠN VỊ FILE (SECTION 13)
-- ============================================================

CREATE TABLE IF NOT EXISTS imports (
    id TEXT PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_hash TEXT,
    stored_file_path TEXT,
    status TEXT NOT NULL,
    total_rows INTEGER DEFAULT 0,
    accepted_rows INTEGER DEFAULT 0,
    rejected_rows INTEGER DEFAULT 0,
    error_summary TEXT,
    created_at TEXT NOT NULL,
    completed_at TEXT
);

ALTER TABLE imports ADD COLUMN parent_unit_id TEXT REFERENCES units(id);
ALTER TABLE imports ADD COLUMN file_unit_id TEXT REFERENCES units(id);
ALTER TABLE imports ADD COLUMN original_file_name TEXT;
ALTER TABLE imports ADD COLUMN display_unit_name TEXT;
ALTER TABLE imports ADD COLUMN parser_mode TEXT DEFAULT 'STANDARD_CTC';
ALTER TABLE imports ADD COLUMN template_signature TEXT;

CREATE INDEX IF NOT EXISTS idx_imports_parent_unit ON imports(parent_unit_id);
CREATE INDEX IF NOT EXISTS idx_imports_file_unit ON imports(file_unit_id);
