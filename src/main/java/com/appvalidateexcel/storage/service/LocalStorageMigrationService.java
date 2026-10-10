package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.config.DataDirectoryResolver;
import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import com.appvalidateexcel.storage.model.UnitModel;
import com.appvalidateexcel.storage.model.ValidationErrorModel;
import com.appvalidateexcel.storage.repository.MigrationAuditRepository;
import com.appvalidateexcel.storage.repository.PersonnelRecordRepository;
import com.appvalidateexcel.storage.repository.UnitRepository;
import com.appvalidateexcel.storage.repository.ValidationErrorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
public class LocalStorageMigrationService {

    private final DataDirectoryResolver directoryResolver;
    private final UnitRepository unitRepository;
    private final PersonnelRecordRepository recordRepository;
    private final ValidationErrorRepository errorRepository;
    private final MigrationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final StorageMaintenanceLock maintenanceLock;

    public LocalStorageMigrationService(
            DataDirectoryResolver directoryResolver,
            UnitRepository unitRepository,
            PersonnelRecordRepository recordRepository,
            ValidationErrorRepository errorRepository,
            MigrationAuditRepository auditRepository,
            ObjectMapper objectMapper,
            StorageMaintenanceLock maintenanceLock) {
        this.directoryResolver = directoryResolver;
        this.unitRepository = unitRepository;
        this.recordRepository = recordRepository;
        this.errorRepository = errorRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.maintenanceLock = maintenanceLock;
    }

    @Transactional
    public Map<String, Object> migrate(Map<String, Object> payload) {
        return maintenanceLock.callWithWriteAccess(() -> doMigrate(payload));
    }

