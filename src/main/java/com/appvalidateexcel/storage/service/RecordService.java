package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import com.appvalidateexcel.storage.model.ValidationErrorModel;
import com.appvalidateexcel.storage.repository.ImportRepository;
import com.appvalidateexcel.storage.repository.PersonnelRecordRepository;
import com.appvalidateexcel.storage.repository.ValidationErrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RecordService {

    private final PersonnelRecordRepository recordRepository;
    private final ValidationErrorRepository errorRepository;
    private final ImportRepository importRepository;
    private final StorageMaintenanceLock maintenanceLock;

    public RecordService(PersonnelRecordRepository recordRepository,
                         ValidationErrorRepository errorRepository,
                         ImportRepository importRepository,
                         StorageMaintenanceLock maintenanceLock) {
        this.recordRepository = recordRepository;
        this.errorRepository = errorRepository;
        this.importRepository = importRepository;
        this.maintenanceLock = maintenanceLock;
    }

    public Map<String, Object> findPaged(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q, int page, int size) {
        return maintenanceLock.callWithReadAccess(() -> {
            int safePage = Math.max(0, page);
            int safeSize = (size <= 0) ? 50 : Math.min(size, 10000);
            int offset = safePage * safeSize;

            int totalElements = recordRepository.countRecords(source, unitId, scope, sheetType, categoryCode, status, q);
            int totalPages = (int) Math.ceil((double) totalElements / safeSize);

            List<PersonnelRecordModel> items = recordRepository.findPaged(source, unitId, scope, sheetType, categoryCode, status, q, offset, safeSize);

            if (!items.isEmpty()) {
                List<String> ids = items.stream().map(PersonnelRecordModel::getId).toList();
                Map<String, List<ValidationErrorModel>> errorMap = errorRepository.findByRecordIds(ids);
                for (PersonnelRecordModel item : items) {
                    item.setErrorDetails(errorMap.getOrDefault(item.getId(), Collections.emptyList()));
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("items", items);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("page", safePage);
            response.put("currentPage", safePage);
            response.put("size", safeSize);

            return response;
        });
    }

    public List<PersonnelRecordModel> findAllBySource(String source) {
        return findAllBySource(source, true);
    }

    public List<PersonnelRecordModel> findAllBySource(String source, boolean includeErrors) {
        return findAllBySource(source, null, "branch", null, null, null, null, includeErrors);
    }

    public List<PersonnelRecordModel> findAllBySource(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q, boolean includeErrors) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Tham số 'source' là bắt buộc.");
        }
        return maintenanceLock.callWithReadAccess(() -> {
            int total = recordRepository.countRecords(source, unitId, scope, sheetType, categoryCode, status, q);
            if (total == 0) return Collections.emptyList();

            List<PersonnelRecordModel> items = recordRepository.findPaged(source, unitId, scope, sheetType, categoryCode, status, q, 0, Math.max(total, 10000));
            if (includeErrors && !items.isEmpty()) {
                List<String> ids = items.stream().map(PersonnelRecordModel::getId).toList();
                Map<String, List<ValidationErrorModel>> errorMap = errorRepository.findByRecordIds(ids);
                for (PersonnelRecordModel item : items) {
                    item.setErrorDetails(errorMap.getOrDefault(item.getId(), Collections.emptyList()));
                }
            }
            return items;
        });
    }

    public Map<String, Integer> getCountsBySource() {
        return maintenanceLock.callWithReadAccess(() -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("manual", recordRepository.countRecords("manual", null, null, null, null, null, null));
            counts.put("excel", recordRepository.countRecords("excel", null, null, null, null, null, null));
            counts.put("validated", recordRepository.countRecords("validated", null, null, null, null, null, null));
            return counts;
        });
    }

    public Optional<PersonnelRecordModel> findById(String id) {
        return maintenanceLock.callWithReadAccess(() -> {
            Optional<PersonnelRecordModel> recordOpt = recordRepository.findById(id);
            recordOpt.ifPresent(record -> {
                List<ValidationErrorModel> errors = errorRepository.findByRecordId(id);
                record.setErrorDetails(errors);
            });
            return recordOpt;
        });
    }

    @Transactional
    public void saveRecord(PersonnelRecordModel record) {
        maintenanceLock.runWithWriteAccess(() -> {
            if (record.getId() == null || record.getId().trim().isEmpty()) {
                record.setId(UUID.randomUUID().toString());
            }
            recordRepository.save(record);
            if (record.getErrorDetails() != null) {
                errorRepository.deleteByRecordId(record.getId());
                if (!record.getErrorDetails().isEmpty()) {
                    for (ValidationErrorModel err : record.getErrorDetails()) {
                        err.setRecordId(record.getId());
                    }
                    errorRepository.saveBatch(record.getErrorDetails());
                }
            }
        });
    }

    @Transactional
    public void saveBatch(List<PersonnelRecordModel> records) {
        if (records == null || records.isEmpty()) return;
        maintenanceLock.runWithWriteAccess(() -> {
            for (PersonnelRecordModel r : records) {
                if (r.getId() == null || r.getId().trim().isEmpty()) {
                    r.setId(UUID.randomUUID().toString());
                }
            }

            recordRepository.saveBatch(records);

            // Xử lý validation_errors an toàn:
            // 1. Chỉ các record có errorDetails != null mới bị thay thế lỗi
            List<String> idsWithExplicitErrors = new ArrayList<>();
            List<ValidationErrorModel> errorsToInsert = new ArrayList<>();

            for (PersonnelRecordModel r : records) {
                if (r.getErrorDetails() != null) {
                    idsWithExplicitErrors.add(r.getId());
                    for (ValidationErrorModel err : r.getErrorDetails()) {
                        err.setRecordId(r.getId());
                        errorsToInsert.add(err);
                    }
                }
            }

            if (!idsWithExplicitErrors.isEmpty()) {
                errorRepository.deleteByRecordIds(idsWithExplicitErrors);
            }

            if (!errorsToInsert.isEmpty()) {
                errorRepository.saveBatch(errorsToInsert);
            }
        });
    }

    @Transactional
    public Map<String, Object> replaceSource(String source, List<PersonnelRecordModel> records) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Tham số 'source' là bắt buộc.");
        }
        return maintenanceLock.callWithWriteAccess(() -> {
            int deletedOldCount = recordRepository.deleteBySource(source.trim());

            if (records != null && !records.isEmpty()) {
                for (PersonnelRecordModel r : records) {
                    r.setSource(source.trim());
                    if (r.getId() == null || r.getId().trim().isEmpty()) {
                        r.setId(UUID.randomUUID().toString());
                    }
                }
                saveBatch(records);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("source", source.trim());
            result.put("deletedOldCount", deletedOldCount);
            result.put("insertedCount", records != null ? records.size() : 0);
            return result;
        });
    }

    @Transactional
    public void deleteById(String id) {
        maintenanceLock.runWithWriteAccess(() -> {
            errorRepository.deleteByRecordId(id);
            recordRepository.deleteById(id);
            if (id != null) {
                if (id.startsWith("val_")) {
                    String pairId = "excel_" + id.substring(4);
                    errorRepository.deleteByRecordId(pairId);
                    recordRepository.deleteById(pairId);
                } else if (id.startsWith("excel_")) {
                    String pairId = "val_" + id.substring(6);
                    errorRepository.deleteByRecordId(pairId);
                    recordRepository.deleteById(pairId);
                }
            }
            importRepository.syncAllImportsStatus();
        });
    }

    @Transactional
    public int deleteBySource(String source) {
        return maintenanceLock.callWithWriteAccess(() -> {
            int deleted = recordRepository.deleteBySource(source);
            if (source != null && ("validated".equalsIgnoreCase(source.trim()) || "excel".equalsIgnoreCase(source.trim()))) {
                String pairSource = "validated".equalsIgnoreCase(source.trim()) ? "excel" : "validated";
                deleted += recordRepository.deleteBySource(pairSource);
            }
            importRepository.syncAllImportsStatus();
            return deleted;
        });
    }

    public static class ExportSnapshotSession {
        private final String sessionId;
        private final List<PersonnelRecordModel> records;
        private final List<String> recordIds;
        private final long createdAt;

        public ExportSnapshotSession(String sessionId, List<PersonnelRecordModel> records) {
            this.sessionId = sessionId;
            this.records = records != null ? new ArrayList<>(records) : Collections.emptyList();
            this.recordIds = this.records.stream().map(PersonnelRecordModel::getId).collect(java.util.stream.Collectors.toList());
            this.createdAt = System.currentTimeMillis();
        }

        public String getSessionId() { return sessionId; }
        public List<PersonnelRecordModel> getRecords() { return records; }
        public List<String> getRecordIds() { return recordIds; }
        public long getCreatedAt() { return createdAt; }
    }

    public static final int MAX_EXPORT_LIMIT = 50000;
    public static final int MAX_ACTIVE_SESSIONS = 20;

    private final Map<String, ExportSnapshotSession> exportSessions = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        exportSessions.entrySet().removeIf(e -> (now - e.getValue().getCreatedAt()) > 600_000);
    }

    public Map<String, Object> createExportSession(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Tham số 'source' là bắt buộc.");
        }
        return maintenanceLock.callWithReadAccess(() -> {
            cleanupExpiredSessions();

            if (exportSessions.size() >= MAX_ACTIVE_SESSIONS) {
                throw new IllegalStateException("Hệ thống đang xử lý số lượng phiên xuất tối đa (" + MAX_ACTIVE_SESSIONS + " phiên). Vui lòng đợi các phiên trước hoàn tất hoặc thử lại sau.");
            }

            List<PersonnelRecordModel> records = recordRepository.findMatchingRecords(source, unitId, scope, sheetType, categoryCode, status, q);
            if (records.size() > MAX_EXPORT_LIMIT) {
                throw new IllegalArgumentException("Số lượng hồ sơ cần xuất (" + records.size() + ") vượt quá giới hạn an toàn tối đa của hệ thống (" + MAX_EXPORT_LIMIT + " hồ sơ). Vui lòng thu hẹp bộ lọc theo Đơn vị, Loại bảng hoặc Trạng thái trước khi xuất.");
            }

            String sessionId = UUID.randomUUID().toString();
            exportSessions.put(sessionId, new ExportSnapshotSession(sessionId, records));

            int totalElements = records.size();
            int pageSize = 1000;
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("sessionId", sessionId);
            res.put("totalElements", totalElements);
            res.put("pageSize", pageSize);
            res.put("totalPages", totalPages);
            return res;
        });
    }

    public Map<String, Object> getExportSessionPage(String sessionId, int page, int size, boolean includeErrors) {
        cleanupExpiredSessions();
        ExportSnapshotSession session = exportSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Phiên xuất dữ liệu (sessionId: " + sessionId + ") không tồn tại hoặc đã hết hạn. Vui lòng thử lại.");
        }

        int safePage = Math.max(0, page);
        int safeSize = (size <= 0) ? 1000 : Math.min(size, 2000);
        int totalElements = session.getRecords().size();
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        int fromIndex = safePage * safeSize;
        if (fromIndex >= totalElements) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("page", safePage);
            res.put("size", safeSize);
            res.put("totalElements", totalElements);
            res.put("totalPages", totalPages);
            res.put("items", Collections.emptyList());
            return res;
        }

        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<PersonnelRecordModel> slice = session.getRecords().subList(fromIndex, toIndex);

        // Sao chép an toàn để tránh bị đột biến giữa các luồng
        List<PersonnelRecordModel> items = new ArrayList<>(slice.size());
        for (PersonnelRecordModel r : slice) {
            items.add(r.toBuilder().build());
        }

        if (includeErrors && !items.isEmpty()) {
            List<String> sliceIds = items.stream().map(PersonnelRecordModel::getId).collect(java.util.stream.Collectors.toList());
            Map<String, List<ValidationErrorModel>> errorMap = errorRepository.findByRecordIds(sliceIds);
            for (PersonnelRecordModel r : items) {
                r.setErrorDetails(errorMap.getOrDefault(r.getId(), Collections.emptyList()));
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("page", safePage);
        res.put("size", safeSize);
        res.put("totalElements", totalElements);
        res.put("totalPages", totalPages);
        res.put("items", items);
        return res;
    }

    public void closeExportSession(String sessionId) {
        if (sessionId != null) {
            exportSessions.remove(sessionId);
        }
    }
}
