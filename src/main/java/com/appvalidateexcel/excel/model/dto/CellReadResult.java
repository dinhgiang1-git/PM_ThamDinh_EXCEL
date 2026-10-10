package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CellReadResult<T> {
    private String sheetName;
    private String cellAddress;
    private String formula;
    private T value;
    private String valueType;
    private CellStatus status;
    private String valueSource; // "literal", "cached", "evaluated", "none"
    private String errorCode;
    private String rawText;

    public boolean isFormula() {
        return formula != null && !formula.isEmpty();
    }

    public boolean isSuccess() {
        return status == CellStatus.VALUE 
            || status == CellStatus.FORMULA_CACHED 
            || status == CellStatus.FORMULA_EVALUATED 
            || status == CellStatus.FORMULA_EMPTY 
            || status == CellStatus.BLANK;
    }

    public boolean isError() {
        return status == CellStatus.FORMULA_ERROR 
            || status == CellStatus.FORMULA_NO_RESULT 
            || status == CellStatus.INVALID_TYPE;
    }
}
