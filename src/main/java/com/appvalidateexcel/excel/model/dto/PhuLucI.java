package com.appvalidateexcel.excel.model.dto;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PhuLucI {
    private int rowIndex;

    private String loai;
    private String khoan;
    private String muc;
    private String tieuMuc;
    private String tietMuc;
    private String nganh;
    private String noiDung;
    
    private Integer soNguoi178;
    private BigDecimal soTien178;
    
    private Integer soNguoi177;
    private BigDecimal soTien177;
    
    private BigDecimal tongSoTien;

    public boolean isBlank() {
        return (loai == null || loai.trim().isEmpty())
                && (khoan == null || khoan.trim().isEmpty())
                && (muc == null || muc.trim().isEmpty())
                && (tieuMuc == null || tieuMuc.trim().isEmpty())
                && (tietMuc == null || tietMuc.trim().isEmpty())
                && (noiDung == null || noiDung.trim().isEmpty());
    }
}
