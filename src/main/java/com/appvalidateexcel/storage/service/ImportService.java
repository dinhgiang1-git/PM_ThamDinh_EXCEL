package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.dto.BatchImportRequest;
import com.appvalidateexcel.storage.dto.BatchImportResult;
import com.appvalidateexcel.storage.model.ImportModel;
import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import com.appvalidateexcel.storage.model.UnitModel;
import com.appvalidateexcel.storage.repository.ImportRepository;
import com.appvalidateexcel.storage.repository.PersonnelRecordRepository;
import com.appvalidateexcel.storage.repository.UnitRepository;
import com.appvalidateexcel.storage.util.UnitNameCanonicalizer;
import com.appvalidateexcel.storage.model.ValidationErrorModel;
import com.appvalidateexcel.storage.repository.ValidationErrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
public class ImportService {

    private final ImportRepository importRepository;
    private final UnitRepository unitRepository;
    private final PersonnelRecordRepository personnelRecordRepository;
    private final ValidationErrorRepository validationErrorRepository;
    private final StorageMaintenanceLock maintenanceLock;
    private final PlatformTransactionManager transactionManager;

    public ImportService(
            ImportRepository importRepository,
            UnitRepository unitRepository,
            PersonnelRecordRepository personnelRecordRepository,
            ValidationErrorRepository validationErrorRepository,
            StorageMaintenanceLock maintenanceLock,
            PlatformTransactionManager transactionManager
    ) {
        this.importRepository = importRepository;
        this.unitRepository = unitRepository;
        this.personnelRecordRepository = personnelRecordRepository;
        this.validationErrorRepository = validationErrorRepository;
        this.maintenanceLock = maintenanceLock;
        this.transactionManager = transactionManager;
    }

