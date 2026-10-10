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

/**
 * Parser cho Phụ lục I.3 (form mới 26.9.PHU_LUC_SUA.xlsx):
 * C1=STT, C2=HọTên, C3=NgàySinh, C4=CấpBậc, C5=ChứcVụ,
 * C6=NhậpNgũ, C7=SápNhập, C8=ThờiĐiểmNghỉHưu, C9=LươngTháng,
 * C10=SốNămCôngTácĐóngBHXH, C11=SốNămNghỉSớm,
 * C12=TrợCấpNghỉSớm, C13=BHXH20NămĐầu, C14=BHXHVượtMốc, C15=TổngCộng
 */
@Component
public class ExcelRowParsePLI3 {

    public Optional<PhuLucI3> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        return parse(row, rowIndex, formulaEvaluator, null);
    }

    public Optional<PhuLucI3> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator, Map<Integer, Integer> colMap) {
        if (row == null) {
            return Optional.empty();
        }

        int c1  = getCol(colMap, 1,  1);
        int c2  = getCol(colMap, 2,  2);
        int c3  = getCol(colMap, 3,  3);
        int c4  = getCol(colMap, 4,  4);
        int c5  = getCol(colMap, 5,  5);
        int c6  = getCol(colMap, 6,  6);
        int c7  = getCol(colMap, 7,  7);
        int c8  = getCol(colMap, 8,  8);
        int c9  = getCol(colMap, 9,  9);
        int c10 = getCol(colMap, 10, 10);
        int c11 = getCol(colMap, 11, 11);
        int c12 = getCol(colMap, 12, 12);
        int c13 = getCol(colMap, 13, 13);
        int c14 = getCol(colMap, 14, 14);
        // Cột tổng: ưu tiên colMap[15]; fallback c15
        int c15 = getCol(colMap, 15, 15);

        String ngaySinhRaw = ExcelParserUtils.getString(row, c3, formulaEvaluator);
        boolean isNu = MilitaryRankHelper.detectIsNu(ngaySinhRaw);

        PhuLucI3 data = PhuLucI3.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, c2, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, c3, formulaEvaluator))
                .isNu(isNu)
                .capBac(ExcelParserUtils.getString(row, c4, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, c5, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, c6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, c7, formulaEvaluator))
                .thoiDiemNghiHuuHuongTroCap(ExcelParserUtils.getDate(row, c8, formulaEvaluator))
                .luongThangHienThuongTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c9, formulaEvaluator))
                .soThangThoiViecTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c10, formulaEvaluator))
                .soNamHuongTroCapTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c11, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho1NamNghiSom(ExcelParserUtils.getBigDecimal(row, c12, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac(ExcelParserUtils.getBigDecimal(row, c13, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong(ExcelParserUtils.getBigDecimal(row, c14, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo177(ExcelParserUtils.getBigDecimal(row, c15, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }

    private static int getCol(Map<Integer, Integer> colMap, int stdCol, int defaultCol) {
        return (colMap != null && colMap.containsKey(stdCol)) ? colMap.get(stdCol) : defaultCol;
    }
}
