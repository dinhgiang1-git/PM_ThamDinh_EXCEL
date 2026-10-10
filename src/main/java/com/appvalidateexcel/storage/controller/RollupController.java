package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.service.RollupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rollups")
public class RollupController {

    private final RollupService rollupService;

    public RollupController(RollupService rollupService) {
        this.rollupService = rollupService;
    }

    @GetMapping("/units/{unitId}")
    public ResponseEntity<Map<String, Object>> getUnitRollup(
            @PathVariable String unitId,
            @RequestParam(required = false, defaultValue = "branch") String scope,
            @RequestParam(required = false, defaultValue = "validated") String source
    ) {
        return ResponseEntity.ok(rollupService.calculateRollup(unitId, scope, source));
    }

    @GetMapping("/overall")
    public ResponseEntity<Map<String, Object>> getOverallRollup(
            @RequestParam(required = false, defaultValue = "validated") String source
    ) {
        return ResponseEntity.ok(rollupService.calculateRollup(null, "branch", source));
    }
}
