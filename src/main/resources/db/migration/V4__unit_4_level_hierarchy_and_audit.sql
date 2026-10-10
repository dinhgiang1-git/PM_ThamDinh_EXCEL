-- ============================================================
-- V4: NÂNG CẤP QUẢN LÝ ĐƠN VỊ 4 CẤP VÀ LỊCH SỬ THAY ĐỔI
-- ============================================================

-- 1. Bảng lịch sử thay đổi đơn vị (Unit Audit Log)
CREATE TABLE IF NOT EXISTS unit_audit_log (
    id TEXT PRIMARY KEY,
    unit_id TEXT NOT NULL,
    action TEXT NOT NULL,
    old_data_json TEXT,
    new_data_json TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_unit_audit_unit_id ON unit_audit_log(unit_id);
CREATE INDEX IF NOT EXISTS idx_unit_audit_created ON unit_audit_log(created_at);

-- 2. Chỉ mục tối ưu hóa tìm kiếm đơn vị theo đơn vị cha và tên chuẩn hóa
CREATE INDEX IF NOT EXISTS idx_units_parent_norm_name
    ON units(parent_id, normalized_name);

-- 3. Chuẩn hóa normalized_name cho các bản ghi đơn vị hiện có nếu còn thiếu
UPDATE units
SET normalized_name = lower(trim(name))
WHERE (normalized_name IS NULL OR trim(normalized_name) = '') AND name IS NOT NULL;

-- 4. Chuẩn hóa cấp (level) cho cây đơn vị phân cấp
-- Cấp 1: Các đơn vị gốc không có parent_id
UPDATE units
SET level = 1
WHERE (parent_id IS NULL OR trim(parent_id) = '');

-- Cấp 2: Các đơn vị con trực tiếp của đơn vị gốc cấp 1
UPDATE units
SET level = 2
WHERE parent_id IN (SELECT id FROM units WHERE parent_id IS NULL OR trim(parent_id) = '');

-- Cấp 3: Các đơn vị con của cấp 2
UPDATE units
SET level = 3
WHERE parent_id IN (SELECT id FROM units WHERE level = 2);

-- Cấp 4: Các đơn vị con của cấp 3 (hoặc các cấp sâu hơn nếu có)
UPDATE units
SET level = 4
WHERE parent_id IN (SELECT id FROM units WHERE level = 3);
