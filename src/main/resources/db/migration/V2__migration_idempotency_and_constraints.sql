-- ============================================================
-- V2: TĂNG CƯỜNG RÀNG BUỘC VÀ TÍNH IDEMPOTENT CHO LƯU TRỮ VÀ DI CHUYỂN DỮ LIỆU
-- ============================================================

-- 1. Tạo index chống trùng lặp di chuyển dữ liệu (idempotency by content checksum)
CREATE UNIQUE INDEX IF NOT EXISTS idx_migration_audit_checksum_success
    ON migration_audit(checksum)
    WHERE status = 'SUCCESS' AND checksum IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_migration_audit_mig_id
    ON migration_audit(migration_id);

-- 2. Đảm bảo ràng buộc toàn vẹn và tối ưu truy vấn danh mục đơn vị
CREATE INDEX IF NOT EXISTS idx_units_normalized_name
    ON units(normalized_name);
