package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.model.ImportModel;
import com.appvalidateexcel.storage.model.UnitModel;
import com.appvalidateexcel.storage.repository.ImportRepository;
import com.appvalidateexcel.storage.repository.PersonnelRecordRepository;
import com.appvalidateexcel.storage.repository.UnitRepository;
import com.appvalidateexcel.storage.util.UnitNameCanonicalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final PersonnelRecordRepository recordRepository;
    private final ImportRepository importRepository;
    private final StorageMaintenanceLock maintenanceLock;
    private final ObjectMapper objectMapper;

    public UnitService(UnitRepository unitRepository,
                       PersonnelRecordRepository recordRepository,
                       ImportRepository importRepository,
                       StorageMaintenanceLock maintenanceLock,
                       ObjectMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.recordRepository = recordRepository;
        this.importRepository = importRepository;
        this.maintenanceLock = maintenanceLock;
        this.objectMapper = objectMapper;
    }

    public List<UnitModel> getAllUnits() {
        return maintenanceLock.callWithReadAccess(() -> unitRepository.findAll());
    }

    public List<UnitModel> getUnitTree() {
        return maintenanceLock.callWithReadAccess(() -> {
            List<UnitModel> allUnits = unitRepository.findAllActive();
            Map<String, UnitModel> unitMap = new LinkedHashMap<>();
            for (UnitModel u : allUnits) {
                u.setChildren(new ArrayList<>());
                unitMap.put(u.getId(), u);
            }

            List<UnitModel> rootNodes = new ArrayList<>();
            for (UnitModel u : allUnits) {
                String parentId = u.getParentId();
                if (parentId == null || parentId.trim().isEmpty() || !unitMap.containsKey(parentId)) {
                    rootNodes.add(u);
                } else {
                    UnitModel parent = unitMap.get(parentId);
                    parent.getChildren().add(u);
                }
            }

            return rootNodes;
        });
    }

    public Optional<UnitModel> getUnitById(String id) {
        return maintenanceLock.callWithReadAccess(() -> unitRepository.findById(id));
    }

    @Transactional
    public UnitModel createUnit(UnitModel unit) {
        return maintenanceLock.callWithWriteAccess(() -> {
            if (unit.getName() == null || unit.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Tên đơn vị không được để trống!");
            }
            if (unit.getId() == null || unit.getId().trim().isEmpty()) {
                unit.setId(UUID.randomUUID().toString());
            }
            unit.setName(unit.getName().trim());
            unit.setNormalizedName(normalizeUnitName(unit.getName()));

            // 1. Tính toán level dựa trên parentId
            String parentId = (unit.getParentId() != null && !unit.getParentId().trim().isEmpty())
                    ? unit.getParentId().trim() : null;
            unit.setParentId(parentId);

            if (parentId == null) {
                // Đơn vị cấp 1 (Gốc)
                unit.setLevel(1);
            } else {
                UnitModel parent = unitRepository.findById(parentId)
                        .orElseThrow(() -> new IllegalArgumentException("Đơn vị cha không tồn tại (ID: " + parentId + ")"));
                if (parent.getIsActive() != null && !parent.getIsActive() && (unit.getIsActive() == null || unit.getIsActive())) {
                    throw new IllegalArgumentException("Không thể tạo đơn vị hoạt động trực thuộc đơn vị cha đang tạm dừng hoạt động ('" + parent.getName() + "')!");
                }
                int calculatedLevel = (parent.getLevel() != null ? parent.getLevel() : 1) + 1;
                if (calculatedLevel > 4) {
                    throw new IllegalArgumentException("Không thể tạo đơn vị vượt quá giới hạn 4 cấp (Đơn vị cha '" +
                            parent.getName() + "' đã ở cấp " + parent.getLevel() + ")!");
                }
                unit.setLevel(calculatedLevel);
            }

            // 2. Kiểm tra tính duy nhất của mã đơn vị (code)
            if (unit.getCode() != null && !unit.getCode().trim().isEmpty()) {
                unit.setCode(unit.getCode().trim().toUpperCase());
                if (unitRepository.existsByCode(unit.getCode(), null)) {
                    throw new IllegalArgumentException("Mã đơn vị '" + unit.getCode() + "' đã tồn tại trong hệ thống!");
                }
            } else {
                unit.setCode(null);
            }

            // 3. Kiểm tra tính duy nhất của tên trong cùng đơn vị cha
            if (unitRepository.existsByNormalizedNameAndParentId(unit.getNormalizedName(), parentId, null)) {
                throw new IllegalArgumentException("Đơn vị có tên '" + unit.getName() + "' đã tồn tại trong cùng đơn vị cha!");
            }

            unitRepository.insert(unit);
            UnitModel created = unitRepository.findById(unit.getId()).orElse(unit);

            recordAudit(unit.getId(), "CREATE", null, created);
            return created;
        });
    }

    @Transactional
    public UnitModel updateUnit(String id, UnitModel updated) {
        return maintenanceLock.callWithWriteAccess(() -> {
            UnitModel existing = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));

            UnitModel oldState = cloneUnit(existing);

            String newParentId = (updated.getParentId() != null && !updated.getParentId().trim().isEmpty())
                    ? updated.getParentId().trim() : null;

            // Kiểm tra tên
            String newName = updated.getName() != null ? updated.getName().trim() : existing.getName();
            if (newName.isEmpty()) {
                throw new IllegalArgumentException("Tên đơn vị không được để trống!");
            }
            String newNormName = normalizeUnitName(newName);

            // Kiểm tra trùng tên trong đơn vị cha
            String effectiveParentId = (updated.getParentId() != null) ? newParentId : existing.getParentId();
            if (unitRepository.existsByNormalizedNameAndParentId(newNormName, effectiveParentId, id)) {
                throw new IllegalArgumentException("Đơn vị có tên '" + newName + "' đã tồn tại trong cùng đơn vị cha!");
            }

            // Kiểm tra mã code
            String newCode = updated.getCode() != null ? updated.getCode().trim().toUpperCase() : existing.getCode();
            if (newCode != null && !newCode.isEmpty()) {
                if (unitRepository.existsByCode(newCode, id)) {
                    throw new IllegalArgumentException("Mã đơn vị '" + newCode + "' đã tồn tại trong hệ thống!");
                }
            } else {
                newCode = null;
            }

            existing.setName(newName);
            existing.setNormalizedName(newNormName);
            existing.setCode(newCode);
            if (updated.getUnitType() != null) existing.setUnitType(updated.getUnitType().trim());
            if (updated.getDisplayOrder() != null) existing.setDisplayOrder(updated.getDisplayOrder());
            if (updated.getAliasesJson() != null) existing.setAliasesJson(updated.getAliasesJson());
            if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

            // Nếu thay đổi parentId -> thực hiện chuyển nhánh an toàn và cập nhật level cả cây con
            if (updated.getParentId() != null && !Objects.equals(newParentId, existing.getParentId())) {
                moveUnitInternal(id, newParentId, existing);
            } else {
                unitRepository.update(existing);
            }

            UnitModel saved = unitRepository.findById(id).orElse(existing);
            recordAudit(id, "UPDATE", oldState, saved);
            return saved;
        });
    }

    @Transactional
    public void moveUnit(String id, String newParentId) {
        maintenanceLock.runWithWriteAccess(() -> {
            UnitModel existing = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));
            UnitModel oldState = cloneUnit(existing);

            String targetParentId = (newParentId != null && !newParentId.trim().isEmpty())
                    ? newParentId.trim() : null;

            moveUnitInternal(id, targetParentId, existing);

            UnitModel newState = unitRepository.findById(id).orElse(existing);
            recordAudit(id, "MOVE", oldState, newState);
        });
    }

    private void moveUnitInternal(String id, String newParentId, UnitModel existing) {
        if (id.equals(newParentId)) {
            throw new IllegalArgumentException("Đơn vị không thể chọn chính mình làm đơn vị cha!");
        }

        int targetParentLevel = 0;
        if (newParentId != null) {
            UnitModel targetParent = unitRepository.findById(newParentId)
                    .orElseThrow(() -> new IllegalArgumentException("Đơn vị cha mới không tồn tại (ID: " + newParentId + ")"));
            if (targetParent.getIsActive() != null && !targetParent.getIsActive() && (existing.getIsActive() == null || existing.getIsActive())) {
                throw new IllegalArgumentException("Không thể di chuyển đơn vị đang hoạt động vào đơn vị cha đang tạm dừng hoạt động ('" + targetParent.getName() + "')!");
            }
            if (unitRepository.wouldCreateCycle(id, newParentId)) {
                throw new IllegalStateException("Không thể chuyển đơn vị: Tạo thành chu trình vòng lặp cha-con!");
            }
            targetParentLevel = targetParent.getLevel() != null ? targetParent.getLevel() : 1;
        }

        // Kiểm tra độ sâu tối đa của cây con
        int maxSubtreeDepth = unitRepository.getMaxSubtreeDepth(id);
        int newRootLevel = targetParentLevel + 1;
        if (targetParentLevel + maxSubtreeDepth > 4) {
            throw new IllegalArgumentException("Không thể chuyển đơn vị: Nhánh con của đơn vị sau khi chuyển sẽ vượt quá cấp 4 (Độ sâu nhánh: " +
                    maxSubtreeDepth + ", cấp cha mới: " + targetParentLevel + ")!");
        }

        // Cập nhật parentId và level
        existing.setParentId(newParentId);
        existing.setLevel(newRootLevel);
        unitRepository.update(existing);

        // Đệ quy cập nhật level cho toàn bộ các đơn vị con cháu trong nhánh
        syncSubtreeLevels(id, newRootLevel);
    }

    private void syncSubtreeLevels(String parentUnitId, int currentLevel) {
        List<UnitModel> children = unitRepository.findByParentId(parentUnitId);
        for (UnitModel child : children) {
            int childLevel = currentLevel + 1;
            unitRepository.updateLevel(child.getId(), childLevel);
            syncSubtreeLevels(child.getId(), childLevel);
        }
    }

    @Transactional
    public void deactivateUnit(String id) {
        maintenanceLock.runWithWriteAccess(() -> {
            UnitModel existing = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));
            List<String> descendants = unitRepository.getAllDescendantIds(id);
            for (String descId : descendants) {
                unitRepository.setActive(descId, false);
            }
            recordAudit(id, "DEACTIVATE", existing, Map.of("isActive", false, "descendantCount", descendants.size()));
        });
    }

    @Transactional
    public void activateUnit(String id, boolean includeDescendants) {
        maintenanceLock.runWithWriteAccess(() -> {
            UnitModel existing = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));
            unitRepository.setActive(id, true);
            int descendantCount = 0;
            if (includeDescendants) {
                List<String> descendants = unitRepository.getAllDescendantIds(id);
                descendants.remove(id); // bỏ chính đơn vị này vì đã setActive ở trên
                for (String descId : descendants) {
                    unitRepository.setActive(descId, true);
                }
                descendantCount = descendants.size();
            }
            recordAudit(id, "ACTIVATE", existing, Map.of("isActive", true, "includeDescendants", includeDescendants, "descendantCount", descendantCount));
        });
    }

    @Transactional
    public boolean deleteUnit(String id) {
        return maintenanceLock.callWithWriteAccess(() -> {
            UnitModel existing = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));

            if (unitRepository.hasRecords(id)) {
                throw new IllegalStateException("Đơn vị '" + existing.getName() + "' đang chứa hồ sơ nghiệp vụ, chỉ được phép ngừng sử dụng, không được xóa cứng!");
            }
            if (unitRepository.hasChildren(id)) {
                throw new IllegalStateException("Đơn vị '" + existing.getName() + "' đang chứa các đơn vị trực thuộc cấp dưới. Vui lòng di chuyển hoặc xóa các đơn vị con trước!");
            }

            int importRefCount = importRepository.countImportsReferencingUnit(id);
            if (importRefCount > 0) {
                importRepository.clearUnitReferences(id);
            }

            boolean ok = unitRepository.delete(id);
            if (ok) {
                recordAudit(id, "DELETE", existing, null);
            }
            return ok;
        });
    }

    @Transactional
    public int deleteUnitCascade(String id) {
        return maintenanceLock.callWithWriteAccess(() -> {
            List<String> descendants = unitRepository.getAllDescendantIds(id);
            int recordCount = unitRepository.countRecordsInUnits(descendants);
            if (recordCount > 0) {
                throw new IllegalStateException("Không thể xóa đơn vị vì đang có " + recordCount + " hồ sơ thuộc đơn vị này hoặc cấp dưới.");
            }
            for (String uid : descendants) {
                importRepository.clearUnitReferences(uid);
            }
            int deleted = unitRepository.deleteBatch(descendants);
            recordAudit(id, "DELETE_CASCADE", Map.of("descendants", descendants), null);
            return deleted;
        });
    }

    @Transactional
    public int batchSave(List<UnitModel> units) {
        if (units == null || units.isEmpty()) return 0;
        return maintenanceLock.callWithWriteAccess(() -> {
            Set<String> seen = new HashSet<>();
            Map<String, UnitModel> batchMap = new HashMap<>();
            for (UnitModel u : units) {
                if (u.getName() == null || u.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("Tên đơn vị không được để trống trong dữ liệu hàng loạt.");
                }
                if (u.getId() == null || u.getId().trim().isEmpty()) {
                    u.setId(UUID.randomUUID().toString());
                }
                if (batchMap.containsKey(u.getId())) {
                    throw new IllegalArgumentException("Dữ liệu hàng loạt chứa ID trùng lặp: " + u.getId());
                }
                batchMap.put(u.getId(), u);
            }

            for (UnitModel u : units) {
                u.setNormalizedName(UnitNameCanonicalizer.canonicalize(u.getName()));
                String key = (u.getParentId() == null ? "" : u.getParentId()) + "::" + u.getNormalizedName();
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Dữ liệu nhập hàng loạt chứa đơn vị trùng lặp: '" + u.getName() + "'");
                }

                if (u.getParentId() != null && !u.getParentId().trim().isEmpty()) {
                    if (u.getId().equals(u.getParentId())) {
                        throw new IllegalArgumentException("Đơn vị không thể tự làm cha của chính mình: '" + u.getName() + "'");
                    }
                    UnitModel parent = batchMap.get(u.getParentId());
                    if (parent == null) {
                        parent = unitRepository.findById(u.getParentId()).orElse(null);
                    }
                    if (parent != null) {
                        boolean childActive = u.getIsActive() == null || Boolean.TRUE.equals(u.getIsActive());
                        boolean parentInactive = Boolean.FALSE.equals(parent.getIsActive());
                        if (childActive && parentInactive) {
                            throw new IllegalArgumentException("Không thể tạo đơn vị đang hoạt động '" + u.getName() +
                                    "' dưới đơn vị cha đang tạm dừng hoạt động ('" + parent.getName() + "').");
                        }
                    }
                }
            }
            unitRepository.batchInsert(units);
            return units.size();
        });
    }

    @Transactional
    public int syncUnits(List<UnitModel> units) {
        if (units == null || units.isEmpty()) {
            List<UnitModel> existing = unitRepository.findAll();
            if (existing.isEmpty()) {
                return 0;
            }
            throw new IllegalArgumentException("Payload đồng bộ đơn vị không được rỗng khi hệ thống đang có " +
                    existing.size() + " đơn vị. Để bảo vệ cấu hình cây đơn vị, thao tác xóa toàn bộ bị từ chối.");
        }

        for (UnitModel u : units) {
            if (u.getName() == null || u.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Đơn vị trong danh sách đồng bộ không được có tên rỗng!");
            }
            if (u.getId() != null && u.getId().equals(u.getParentId())) {
                throw new IllegalArgumentException("Đơn vị '" + u.getName() + "' không thể chọn chính mình làm đơn vị cha!");
            }
        }

        return maintenanceLock.callWithWriteAccess(() -> {
            List<UnitModel> existing = unitRepository.findAll();
            Set<String> incomingIds = units.stream()
                    .map(UnitModel::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            // 1. Xác định các đơn vị có trong DB nhưng không có trong danh sách đồng bộ gửi lên -> cần xóa
            List<UnitModel> toDelete = existing.stream()
                    .filter(u -> !incomingIds.contains(u.getId()))
                    .collect(java.util.stream.Collectors.toList());

            if (!toDelete.isEmpty()) {
                // Kiểm tra nghiêm ngặt: Nếu bất kỳ đơn vị nào bị yêu cầu loại bỏ đang có hồ sơ (chính nó hoặc các đơn vị con),
                // NÉM NGOẠI LỆ 409 để rollback transaction và yêu cầu người dùng xử lý dữ liệu trước
                List<String> conflictUnitNames = new ArrayList<>();
                int totalConflictRecords = 0;
                for (UnitModel u : toDelete) {
                    List<String> desc = unitRepository.getAllDescendantIds(u.getId());
                    int recs = unitRepository.countRecordsInUnits(desc);
                    if (recs > 0) {
                        conflictUnitNames.add("'" + u.getName() + "' (" + recs + " hồ sơ)");
                        totalConflictRecords += recs;
                    }
                }
                if (!conflictUnitNames.isEmpty()) {
                    throw new IllegalStateException("Không thể đồng bộ đơn vị: Có " + conflictUnitNames.size() +
                            " đơn vị bị loại bỏ nhưng đang chứa dữ liệu (" + totalConflictRecords +
                            " hồ sơ): " + String.join(", ", conflictUnitNames) +
                            ". Vui lòng xóa hoặc di chuyển các hồ sơ này trước khi xóa đơn vị.");
                }

                // Xóa an toàn từ lá lên gốc: lặp xóa các đơn vị lá (không còn con nào trong DB)
                Set<String> remainingToDelete = toDelete.stream().map(UnitModel::getId).collect(java.util.stream.Collectors.toSet());
                boolean deletedAny = true;
                while (!remainingToDelete.isEmpty() && deletedAny) {
                    deletedAny = false;
                    List<String> leaves = new ArrayList<>();
                    for (String id : remainingToDelete) {
                        if (!unitRepository.hasChildren(id)) {
                            leaves.add(id);
                        }
                    }
                    if (!leaves.isEmpty()) {
                        unitRepository.deleteBatch(leaves);
                        remainingToDelete.removeAll(leaves);
                        deletedAny = true;
                    }
                }
            }

            // 2. Chèn / cập nhật các đơn vị mới và hiện có theo level tăng dần (gốc trước, lá sau)
            if (units != null && !units.isEmpty()) {
                List<UnitModel> sorted = new ArrayList<>(units);
                sorted.sort(Comparator.comparingInt(u -> (u.getLevel() != null ? u.getLevel() : 4)));
                batchSave(sorted);
            }

            return unitRepository.findAll().size();
        });
    }

    public List<String> getAllDescendantIds(String unitId) {
        return maintenanceLock.callWithReadAccess(() -> unitRepository.getAllDescendantIds(unitId));
    }

    public List<Map<String, Object>> getAuditHistory(String unitId) {
        return maintenanceLock.callWithReadAccess(() -> unitRepository.getAuditLogsByUnitId(unitId));
    }

    public Map<String, Object> getInventoryReport() {
        return maintenanceLock.callWithReadAccess(() -> {
            List<UnitModel> allUnits = unitRepository.findAll();
            Map<String, Object> report = new LinkedHashMap<>();

            int totalUnits = allUnits.size();
            int level1 = 0, level2 = 0, level3 = 0, level4 = 0, invalidLevel = 0;
            int activeCount = 0, inactiveCount = 0;

            Set<String> allUnitIds = new HashSet<>();
            for (UnitModel u : allUnits) {
                allUnitIds.add(u.getId());
                Integer lvl = u.getLevel();
                if (lvl == null) invalidLevel++;
                else if (lvl == 1) level1++;
                else if (lvl == 2) level2++;
                else if (lvl == 3) level3++;
                else if (lvl == 4) level4++;
                else invalidLevel++;

                if (Boolean.TRUE.equals(u.getIsActive())) activeCount++;
                else inactiveCount++;
            }

            List<Map<String, Object>> orphanUnits = new ArrayList<>();
            Map<String, List<String>> codeGroups = new HashMap<>();
            Map<String, List<String>> nameInParentGroups = new HashMap<>();

            for (UnitModel u : allUnits) {
                String pId = u.getParentId();
                if (pId != null && !pId.trim().isEmpty() && !allUnitIds.contains(pId)) {
                    orphanUnits.add(Map.of("id", u.getId(), "name", u.getName(), "invalidParentId", pId));
                }

                if (u.getCode() != null && !u.getCode().trim().isEmpty()) {
                    codeGroups.computeIfAbsent(u.getCode().toUpperCase(), k -> new ArrayList<>()).add(u.getId());
                }

                String pKey = (pId == null ? "ROOT" : pId) + "::" + (u.getNormalizedName() == null ? "" : u.getNormalizedName());
                nameInParentGroups.computeIfAbsent(pKey, k -> new ArrayList<>()).add(u.getId());
            }

            List<Map<String, Object>> duplicateCodes = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : codeGroups.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplicateCodes.add(Map.of("code", entry.getKey(), "unitIds", entry.getValue()));
                }
            }

            List<Map<String, Object>> duplicateNamesInParent = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : nameInParentGroups.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplicateNamesInParent.add(Map.of("parentAndName", entry.getKey(), "unitIds", entry.getValue()));
                }
            }

            report.put("totalUnits", totalUnits);
            report.put("activeUnits", activeCount);
            report.put("inactiveUnits", inactiveCount);
            report.put("level1Count", level1);
            report.put("level2Count", level2);
            report.put("level3Count", level3);
            report.put("level4Count", level4);
            report.put("invalidLevelCount", invalidLevel);
            report.put("orphanUnitsCount", orphanUnits.size());
            report.put("orphanUnits", orphanUnits);
            report.put("duplicateCodesCount", duplicateCodes.size());
            report.put("duplicateCodes", duplicateCodes);
            report.put("duplicateNamesInParentCount", duplicateNamesInParent.size());
            report.put("duplicateNamesInParent", duplicateNamesInParent);
            // Kiểm tra toàn vẹn nghiệp vụ (Section 4.7 RA_SOAT_LAN_3)
            int activeRootCount = 0;
            Map<String, UnitModel> allUnitMap = new HashMap<>();
            for (UnitModel u : allUnits) {
                allUnitMap.put(u.getId(), u);
                if ((u.getParentId() == null || u.getParentId().trim().isEmpty()) && Boolean.TRUE.equals(u.getIsActive())) {
                    activeRootCount++;
                }
            }

            List<Map<String, Object>> activeUnitsWithInactiveAncestor = new ArrayList<>();
            for (UnitModel u : allUnits) {
                if (Boolean.TRUE.equals(u.getIsActive()) && u.getParentId() != null && !u.getParentId().trim().isEmpty()) {
                    String curParentId = u.getParentId();
                    while (curParentId != null && allUnitMap.containsKey(curParentId)) {
                        UnitModel p = allUnitMap.get(curParentId);
                        if (Boolean.FALSE.equals(p.getIsActive())) {
                            activeUnitsWithInactiveAncestor.add(Map.of(
                                    "id", u.getId(),
                                    "name", u.getName(),
                                    "inactiveAncestorId", p.getId(),
                                    "inactiveAncestorName", p.getName()
                            ));
                            break;
                        }
                        curParentId = p.getParentId();
                    }
                }
            }

            List<ImportModel> allImports = importRepository.findAll();
            List<Map<String, Object>> importsWithMissingRecords = new ArrayList<>();
            List<Map<String, Object>> importsReferencingInactiveUnits = new ArrayList<>();
            List<Map<String, Object>> importsAcceptedVsActualMismatch = new ArrayList<>();

            for (ImportModel imp : allImports) {
                int curRec = imp.getCurrentRecordCount() != null ? imp.getCurrentRecordCount() : 0;
                if ("SUCCESS".equalsIgnoreCase(imp.getStatus()) && curRec == 0) {
                    importsWithMissingRecords.add(Map.of(
                            "id", imp.getId(),
                            "fileName", imp.getFileName() != null ? imp.getFileName() : ""
                    ));
                }
                if (imp.getAcceptedRows() != null && imp.getAcceptedRows() > 0 && curRec != imp.getAcceptedRows() && !"CLEARED".equalsIgnoreCase(imp.getStatus())) {
                    importsAcceptedVsActualMismatch.add(Map.of(
                            "id", imp.getId(),
                            "accepted", imp.getAcceptedRows(),
                            "current", curRec
                    ));
                }
                if (imp.getParentUnitId() != null && allUnitMap.containsKey(imp.getParentUnitId())) {
                    UnitModel pu = allUnitMap.get(imp.getParentUnitId());
                    if (Boolean.FALSE.equals(pu.getIsActive())) {
                        importsReferencingInactiveUnits.add(Map.of(
                                "id", imp.getId(),
                                "inactiveParentUnitId", pu.getId(),
                                "inactiveParentName", pu.getName()
                        ));
                    }
                }
                if (imp.getFileUnitId() != null && allUnitMap.containsKey(imp.getFileUnitId())) {
                    UnitModel fu = allUnitMap.get(imp.getFileUnitId());
                    if (Boolean.FALSE.equals(fu.getIsActive())) {
                        importsReferencingInactiveUnits.add(Map.of(
                                "id", imp.getId(),
                                "inactiveFileUnitId", fu.getId(),
                                "inactiveFileName", fu.getName()
                        ));
                    }
                }
            }

            boolean physicalHealthy = orphanUnits.isEmpty() && duplicateCodes.isEmpty() && duplicateNamesInParent.isEmpty() && invalidLevel == 0;
            boolean businessHealthy = activeUnitsWithInactiveAncestor.isEmpty()
                    && importsWithMissingRecords.isEmpty()
                    && importsReferencingInactiveUnits.isEmpty()
                    && importsAcceptedVsActualMismatch.isEmpty()
                    && (activeCount == 0 || activeRootCount > 0);

            Map<String, Object> businessIntegrity = new LinkedHashMap<>();
            businessIntegrity.put("healthy", businessHealthy);
            businessIntegrity.put("activeRootCount", activeRootCount);
            businessIntegrity.put("activeUnitsWithInactiveAncestorCount", activeUnitsWithInactiveAncestor.size());
            businessIntegrity.put("activeUnitsWithInactiveAncestor", activeUnitsWithInactiveAncestor);
            businessIntegrity.put("importsWithMissingRecordsCount", importsWithMissingRecords.size());
            businessIntegrity.put("importsWithMissingRecords", importsWithMissingRecords);
            businessIntegrity.put("importsReferencingInactiveUnitsCount", importsReferencingInactiveUnits.size());
            businessIntegrity.put("importsReferencingInactiveUnits", importsReferencingInactiveUnits);
            businessIntegrity.put("importsAcceptedVsActualMismatchCount", importsAcceptedVsActualMismatch.size());
            businessIntegrity.put("importsAcceptedVsActualMismatch", importsAcceptedVsActualMismatch);

            report.put("businessIntegrity", businessIntegrity);
            report.put("healthy", physicalHealthy && businessHealthy);

            return report;
        });
    }

    private UnitModel cloneUnit(UnitModel u) {
        if (u == null) return null;
        return UnitModel.builder()
                .id(u.getId())
                .code(u.getCode())
                .name(u.getName())
                .normalizedName(u.getNormalizedName())
                .level(u.getLevel())
                .unitType(u.getUnitType())
                .parentId(u.getParentId())
                .displayOrder(u.getDisplayOrder())
                .aliasesJson(u.getAliasesJson())
                .isPreset(u.getIsPreset())
                .isActive(u.getIsActive())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private void recordAudit(String unitId, String action, Object oldState, Object newState) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String oldJson = oldState != null ? mapper.writeValueAsString(oldState) : null;
            String newJson = newState != null ? mapper.writeValueAsString(newState) : null;
            unitRepository.recordAudit(unitId, action, oldJson, newJson);
        } catch (Exception e) {
            System.err.println("[BQP Unit Audit] Lỗi ghi nhật ký audit: " + e.getMessage());
        }
    }

    /**
     * Chuẩn hóa tên đơn vị dùng để đối chiếu trùng lặp thông qua UnitNameCanonicalizer dùng chung.
     */
    private String normalizeUnitName(String name) {
        return UnitNameCanonicalizer.canonicalize(name);
    }

    /**
     * Tính toán lại normalized_name cho tất cả đơn vị theo thuật toán hiện tại.
     * Gọi sau khi nâng cấp thuật toán normalizeUnitName để đồng bộ dữ liệu cũ.
     * @return số lượng đơn vị đã được cập nhật
     */
    @Transactional
    public int recomputeNormalizedNames() {
        return maintenanceLock.callWithWriteAccess(() -> {
            List<UnitModel> allUnits = unitRepository.findAll();
            int updated = 0;
            for (UnitModel u : allUnits) {
                String newNorm = normalizeUnitName(u.getName());
                if (!newNorm.equals(u.getNormalizedName())) {
                    u.setNormalizedName(newNorm);
                    unitRepository.update(u);
                    updated++;
                }
            }
            return updated;
        });
    }

    /**
     * Gộp các đơn vị phụ (secondary) vào một đơn vị chính (primary).
     * Chuyển toàn bộ hồ sơ, con cháu, gộp bí danh và xóa an toàn các nút phụ.
     */
    @Transactional
    public Map<String, Object> mergeUnits(String primaryUnitId, List<String> secondaryUnitIds) {
        return maintenanceLock.callWithWriteAccess(() -> {
            UnitModel primary = unitRepository.findById(primaryUnitId)
                    .orElseThrow(() -> new IllegalArgumentException("Đơn vị chính (ID: " + primaryUnitId + ") không tồn tại!"));

            int totalRecordsMoved = 0;
            int totalChildrenMoved = 0;
            List<String> mergedIds = new ArrayList<>();
            List<String> aliasList = new ArrayList<>();

            if (primary.getAliasesJson() != null && !primary.getAliasesJson().trim().isEmpty()) {
                try {
                    List<String> existing = objectMapper.readValue(primary.getAliasesJson(), new TypeReference<List<String>>() {});
                    aliasList.addAll(existing);
                } catch (Exception ignored) {}
            }

            // Kiểm tra chu trình trước khi gộp (mục 4.6 RA_SOAT_LAN_3)
            List<String> primaryDescendants = unitRepository.getAllDescendantIds(primaryUnitId);
            for (String secId : secondaryUnitIds) {
                if (secId == null || secId.trim().isEmpty() || secId.equals(primaryUnitId)) continue;
                if (primaryDescendants.contains(secId)) {
                    throw new IllegalArgumentException("Không thể gộp đơn vị con/cháu vào đơn vị cha/ông vì sẽ tạo vòng lặp cây đơn vị!");
                }
                List<String> secDescendants = unitRepository.getAllDescendantIds(secId);
                if (secDescendants.contains(primaryUnitId)) {
                    throw new IllegalArgumentException("Không thể gộp đơn vị tổ tiên vào đơn vị con/cháu vì sẽ tạo vòng lặp cây đơn vị!");
                }
            }

            for (String secId : secondaryUnitIds) {
                if (secId == null || secId.trim().isEmpty() || secId.equals(primaryUnitId)) continue;
                Optional<UnitModel> secOpt = unitRepository.findById(secId);
                if (secOpt.isEmpty()) continue;
                UnitModel sec = secOpt.get();

                if (sec.getName() != null && !sec.getName().equalsIgnoreCase(primary.getName()) && !aliasList.contains(sec.getName())) {
                    aliasList.add(sec.getName());
                }
                if (sec.getAliasesJson() != null && !sec.getAliasesJson().trim().isEmpty()) {
                    try {
                        List<String> secAliases = objectMapper.readValue(sec.getAliasesJson(), new TypeReference<List<String>>() {});
                        for (String a : secAliases) {
                            if (!a.equalsIgnoreCase(primary.getName()) && !aliasList.contains(a)) {
                                aliasList.add(a);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 1. Chuyển hồ sơ
                int recMoved = recordRepository.reassignUnit(secId, primaryUnitId);
                totalRecordsMoved += recMoved;

                // 2. Chuyển liên kết import sang đơn vị chính (mục 4.6 RA_SOAT_LAN_3)
                importRepository.reassignUnitReferences(secId, primaryUnitId);

                // 3. Xử lý va chạm tên đơn vị con trước khi chuyển con
                List<UnitModel> primaryChildren = unitRepository.findByParentId(primaryUnitId);
                Map<String, UnitModel> primaryChildMap = new HashMap<>();
                for (UnitModel pc : primaryChildren) {
                    if (pc.getNormalizedName() != null) {
                        primaryChildMap.put(pc.getNormalizedName(), pc);
                    }
                }

                List<UnitModel> secChildren = unitRepository.findByParentId(secId);
                for (UnitModel sc : secChildren) {
                    if (sc.getNormalizedName() != null && primaryChildMap.containsKey(sc.getNormalizedName())) {
                        UnitModel matchingPrimaryChild = primaryChildMap.get(sc.getNormalizedName());
                        recordRepository.reassignUnit(sc.getId(), matchingPrimaryChild.getId());
                        importRepository.reassignUnitReferences(sc.getId(), matchingPrimaryChild.getId());
                        unitRepository.reassignParent(sc.getId(), matchingPrimaryChild.getId());
                        unitRepository.delete(sc.getId());
                    }
                }

                // Chuyển các đơn vị con còn lại sang primaryUnit
                int childMoved = unitRepository.reassignParent(secId, primaryUnitId);
                totalChildrenMoved += childMoved;

                // 4. Xóa đơn vị phụ
                unitRepository.delete(secId);
                mergedIds.add(secId);

                // 5. Ghi audit log
                unitRepository.recordAudit(primaryUnitId, "MERGE",
                        "{\"sourceUnitId\":\"" + secId + "\",\"sourceName\":\"" + sec.getName() + "\",\"recordsMoved\":" + recMoved + "}",
                        "{\"targetUnitId\":\"" + primaryUnitId + "\"}");
            }

            try {
                primary.setAliasesJson(objectMapper.writeValueAsString(aliasList));
            } catch (Exception ignored) {}
            primary.setNormalizedName(UnitNameCanonicalizer.canonicalize(primary.getName()));
            unitRepository.update(primary);

            return Map.of(
                    "status", "MERGED",
                    "primaryUnitId", primaryUnitId,
                    "primaryName", primary.getName(),
                    "mergedUnitCount", mergedIds.size(),
                    "recordsMoved", totalRecordsMoved,
                    "childrenMoved", totalChildrenMoved
            );
        });
    }

    /**
     * Lấy thống kê nhánh đơn vị (để hiển thị xem trước trước khi xóa - mục 235).
     * @return Map gồm: descendantCount, directRecordCount, totalRecordCount
     */
    public Map<String, Object> getBranchInfo(String id) {
        return maintenanceLock.callWithReadAccess(() -> {
            UnitModel unit = unitRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị với ID: " + id));
            List<String> allIds = unitRepository.getAllDescendantIds(id);
            // allIds bao gồm cả id gốc (theo CTE hiện tại)
            int directRecordCount = unitRepository.countRecordsInUnits(List.of(id));
            int totalRecordCount  = unitRepository.countRecordsInUnits(allIds);
            int descendantCount   = Math.max(0, allIds.size() - 1); // trừ chính nó
            return Map.of(
                    "id",                id,
                    "name",              unit.getName(),
                    "descendantCount",   descendantCount,
                    "directRecordCount", directRecordCount,
                    "totalRecordCount",  totalRecordCount,
                    "canDelete",         totalRecordCount == 0
            );
        });
    }
}
