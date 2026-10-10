package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.repository.PersonnelRecordRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RollupService {

    private final PersonnelRecordRepository recordRepository;
    private final StorageMaintenanceLock maintenanceLock;

    public RollupService(PersonnelRecordRepository recordRepository, StorageMaintenanceLock maintenanceLock) {
        this.recordRepository = recordRepository;
        this.maintenanceLock = maintenanceLock;
    }

    public Map<String, Object> calculateRollup(String unitId, String scope) {
        return calculateRollup(unitId, scope, null);
    }

    public Map<String, Object> calculateRollup(String unitId, String scope, String source) {
        return maintenanceLock.callWithReadAccess(() -> {
            String effectiveScope = (scope != null && scope.equalsIgnoreCase("direct")) ? "direct" : "branch";
            String effectiveSource = (source != null && !source.trim().isEmpty()) ? source.trim() : null;

            Map<String, Object> branchData = recordRepository.calculateRollup(unitId, "branch", effectiveSource);
            Map<String, Object> directData = recordRepository.calculateRollup(unitId, "direct", effectiveSource);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("unitId", unitId);
            response.put("requestedScope", effectiveScope);
            response.put("source", effectiveSource != null ? effectiveSource : "all");
            response.put("branchTotals", branchData);
            response.put("directTotals", directData);

            return response;
        });
    }
}
