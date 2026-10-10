package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ExcelRowParsePLI2 {

    public Optional<PhuLucI2> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        return parse(row, rowIndex, formulaEvaluator, null);
    }

    public Optional<PhuLucI2> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator, Map<Integer, Integer> colMap) {
        if (row == null) {
            return Optional.empty();
        }

        int c1 = getCol(colMap, 1);
        int c2 = getCol(colMap, 2);
        int c3 = getCol(colMap, 3);
        int c4 = getCol(colMap, 4);
        int c5 = getCol(colMap, 5);
        int c6 = getCol(colMap, 6);
        int c7 = getCol(colMap, 7);
        int c8 = getCol(colMap, 8);
        int c9 = getCol(colMap, 9);
        int c10 = getCol(colMap, 10);
        int c11 = getCol(colMap, 11);
        int c12 = getCol(colMap, 12);
        int c13 = getCol(colMap, 13);
        int c14 = getCol(colMap, 14);
        int c15 = getCol(colMap, 15);
        int c16 = getCol(colMap, 16);
        int c17 = getCol(colMap, 17);
        int c18 = getCol(colMap, 18);

        String ngaySinhRaw = ExcelParserUtils.getString(row, c3, formulaEvaluator);
        boolean isNu = MilitaryRankHelper.detectIsNu(ngaySinhRaw);

        PhuLucI2 data = PhuLucI2.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, c2, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, c3, formulaEvaluator))
                .isNu(isNu)
                .capBac(ExcelParserUtils.getString(row, c4, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, c5, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, c6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, c7, formulaEvaluator))
                .thoiDiemThoiViecHuongTroCap(ExcelParserUtils.getDate(row, c8, formulaEvaluator))
                .luongThangHienThuongTheoThongTu(ExcelParserUtils.getBigDecimal(row, c9, formulaEvaluator))
                .soThangThoiViecTheoThongTu(ExcelParserUtils.getInteger(row, c10, formulaEvaluator))
                .soNamHuongTroCapTheoThongTu(ExcelParserUtils.getBigDecimal(row, c11, formulaEvaluator))
                .troCap1LanChoSoThangCongTacCoDongBHXH1(ExcelParserUtils.getBigDecimal(row, c12, formulaEvaluator))
                .troCap1LanChoSoNamCongTacDongBHXH1(ExcelParserUtils.getBigDecimal(row, c13, formulaEvaluator))
                .troCapTaoViecLam1(ExcelParserUtils.getBigDecimal(row, c14, formulaEvaluator))
                .troCap1LanChoSoThangCongTacCoDongBHXH2(ExcelParserUtils.getBigDecimal(row, c15, formulaEvaluator))
                .troCap1LanChoSoNamCongTacDongBHXH2(ExcelParserUtils.getBigDecimal(row, c16, formulaEvaluator))
                .troCapTaoViecLam2(ExcelParserUtils.getBigDecimal(row, c17, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo178(ExcelParserUtils.getBigDecimal(row, c18, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }

    private static int getCol(Map<Integer, Integer> colMap, int stdCol) {
        return (colMap != null && colMap.containsKey(stdCol)) ? colMap.get(stdCol) : stdCol;
    }
}
