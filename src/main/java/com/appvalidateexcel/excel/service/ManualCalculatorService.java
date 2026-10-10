package com.appvalidateexcel.excel.service;

import com.appvalidateexcel.excel.model.dto.CalculationItemDto;
import com.appvalidateexcel.excel.model.dto.ManualCalculateRequestDto;
import com.appvalidateexcel.excel.model.dto.ManualCalculateResponseDto;
import com.appvalidateexcel.excel.model.dto.PhuLucI1;
import com.appvalidateexcel.excel.model.dto.PhuLucI2;
import com.appvalidateexcel.excel.model.dto.PhuLucI3;
import com.appvalidateexcel.excel.model.expected.PLI1ExpectedResult;
import com.appvalidateexcel.excel.model.expected.PLI2ExpectedResult;
import com.appvalidateexcel.excel.model.expected.PLI3ExpectedResult;
import com.appvalidateexcel.excel.service.rules.MilitaryRankHelper;
import com.appvalidateexcel.excel.service.rules.PLI1Calculator;
import com.appvalidateexcel.excel.service.rules.PLI2Calculator;
import com.appvalidateexcel.excel.service.rules.PLI3Calculator;
import com.appvalidateexcel.excel.util.VietnameseNumberToWords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ManualCalculatorService {

    private final DecimalFormat moneyFmt = new DecimalFormat("#,###");
    private final DecimalFormat numFmt = new DecimalFormat("#,###.##");

    public ManualCalculateResponseDto calculate(ManualCalculateRequestDto req) {
        if (req == null) {
            return ManualCalculateResponseDto.builder()
                    .success(false)
                    .errorMessage("Dữ liệu yêu cầu không được rỗng.")
                    .build();
        }

        String type = req.getSheetType() != null ? req.getSheetType().trim().toUpperCase() : "I.1";
        if (type.contains("I.1") || type.contains("I1") || type.contains("PL1")) {
            return calculatePLI1(req);
        } else if (type.contains("I.2") || type.contains("I2") || type.contains("PL2")) {
            return calculatePLI2(req);
        } else if (type.contains("I.3") || type.contains("I3") || type.contains("PL3")) {
            return calculatePLI3(req);
        } else {
            return calculatePLI1(req);
        }
    }

    public ManualCalculateResponseDto calculatePLI1(ManualCalculateRequestDto req) {
        try {
            Date dob = parseDate(req.getNgaySinh());
            Date retire = parseDate(req.getThoiDiemNghi());
            Date enlist = parseDate(req.getNhapNgu());
            boolean isNu = MilitaryRankHelper.detectIsNu(req.getNgaySinh());

            if (dob == null || retire == null) {
                return ManualCalculateResponseDto.builder()
                        .success(false)
                        .errorMessage("Vui lòng cung cấp Ngày sinh và Thời điểm nghỉ hưu.")
                        .build();
            }

            int tran = req.getTranTuoi() != null && req.getTranTuoi() > 0
                    ? req.getTranTuoi()
                    : MilitaryRankHelper.getTran(req.getCapBac(), req.getChucVu(), isNu);
            if (tran <= 0) tran = 58;

            BigDecimal luong = req.getLuongThang() != null ? req.getLuongThang() : BigDecimal.ZERO;

            PhuLucI1 p = PhuLucI1.builder()
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .ngaySinh(dob)
                    .isNu(isNu)
                    .nhapNgu(enlist)
                    .thoiDiemNghiHuuHuongTroCap(retire)
                    .luongThangHienThuongTheoThongTu(luong)
                    .build();

            PLI1ExpectedResult exp = PLI1Calculator.calculateExpected(p);

            List<CalculationItemDto> items = new ArrayList<>();
            items.add(CalculationItemDto.builder()
                    .colIndex(10).colName("Cột 10").title("Số tháng nghỉ hưu trước tuổi")
                    .value(exp.getCot10()).formattedValue(exp.getCot10() + " tháng").unit("tháng")
                    .formula("(Ngày sinh + " + tran + " tuổi) - Ngày nghỉ (tính cả 2 đầu tháng)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(11).colName("Cột 11").title("Số năm nghỉ trước tuổi (làm tròn)")
                    .value(exp.getCot11()).formattedValue(fmt(exp.getCot11()) + " năm").unit("năm")
                    .formula("Cột 10 / 12 (<= 6 tháng làm tròn 0.5, > 6 tháng làm tròn 1.0)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(12).colName("Cột 12").title("Thời gian công tác đóng BHXH")
                    .value(exp.getCot12()).formattedValue(fmt(exp.getCot12()) + " năm").unit("năm")
                    .formula("(Ngày nghỉ - 1 tháng - Ngày nhập ngũ) / 12 (< 6 tháng dư +0.5, >= 6 tháng dư +1)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(13).colName("Cột 13").title("Trợ cấp nghỉ sớm (HS 1.0) - Nhóm <= 12 tháng")
                    .value(exp.getCot13()).formattedValue(fmtMoney(exp.getCot13()) + " đ").unit("VNĐ")
                    .formula(exp.getCot13() != null && exp.getCot13().compareTo(BigDecimal.ZERO) > 0 ? "min(Cột 10, 60) * 1.0 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(14).colName("Cột 14").title("Trợ cấp nghỉ sớm (HS 0.9) - Nhóm <= 12 tháng")
                    .value(exp.getCot14()).formattedValue(fmtMoney(exp.getCot14()) + " đ").unit("VNĐ")
                    .formula(exp.getCot14() != null && exp.getCot14().compareTo(BigDecimal.ZERO) > 0 ? "min(Cột 10, 60) * 0.9 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(15).colName("Cột 15").title("Trợ cấp nghỉ sớm (HS 0.5) - Nhóm > 12 tháng (tuổi <= 5 năm)")
                    .value(exp.getCot15()).formattedValue(fmtMoney(exp.getCot15()) + " đ").unit("VNĐ")
                    .formula(exp.getCot15() != null && exp.getCot15().compareTo(BigDecimal.ZERO) > 0 ? "min(Cột 10, 60) * 0.5 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(16).colName("Cột 16").title("Trợ cấp nghỉ sớm (HS 0.45) - Nhóm > 12 tháng (tuổi > 5 năm)")
                    .value(exp.getCot16()).formattedValue(fmtMoney(exp.getCot16()) + " đ").unit("VNĐ")
                    .formula(exp.getCot16() != null && exp.getCot16().compareTo(BigDecimal.ZERO) > 0 ? "min(Cột 10, 60) * 0.45 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(17).colName("Cột 17").title("Trợ cấp tuổi đời (Nhóm 2 - 5 năm)")
                    .value(exp.getCot17()).formattedValue(fmtMoney(exp.getCot17()) + " đ").unit("VNĐ")
                    .formula(exp.getCot17() != null && exp.getCot17().compareTo(BigDecimal.ZERO) > 0 ? "Cột 11 * 5 tháng * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(18).colName("Cột 18").title("Trợ cấp BHXH 20 năm đầu (Nhóm 2 - 5 năm)")
                    .value(exp.getCot18()).formattedValue(fmtMoney(exp.getCot18()) + " đ").unit("VNĐ")
                    .formula(exp.getCot18() != null && exp.getCot18().compareTo(BigDecimal.ZERO) > 0 ? "Cột 9 * hệ số BHXH đầu" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(19).colName("Cột 19").title("Trợ cấp BHXH vượt mức (Nhóm 2 - 5 năm)")
                    .value(exp.getCot19()).formattedValue(fmtMoney(exp.getCot19()) + " đ").unit("VNĐ")
                    .formula(exp.getCot19() != null && exp.getCot19().compareTo(BigDecimal.ZERO) > 0 ? "Cột 9 * 0.5 * (Cột 12 - mốc)" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(20).colName("Cột 20").title("Trợ cấp tuổi đời (Nhóm 5 - 10 năm)")
                    .value(exp.getCot20()).formattedValue(fmtMoney(exp.getCot20()) + " đ").unit("VNĐ")
                    .formula(exp.getCot20() != null && exp.getCot20().compareTo(BigDecimal.ZERO) > 0 ? "Cột 11 * 4 tháng * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(21).colName("Cột 21").title("Trợ cấp BHXH 20 năm đầu (Nhóm 5 - 10 năm)")
                    .value(exp.getCot21()).formattedValue(fmtMoney(exp.getCot21()) + " đ").unit("VNĐ")
                    .formula(exp.getCot21() != null && exp.getCot21().compareTo(BigDecimal.ZERO) > 0 ? "Cột 9 * hệ số BHXH đầu" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(22).colName("Cột 22").title("Trợ cấp BHXH vượt mức (Nhóm 5 - 10 năm)")
                    .value(exp.getCot22()).formattedValue(fmtMoney(exp.getCot22()) + " đ").unit("VNĐ")
                    .formula(exp.getCot22() != null && exp.getCot22().compareTo(BigDecimal.ZERO) > 0 ? "Cột 9 * 0.5 * (Cột 12 - mốc)" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(23).colName("Cột 23").title("TỔNG CỘNG SỐ TIỀN ĐƯỢC HƯỞNG")
                    .value(exp.getCot23()).formattedValue(fmtMoney(exp.getCot23()) + " đ").unit("VNĐ")
                    .formula("Cột 13 + 14 + 15 + 16 + 17 + 18 + 19 + 20 + 21 + 22")
                    .build());

            List<String> notes = new ArrayList<>();
            notes.add("Trần tuổi áp dụng: " + tran + " tuổi.");
            if (exp.getCot10() > 60) {
                notes.add("Thời gian nghỉ trước tuổi vượt 60 tháng (" + exp.getCot10() + " tháng), áp dụng khống chế tối đa 60 tháng cho nhóm tương ứng.");
            }
            notes.add("Lưu ý quy định BQP: Đơn vị có thể cộng gộp Cột 18 + Cột 19 hoặc Cột 21 + Cột 22 vào một cột hợp lệ.");

            return ManualCalculateResponseDto.builder()
                    .success(true)
                    .sheetType("I.1")
                    .sheetTitle("Phụ lục I.1 - Nghỉ hưu trước hạn tuổi (Nghị định 178)")
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .tranTuoi(tran)
                    .items(items)
                    .totalAmount(exp.getCot23())
                    .formattedTotalAmount(fmtMoney(exp.getCot23()) + " VNĐ")
                    .totalAmountWords(VietnameseNumberToWords.toWords(exp.getCot23()))
                    .notes(notes)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi tính toán Phụ lục I.1", e);
            return ManualCalculateResponseDto.builder()
                    .success(false)
                    .errorMessage("Lỗi tính toán: " + e.getMessage())
                    .build();
        }
    }

    public ManualCalculateResponseDto calculatePLI2(ManualCalculateRequestDto req) {
        try {
            Date dob = parseDate(req.getNgaySinh());
            Date retire = parseDate(req.getThoiDiemNghi());
            Date enlist = parseDate(req.getNhapNgu());
            Date sapNhap = parseDate(req.getThoiGianDonViSapNhapGiaiThe());
            boolean isNu = MilitaryRankHelper.detectIsNu(req.getNgaySinh());

            if (dob == null || retire == null) {
                return ManualCalculateResponseDto.builder()
                        .success(false)
                        .errorMessage("Vui lòng cung cấp Ngày sinh và Thời điểm thôi việc.")
                        .build();
            }

            int tran = req.getTranTuoi() != null && req.getTranTuoi() > 0
                    ? req.getTranTuoi()
                    : MilitaryRankHelper.getTran(req.getCapBac(), req.getChucVu(), isNu);
            if (tran <= 0) tran = 54;

            BigDecimal luong = req.getLuongThang() != null ? req.getLuongThang() : BigDecimal.ZERO;

            PhuLucI2 p = PhuLucI2.builder()
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .ngaySinh(dob)
                    .isNu(isNu)
                    .nhapNgu(enlist)
                    .thoiDiemThoiViecHuongTroCap(retire)
                    .thoiGianDonViSapNhapGiaiThe(sapNhap)
                    .luongThangHienThuongTheoThongTu(luong)
                    .build();

            PLI2ExpectedResult exp = PLI2Calculator.calculateExpected(p);

            List<CalculationItemDto> items = new ArrayList<>();
            items.add(CalculationItemDto.builder()
                    .colIndex(10).colName("Cột 10").title("Số tháng thôi việc")
                    .value(exp.getCot10()).formattedValue(exp.getCot10() + " tháng").unit("tháng")
                    .formula("Khống chế tối đa 60 tháng")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(11).colName("Cột 11").title("Thời gian công tác đóng BHXH")
                    .value(exp.getCot11()).formattedValue(fmt(exp.getCot11()) + " năm").unit("năm")
                    .formula("(Ngày thôi việc - Ngày nhập ngũ) / 12 (làm tròn)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(12).colName("Cột 12").title("Trợ cấp thôi việc ngay (HS 0.8)")
                    .value(exp.getCot12()).formattedValue(fmtMoney(exp.getCot12()) + " đ").unit("VNĐ")
                    .formula(exp.getCot12() != null && exp.getCot12().compareTo(BigDecimal.ZERO) > 0 ? "Cột 10 * 0.8 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(13).colName("Cột 13").title("Trợ cấp theo BHXH (1.5 tháng)")
                    .value(exp.getCot13()).formattedValue(fmtMoney(exp.getCot13()) + " đ").unit("VNĐ")
                    .formula(exp.getCot13() != null && exp.getCot13().compareTo(BigDecimal.ZERO) > 0 ? "Cột 11 * 1.5 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(14).colName("Cột 14").title("Trợ cấp tìm việc làm")
                    .value(exp.getCot14()).formattedValue(fmtMoney(exp.getCot14()) + " đ").unit("VNĐ")
                    .formula(exp.getCot14() != null && exp.getCot14().compareTo(BigDecimal.ZERO) > 0 ? "3 tháng * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(15).colName("Cột 15").title("Trợ cấp sau 12 tháng (HS 0.4)")
                    .value(exp.getCot15()).formattedValue(fmtMoney(exp.getCot15()) + " đ").unit("VNĐ")
                    .formula(exp.getCot15() != null && exp.getCot15().compareTo(BigDecimal.ZERO) > 0 ? "Cột 10 * 0.4 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(16).colName("Cột 16").title("Trợ cấp theo BHXH sau 12 tháng (1.5 tháng)")
                    .value(exp.getCot16()).formattedValue(fmtMoney(exp.getCot16()) + " đ").unit("VNĐ")
                    .formula(exp.getCot16() != null && exp.getCot16().compareTo(BigDecimal.ZERO) > 0 ? "Cột 11 * 1.5 * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(17).colName("Cột 17").title("Trợ cấp tìm việc làm sau 12 tháng")
                    .value(exp.getCot17()).formattedValue(fmtMoney(exp.getCot17()) + " đ").unit("VNĐ")
                    .formula(exp.getCot17() != null && exp.getCot17().compareTo(BigDecimal.ZERO) > 0 ? "3 tháng * Cột 9" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(18).colName("Cột 18").title("TỔNG CỘNG SỐ TIỀN THÔI VIỆC")
                    .value(exp.getCot18()).formattedValue(fmtMoney(exp.getCot18()) + " đ").unit("VNĐ")
                    .formula("Cột 12 + 13 + 14 + 15 + 16 + 17")
                    .build());

            List<String> notes = new ArrayList<>();
            notes.add("Trần tuổi áp dụng: " + tran + " tuổi.");
            notes.add("Kiểm tra tổng phụ: Nếu có phát sinh Cột 19 thì Cột 20 = Cột 18 + Cột 19.");

            return ManualCalculateResponseDto.builder()
                    .success(true)
                    .sheetType("I.2")
                    .sheetTitle("Phụ lục I.2 - Thôi việc ngay (Nghị định 178)")
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .tranTuoi(tran)
                    .items(items)
                    .totalAmount(exp.getCot18())
                    .formattedTotalAmount(fmtMoney(exp.getCot18()) + " VNĐ")
                    .totalAmountWords(VietnameseNumberToWords.toWords(exp.getCot18()))
                    .notes(notes)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi tính toán Phụ lục I.2", e);
            return ManualCalculateResponseDto.builder()
                    .success(false)
                    .errorMessage("Lỗi tính toán: " + e.getMessage())
                    .build();
        }
    }

    public ManualCalculateResponseDto calculatePLI3(ManualCalculateRequestDto req) {
        try {
            Date dob = parseDate(req.getNgaySinh());
            Date retire = parseDate(req.getThoiDiemNghi());
            Date enlist = parseDate(req.getNhapNgu());
            boolean isNu = MilitaryRankHelper.detectIsNu(req.getNgaySinh());

            if (dob == null || retire == null) {
                return ManualCalculateResponseDto.builder()
                        .success(false)
                        .errorMessage("Vui lòng cung cấp Ngày sinh và Thời điểm nghỉ hưu.")
                        .build();
            }

            int tran = req.getTranTuoi() != null && req.getTranTuoi() > 0
                    ? req.getTranTuoi()
                    : MilitaryRankHelper.getTran(req.getCapBac(), req.getChucVu(), isNu);
            if (tran <= 0) tran = 58;

            BigDecimal luong = req.getLuongThang() != null ? req.getLuongThang() : BigDecimal.ZERO;

            PhuLucI3 p = PhuLucI3.builder()
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .ngaySinh(dob)
                    .isNu(isNu)
                    .nhapNgu(enlist)
                    .thoiDiemNghiHuuHuongTroCap(retire)
                    .luongThangHienThuongTheoHuongDan(luong)
                    .build();

            PLI3ExpectedResult exp = PLI3Calculator.calculateExpected(p);

            List<CalculationItemDto> items = new ArrayList<>();
            items.add(CalculationItemDto.builder()
                    .colIndex(10).colName("Cột 10").title("Thời gian công tác đóng BHXH")
                    .value(exp.getCot10()).formattedValue(fmt(exp.getCot10()) + " năm").unit("năm")
                    .formula("(Ngày nghỉ - Ngày nhập ngũ) / 12 (làm tròn)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(11).colName("Cột 11").title("Số năm nghỉ hưu trước tuổi")
                    .value(exp.getCot11()).formattedValue(fmt(exp.getCot11()) + " năm").unit("năm")
                    .formula("(Ngày sinh + " + tran + " tuổi) - Ngày nghỉ (làm tròn < 6 tháng)")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(12).colName("Cột 12").title("Trợ cấp số năm nghỉ trước tuổi")
                    .value(exp.getCot12()).formattedValue(fmtMoney(exp.getCot12()) + " đ").unit("VNĐ")
                    .formula("Cột 11 * 5 tháng * Cột 9")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(13).colName("Cột 13").title("Trợ cấp 5 tháng tiền lương hiện hưởng")
                    .value(exp.getCot13()).formattedValue(fmtMoney(exp.getCot13()) + " đ").unit("VNĐ")
                    .formula("5 * Cột 9")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(14).colName("Cột 14").title("Trợ cấp thời gian đóng BHXH")
                    .value(exp.getCot14()).formattedValue(fmtMoney(exp.getCot14()) + " đ").unit("VNĐ")
                    .formula(exp.getCot14() != null && exp.getCot14().compareTo(BigDecimal.ZERO) > 0 ? "Cột 9 * 0.5 * (Cột 10 - mốc BHXH)" : "0 đ")
                    .build());

            items.add(CalculationItemDto.builder()
                    .colIndex(15).colName("Cột 15").title("TỔNG CỘNG SỐ TIỀN ĐƯỢC HƯỞNG")
                    .value(exp.getCot15()).formattedValue(fmtMoney(exp.getCot15()) + " đ").unit("VNĐ")
                    .formula("Cột 12 + Cột 13 + Cột 14")
                    .build());

            List<String> notes = new ArrayList<>();
            notes.add("Trần tuổi áp dụng: " + tran + " tuổi.");

            return ManualCalculateResponseDto.builder()
                    .success(true)
                    .sheetType("I.3")
                    .sheetTitle("Phụ lục I.3 - Nghỉ hưu trước hạn tuổi (Nghị định 177)")
                    .hoTen(req.getHoTen())
                    .capBac(req.getCapBac())
                    .chucVu(req.getChucVu())
                    .tranTuoi(tran)
                    .items(items)
                    .totalAmount(exp.getCot15())
                    .formattedTotalAmount(fmtMoney(exp.getCot15()) + " VNĐ")
                    .totalAmountWords(VietnameseNumberToWords.toWords(exp.getCot15()))
                    .notes(notes)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi tính toán Phụ lục I.3", e);
            return ManualCalculateResponseDto.builder()
                    .success(false)
                    .errorMessage("Lỗi tính toán: " + e.getMessage())
                    .build();
        }
    }

    private String fmt(BigDecimal bd) {
        if (bd == null) return "0";
        bd = bd.stripTrailingZeros();
        return numFmt.format(bd);
    }

    private String fmtMoney(BigDecimal bd) {
        if (bd == null) return "0";
        return moneyFmt.format(bd);
    }

    private Date parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim().replace('-', '/').replace('.', '/');
        s = s.replaceAll("(?i)[N]\\s*$", "").trim();
        String[] patterns = {
                "dd/MM/yyyy", "MM/yyyy", "yyyy/MM/dd", "yyyy/MM", "yyyy"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p);
                sdf.setLenient(false);
                return sdf.parse(s);
            } catch (ParseException ignored) {}
        }
        return null;
    }
}
