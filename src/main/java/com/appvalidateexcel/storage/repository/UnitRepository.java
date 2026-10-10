package com.appvalidateexcel.storage.repository;

import com.appvalidateexcel.storage.model.UnitModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class UnitRepository {

    private final JdbcTemplate jdbcTemplate;

    public UnitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UnitModel> rowMapper = (rs, rowNum) -> UnitModel.builder()
            .id(rs.getString("id"))
            .code(rs.getString("code"))
            .name(rs.getString("name"))
            .normalizedName(rs.getString("normalized_name"))
            .level(rs.getInt("level"))
            .unitType(rs.getString("unit_type"))
            .parentId(rs.getString("parent_id"))
            .displayOrder(rs.getInt("display_order"))
            .aliasesJson(rs.getString("aliases_json"))
            .isPreset(rs.getInt("is_preset") == 1)
            .isActive(rs.getInt("is_active") == 1)
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    public List<UnitModel> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM units ORDER BY level ASC, display_order ASC, name ASC",
                rowMapper
        );
    }

    public List<UnitModel> findAllActive() {
        return jdbcTemplate.query(
                "SELECT * FROM units WHERE is_active = 1 ORDER BY level ASC, display_order ASC, name ASC",
                rowMapper
        );
    }

    public Optional<UnitModel> findById(String id) {
        List<UnitModel> list = jdbcTemplate.query(
                "SELECT * FROM units WHERE id = ?",
                rowMapper,
                id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<UnitModel> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        List<UnitModel> list = jdbcTemplate.query(
                "SELECT * FROM units WHERE code = ?",
                rowMapper,
                code.trim()
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<UnitModel> findByParentId(String parentId) {
        if (parentId == null) {
            return jdbcTemplate.query(
                    "SELECT * FROM units WHERE parent_id IS NULL ORDER BY display_order ASC, name ASC",
                    rowMapper
            );
        }
        return jdbcTemplate.query(
                "SELECT * FROM units WHERE parent_id = ? ORDER BY display_order ASC, name ASC",
                rowMapper,
                parentId
        );
    }

    public void insert(UnitModel unit) {
        String now = Instant.now().toString();
        if (unit.getCreatedAt() == null) unit.setCreatedAt(now);
        if (unit.getUpdatedAt() == null) unit.setUpdatedAt(now);
        if (unit.getIsActive() == null) unit.setIsActive(true);
        if (unit.getIsPreset() == null) unit.setIsPreset(false);
        if (unit.getDisplayOrder() == null) unit.setDisplayOrder(0);
        if (unit.getLevel() == null) unit.setLevel(4);

        jdbcTemplate.update(
                "INSERT INTO units (id, code, name, normalized_name, level, unit_type, parent_id, display_order, aliases_json, is_preset, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "code = excluded.code, " +
                "name = excluded.name, " +
                "normalized_name = excluded.normalized_name, " +
                "level = excluded.level, " +
                "unit_type = excluded.unit_type, " +
                "parent_id = excluded.parent_id, " +
                "display_order = excluded.display_order, " +
                "aliases_json = excluded.aliases_json, " +
                "is_preset = excluded.is_preset, " +
                "is_active = excluded.is_active, " +
                "updated_at = excluded.updated_at",
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getNormalizedName(),
                unit.getLevel(),
                unit.getUnitType(),
                unit.getParentId(),
                unit.getDisplayOrder(),
                unit.getAliasesJson(),
                unit.getIsPreset() ? 1 : 0,
                unit.getIsActive() ? 1 : 0,
                unit.getCreatedAt(),
                unit.getUpdatedAt()
        );
    }

    public void update(UnitModel unit) {
        unit.setUpdatedAt(Instant.now().toString());
        jdbcTemplate.update(
                "UPDATE units SET code = ?, name = ?, normalized_name = ?, level = ?, unit_type = ?, parent_id = ?, display_order = ?, aliases_json = ?, is_preset = ?, is_active = ?, updated_at = ? WHERE id = ?",
                unit.getCode(),
                unit.getName(),
                unit.getNormalizedName(),
                unit.getLevel(),
                unit.getUnitType(),
                unit.getParentId(),
                unit.getDisplayOrder(),
                unit.getAliasesJson(),
                (unit.getIsPreset() != null && unit.getIsPreset()) ? 1 : 0,
                (unit.getIsActive() != null && unit.getIsActive()) ? 1 : 0,
                unit.getUpdatedAt(),
                unit.getId()
        );
    }

    public void setActive(String id, boolean active) {
        jdbcTemplate.update(
                "UPDATE units SET is_active = ?, updated_at = ? WHERE id = ?",
                active ? 1 : 0,
                Instant.now().toString(),
                id
        );
    }

    public void move(String id, String newParentId) {
        jdbcTemplate.update(
                "UPDATE units SET parent_id = ?, updated_at = ? WHERE id = ?",
                newParentId,
                Instant.now().toString(),
                id
        );
    }

    public boolean hasRecords(String unitId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personnel_records WHERE unit_id = ?",
                Integer.class,
                unitId
        );
        return count != null && count > 0;
    }

    public boolean hasChildren(String unitId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM units WHERE parent_id = ?",
                Integer.class,
                unitId
        );
        return count != null && count > 0;
    }

    public boolean delete(String id) {
        if (hasRecords(id) || hasChildren(id)) {
            return false;
        }
        int rows = jdbcTemplate.update("DELETE FROM units WHERE id = ?", id);
        return rows > 0;
    }

    /**
     * Lấy toàn bộ mã ID con cháu (hậu duệ) của một đơn vị bằng Recursive CTE và tập visited chống lặp
    /**
     * Lấy toàn bộ ID của đơn vị và các đơn vị con cháu, sắp xếp theo level GIẢM DẦN (lá trước, gốc sau)
     * Đảm bảo an toàn tuyệt đối khi xóa không bị vi phạm foreign key ON DELETE RESTRICT
     */
    public List<String> getAllDescendantIds(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String sql = "WITH RECURSIVE sub_units(id, level, path) AS (" +
                "  SELECT id, level, ',' || id || ',' FROM units WHERE id = ? " +
                "  UNION ALL " +
                "  SELECT u.id, u.level, s.path || u.id || ',' " +
                "  FROM units u JOIN sub_units s ON u.parent_id = s.id " +
                "  WHERE instr(s.path, ',' || u.id || ',') = 0 " +
                ") SELECT id FROM sub_units ORDER BY level DESC";
        return jdbcTemplate.queryForList(sql, String.class, unitId);
    }

    /**
     * Kiểm tra xem việc gán parentId có tạo thành chu trình (vòng lặp cha-con) không
     */
    public boolean wouldCreateCycle(String unitId, String targetParentId) {
        if (unitId == null || targetParentId == null) return false;
        if (unitId.equals(targetParentId)) return true;

        List<String> descendants = getAllDescendantIds(unitId);
        return descendants.contains(targetParentId);
    }

    public int countRecordsInUnits(List<String> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) return 0;
        int total = 0;
        int chunkSize = 500;
        for (int i = 0; i < unitIds.size(); i += chunkSize) {
            List<String> chunk = unitIds.subList(i, Math.min(i + chunkSize, unitIds.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM personnel_records WHERE unit_id IN (" + placeholders + ")",
                    Integer.class,
                    chunk.toArray()
            );
            if (cnt != null) total += cnt;
        }
        return total;
    }

    public void batchInsert(List<UnitModel> units) {
        if (units == null || units.isEmpty()) return;
        String sql = "INSERT INTO units (" +
                "id, code, name, normalized_name, level, unit_type, parent_id, display_order, aliases_json, is_preset, is_active, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "code = excluded.code, " +
                "name = excluded.name, " +
                "normalized_name = excluded.normalized_name, " +
                "level = excluded.level, " +
                "unit_type = excluded.unit_type, " +
                "parent_id = excluded.parent_id, " +
                "display_order = excluded.display_order, " +
                "aliases_json = excluded.aliases_json, " +
                "is_preset = excluded.is_preset, " +
                "is_active = excluded.is_active, " +
                "updated_at = excluded.updated_at";

        String now = Instant.now().toString();
        List<Object[]> batchArgs = units.stream().map(unit -> new Object[]{
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getNormalizedName(),
                unit.getLevel() != null ? unit.getLevel() : 4,
                unit.getUnitType(),
                unit.getParentId(),
                unit.getDisplayOrder() != null ? unit.getDisplayOrder() : 0,
                unit.getAliasesJson(),
                (unit.getIsPreset() != null && unit.getIsPreset()) ? 1 : 0,
                (unit.getIsActive() != null && unit.getIsActive()) ? 1 : 0,
                unit.getCreatedAt() != null ? unit.getCreatedAt() : now,
                unit.getUpdatedAt() != null ? unit.getUpdatedAt() : now
        }).toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public int deleteBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int total = 0;
        for (String id : ids) {
            total += jdbcTemplate.update("DELETE FROM units WHERE id = ?", id);
        }
        return total;
    }

    public boolean existsByCode(String code, String excludeId) {
        if (code == null || code.trim().isEmpty()) return false;
        String sql;
        List<Object> params = new ArrayList<>();
        params.add(code.trim().toUpperCase());
        if (excludeId != null && !excludeId.trim().isEmpty()) {
            sql = "SELECT COUNT(*) FROM units WHERE UPPER(code) = ? AND id != ?";
            params.add(excludeId.trim());
        } else {
            sql = "SELECT COUNT(*) FROM units WHERE UPPER(code) = ?";
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params.toArray());
        return count != null && count > 0;
    }

    public boolean existsByNormalizedNameAndParentId(String normalizedName, String parentId, String excludeId) {
        if (normalizedName == null || normalizedName.trim().isEmpty()) return false;
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM units WHERE normalized_name = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalizedName.trim());

        if (parentId == null || parentId.trim().isEmpty()) {
            sql.append(" AND (parent_id IS NULL OR trim(parent_id) = '')");
        } else {
            sql.append(" AND parent_id = ?");
            params.add(parentId.trim());
        }

        if (excludeId != null && !excludeId.trim().isEmpty()) {
            sql.append(" AND id != ?");
            params.add(excludeId.trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null && count > 0;
    }

    public Optional<UnitModel> findByNormalizedNameAndParentId(String normalizedName, String parentId) {
        if (normalizedName == null || normalizedName.trim().isEmpty()) return Optional.empty();
        StringBuilder sql = new StringBuilder("SELECT * FROM units WHERE normalized_name = ?");
        List<Object> params = new ArrayList<>();
        params.add(normalizedName.trim());

        if (parentId == null || parentId.trim().isEmpty()) {
            sql.append(" AND (parent_id IS NULL OR trim(parent_id) = '')");
        } else {
            sql.append(" AND parent_id = ?");
            params.add(parentId.trim());
        }

        List<UnitModel> list = jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int getMaxSubtreeDepth(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) return 0;
        String sql = "WITH RECURSIVE sub(id, depth, path) AS (" +
                "  SELECT id, 1 AS depth, ',' || id || ',' FROM units WHERE id = ? " +
                "  UNION ALL " +
                "  SELECT u.id, s.depth + 1, s.path || u.id || ',' " +
                "  FROM units u JOIN sub s ON u.parent_id = s.id " +
                "  WHERE instr(s.path, ',' || u.id || ',') = 0 " +
                ") SELECT COALESCE(MAX(depth), 1) FROM sub";
        Integer maxDepth = jdbcTemplate.queryForObject(sql, Integer.class, unitId.trim());
        return maxDepth != null ? maxDepth : 1;
    }

    public void updateLevel(String unitId, int newLevel) {
        jdbcTemplate.update("UPDATE units SET level = ?, updated_at = ? WHERE id = ?",
                newLevel, Instant.now().toString(), unitId);
    }

    public void recordAudit(String unitId, String action, String oldJson, String newJson) {
        jdbcTemplate.update(
                "INSERT INTO unit_audit_log (id, unit_id, action, old_data_json, new_data_json, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), unitId, action, oldJson, newJson, Instant.now().toString()
        );
    }

    public List<Map<String, Object>> getAuditLogsByUnitId(String unitId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM unit_audit_log WHERE unit_id = ? ORDER BY created_at DESC",
                unitId
        );
    }

    public int reassignParent(String oldParentId, String newParentId) {
        if (oldParentId == null || newParentId == null) return 0;
        return jdbcTemplate.update(
                "UPDATE units SET parent_id = ?, updated_at = ? WHERE parent_id = ?",
                newParentId, Instant.now().toString(), oldParentId
        );
    }
}