    public BatchImportResult commitBatch(BatchImportRequest request) {
        return maintenanceLock.callWithWriteAccess(() -> {
            if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
                throw new IllegalArgumentException("Danh sách file nhập không được để trống!");
            }

            if (Boolean.TRUE.equals(request.getAtomic())) {
                return commitAtomicBatch(request);
            }
            return commitIndependentBatch(request);
        });
    }

    private BatchImportResult commitIndependentBatch(BatchImportRequest request) {
        String defaultParentId = request.getCommonParentUnitId();
        List<BatchImportResult.FileResult> fileResults = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (BatchImportRequest.BatchFileDto fileDto : request.getFiles()) {
            BatchImportResult.FileResult fileRes = commitSingleFileWithIsolatedTransaction(fileDto, defaultParentId);
            fileResults.add(fileRes);
            if ("SUCCESS".equals(fileRes.getStatus()) || "REPLACED".equals(fileRes.getStatus())) {
                successCount++;
            } else if ("SKIPPED".equals(fileRes.getStatus())) {
                skippedCount++;
            } else {
                failedCount++;
            }
        }

        return BatchImportResult.builder()
                .totalFiles(request.getFiles().size())
                .successCount(successCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .atomic(false)
                .committed(failedCount == 0)
                .rolledBack(false)
                .fileResults(fileResults)
                .build();
    }

    private BatchImportResult commitAtomicBatch(BatchImportRequest request) {
        String defaultParentId = request.getCommonParentUnitId();
        List<BatchImportRequest.BatchFileDto> files = request.getFiles();

        try {
            if (files == null || files.size() != 2) {
                throw new IllegalArgumentException("Lưu đồng thời bắt buộc phải có chính xác 2 tập dữ liệu (RAW và Đã thẩm định)!");
            }

            BatchImportRequest.BatchFileDto file0 = files.get(0);
            BatchImportRequest.BatchFileDto file1 = files.get(1);

            String src0 = getSourceFromFile(file0);
            String src1 = getSourceFromFile(file1);

            boolean hasExcel = "excel".equalsIgnoreCase(src0) || "excel".equalsIgnoreCase(src1);
            boolean hasVal = "validated".equalsIgnoreCase(src0) || "validated".equalsIgnoreCase(src1);

            if (!hasExcel || !hasVal || src0.equalsIgnoreCase(src1)) {
                throw new IllegalArgumentException("Lưu đồng thời bắt buộc một tập nguồn 'excel' và một tập nguồn 'validated'!");
            }

            int count0 = (file0.getRecords() != null) ? file0.getRecords().size() : 0;
            int count1 = (file1.getRecords() != null) ? file1.getRecords().size() : 0;
            if (count0 != count1) {
                throw new IllegalArgumentException("Số lượng bản ghi giữa 2 nguồn không khớp (" + count0 + " != " + count1 + ")!");
            }

            // Kiểm tra tính nhất quán đơn vị hiển thị và cấu hình
            String dName0 = file0.getDisplayUnitName() != null ? file0.getDisplayUnitName().trim() : "";
            String dName1 = file1.getDisplayUnitName() != null ? file1.getDisplayUnitName().trim() : "";
            if (!dName0.isEmpty() && !dName1.isEmpty() && !dName0.equalsIgnoreCase(dName1)) {
                throw new IllegalArgumentException("Đơn vị hiển thị giữa 2 danh sách không thống nhất ('" + dName0 + "' != '" + dName1 + "')!");
            }

            // Bắt buộc chung một duplicate policy cho cả cặp (P1-02)
            String dupAction0 = file0.getDuplicateAction() != null ? file0.getDuplicateAction().trim().toUpperCase() : "SKIP";
            String dupAction1 = file1.getDuplicateAction() != null ? file1.getDuplicateAction().trim().toUpperCase() : "SKIP";
            if (!dupAction0.equals(dupAction1)) {
                throw new IllegalArgumentException("Lưu đồng thời yêu cầu cả 2 danh sách phải dùng chung một chính sách trùng lặp (duplicateAction)! (" + dupAction0 + " != " + dupAction1 + ")");
            }
            String dupAction = dupAction0;

            // Kiểm tra tương ứng từng bản ghi giữa 2 nửa (Pair Integrity P1-02)
            if (count0 > 0) {
                Map<String, Integer> keys0 = new HashMap<>();
                Map<String, Integer> keys1 = new HashMap<>();
                for (PersonnelRecordModel r : file0.getRecords()) {
                    String k = (r.getSheetType() != null ? r.getSheetType().trim() : "") + "#" +
                               (r.getSourceRow() != null ? r.getSourceRow() : "") + "#" +
                               (r.getFullName() != null ? r.getFullName().trim().toLowerCase() : "");
                    keys0.put(k, keys0.getOrDefault(k, 0) + 1);
                }
                for (PersonnelRecordModel r : file1.getRecords()) {
                    String k = (r.getSheetType() != null ? r.getSheetType().trim() : "") + "#" +
                               (r.getSourceRow() != null ? r.getSourceRow() : "") + "#" +
                               (r.getFullName() != null ? r.getFullName().trim().toLowerCase() : "");
                    keys1.put(k, keys1.getOrDefault(k, 0) + 1);
                }
                if (!keys0.equals(keys1)) {
                    throw new IllegalArgumentException("Dữ liệu giữa 2 nửa RAW và Đã thẩm định không tương ứng cùng một tập đối tượng (lệch khóa định danh sheet/dòng/họ tên)!");
                }
            }

            // Kiểm tra tính nhất quán trùng lặp trên cả cặp dữ liệu (Section P1.2)
            String hash0 = file0.getFileHash() != null ? file0.getFileHash().trim() : null;
            String hash1 = file1.getFileHash() != null ? file1.getFileHash().trim() : null;

            Optional<ImportModel> existing0 = (hash0 != null && !hash0.isEmpty()) ? importRepository.findByFileHash(hash0) : Optional.empty();
            Optional<ImportModel> existing1 = (hash1 != null && !hash1.isEmpty()) ? importRepository.findByFileHash(hash1) : Optional.empty();

            boolean exist0 = existing0.isPresent() && !isClearedImport(existing0.get());
            boolean exist1 = existing1.isPresent() && !isClearedImport(existing1.get());

            if ("SKIP".equals(dupAction)) {
                if (exist0 && exist1) {
                    List<BatchImportResult.FileResult> fileResults = new ArrayList<>();
                    for (BatchImportRequest.BatchFileDto f : files) {
                        fileResults.add(BatchImportResult.FileResult.builder()
                                .fileName(f.getFileName())
                                .displayUnitName(f.getDisplayUnitName())
                                .status("SKIPPED")
                                .recordsCount(count0)
                                .message("Tệp có cùng mã băm đã tồn tại trong hệ thống. Đã bỏ qua.")
                                .build());
                    }
                    return BatchImportResult.builder()
                            .totalFiles(2)
                            .successCount(0)
                            .skippedCount(2)
                            .failedCount(0)
                            .atomic(true)
                            .committed(false)
                            .rolledBack(false)
                            .message("Tệp có cùng mã băm đã tồn tại trong hệ thống. Đã bỏ qua cả 2 danh sách theo cấu hình.")
                            .fileResults(fileResults)
                            .build();
                } else if (exist0 != exist1) {
                    throw new IllegalStateException("PAIR_CONFLICT: Phát hiện dữ liệu cặp không đồng bộ (chỉ 1 trong 2 danh sách tồn tại). Vui lòng chọn Thay thế (REPLACE) để đồng bộ lại cả cặp!");
                }
            }

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

            return txTemplate.execute(status -> executeAtomicBatchCommit(request, defaultParentId));
        } catch (Exception e) {
            System.err.println("[BQP Atomic Batch Import Error] Giao dịch lưu đồng thời thất bại và đã rollback toàn bộ: " + e.getMessage());
            List<BatchImportResult.FileResult> fileResults = new ArrayList<>();
            for (BatchImportRequest.BatchFileDto f : files) {
                fileResults.add(BatchImportResult.FileResult.builder()
                        .fileName(f.getFileName())
                        .displayUnitName(f.getDisplayUnitName())
                        .status("FAILED")
                        .message("Giao dịch lưu đồng thời bị hủy: " + e.getMessage())
                        .build());
            }
            return BatchImportResult.builder()
                    .totalFiles(files.size())
                    .successCount(0)
                    .skippedCount(0)
                    .failedCount(files.size())
                    .atomic(true)
                    .committed(false)
                    .rolledBack(true)
                    .message("Lỗi lưu đồng thời (đã hoàn tác toàn bộ): " + e.getMessage())
                    .fileResults(fileResults)
                    .build();
        }
    }

    private String getSourceFromFile(BatchImportRequest.BatchFileDto f) {
        if (f.getRecords() != null && !f.getRecords().isEmpty()) {
            for (PersonnelRecordModel r : f.getRecords()) {
                if (r.getSource() != null && !r.getSource().trim().isEmpty()) {
                    return r.getSource().trim().toLowerCase();
                }
            }
        }
        if (f.getFileName() != null && f.getFileName().toLowerCase().contains("[gốc]")) {
            return "excel";
        }
        return "validated";
    }

    private boolean isClearedImport(ImportModel imp) {
        int count = imp.getCurrentRecordCount() != null ? imp.getCurrentRecordCount() : 0;
        return "CLEARED".equalsIgnoreCase(imp.getStatus()) || count == 0;
    }

    private BatchImportResult executeAtomicBatchCommit(
            BatchImportRequest request,
            String defaultParentId
    ) {
        List<BatchImportRequest.BatchFileDto> files = request.getFiles();

        // 1. Phân giải đơn vị cha (Common Parent Unit)
        String rawParent = defaultParentId;
        if (rawParent == null || rawParent.trim().isEmpty()) {
            for (BatchImportRequest.BatchFileDto f : files) {
                if (f.getParentUnitId() != null && !f.getParentUnitId().trim().isEmpty()) {
                    rawParent = f.getParentUnitId().trim();
                    break;
                }
            }
        }
        if (rawParent == null || rawParent.trim().isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn đơn vị cha tiếp nhận!");
        }
        final String parentUnitId = rawParent.trim();

        UnitModel parentUnit = unitRepository.findById(parentUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn vị cha (ID: " + parentUnitId + ") không tồn tại!"));

        if (Boolean.FALSE.equals(parentUnit.getIsActive())) {
            throw new IllegalArgumentException("Đơn vị cha '" + parentUnit.getName() + "' đang ở trạng thái tạm dừng, không thể tiếp nhận dữ liệu nhập mới!");
        }

        if (parentUnit.getLevel() != null && parentUnit.getLevel() >= 4) {
            throw new IllegalArgumentException("Không thể tạo đơn vị dưới đơn vị cấp 4 ('" + parentUnit.getName() + "')!");
        }

        // 2. Phân giải tên đơn vị theo file (File Unit) dùng chung
        String displayUnitName = null;
        for (BatchImportRequest.BatchFileDto f : files) {
            if (f.getDisplayUnitName() != null && !f.getDisplayUnitName().trim().isEmpty()) {
                displayUnitName = f.getDisplayUnitName().trim();
                break;
            }
        }
        if (displayUnitName == null || displayUnitName.isEmpty()) {
            displayUnitName = files.get(0).getFileName();
        }

        String normFileUnitName = normalizeUnitName(displayUnitName);
        String normParentName = normalizeUnitName(parentUnit.getName());
        UnitModel fileUnit;
        if (normFileUnitName.equals(normParentName)) {
            fileUnit = parentUnit;
        } else {
            Optional<UnitModel> existingFileUnit = unitRepository.findByNormalizedNameAndParentId(normFileUnitName, parentUnitId);
            if (existingFileUnit.isPresent()) {
                fileUnit = existingFileUnit.get();
                if (Boolean.FALSE.equals(fileUnit.getIsActive())) {
                    fileUnit.setIsActive(true);
                    unitRepository.update(fileUnit);
                }
            } else {
                int fileUnitLevel = Math.min((parentUnit.getLevel() != null ? parentUnit.getLevel() : 1) + 1, 4);
                fileUnit = UnitModel.builder()
                        .id(UUID.randomUUID().toString())
                        .name(displayUnitName)
                        .normalizedName(normFileUnitName)
                        .level(fileUnitLevel)
                        .parentId(parentUnitId)
                        .isActive(true)
                        .isPreset(false)
                        .build();
                unitRepository.insert(fileUnit);
            }
        }

        // 3. Phân giải và tạo các đơn vị nội bộ (Internal Units) dùng chung một lần
        Map<String, String> sharedInternalUnitMap = new HashMap<>();
        int internalUnitsCreatedOrFound = 0;
        int internalUnitLevel = Math.min((fileUnit.getLevel() != null ? fileUnit.getLevel() : 2) + 1, 4);

        Set<String> processedInternalNorms = new HashSet<>();
        for (BatchImportRequest.BatchFileDto fileDto : files) {
            if (fileDto.getInternalUnits() != null) {
                for (BatchImportRequest.BatchInternalUnitDto internalDto : fileDto.getInternalUnits()) {
                    if (internalDto.getName() == null || internalDto.getName().trim().isEmpty()) continue;
                    String internalName = internalDto.getName().trim();
                    String normInternalName = normalizeUnitName(internalName);
                    if (processedInternalNorms.contains(normInternalName)) continue;
                    processedInternalNorms.add(normInternalName);

                    Optional<UnitModel> existingInternal = unitRepository.findByNormalizedNameAndParentId(normInternalName, fileUnit.getId());
                    UnitModel internalUnit;
                    if (existingInternal.isPresent()) {
                        internalUnit = existingInternal.get();
                        if (Boolean.FALSE.equals(internalUnit.getIsActive())) {
                            internalUnit.setIsActive(true);
                            unitRepository.update(internalUnit);
                        }
                    } else {
                        internalUnit = UnitModel.builder()
                                .id(UUID.randomUUID().toString())
                                .name(internalName)
                                .normalizedName(normInternalName)
                                .level(internalUnitLevel)
                                .parentId(fileUnit.getId())
                                .code(internalDto.getCode())
                                .isActive(true)
                                .isPreset(false)
                                .build();
                        unitRepository.insert(internalUnit);
                    }
                    sharedInternalUnitMap.put(normInternalName, internalUnit.getId());
                    internalUnitsCreatedOrFound++;
                }
            }
        }

        // 4. Lưu từng file trong cùng một transaction
        List<BatchImportResult.FileResult> fileResults = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;

        for (BatchImportRequest.BatchFileDto fileDto : files) {
            String fileName = fileDto.getFileName();
            String fileHash = fileDto.getFileHash() != null ? fileDto.getFileHash().trim() : null;
            String duplicateAction = fileDto.getDuplicateAction() != null ? fileDto.getDuplicateAction().trim().toUpperCase() : "SKIP";

            Optional<ImportModel> existingImport = (fileHash != null && !fileHash.isEmpty())
                    ? importRepository.findByFileHash(fileHash)
                    : Optional.empty();

            if (existingImport.isPresent()) {
                ImportModel curImp = existingImport.get();
                int curRecCount = curImp.getCurrentRecordCount() != null ? curImp.getCurrentRecordCount() : 0;
                boolean isCleared = "CLEARED".equalsIgnoreCase(curImp.getStatus()) || curRecCount == 0;

                if (isCleared) {
                    importRepository.deleteImport(curImp.getId());
                } else if ("SKIP".equals(duplicateAction)) {
                    fileResults.add(BatchImportResult.FileResult.builder()
                            .fileName(fileName)
                            .displayUnitName(displayUnitName)
                            .status("SKIPPED")
                            .importId(curImp.getId())
                            .fileUnitId(curImp.getFileUnitId())
                            .message("Tệp có cùng mã băm đã tồn tại trong hệ thống. Đã bỏ qua.")
                            .build());
                    skippedCount++;
                    continue;
                } else if ("REPLACE".equals(duplicateAction)) {
                    importRepository.deleteImport(curImp.getId());
                }
            }

            String importId = (existingImport.isPresent() && "REPLACE".equals(duplicateAction))
                    ? existingImport.get().getId()
                    : UUID.randomUUID().toString();

            List<PersonnelRecordModel> recordsToSave = new ArrayList<>();
            if (fileDto.getRecords() != null) {
                for (PersonnelRecordModel rec : fileDto.getRecords()) {
                    if (rec.getId() == null || rec.getId().trim().isEmpty()) {
                        rec.setId(UUID.randomUUID().toString());
                    }
                    rec.setImportId(importId);
                    if (rec.getSource() == null || rec.getSource().trim().isEmpty()) {
                        rec.setSource("excel");
                    }

                    String recUnitName = rec.getUnitName();
                    String normRecUnit = recUnitName != null ? normalizeUnitName(recUnitName) : "";

                    if (!normRecUnit.isEmpty() && sharedInternalUnitMap.containsKey(normRecUnit)) {
                        rec.setUnitId(sharedInternalUnitMap.get(normRecUnit));
                    } else if (rec.getUnitId() != null && !rec.getUnitId().trim().isEmpty()) {
                        // Giữ unitId đã chỉ định
                    } else {
                        rec.setUnitId(fileUnit.getId());
                    }

                    if (rec.getUnitId() == null || rec.getUnitId().trim().isEmpty()) {
                        throw new IllegalStateException("Hồ sơ '" + rec.getFullName() + "' không xác định được đơn vị quản lý (unitId rỗng)!");
                    }

                    if (rec.getActualTotal() != null) {
                        rec.setActualTotal((double) Math.round(rec.getActualTotal()));
                    }
                    if (!"excel".equalsIgnoreCase(rec.getSource())) {
                        if (rec.getCalculatedTotal() != null) {
                            rec.setCalculatedTotal((double) Math.round(rec.getCalculatedTotal()));
                        }
                        if (rec.getActualTotal() != null && rec.getCalculatedTotal() != null) {
                            rec.setDifference(rec.getCalculatedTotal() - rec.getActualTotal());
                        }
                    } else {
                        rec.setCalculatedTotal(0.0);
                        rec.setDifference(0.0);
                    }

                    recordsToSave.add(rec);
                }
            }

            ImportModel importModel = ImportModel.builder()
                    .id(importId)
                    .fileName(fileName)
                    .fileHash(fileHash)
                    .status("REPLACE".equals(duplicateAction) ? "REPLACED" : "SUCCESS")
                    .totalRows(recordsToSave.size())
                    .acceptedRows(recordsToSave.size())
                    .rejectedRows(0)
                    .parentUnitId(parentUnitId)
                    .fileUnitId(fileUnit.getId())
                    .originalFileName(fileName)
                    .displayUnitName(displayUnitName)
                    .parserMode(fileDto.getParserMode() != null ? fileDto.getParserMode() : "STANDARD_CTC")
                    .templateSignature(fileDto.getTemplateSignature())
                    .completedAt(Instant.now().toString())
                    .build();

            importRepository.save(importModel);

            if (!recordsToSave.isEmpty()) {
                personnelRecordRepository.saveBatch(recordsToSave);

                List<ValidationErrorModel> allErrors = new ArrayList<>();
                for (PersonnelRecordModel r : recordsToSave) {
                    if (r.getErrorDetails() != null && !r.getErrorDetails().isEmpty()) {
                        for (ValidationErrorModel err : r.getErrorDetails()) {
                            if (err.getRecordId() == null || err.getRecordId().trim().isEmpty()) {
                                err.setRecordId(r.getId());
                            }
                            allErrors.add(err);
                        }
                    }
                }
                if (!allErrors.isEmpty()) {
                    validationErrorRepository.saveBatch(allErrors);
                }
            }

            fileResults.add(BatchImportResult.FileResult.builder()
                    .fileName(fileName)
                    .displayUnitName(displayUnitName)
                    .status("REPLACE".equals(duplicateAction) ? "REPLACED" : "SUCCESS")
                    .importId(importId)
                    .fileUnitId(fileUnit.getId())
                    .internalUnitsCount(internalUnitsCreatedOrFound)
                    .recordsCount(recordsToSave.size())
                    .message("Đã lưu thành công " + recordsToSave.size() + " hồ sơ vào cây đơn vị.")
                    .build());
            successCount++;
        }

        return BatchImportResult.builder()
                .totalFiles(files.size())
                .successCount(successCount)
                .skippedCount(skippedCount)
                .failedCount(0)
                .atomic(true)
                .committed(true)
                .rolledBack(false)
                .message("Đã lưu đồng thời thành công toàn bộ dữ liệu vào cây đơn vị.")
                .fileResults(fileResults)
                .build();
    }

    private BatchImportResult.FileResult commitSingleFileWithIsolatedTransaction(
            BatchImportRequest.BatchFileDto fileDto,
            String defaultParentId
    ) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return txTemplate.execute(status -> executeSingleFileCommit(fileDto, defaultParentId));
        } catch (Exception e) {
            System.err.println("[BQP Batch Import Error] File " + fileDto.getFileName() + " thất bại: " + e.getMessage());
            return BatchImportResult.FileResult.builder()
                    .fileName(fileDto.getFileName())
                    .displayUnitName(fileDto.getDisplayUnitName())
                    .status("FAILED")
                    .message("Lỗi nhập dữ liệu: " + e.getMessage())
                    .build();
        }
    }

    private BatchImportResult.FileResult executeSingleFileCommit(
            BatchImportRequest.BatchFileDto fileDto,
            String defaultParentId
    ) {
        String fileName = fileDto.getFileName();
        String displayUnitName = fileDto.getDisplayUnitName();
        if (displayUnitName == null || displayUnitName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên đơn vị đại diện cho file '" + fileName + "' không được để trống!");
        }
        displayUnitName = displayUnitName.trim();

        String rawParent = fileDto.getParentUnitId();
        if (rawParent == null || rawParent.trim().isEmpty()) {
            rawParent = defaultParentId;
        }
        if (rawParent == null || rawParent.trim().isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn đơn vị cha cho file '" + fileName + "'!");
        }
        final String parentUnitId = rawParent.trim();

        UnitModel parentUnit = unitRepository.findById(parentUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn vị cha (ID: " + parentUnitId + ") không tồn tại!"));

        if (Boolean.FALSE.equals(parentUnit.getIsActive())) {
            throw new IllegalArgumentException("Đơn vị cha '" + parentUnit.getName() + "' đang ở trạng thái tạm dừng (không hoạt động), không thể tiếp nhận dữ liệu nhập mới!");
        }

        if (parentUnit.getLevel() != null && parentUnit.getLevel() >= 4) {
            throw new IllegalArgumentException("Không thể tạo đơn vị dưới đơn vị cấp 4 ('" + parentUnit.getName() + "')!");
        }

        String fileHash = fileDto.getFileHash() != null ? fileDto.getFileHash().trim() : null;
        String duplicateAction = fileDto.getDuplicateAction() != null ? fileDto.getDuplicateAction().trim().toUpperCase() : "SKIP";

        Optional<ImportModel> existingImport = (fileHash != null && !fileHash.isEmpty())
                ? importRepository.findByFileHash(fileHash)
                : Optional.empty();

        if (existingImport.isPresent()) {
            ImportModel curImp = existingImport.get();
            int curRecCount = curImp.getCurrentRecordCount() != null ? curImp.getCurrentRecordCount() : 0;
            boolean isCleared = "CLEARED".equalsIgnoreCase(curImp.getStatus()) || curRecCount == 0;

            if (isCleared) {
                // File từng được nhập nhưng dữ liệu đã bị xóa -> tự động dọn dẹp import cũ để nhập lại mới
                importRepository.deleteImport(curImp.getId());
            } else if ("SKIP".equals(duplicateAction)) {
                return BatchImportResult.FileResult.builder()
                        .fileName(fileName)
                        .displayUnitName(displayUnitName)
                        .status("SKIPPED")
                        .importId(curImp.getId())
                        .fileUnitId(curImp.getFileUnitId())
                        .message("Tệp có cùng mã băm (hash) đã tồn tại trong hệ thống. Đã bỏ qua theo cấu hình.")
                        .build();
            } else if ("REPLACE".equals(duplicateAction)) {
                // Xóa hồ sơ của lần import cũ trước khi ghi lại
                importRepository.deleteImport(curImp.getId());
            }
        }

        // 1. Tạo hoặc tái sử dụng File Unit dưới đơn vị cha
        String normFileUnitName = normalizeUnitName(displayUnitName);
        String normParentName = normalizeUnitName(parentUnit.getName());
        UnitModel fileUnit;
        if (normFileUnitName.equals(normParentName)) {
            // Trường hợp file đại diện cho chính đơn vị cha đã chọn
            fileUnit = parentUnit;
        } else {
            Optional<UnitModel> existingFileUnit = unitRepository.findByNormalizedNameAndParentId(normFileUnitName, parentUnitId);
            if (existingFileUnit.isPresent()) {
                fileUnit = existingFileUnit.get();
                if (Boolean.FALSE.equals(fileUnit.getIsActive())) {
                    fileUnit.setIsActive(true);
                    unitRepository.update(fileUnit);
                }
            } else {
                int fileUnitLevel = Math.min((parentUnit.getLevel() != null ? parentUnit.getLevel() : 1) + 1, 4);
                fileUnit = UnitModel.builder()
                        .id(UUID.randomUUID().toString())
                        .name(displayUnitName)
                        .normalizedName(normFileUnitName)
                        .level(fileUnitLevel)
                        .parentId(parentUnitId)
                        .isActive(true)
                        .isPreset(false)
                        .build();
                unitRepository.insert(fileUnit);
            }
        }

        // 2. Tạo hoặc tái sử dụng các Internal Units dưới File Unit
        Map<String, String> internalUnitMap = new HashMap<>();
        int internalUnitsCreatedOrFound = 0;

        if (fileDto.getInternalUnits() != null) {
            int internalUnitLevel = Math.min((fileUnit.getLevel() != null ? fileUnit.getLevel() : 2) + 1, 4);

            for (BatchImportRequest.BatchInternalUnitDto internalDto : fileDto.getInternalUnits()) {
                if (internalDto.getName() == null || internalDto.getName().trim().isEmpty()) continue;
                String internalName = internalDto.getName().trim();
                String normInternalName = normalizeUnitName(internalName);

                Optional<UnitModel> existingInternal = unitRepository.findByNormalizedNameAndParentId(normInternalName, fileUnit.getId());
                UnitModel internalUnit;
                if (existingInternal.isPresent()) {
                    internalUnit = existingInternal.get();
                    if (Boolean.FALSE.equals(internalUnit.getIsActive())) {
                        internalUnit.setIsActive(true);
                        unitRepository.update(internalUnit);
                    }
                } else {
                    internalUnit = UnitModel.builder()
                            .id(UUID.randomUUID().toString())
                            .name(internalName)
                            .normalizedName(normInternalName)
                            .level(internalUnitLevel)
                            .parentId(fileUnit.getId())
                            .code(internalDto.getCode())
                            .isActive(true)
                            .isPreset(false)
                            .build();
                    unitRepository.insert(internalUnit);
                }
                internalUnitMap.put(normInternalName, internalUnit.getId());
                internalUnitsCreatedOrFound++;
            }
        }

        // 3. Chuẩn bị bản ghi hồ sơ và liên kết đúng unitId
        String importId = (existingImport.isPresent() && "REPLACE".equals(duplicateAction))
                ? existingImport.get().getId()
                : UUID.randomUUID().toString();

        List<PersonnelRecordModel> recordsToSave = new ArrayList<>();
        if (fileDto.getRecords() != null) {
            for (PersonnelRecordModel rec : fileDto.getRecords()) {
                if (rec.getId() == null || rec.getId().trim().isEmpty()) {
                    rec.setId(UUID.randomUUID().toString());
                }
                rec.setImportId(importId);
                if (rec.getSource() == null || rec.getSource().trim().isEmpty()) {
                    rec.setSource("excel");
                }

                // Gắn unitId: nếu có tên đơn vị nội bộ đã xác định thì gắn vào Internal Unit, ngược lại gắn vào File Unit
                String recUnitName = rec.getUnitName();
                String normRecUnit = recUnitName != null ? normalizeUnitName(recUnitName) : "";

                if (!normRecUnit.isEmpty() && internalUnitMap.containsKey(normRecUnit)) {
                    rec.setUnitId(internalUnitMap.get(normRecUnit));
                } else if (rec.getUnitId() != null && !rec.getUnitId().trim().isEmpty()) {
                    // Giữ unitId đã xác định
                } else {
                    rec.setUnitId(fileUnit.getId());
                }

                // Bắt buộc hồ sơ phải có unit_id hợp lệ (mục 4.7, 4.8)
                if (rec.getUnitId() == null || rec.getUnitId().trim().isEmpty()) {
                    throw new IllegalStateException("Hồ sơ '" + rec.getFullName() + "' không xác định được đơn vị quản lý (unitId rỗng)!");
                }

                // Chuẩn hóa tiền về số đồng nguyên (mục 4.11)
                if (rec.getActualTotal() != null) {
                    rec.setActualTotal((double) Math.round(rec.getActualTotal()));
                }
                if (!"excel".equalsIgnoreCase(rec.getSource())) {
                    if (rec.getCalculatedTotal() != null) {
                        rec.setCalculatedTotal((double) Math.round(rec.getCalculatedTotal()));
                    }
                    if (rec.getActualTotal() != null && rec.getCalculatedTotal() != null) {
                        rec.setDifference(rec.getCalculatedTotal() - rec.getActualTotal());
                    }
                } else {
                    rec.setCalculatedTotal(0.0);
                    rec.setDifference(0.0);
                }

                recordsToSave.add(rec);
            }
        }

        // 4. Lưu thông tin ImportModel trước để thỏa mãn ràng buộc khóa ngoại import_id trong personnel_records
        ImportModel importModel = ImportModel.builder()
                .id(importId)
                .fileName(fileName)
                .fileHash(fileHash)
                .status("REPLACE".equals(duplicateAction) ? "REPLACED" : "SUCCESS")
                .totalRows(recordsToSave.size())
                .acceptedRows(recordsToSave.size())
                .rejectedRows(0)
                .parentUnitId(parentUnitId)
                .fileUnitId(fileUnit.getId())
                .originalFileName(fileName)
                .displayUnitName(displayUnitName)
                .parserMode(fileDto.getParserMode() != null ? fileDto.getParserMode() : "STANDARD_CTC")
                .templateSignature(fileDto.getTemplateSignature())
                .completedAt(Instant.now().toString())
                .build();

        importRepository.save(importModel);

        if (!recordsToSave.isEmpty()) {
            personnelRecordRepository.saveBatch(recordsToSave);

            List<ValidationErrorModel> allErrors = new ArrayList<>();
            for (PersonnelRecordModel r : recordsToSave) {
                if (r.getErrorDetails() != null && !r.getErrorDetails().isEmpty()) {
                    for (ValidationErrorModel err : r.getErrorDetails()) {
                        if (err.getRecordId() == null || err.getRecordId().trim().isEmpty()) {
                            err.setRecordId(r.getId());
                        }
                        allErrors.add(err);
                    }
                }
            }
            if (!allErrors.isEmpty()) {
                validationErrorRepository.saveBatch(allErrors);
            }
        }

        return BatchImportResult.FileResult.builder()
                .fileName(fileName)
                .displayUnitName(displayUnitName)
                .status("REPLACE".equals(duplicateAction) ? "REPLACED" : "SUCCESS")
                .importId(importId)
                .fileUnitId(fileUnit.getId())
                .internalUnitsCount(internalUnitsCreatedOrFound)
                .recordsCount(recordsToSave.size())
                .message("Đã lưu thành công " + recordsToSave.size() + " hồ sơ vào cây đơn vị.")
                .build();
    }

    public Optional<ImportModel> getImportById(String id) {
        return maintenanceLock.callWithReadAccess(() -> importRepository.findById(id));
    }

    public Optional<ImportModel> getImportByHash(String hash) {
        return maintenanceLock.callWithReadAccess(() -> importRepository.findByFileHash(hash));
    }

    public List<ImportModel> getAllImports() {
        return maintenanceLock.callWithReadAccess(importRepository::findAll);
    }

    public void deleteImport(String id) {
        maintenanceLock.callWithWriteAccess(() -> {
            importRepository.deleteImport(id);
            return null;
        });
    }

    private String normalizeUnitName(String name) {
        return UnitNameCanonicalizer.canonicalize(name);
    }
}
