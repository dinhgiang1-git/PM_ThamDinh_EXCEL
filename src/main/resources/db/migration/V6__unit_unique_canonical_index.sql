-- ============================================================
-- V6: CHỈ MỤC DUY NHẤT CHỐNG TRÙNG ĐƠN VỊ CÙNG CHA (CANONICAL KEY)
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_units_parent_norm_name_unique
ON units(COALESCE(parent_id, ''), normalized_name);
