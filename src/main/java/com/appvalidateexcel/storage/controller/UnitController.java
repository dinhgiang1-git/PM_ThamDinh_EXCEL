package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.model.UnitModel;
import com.appvalidateexcel.storage.service.UnitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<UnitModel>> getAllUnits() {
        return ResponseEntity.ok(unitService.getAllUnits());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<UnitModel>> getUnitTree() {
        return ResponseEntity.ok(unitService.getUnitTree());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitModel> getUnitById(@PathVariable String id) {
        return unitService.getUnitById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> getInventoryReport() {
        return ResponseEntity.ok(unitService.getInventoryReport());
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> getUnitHistory(@PathVariable String id) {
        return ResponseEntity.ok(unitService.getAuditHistory(id));
    }

    @PostMapping
    public ResponseEntity<?> createUnit(@RequestBody UnitModel unit) {
        try {
            return ResponseEntity.ok(unitService.createUnit(unit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUnit(@PathVariable String id, @RequestBody UnitModel unit) {
        try {
            return ResponseEntity.ok(unitService.updateUnit(id, unit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<?> moveUnit(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String newParentId = body.get("newParentId");
            unitService.moveUnit(id, newParentId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateUnit(@PathVariable String id) {
        unitService.deactivateUnit(id);
        return ResponseEntity.ok(Map.of("status", "DEACTIVATED"));
    }

    /**
     * Kích hoạt đơn vị (mục 238 - RA_SOAT_NANG_CAP).
     * @param includeDescendants Nếu true, kích hoạt lại cả đơn vị lẫn toàn bộ các đơn vị con cháu.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateUnit(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "false") boolean includeDescendants
    ) {
        unitService.activateUnit(id, includeDescendants);
        return ResponseEntity.ok(Map.of("status", "ACTIVATED", "includeDescendants", includeDescendants));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchSaveUnits(@RequestBody List<UnitModel> units) {
        int count = unitService.batchSave(units);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "count", count));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncUnits(@RequestBody List<UnitModel> units) {
        try {
            int count = unitService.syncUnits(units);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "count", count,
                    "units", unitService.getAllUnits()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "ERROR", "error", e.getMessage(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "error", e.getMessage(), "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUnit(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "false") boolean includeDescendants
    ) {
        if (includeDescendants) {
            try {
                int count = unitService.deleteUnitCascade(id);
                return ResponseEntity.ok(Map.of("status", "DELETED", "count", count));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }
        try {
            boolean success = unitService.deleteUnit(id);
            if (success) {
                return ResponseEntity.ok(Map.of("status", "DELETED"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa đơn vị đã có hồ sơ hoặc có đơn vị con. Hãy dùng chức năng ngưng sử dụng (deactivate) hoặc xóa kèm đơn vị con."));
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy thống kê nhánh đơn vị để hiển thị xem trước trước khi xóa (mục 235 - RA_SOAT_NANG_CAP).
     * Trả về: descendantCount, directRecordCount, totalRecordCount, canDelete
     */
    @GetMapping("/{id}/branch-info")
    public ResponseEntity<?> getBranchInfo(@PathVariable String id) {
        try {
            return ResponseEntity.ok(unitService.getBranchInfo(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gộp các đơn vị trùng vào đơn vị chính.
     */
    @PostMapping("/merge")
    public ResponseEntity<?> mergeUnits(@RequestBody Map<String, Object> body) {
        try {
            String primaryUnitId = (String) body.get("primaryUnitId");
            @SuppressWarnings("unchecked")
            List<String> secondaryUnitIds = (List<String>) body.get("secondaryUnitIds");
            if (primaryUnitId == null || secondaryUnitIds == null || secondaryUnitIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "primaryUnitId và secondaryUnitIds không được để trống!"));
            }
            Map<String, Object> result = unitService.mergeUnits(primaryUnitId, secondaryUnitIds);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Tính lại normalized_name cho toàn bộ đơn vị sau khi nâng cấp thuật toán (mục 237).
     * Chỉ gọi một lần sau khi deploy phân bản này.
     */
    @PostMapping("/admin/recompute-normalized-names")
    public ResponseEntity<Map<String, Object>> recomputeNormalizedNames() {
        int count = unitService.recomputeNormalizedNames();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "updatedCount", count));
    }
}
