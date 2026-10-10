-- ====================================================================
-- BỘ QUỐC PHÒNG - HỆ THỐNG QUẢN LÝ DỮ LIỆU CHẾ ĐỘ CHÍNH SÁCH
-- Lược đồ cơ sở dữ liệu SQLite ban đầu (V1)
-- ====================================================================

-- 1. BẢNG ĐƠN VỊ (Cơ cấu 4 cấp)
CREATE TABLE IF NOT EXISTS units (
    id TEXT PRIMARY KEY,
    code TEXT,
    name TEXT NOT NULL,
    normalized_name TEXT,
    level INTEGER NOT NULL DEFAULT 4,
    unit_type TEXT,
    parent_id TEXT REFERENCES units(id) ON DELETE RESTRICT,
    display_order INTEGER DEFAULT 0,
    aliases_json TEXT,
    is_preset INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_units_parent ON units(parent_id, display_order);
CREATE INDEX IF NOT EXISTS idx_units_code ON units(code);
CREATE INDEX IF NOT EXISTS idx_units_normalized_name ON units(normalized_name);
CREATE INDEX IF NOT EXISTS idx_units_level ON units(level);

-- 2. BẢNG LẦN NHẬP FILE (Imports)
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

CREATE INDEX IF NOT EXISTS idx_imports_hash ON imports(file_hash);
CREATE INDEX IF NOT EXISTS idx_imports_status ON imports(status);

-- 3. BẢNG HỒ SƠ QUÂN NHÂN / ĐỐI TƯỢNG (Personnel Records)
CREATE TABLE IF NOT EXISTS personnel_records (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL, -- 'manual', 'excel', 'validated'
    import_id TEXT REFERENCES imports(id) ON DELETE SET NULL,
    source_row INTEGER,
    unit_id TEXT REFERENCES units(id) ON DELETE RESTRICT,
    category_code TEXT DEFAULT 'UNKNOWN', -- 'SQ', 'QNCN', 'CNVCQP', 'LDHD', 'UNKNOWN'
    sheet_type TEXT NOT NULL, -- 'I.1', 'I.2', 'I.3', 'I.5'
    policy_code TEXT, -- 'ND178', 'ND177', etc.
    classification_source TEXT DEFAULT 'INFERRED', -- 'EXPLICIT_MARKER', 'UNIT_ALIAS', 'FILENAME', 'INFERRED', 'MANUAL'
    classification_confidence REAL DEFAULT 1.0,
    full_name TEXT NOT NULL,
    birth_date TEXT,
    rank TEXT,
    position TEXT,
    enlistment_date TEXT,
    merger_date TEXT,
    retirement_date TEXT,
    monthly_salary REAL DEFAULT 0,
    actual_total REAL DEFAULT 0,
    calculated_total REAL DEFAULT 0,
    difference REAL DEFAULT 0,
    has_errors INTEGER DEFAULT 0,
    raw_columns_json TEXT,
    input_json TEXT,
    result_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_records_unit ON personnel_records(unit_id);
CREATE INDEX IF NOT EXISTS idx_records_source ON personnel_records(source, created_at);
CREATE INDEX IF NOT EXISTS idx_records_sheet_cat ON personnel_records(sheet_type, category_code);
CREATE INDEX IF NOT EXISTS idx_records_import ON personnel_records(import_id);
CREATE INDEX IF NOT EXISTS idx_records_errors ON personnel_records(has_errors);
CREATE INDEX IF NOT EXISTS idx_records_unit_sheet_cat ON personnel_records(unit_id, sheet_type, category_code);
CREATE INDEX IF NOT EXISTS idx_records_full_name ON personnel_records(full_name);

-- 4. BẢNG CHI TIẾT LỖI THẨM ĐỊNH (Validation Errors)
CREATE TABLE IF NOT EXISTS validation_errors (
    id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL REFERENCES personnel_records(id) ON DELETE CASCADE,
    column_number INTEGER,
    error_code TEXT,
    message TEXT NOT NULL,
    actual_value TEXT,
    expected_value TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_errors_record ON validation_errors(record_id);

-- 5. BẢNG CẤU HÌNH HỆ THỐNG
CREATE TABLE IF NOT EXISTS application_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- 6. BẢNG LỊCH SỬ CHUYỂN ĐỔI LOCALSTORAGE
CREATE TABLE IF NOT EXISTS migration_audit (
    id TEXT PRIMARY KEY,
    migration_id TEXT NOT NULL,
    source_type TEXT NOT NULL,
    record_count INTEGER DEFAULT 0,
    unit_count INTEGER DEFAULT 0,
    checksum TEXT,
    status TEXT NOT NULL,
    details_json TEXT,
    created_at TEXT NOT NULL
);

-- 7. BẢNG LỊCH SỬ NÂNG CẤP LƯỢC ĐỒ (Schema Version)
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    script_name TEXT NOT NULL,
    checksum TEXT,
    installed_at TEXT NOT NULL,
    execution_time_ms INTEGER,
    success INTEGER NOT NULL
);
