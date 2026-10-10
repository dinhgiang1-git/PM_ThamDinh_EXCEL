package com.appvalidateexcel.storage.repository;

import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class PersonnelRecordRepository {

    private final JdbcTemplate jdbcTemplate;
    private final UnitRepository unitRepository;

    public PersonnelRecordRepository(JdbcTemplate jdbcTemplate, UnitRepository unitRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.unitRepository = unitRepository;
    }

    private final RowMapper<PersonnelRecordModel> rowMapper = (rs, rowNum) -> PersonnelRecordModel.builder()
            .id(rs.getString("id"))
            .source(rs.getString("source"))
            .importId(rs.getString("import_id"))
            .sourceRow(rs.getObject("source_row") != null ? rs.getInt("source_row") : null)
            .unitId(rs.getString("unit_id"))
            .categoryCode(rs.getString("category_code"))
            .sheetType(rs.getString("sheet_type"))
            .policyCode(rs.getString("policy_code"))
            .classificationSource(rs.getString("classification_source"))
            .classificationConfidence(rs.getObject("classification_confidence") != null ? rs.getDouble("classification_confidence") : null)
            .fullName(rs.getString("full_name"))
            .birthDate(rs.getString("birth_date"))
            .rank(rs.getString("rank"))
            .position(rs.getString("position"))
            .enlistmentDate(rs.getString("enlistment_date"))
            .mergerDate(rs.getString("merger_date"))
            .retirementDate(rs.getString("retirement_date"))
            .monthlySalary(rs.getObject("monthly_salary") != null ? rs.getDouble("monthly_salary") : 0.0)
            .actualTotal(rs.getObject("actual_total") != null ? rs.getDouble("actual_total") : 0.0)
            .calculatedTotal(rs.getObject("calculated_total") != null ? rs.getDouble("calculated_total") : 0.0)
            .difference(rs.getObject("difference") != null ? rs.getDouble("difference") : 0.0)
            .hasErrors(rs.getInt("has_errors") == 1)
            .rawColumnsJson(rs.getString("raw_columns_json"))
            .inputJson(rs.getString("input_json"))
            .resultJson(rs.getString("result_json"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .unitName(rs.getString("unit_name"))
            .build();

    public void save(PersonnelRecordModel record) {
        saveBatch(Collections.singletonList(record));
    }

    public void saveBatch(List<PersonnelRecordModel> records) {
        if (records == null || records.isEmpty()) return;

        String sql = "INSERT INTO personnel_records (" +
                "id, source, import_id, source_row, unit_id, category_code, sheet_type, policy_code, " +
                "classification_source, classification_confidence, full_name, birth_date, rank, position, " +
                "enlistment_date, merger_date, retirement_date, monthly_salary, actual_total, calculated_total, " +
                "difference, has_errors, raw_columns_json, input_json, result_json, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "source = excluded.source, " +
                "import_id = excluded.import_id, " +
                "source_row = excluded.source_row, " +
                "unit_id = excluded.unit_id, " +
                "category_code = excluded.category_code, " +
                "sheet_type = excluded.sheet_type, " +
                "policy_code = excluded.policy_code, " +
                "classification_source = excluded.classification_source, " +
                "classification_confidence = excluded.classification_confidence, " +
                "full_name = excluded.full_name, " +
                "birth_date = excluded.birth_date, " +
                "rank = excluded.rank, " +
                "position = excluded.position, " +
                "enlistment_date = excluded.enlistment_date, " +
                "merger_date = excluded.merger_date, " +
                "retirement_date = excluded.retirement_date, " +
                "monthly_salary = excluded.monthly_salary, " +
                "actual_total = excluded.actual_total, " +
                "calculated_total = excluded.calculated_total, " +
                "difference = excluded.difference, " +
                "has_errors = excluded.has_errors, " +
                "raw_columns_json = excluded.raw_columns_json, " +
                "input_json = excluded.input_json, " +
                "result_json = excluded.result_json, " +
                "updated_at = excluded.updated_at";

        String now = Instant.now().toString();

        List<Object[]> batchArgs = records.stream().map(r -> {
            String recordId = r.getId() != null ? r.getId() : UUID.randomUUID().toString();
            String createdAt = r.getCreatedAt() != null ? r.getCreatedAt() : now;
            String updatedAt = now;
            return new Object[]{
                    recordId,
                    r.getSource() != null ? r.getSource() : "manual",
                    r.getImportId(),
                    r.getSourceRow(),
                    r.getUnitId(),
                    r.getCategoryCode() != null ? r.getCategoryCode() : "UNKNOWN",
                    r.getSheetType() != null ? r.getSheetType() : "I.1",
                    r.getPolicyCode(),
                    r.getClassificationSource() != null ? r.getClassificationSource() : "INFERRED",
                    r.getClassificationConfidence() != null ? r.getClassificationConfidence() : 1.0,
                    r.getFullName() != null ? r.getFullName() : "",
                    r.getBirthDate(),
                    r.getRank(),
                    r.getPosition(),
                    r.getEnlistmentDate(),
                    r.getMergerDate(),
                    r.getRetirementDate(),
                    r.getMonthlySalary() != null ? r.getMonthlySalary() : 0.0,
                    r.getActualTotal() != null ? r.getActualTotal() : 0.0,
                    r.getCalculatedTotal() != null ? r.getCalculatedTotal() : 0.0,
                    r.getDifference() != null ? r.getDifference() : 0.0,
                    (r.getHasErrors() != null && r.getHasErrors()) ? 1 : 0,
                    r.getRawColumnsJson(),
                    r.getInputJson(),
                    r.getResultJson(),
                    createdAt,
                    updatedAt
            };
        }).toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public Optional<PersonnelRecordModel> findById(String id) {
        String sql = "SELECT r.*, u.name AS unit_name " +
                     "FROM personnel_records r " +
                     "LEFT JOIN units u ON r.unit_id = u.id " +
                     "WHERE r.id = ?";
        List<PersonnelRecordModel> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM personnel_records WHERE id = ?", id);
    }

    public int deleteBySource(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Tham số 'source' là bắt buộc để ngăn việc vô tình xóa toàn bộ hồ sơ.");
        }
        return jdbcTemplate.update("DELETE FROM personnel_records WHERE source = ?", source.trim());
    }

    private static class QueryBuilder {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
    }

    private QueryBuilder buildFilter(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        QueryBuilder qb = new QueryBuilder();

        if (source != null && !source.trim().isEmpty()) {
            qb.where.append(" AND r.source = ? ");
            qb.params.add(source.trim());
        }

        if (unitId != null && !unitId.trim().isEmpty()) {
            if ("branch".equalsIgnoreCase(scope)) {
                List<String> descendantIds = unitRepository.getAllDescendantIds(unitId.trim());
                if (!descendantIds.isEmpty()) {
                    String placeholders = String.join(",", Collections.nCopies(descendantIds.size(), "?"));
                    qb.where.append(" AND r.unit_id IN (").append(placeholders).append(") ");
                    qb.params.addAll(descendantIds);
                } else {
                    qb.where.append(" AND r.unit_id = ? ");
                    qb.params.add(unitId.trim());
                }
            } else {
                qb.where.append(" AND r.unit_id = ? ");
                qb.params.add(unitId.trim());
            }
        }

        if (sheetType != null && !sheetType.trim().isEmpty()) {
            qb.where.append(" AND r.sheet_type = ? ");
            qb.params.add(sheetType.trim());
        } else {
            qb.where.append(" AND UPPER(COALESCE(r.sheet_type,'')) NOT IN ('I.5') ");
        }

        if (categoryCode != null && !categoryCode.trim().isEmpty()) {
            qb.where.append(" AND r.category_code = ? ");
            qb.params.add(categoryCode.trim());
        }

        if (status != null && !status.trim().isEmpty()) {
            String s = status.trim().toLowerCase();
            if ("error".equals(s) || "err".equals(s)) {
                qb.where.append(" AND (r.has_errors = 1 OR abs(r.difference) > 1000) ");
            } else if ("valid".equals(s) || "ok".equals(s)) {
                qb.where.append(" AND (r.has_errors = 0 AND abs(r.difference) <= 1000) ");
            } else if ("param_err".equals(s) || "param_error".equals(s)) {
                qb.where.append(" AND (r.has_errors = 1 AND abs(r.difference) <= 1000) ");
            } else if ("money_err".equals(s) || "money_diff".equals(s)) {
                qb.where.append(" AND (r.has_errors = 0 AND abs(r.difference) > 1000) ");
            } else if ("both_err".equals(s) || "both".equals(s)) {
                qb.where.append(" AND (r.has_errors = 1 AND abs(r.difference) > 1000) ");
            } else if ("error_under".equals(s)) {
                qb.where.append(" AND r.difference > 1000 ");
            } else if ("error_over".equals(s)) {
                qb.where.append(" AND r.difference < -1000 ");
            } else if ("diff".equals(s)) {
                qb.where.append(" AND abs(r.difference) > 1000 ");
            }
        }

        if (q != null && !q.trim().isEmpty()) {
            String pattern = "%" + q.trim() + "%";
            qb.where.append(" AND (r.full_name LIKE ? OR r.rank LIKE ? OR r.position LIKE ?) ");
            qb.params.add(pattern);
            qb.params.add(pattern);
            qb.params.add(pattern);
        }

        return qb;
    }

    public int countRecords(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        QueryBuilder qb = buildFilter(source, unitId, scope, sheetType, categoryCode, status, q);
        String sql = "SELECT COUNT(*) FROM personnel_records r " + qb.where.toString();
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, qb.params.toArray());
        return count != null ? count : 0;
    }

    public List<PersonnelRecordModel> findPaged(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q, int offset, int limit) {
        QueryBuilder qb = buildFilter(source, unitId, scope, sheetType, categoryCode, status, q);
        String sql = "SELECT r.*, u.name AS unit_name " +
                     "FROM personnel_records r " +
                     "LEFT JOIN units u ON r.unit_id = u.id " +
                     qb.where.toString() +
                     "ORDER BY r.source_row ASC, r.created_at DESC, r.id ASC " +
                     "LIMIT ? OFFSET ?";

        List<Object> args = new ArrayList<>(qb.params);
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(sql, rowMapper, args.toArray());
    }

    public List<String> findMatchingIds(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        QueryBuilder qb = buildFilter(source, unitId, scope, sheetType, categoryCode, status, q);
        String sql = "SELECT r.id FROM personnel_records r " +
                     qb.where.toString() +
                     "ORDER BY r.source_row ASC, r.created_at DESC, r.id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("id"), qb.params.toArray());
    }

    public List<PersonnelRecordModel> findMatchingRecords(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        QueryBuilder qb = buildFilter(source, unitId, scope, sheetType, categoryCode, status, q);
        String sql = "SELECT r.*, u.name AS unit_name " +
                     "FROM personnel_records r " +
                     "LEFT JOIN units u ON r.unit_id = u.id " +
                     qb.where.toString() +
                     "ORDER BY r.source_row ASC, r.created_at DESC, r.id ASC";
        return jdbcTemplate.query(sql, rowMapper, qb.params.toArray());
    }

    public List<PersonnelRecordModel> findByIdsPreservingOrder(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT r.*, u.name AS unit_name " +
                     "FROM personnel_records r " +
                     "LEFT JOIN units u ON r.unit_id = u.id " +
                     "WHERE r.id IN (" + placeholders + ")";
        List<PersonnelRecordModel> unordered = jdbcTemplate.query(sql, rowMapper, ids.toArray());
        Map<String, PersonnelRecordModel> map = new HashMap<>();
        for (PersonnelRecordModel r : unordered) {
            map.put(r.getId(), r);
        }
        List<PersonnelRecordModel> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            PersonnelRecordModel r = map.get(id);
            if (r != null) {
                ordered.add(r);
            }
        }
        return ordered;
    }

    public Map<String, Object> calculateRollup(String unitId, String scope) {
        return calculateRollup(unitId, scope, "validated");
    }

    public Map<String, Object> calculateRollup(String unitId, String scope, String source) {
        List<String> targetUnitIds;
        if (unitId == null || unitId.trim().isEmpty()) {
            // Toàn quân (không lọc unit)
            targetUnitIds = Collections.emptyList();
        } else if ("branch".equalsIgnoreCase(scope)) {
            targetUnitIds = unitRepository.getAllDescendantIds(unitId.trim());
        } else {
            targetUnitIds = Collections.singletonList(unitId.trim());
        }

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (!targetUnitIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(targetUnitIds.size(), "?"));
            conditions.add("unit_id IN (" + placeholders + ")");
            params.addAll(targetUnitIds);
        }

        String effectiveSource = (source != null && !source.trim().isEmpty()) ? source.trim() : null;
        if (effectiveSource != null && !"all".equalsIgnoreCase(effectiveSource)) {
            conditions.add("source = ?");
            params.add(effectiveSource);
        }

        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions) + " ";

        // 1. Thống kê theo Sheet
        String sqlSheet = "SELECT sheet_type, " +
                          "COUNT(*) AS total_count, " +
                          "COALESCE(SUM(actual_total), 0) AS sum_actual, " +
                          "COALESCE(SUM(calculated_total), 0) AS sum_calc, " +
                          "COALESCE(SUM(difference), 0) AS sum_diff, " +
                          "COALESCE(SUM(CASE WHEN has_errors = 1 THEN 1 ELSE 0 END), 0) AS error_count " +
                          "FROM personnel_records " + whereClause +
                          "GROUP BY sheet_type";

        List<Map<String, Object>> sheetRows = jdbcTemplate.queryForList(sqlSheet, params.toArray());

        // 2. Thống kê theo Category (Đối tượng)
        String sqlCat = "SELECT category_code, " +
                        "COUNT(*) AS total_count, " +
                        "COALESCE(SUM(actual_total), 0) AS sum_actual, " +
                        "COALESCE(SUM(calculated_total), 0) AS sum_calc " +
                        "FROM personnel_records " + whereClause +
                        "GROUP BY category_code";

        List<Map<String, Object>> catRows = jdbcTemplate.queryForList(sqlCat, params.toArray());

        // 3. Tổng hợp chung
        String sqlOverall = "SELECT " +
                            "COUNT(*) AS total_records, " +
                            "COALESCE(SUM(actual_total), 0) AS total_actual, " +
                            "COALESCE(SUM(calculated_total), 0) AS total_calc, " +
                            "COALESCE(SUM(difference), 0) AS total_diff, " +
                            "COALESCE(SUM(CASE WHEN has_errors = 1 THEN 1 ELSE 0 END), 0) AS total_errors " +
                            "FROM personnel_records " + whereClause;

        Map<String, Object> overall = jdbcTemplate.queryForMap(sqlOverall, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unitId", unitId);
        result.put("scope", scope);
        result.put("source", effectiveSource);
        result.put("overall", overall);
        result.put("bySheet", sheetRows);
        result.put("byCategory", catRows);

        return result;
    }

    public int reassignUnit(String oldUnitId, String newUnitId) {
        if (oldUnitId == null || newUnitId == null) return 0;
        return jdbcTemplate.update(
                "UPDATE personnel_records SET unit_id = ?, updated_at = ? WHERE unit_id = ?",
                newUnitId, Instant.now().toString(), oldUnitId
        );
    }
}
