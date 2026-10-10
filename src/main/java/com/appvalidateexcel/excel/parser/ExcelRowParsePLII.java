package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExcelRowParsePLII {

    public Optional<PhuLucII> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return Optional.empty();
        }

        PhuLucII data = PhuLucII.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, 2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, 5, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, 6, formulaEvaluator))
                .nghiHuuTheoND177(ExcelParserUtils.getString(row, 7, formulaEvaluator))
                .nghiHuuTheoND178ND67(ExcelParserUtils.getString(row, 8, formulaEvaluator))
                .nghiThoiViec(ExcelParserUtils.getString(row, 9, formulaEvaluator))
                .thuocDonViTacDongTrucTiep(ExcelParserUtils.getString(row, 10, formulaEvaluator))
                .thuocDonViTacDongGianTiep(ExcelParserUtils.getString(row, 11, formulaEvaluator))
                .saiThoiGianDuocHuong(ExcelParserUtils.getString(row, 12, formulaEvaluator))
                .saiTienLuongThangLamCanCu(ExcelParserUtils.getString(row, 13, formulaEvaluator))
                .tinhTrungCheDoDoiDu(ExcelParserUtils.getString(row, 14, formulaEvaluator))
                .nguyenNhanKhac(ExcelParserUtils.getString(row, 15, formulaEvaluator))
                .soTienTinhSai(ExcelParserUtils.getBigDecimal(row, 16, formulaEvaluator))
                .giaiThich(ExcelParserUtils.getString(row, 17, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
