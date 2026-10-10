package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulaReadReport {
    private int totalFormulas;
    private int cachedFormulas;
    private int evaluatedFormulas;
    private int emptyFormulas;
    private int noResultFormulas;
    private int errorFormulas;
    private int suspectedOldCacheFormulas;
    
    @Builder.Default
    private Map<String, Integer> sheetFormulaCounts = new HashMap<>();

    @Builder.Default
    private List<FormulaIssue> issues = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormulaIssue {
        private String sheet;
        private String address;
        private String formula;
        private CellStatus status;
        private String errorCode;
        private int row;
        private int col;
    }
}