    private Map<String, Object> doMigrate(Map<String, Object> payload) {
        // 0. Tính SHA-256 nội dung payload và kiểm tra chống chạy lặp
        String contentHash = computePayloadChecksum(payload);
        String migrationId = (String) payload.getOrDefault("migrationId", contentHash);

        if (auditRepository.isMigrationCompleted(migrationId) || auditRepository.isMigrationCompleted(contentHash)) {
            System.out.println("[BQP Migration] Migration " + migrationId + " (hash: " + contentHash + ") đã hoàn tất trước đó, bỏ qua.");
            return Map.of(
                    "status", "ALREADY_COMPLETED",
                    "migrationId", migrationId,
                    "contentHash", contentHash,
                    "message", "Di chuyển dữ liệu này đã được thực hiện thành công trước đó."
            );
        }

        long start = System.currentTimeMillis();

        // 1. Lưu bản sao nguyên gốc payload ra thư mục backups (BẮT BUỘC, lỗi là throw)
        savePreMigrationBackup(migrationId, payload);

        // 2. Chuyển đổi Units: Sắp xếp theo level tăng dần (cha trước, con sau) để bảo đảm ràng buộc toàn vẹn
        List<Map<String, Object>> unitsRaw = (List<Map<String, Object>>) payload.get("units");
        int unitCount = 0;
        if (unitsRaw != null && !unitsRaw.isEmpty()) {
            List<Map<String, Object>> sortedUnits = new ArrayList<>(unitsRaw);
            sortedUnits.sort(Comparator.comparingInt(uMap -> {
                Object lvl = uMap.get("level");
                return lvl instanceof Number n ? n.intValue() : 4;
            }));

            for (Map<String, Object> uMap : sortedUnits) {
                String id = (String) uMap.get("id");
                if (id == null || id.trim().isEmpty()) continue;
                String name = (String) uMap.getOrDefault("name", "Đơn vị");
                String parentId = (String) uMap.get("parentId");
                Integer level = uMap.get("level") != null ? ((Number) uMap.get("level")).intValue() : 4;
                String code = (String) uMap.get("code");
                Boolean isPreset = Boolean.TRUE.equals(uMap.get("isPreset"));

                Optional<UnitModel> existing = unitRepository.findById(id);
                if (existing.isPresent()) {
                    UnitModel u = existing.get();
                    u.setName(name);
                    u.setParentId(parentId);
                    u.setLevel(level);
                    if (code != null) u.setCode(code);
                    unitRepository.update(u);
                } else {
                    UnitModel u = UnitModel.builder()
                            .id(id)
                            .name(name)
                            .normalizedName(com.appvalidateexcel.storage.util.UnitNameCanonicalizer.canonicalize(name))
                            .parentId(parentId)
                            .level(level)
                            .code(code)
                            .isPreset(isPreset)
                            .isActive(true)
                            .displayOrder(0)
                            .build();
                    unitRepository.insert(u);
                }
                unitCount++;
            }
        }

        // 3. Chuyển đổi Records từ 3 nguồn: 'manual', 'excel', 'validated'
        int recordCount = 0;
        double sumMoney = 0.0;
        int errorCount = 0;

        List<PersonnelRecordModel> recordsToSave = new ArrayList<>();
        List<ValidationErrorModel> errorsToSave = new ArrayList<>();

        Map<String, String> sources = new LinkedHashMap<>();
        if (payload.containsKey("recordsManual") && payload.get("recordsManual") != null) {
            sources.put("recordsManual", "manual");
        } else {
            sources.put("records", "manual");
        }
        sources.put("recordsExcel", "excel");
        sources.put("recordsValidated", "validated");

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String key = entry.getKey();
            String source = entry.getValue();
            Object rawList = payload.get(key);
            if (!(rawList instanceof List<?> listRaw)) continue;

            for (Object obj : listRaw) {
                if (!(obj instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> rMap = (Map<String, Object>) rawMap;

                String id = (String) rMap.get("id");
                if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();

                Map<String, Object> inputMap = extractSubMap(rMap.get("input"));
                Map<String, Object> resultMap = extractSubMap(rMap.get("result"));

                String unitId = (String) rMap.get("unitId");
                // Họ tên: tìm ở root rồi tìm trong input
                String fullName = getFirstNonEmptyString(rMap, inputMap, "hoTen", "fullName", "name");
                String sheetType = getFirstNonEmptyString(rMap, inputMap, "sheet", "sheetType");
                if (sheetType == null || sheetType.isEmpty()) sheetType = "I.1";
                String categoryCode = getFirstNonEmptyString(rMap, inputMap, "categoryCode", "doiTuong");
                if (categoryCode == null || categoryCode.isEmpty()) categoryCode = "UNKNOWN";

                String rank = getFirstNonEmptyString(rMap, inputMap, "capBac", "rank");
                String position = getFirstNonEmptyString(rMap, inputMap, "chucVu", "position");
                String birthDate = getFirstNonEmptyString(rMap, inputMap, "ngaySinh", "birthDate");
                String enlistmentDate = getFirstNonEmptyString(rMap, inputMap, "nhapNgu", "enlistmentDate");
                String mergerDate = getFirstNonEmptyString(rMap, inputMap, "sapNhap", "mergerDate");

                // Thời điểm nghỉ việc: ánh xạ chuẩn thoiDiemNghi / nghiViec
                String retirementDate = getFirstNonEmptyString(rMap, inputMap, "thoiDiemNghi", "nghiViec", "thoiDiemNghiViec", "retirementDate", "ngayNghi");

                // Lương tháng: ánh xạ chuẩn luongThang / luongHienHuong / monthlySalary
                Double salary = getFirstDouble(rMap, inputMap, resultMap, "luongThang", "luongHienHuong", "monthlySalary", "luong");

                // Tiền thực tế, tính lại, chênh lệch
                Double actualTotal = getFirstDouble(rMap, inputMap, resultMap, "tongTienThucTe", "actualTotal", "tienThucTe");
                Double calcTotal = getFirstDouble(rMap, inputMap, resultMap, "tongTienTinhLai", "calculatedTotal", "tienTinhLai");
                Double diff = getFirstDouble(rMap, inputMap, resultMap, "diff", "chenhLech", "difference", "tienChenhLech");

                boolean hasErr = Boolean.TRUE.equals(rMap.get("hasErrors")) || Boolean.TRUE.equals(rMap.get("isRowError"));

                sumMoney += actualTotal;

                PersonnelRecordModel model = PersonnelRecordModel.builder()
                        .id(id)
                        .source(source)
                        .unitId(unitId)
                        .categoryCode(categoryCode)
                        .sheetType(sheetType)
                        .fullName(fullName)
                        .birthDate(birthDate)
                        .rank(rank)
                        .position(position)
                        .enlistmentDate(enlistmentDate)
                        .mergerDate(mergerDate)
                        .retirementDate(retirementDate)
                        .monthlySalary(salary)
                        .actualTotal(actualTotal)
                        .calculatedTotal(calcTotal)
                        .difference(diff)
                        .hasErrors(hasErr)
                        .inputJson(rMap.containsKey("input") ? safeJson(rMap.get("input")) : null)
                        .resultJson(rMap.containsKey("result") ? safeJson(rMap.get("result")) : null)
                        .rawColumnsJson(rMap.containsKey("rawCols") ? safeJson(rMap.get("rawCols")) : null)
                        .build();

                recordsToSave.add(model);

                // Chi tiết lỗi: xử lý an toàn kiểu dữ liệu (tránh ClassCastException)
                Object rawErrObj = rMap.get("errorDetails");
                if (rawErrObj instanceof List<?> errList) {
                    for (Object eItem : errList) {
                        if (eItem instanceof Map<?, ?> eMap) {
                            Integer col = null;
                            Object colObj = eMap.get("col");
                            if (colObj == null) colObj = eMap.get("columnNumber");
                            if (colObj instanceof Number) col = ((Number) colObj).intValue();

                            String code = (String) (eMap.get("code") != null ? eMap.get("code") : eMap.get("errorCode"));
                            String msg = (String) (eMap.get("msg") != null ? eMap.get("msg") : eMap.get("message"));
                            if (msg == null || msg.trim().isEmpty()) msg = "Lỗi dữ liệu";

                            String actual = eMap.get("actual") != null ? String.valueOf(eMap.get("actual")) : null;
                            String expected = eMap.get("expected") != null ? String.valueOf(eMap.get("expected")) : null;

                            errorsToSave.add(ValidationErrorModel.builder()
                                    .id(UUID.randomUUID().toString())
                                    .recordId(id)
                                    .columnNumber(col)
                                    .errorCode(code != null ? code : "DATA_ERROR")
                                    .message(msg)
                                    .actualValue(actual)
                                    .expectedValue(expected)
                                    .build());
                        } else if (eItem instanceof String strMsg) {
                            errorsToSave.add(ValidationErrorModel.builder()
                                    .id(UUID.randomUUID().toString())
                                    .recordId(id)
                                    .errorCode("LEGACY_ERROR")
                                    .message(strMsg)
                                    .build());
                        }
                    }
                } else if (rawErrObj instanceof String strErr && !strErr.trim().isEmpty()) {
                    errorsToSave.add(ValidationErrorModel.builder()
                            .id(UUID.randomUUID().toString())
                            .recordId(id)
                            .errorCode("LEGACY_ERROR")
                            .message(strErr)
                            .build());
                }

                if (hasErr || (!errorsToSave.isEmpty() && errorsToSave.get(errorsToSave.size() - 1).getRecordId().equals(id))) {
                    errorCount++;
                    model.setHasErrors(true);
                }

                recordCount++;
            }
        }

        // 4. Batch insert vào SQLite (UPSERT chống mất cascade)
        recordRepository.saveBatch(recordsToSave);
        if (!errorsToSave.isEmpty()) {
            errorRepository.saveBatch(errorsToSave);
        }

        long elapsed = System.currentTimeMillis() - start;

        // 5. Ghi audit đối chiếu độc lập
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("unitCount", unitCount);
        details.put("recordCount", recordCount);
        details.put("errorCount", errorCount);
        details.put("sumMoney", sumMoney);
        details.put("elapsedMs", elapsed);

        auditRepository.logMigration(
                migrationId,
                "LOCAL_STORAGE_V1",
                recordCount,
                unitCount,
                contentHash,
                "SUCCESS",
                safeJson(details)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("migrationId", migrationId);
        result.put("contentHash", contentHash);
        result.put("unitsMigrated", unitCount);
        result.put("recordsMigrated", recordCount);
        result.put("errorsMigrated", errorCount);
        result.put("totalMoney", sumMoney);
        result.put("elapsedMs", elapsed);

        return result;
    }

    private void savePreMigrationBackup(String migrationId, Map<String, Object> payload) {
        try {
            Path backupDir = directoryResolver.getBackupsDirectory();
            String filename = "pre-migration-" + migrationId + "-" + System.currentTimeMillis() + ".json";
            Path backupFile = backupDir.resolve(filename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile.toFile(), payload);
            System.out.println("[BQP Migration] Đã lưu bản sao lưu an toàn pre-migration: " + backupFile);
        } catch (Exception e) {
            System.err.println("[BQP Migration LỖI BẮT BUỘC] Không thể lưu file pre-migration backup: " + e.getMessage());
            throw new RuntimeException("Bắt buộc phải tạo bản sao lưu dữ liệu trước migration nhưng thất bại: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private String getFirstNonEmptyString(Map<String, Object> root, Map<String, Object> sub, String... keys) {
        for (String k : keys) {
            Object v = root.get(k);
            if (v != null && !v.toString().trim().isEmpty()) return v.toString().trim();
            if (sub != null) {
                Object sv = sub.get(k);
                if (sv != null && !sv.toString().trim().isEmpty()) return sv.toString().trim();
            }
        }
        return "";
    }

    private Double getFirstDouble(Map<String, Object> m1, Map<String, Object> m2, Map<String, Object> m3, String... keys) {
        for (String k : keys) {
            Double d = parseDoubleVal(m1.get(k));
            if (d != null) return d;
            if (m2 != null) {
                d = parseDoubleVal(m2.get(k));
                if (d != null) return d;
            }
            if (m3 != null) {
                d = parseDoubleVal(m3.get(k));
                if (d != null) return d;
            }
        }
        return 0.0;
    }

    private Double parseDoubleVal(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            String clean = s.trim().replaceAll("\\s+", "");
            if (clean.isEmpty()) return null;
            // Xử lý tiền tệ Việt Nam (phân cách hàng nghìn bằng chấm hoặc phẩy)
            if (clean.contains(",") && clean.contains(".")) {
                if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                    clean = clean.replace(".", "").replace(',', '.');
                } else {
                    clean = clean.replace(",", "");
                }
            } else if (clean.contains(".") && !clean.contains(",")) {
                int dotCount = clean.length() - clean.replace(".", "").length();
                if (dotCount > 1 || clean.lastIndexOf('.') == clean.length() - 4) {
                    clean = clean.replace(".", "");
                }
            } else if (clean.contains(",") && !clean.contains(".")) {
                int commaCount = clean.length() - clean.replace(",", "").length();
                if (commaCount > 1 || clean.lastIndexOf(',') == clean.length() - 4) {
                    clean = clean.replace(",", "");
                } else {
                    clean = clean.replace(',', '.');
                }
            }
            try {
                return Double.parseDouble(clean);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String computePayloadChecksum(Map<String, Object> payload) {
        try {
            // Chỉ trích xuất và băm dữ liệu nghiệp vụ ổn định (units, records)
            // Loại bỏ migrationId, client checksum, exportedAt, timestamp để bảo đảm tính idempotent
            Map<String, Object> stableContent = new LinkedHashMap<>();
            stableContent.put("units", payload.get("units"));
            stableContent.put("records", payload.get("records") != null ? payload.get("records") : payload.get("recordsManual"));
            stableContent.put("recordsExcel", payload.get("recordsExcel"));
            stableContent.put("recordsValidated", payload.get("recordsValidated"));

            byte[] bytes = objectMapper.writeValueAsBytes(stableContent);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "mig_hash_" + System.currentTimeMillis();
        }
    }

    private String safeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
