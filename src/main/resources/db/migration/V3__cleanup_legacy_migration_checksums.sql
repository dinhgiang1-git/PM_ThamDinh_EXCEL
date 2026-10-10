-- ============================================================
-- V3: LÀM SẠCH CHECKSUM LEGACY VÀ DỌN DẸP BẢN GHI AUDIT TRÙNG LẶP
-- ============================================================

-- Bước 3.1: Đặt các checksum legacy không hợp lệ (không phải đúng 64 ký tự hex) thành NULL
UPDATE migration_audit
SET checksum = NULL
WHERE checksum IS NOT NULL
  AND (length(checksum) != 64 OR checksum GLOB '*[^0-9a-fA-F]*');

-- Bước 3.2: Dọn dẹp các bản ghi trùng lặp checksum SHA-256 thành công (chỉ giữ bản ghi đại diện có id nhỏ nhất)
UPDATE migration_audit
SET checksum = NULL
WHERE status = 'SUCCESS'
  AND checksum IS NOT NULL
  AND id NOT IN (
      SELECT MIN(id)
      FROM migration_audit
      WHERE status = 'SUCCESS' AND checksum IS NOT NULL
      GROUP BY checksum
  );

-- Bước 3.3: Tái tạo partial unique index để đảm bảo tính duy nhất an toàn tuyệt đối
DROP INDEX IF EXISTS idx_migration_audit_checksum_success;

CREATE UNIQUE INDEX IF NOT EXISTS idx_migration_audit_checksum_success
    ON migration_audit(checksum)
    WHERE status = 'SUCCESS' AND checksum IS NOT NULL;
