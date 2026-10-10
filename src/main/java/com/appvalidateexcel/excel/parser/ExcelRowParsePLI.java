package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExcelRowParsePLI {

    public Optional<PhuLucI> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return Optional.empty();
        }

        PhuLucI data = PhuLucI.builder()
                .rowIndex(rowIndex)
                .loai(ExcelParserUtils.getString(row, 0, formulaEvaluator))
                .khoan(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .muc(ExcelParserUtils.getString(row, 2, formulaEvaluator))
                .tieuMuc(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .tietMuc(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .nganh(ExcelParserUtils.getString(row, 5, formulaEvaluator))
                .noiDung(ExcelParserUtils.getString(row, 6, formulaEvaluator))
                .soNguoi178(ExcelParserUtils.getInteger(row, 7, formulaEvaluator))
                .soTien178(ExcelParserUtils.getBigDecimal(row, 8, formulaEvaluator))
                .soNguoi177(ExcelParserUtils.getInteger(row, 9, formulaEvaluator))
                .soTien177(ExcelParserUtils.getBigDecimal(row, 10, formulaEvaluator))
                .tongSoTien(ExcelParserUtils.getBigDecimal(row, 11, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
