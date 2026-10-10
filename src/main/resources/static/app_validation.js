
// BQP HTML & XSS SECURITY UTILITIES (P0-03)
function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
if (typeof window !== 'undefined') window.escapeHtml = escapeHtml;

function escapeAttr(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}
if (typeof window !== 'undefined') window.escapeAttr = escapeAttr;

function escapeJs(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'")
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r');
}
if (typeof window !== 'undefined') window.escapeJs = escapeJs;

// Fallback Toast and Modal wrappers for app_validation.js
function notifySuccess(message, options) {
    if (typeof window !== 'undefined' && window.ToastManager && typeof window.ToastManager.success === 'function') {
        return window.ToastManager.success(message, options);
    }
    console.log('[Toast Success]', message);
}
function notifyError(message, options) {
    if (typeof window !== 'undefined' && window.ToastManager && typeof window.ToastManager.error === 'function') {
        return window.ToastManager.error(message, options);
    }
    console.error('[Toast Error]', message);
}
function notifyWarning(message, options) {
    if (typeof window !== 'undefined' && window.ToastManager && typeof window.ToastManager.warning === 'function') {
        return window.ToastManager.warning(message, options);
    }
    console.warn('[Toast Warning]', message);
}
function notifyInfo(message, options) {
    if (typeof window !== 'undefined' && window.ToastManager && typeof window.ToastManager.info === 'function') {
        return window.ToastManager.info(message, options);
    }
    console.info('[Toast Info]', message);
}

const SafeConfirmModal = {
    confirm(options) {
        if (typeof window !== 'undefined' && window.ConfirmModal && typeof window.ConfirmModal.confirm === 'function') {
            return window.ConfirmModal.confirm(options);
        }
        console.warn('[SafeConfirmModal] window.ConfirmModal không khả dụng, fail-closed:', options);
        return Promise.resolve(false);
    },
    choose(options) {
        if (typeof window !== 'undefined' && window.ConfirmModal && typeof window.ConfirmModal.choose === 'function') {
            return window.ConfirmModal.choose(options);
        }
        console.warn('[SafeConfirmModal] window.ConfirmModal không khả dụng, fail-closed:', options);
        const dismiss = (options && options.dismissValue !== undefined) ? options.dismissValue : null;
        return Promise.resolve(dismiss);
    }
};

/**
 * Phần mềm phục vụ rà soát, thẩm định chế độ nghỉ hưu, phục viên (theo Nghị định số 177/2024/NĐ-CP, Nghị định số 178/2024/NĐ-CP và Nghị định số 67/2025/NĐ-CP)
 * Chạy 100% Offline trên trình duyệt qua JavaScript thuần
 * Tuyệt đối không dùng icon/emoji - Phong cách chuẩn mực công vụ quân sự
 */

if (typeof window !== 'undefined' && !window.BQPLoader) {
    window.BQPLoader = {
        _el: null,
        _titleEl: null,
        _subEl: null,
        _stepEl: null,
        _init() {
            if (!this._el) {
                this._el = document.getElementById('bqp_global_loading');
                this._titleEl = document.getElementById('bqp_loading_title');
                this._subEl = document.getElementById('bqp_loading_subtitle');
                this._stepEl = document.getElementById('bqp_loading_step');
            }
        },
        show(title, subtitle, step) {
            this._init();
            if (this._titleEl && title) this._titleEl.textContent = title;
            if (this._subEl && subtitle !== undefined) this._subEl.textContent = subtitle;
            if (this._stepEl) {
                if (step) {
                    this._stepEl.textContent = step;
                    this._stepEl.style.display = 'block';
                } else {
                    this._stepEl.style.display = 'none';
                }
            }
            if (this._el) this._el.classList.add('active');
        },
        update(title, subtitle, step) {
            this._init();
            if (this._titleEl && title) this._titleEl.textContent = title;
            if (this._subEl && subtitle !== undefined) this._subEl.textContent = subtitle;
            if (this._stepEl && step !== undefined) {
                this._stepEl.textContent = step;
                this._stepEl.style.display = step ? 'block' : 'none';
            }
        },
        hide() {
            this._init();
            if (this._el) this._el.classList.remove('active');
        },
        async run(title, subtitle, asyncFn, step) {
            this.show(title, subtitle, step);
            await new Promise(r => setTimeout(r, 60));
            try {
                return await asyncFn();
            } finally {
                this.hide();
            }
        }
    };
}

var _bqpGlobal = (typeof window !== 'undefined') ? window : (typeof globalThis !== 'undefined' ? globalThis : global);
if (typeof window === 'undefined') {
    var window = _bqpGlobal;
}

window.BQPValidation = (function () {
    const LCS = 2340000; // Mức lương cơ sở theo Nghị định

    // Trợ giúp phân loại cấp bậc, chức vụ và tính trần tuổi
    const MilitaryRankHelper = {
        checkIsQNCN(capBac, chucVu) {
            let cb = (capBac || '').trim().toLowerCase().replace(/\s+/g, ' ');
            let cv = (chucVu || '').trim().toLowerCase().replace(/\s+/g, ' ');
            let hasNv = cv === 'nv' || cv.startsWith('nv ') || cv.startsWith('nv.') || cv.startsWith('nv/')
                || cv.includes(' nv ') || cv.includes(' nv.') || cv.includes(' nv/') || cv.endsWith(' nv');
            return cv.includes('nhân viên') || hasNv
                || cv.includes('y sĩ') || cv.includes('y sỹ') || cv.includes('y si') || cv.includes('y sy')
                || cv.includes('thủ kho') || cv.includes('thu kho')
                || cv.includes('bảo quản') || cv.includes('bao quan')
                || cv.includes('thủy thủ') || cv.includes('thuỷ thủ') || cv.includes('thuy thu')
                || cv.includes('lái xe') || cv.includes('thợ')
                || cv.includes('chạm') || cv.includes('trạm') || cb.includes('qncn') || cv.includes('qncn')
                || cb.includes(' cn') || cb.includes('/cn') || cb.endsWith('cn') || cb.includes('cia') || cb.includes('chuyên nghiệp');
        },

        getTran(capBac, chucVu) {
            if (!capBac && !chucVu) return 0;
            let cb = (capBac || '').replace(/\u00A0/g, ' ').trim().toLowerCase().replace(/\s+/g, ' ');
            let cv = (chucVu || '').replace(/\u00A0/g, ' ').trim().toLowerCase().replace(/\s+/g, ' ');
            let isQNCN = this.checkIsQNCN(cb, cv);

            cb = cb.replace(/^[-–—\s]+/, '');

            if (cb.startsWith('24.') || cb === '24' || cb.startsWith('24cn') || cb.startsWith('24 cn')) return 58;
            if (cb.startsWith('23.') || cb === '23' || cb.startsWith('23cn') || cb.startsWith('23 cn')) return 56;
            if (cb.startsWith('22.') || cb === '22' || cb.startsWith('22cn') || cb.startsWith('22 cn')) return 54;
            if (cb.startsWith('21.') || cb === '21' || cb.startsWith('21cn') || cb.startsWith('21 cn')) return isQNCN ? 54 : 52;
            if (cb.startsWith('14.') || cb === '14' || cb.startsWith('14cn') || cb.startsWith('14 cn')) return isQNCN ? 52 : 50;

            if (/(?<!\d)4\s*\/\//.test(cb) || cb.includes('đại tá') || cb.includes('đai tá')) return 58;
            if (/(?<!\d)3\s*\/\//.test(cb) || cb.includes('thượng tá') || cb.includes('thượng tạ') || cb.includes('thuong tá')) return 56;
            if (/(?<!\d)2\s*\/\//.test(cb) || cb.includes('trung tá')) return 54;
            if (/(?<!\d)1\s*\/\//.test(cb) || cb.includes('thiếu tá') || cb.includes('thiéu tá')) return isQNCN ? 54 : 52;

            if (/(?<!\d)4\s*\/(?!\/|\d)/.test(cb) || cb.includes('đại uý') || cb.includes('đại úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)3\s*\/(?!\/|\d)/.test(cb) || cb.includes('thượng uý') || cb.includes('thượng úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)2\s*\/(?!\/|\d)/.test(cb) || cb.includes('trung uý') || cb.includes('trung úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)1\s*\/(?!\/|\d)/.test(cb) || cb.includes('thiếu uý') || cb.includes('thiếu úy')) return isQNCN ? 52 : 50;

            if (cb.includes('uý') || cb.includes('úy')) return isQNCN ? 52 : 50;

            if (cv.includes('đại tá') || cv.includes('đai tá')) return 58;
            if (cv.includes('thượng tá') || cv.includes('thuong tá')) return 56;
            if (cv.includes('trung tá')) return 54;
            if (cv.includes('thiếu tá') || cv.includes('thiéu tá')) return isQNCN ? 54 : 52;

            if (isQNCN || cb.includes('quân nhân chuyên nghiệp')) return 54;
            if (cb.includes('vcqp') || cb.includes('cnqp') || cb.includes('viên chức quốc phòng') || cb.includes('công nhân quốc phòng') || cb.includes('lao động hợp đồng') || cb.includes('ldhd')) return 60;

            return 0; // Trả về 0 chuẩn mực giống Java để báo không nhận diện được
        }
    };

    function cleanString(val) {
        if (val == null) return '';
        return String(val)
            .replace(/[\u00A0\u2007\u202F\u200B\uFEFF]/g, ' ')
            .trim()
            .replace(/\s+/g, ' ');
    }

    function toDateObj(d) {
        if (!d) return null;
        if (d instanceof Date) return isNaN(d.getTime()) ? null : d;
        if (typeof d === 'string') {
            let s = d.trim();
            let mY = s.match(/^(\d{1,2})[\/\-](\d{4})$/);
            if (mY) {
                return new Date(parseInt(mY[2], 10), parseInt(mY[1], 10) - 1, 1);
            }
            let dMY = s.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
            if (dMY) {
                return new Date(parseInt(dMY[3], 10), parseInt(dMY[2], 10) - 1, parseInt(dMY[1], 10));
            }
            let parsed = new Date(s);
            if (!isNaN(parsed.getTime())) return parsed;
        }
        return null;
    }

    function calcThang(d1, d2) {
        let dt1 = toDateObj(d1);
        let dt2 = toDateObj(d2);
        if (!dt1 || !dt2) return 0;
        return (dt1.getFullYear() * 12 + dt1.getMonth()) - (dt2.getFullYear() * 12 + dt2.getMonth());
    }

    function calcNamLamTron(thang) {
        if (thang <= 0) return 0;
        let years = Math.floor(thang / 12);
        let rem = thang % 12;
        if (rem === 0) return years;
        if (rem <= 6) return years + 0.5;
        return years + 1.0;
    }

    function isEqual(a, b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(Number(a) - Number(b)) <= 1000;
    }

    function isEqualTime(a, b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(Number(a) - Number(b)) < 0.01;
    }

    function fmtMoney(m) {
        if (m == null || isNaN(m)) return '0';
        return Math.round(Number(m)).toLocaleString('vi-VN');
    }

    function fmtNum(n) {
        if (n == null || isNaN(n)) return '0';
        return Number(n).toFixed(1).replace(/\.0$/, '');
    }

    function formatDate(d) {
        if (!d) return '';
        if (d && typeof d === 'object') {
            if (d instanceof Date) {
                if (isNaN(d.getTime())) return '';
                let m = d.getMonth() + 1;
                let y = d.getFullYear();
                return (m < 10 ? '0' + m : m) + '/' + y;
            }
            if (Array.isArray(d.richText)) {
                d = d.richText.map(t => t.text).join('').trim();
            } else if (d.text != null) {
                d = String(d.text).trim();
            } else if (d.result != null) {
                d = d.result;
            }
        }
        if (d instanceof Date) {
            if (isNaN(d.getTime())) return '';
            let m = d.getMonth() + 1;
            let y = d.getFullYear();
            return (m < 10 ? '0' + m : m) + '/' + y;
        }
        if (typeof d === 'string') {
            let s = d.trim();
            if (/^\d{1,2}[\/\-]\d{4}$/.test(s)) return s;
            const ddmmyyyy = s.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
            if (ddmmyyyy) {
                let m = parseInt(ddmmyyyy[2], 10);
                let y = ddmmyyyy[3];
                return (m < 10 ? '0' + m : m) + '/' + y;
            }
            if (s.includes('-') || s.includes('T') || s.includes('GMT') || s.match(/^[A-Z][a-z]{2}\s+[A-Z][a-z]{2}/) || /^\d{4}[\/\-]\d{1,2}/.test(s)) {
                let parsed = new Date(s);
                if (!isNaN(parsed.getTime())) {
                    let m = parsed.getMonth() + 1;
                    let y = parsed.getFullYear();
                    return (m < 10 ? '0' + m : m) + '/' + y;
                }
            }
            return s;
        }
        return String(d);
    }

    function readNumberToVietnameseWords(amount) {
        if (!amount || isNaN(amount) || amount === 0) return "Không đồng";
        amount = Math.round(Math.abs(amount));

        const digits = ["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"];
        const tiers = ["", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"];

        function readThreeDigits(n, hasHigher) {
            let h = Math.floor(n / 100);
            let rem = n % 100;
            let t = Math.floor(rem / 10);
            let u = rem % 10;
            let s = "";

            if (h > 0 || hasHigher) s += digits[h] + " trăm ";

            if (t > 1) {
                s += digits[t] + " mươi ";
                if (u === 1) s += "mốt";
                else if (u === 5) s += "lăm";
                else if (u > 0) s += digits[u];
            } else if (t === 1) {
                s += "mười ";
                if (u === 5) s += "lăm";
                else if (u > 0) s += digits[u];
            } else {
                if (h > 0 || hasHigher) {
                    if (u > 0) s += "lẻ " + digits[u];
                } else if (u > 0) {
                    s += digits[u];
                }
            }
            return s.trim();
        }

        let val = amount;
        let tierIdx = 0;
        let parts = [];

        while (val > 0) {
            let chunk = val % 1000;
            if (chunk !== 0) {
                let chunkWords = readThreeDigits(chunk, val >= 1000);
                let tier = tiers[tierIdx];
                if (tier) chunkWords += " " + tier;
                parts.unshift(chunkWords);
            }
            val = Math.floor(val / 1000);
            tierIdx++;
        }

        let result = parts.join(" ").replace(/\s+/g, " ").trim();
        return result ? result.charAt(0).toUpperCase() + result.slice(1) + " đồng chẵn" : "Không đồng";
    }

    // ============================================================
    // CÁC BỘ TÍNH TOÁN THEO QUY ĐỊNH BỘ QUỐC PHÒNG
    // ============================================================

    // 1. Phụ lục I.1 (Nghỉ hưu NĐ 178)
    const PLI1Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu);
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);
            let dSapNhap = toDateObj(data.sapNhap);

            let rawCot10 = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let expYear = dNgaySinh.getFullYear() + tran;
                let expMonth = dNgaySinh.getMonth();
                let retYear = dThoiDiemNghi.getFullYear();
                let retMonth = dThoiDiemNghi.getMonth();
                let diff = (expYear * 12 + expMonth) - (retYear * 12 + retMonth);
                rawCot10 = diff > 0 ? diff + 1 : 0; // tính cả 2 đầu tháng
            }
            if (rawCot10 < 0) rawCot10 = 0;

            let actualC10 = data.cot10Actual != null ? data.cot10Actual : (data.rawCols ? data.rawCols[10] : null);
            let c10Num = (typeof actualC10 === 'number') ? actualC10 : (actualC10 != null ? parseFloat(String(actualC10).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
            let isCapped60 = (rawCot10 >= 60 && c10Num === 60);
            let cot10 = isCapped60 ? 60 : rawCot10;

            let rawExp11 = calcNamLamTron(rawCot10);
            let actualC11 = data.cot11Actual != null ? data.cot11Actual : (data.rawCols ? data.rawCols[11] : null);
            let c11Num = (typeof actualC11 === 'number') ? actualC11 : (actualC11 != null ? parseFloat(String(actualC11).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
            let c11Valid = c11Num != null && isEqualTime(c11Num, rawExp11);
            let cot11 = c11Valid ? c11Num : rawExp11;

            let monthsC12 = calcThang(dThoiDiemNghi, dNhapNgu);
            if (monthsC12 < 0) monthsC12 = 0;
            let rawExp12 = calcNamLamTron(monthsC12);
            let actualC12 = data.cot12Actual != null ? data.cot12Actual : (data.rawCols ? data.rawCols[12] : null);
            let c12Num = (typeof actualC12 === 'number') ? actualC12 : (actualC12 != null ? parseFloat(String(actualC12).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
            let c12Valid = c12Num != null && isEqualTime(c12Num, rawExp12);
            let cot12 = c12Valid ? c12Num : rawExp12;

            let luong = Number(data.luongThang) || 0;
            let nghiTruoc172025 = true;
            if (dThoiDiemNghi && dThoiDiemNghi >= new Date(2025, 6, 1)) {
                nghiTruoc172025 = false;
            }

            let timeDiff = (dThoiDiemNghi && dSapNhap) ? calcThang(dThoiDiemNghi, dSapNhap) : 0;
            let nhoHon12 = (!dSapNhap || timeDiff <= 12);

            // Nhóm và tiền luôn theo số năm nghỉ sớm thực tế, không khống chế về 5 năm
            let cot11Money = rawExp11;
            let isOver60 = (rawCot10 > 60 && cot11Money > 5.0);
            let cappedCot10 = Math.min(rawCot10, 60);

            let cot13 = 0, cot14 = 0, cot15 = 0, cot16 = 0;
            if (nhoHon12) {
                if (!isOver60) cot13 = cappedCot10 * 1.0 * luong;
                else cot14 = cappedCot10 * 0.9 * luong;
            } else {
                if (!isOver60) cot15 = cappedCot10 * 0.5 * luong;
                else cot16 = cappedCot10 * 0.45 * luong;
            }

            let cot17 = 0, cot18 = 0, cot19 = 0, cot20 = 0, cot21 = 0, cot22 = 0;
            let val18_21 = 0, val19_22 = 0;
            let exp12ForMoney = cot12;

            let mocBHXH = nghiTruoc172025 ? 20 : 15;
            let hsBHXH = nghiTruoc172025 ? 5 : 4;

            if (exp12ForMoney >= mocBHXH) {
                val18_21 = luong * hsBHXH;
                val19_22 = luong * 0.5 * (exp12ForMoney - mocBHXH);
            }

            if (cot10 >= 24 && !isOver60) {
                cot17 = cot11Money * 5 * luong;
                cot18 = val18_21;
                cot19 = val19_22;
            } else if (isOver60) {
                cot20 = cot11Money * 4 * luong;
                cot21 = val18_21;
                cot22 = val19_22;
            }

            let total = cot13 + cot14 + cot15 + cot16 + cot17 + cot18 + cot19 + cot20 + cot21 + cot22;

            return {
                cot10, cot11, cot12,
                cot13, cot14, cot15, cot16, cot17, cot18, cot19, cot20, cot21, cot22,
                total, tran, rawCot10, isOver60, nghiTruoc172025, mocBHXH, hsBHXH, nhoHon12
            };
        }
    };

    // 2. Phụ lục I.2 (Thôi việc NĐ 178)
    const PLI2Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu) || 54;
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);
            let dSapNhap = toDateObj(data.sapNhap);

            let rawThangConLai = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let dobMonth = dNgaySinh.getFullYear() * 12 + dNgaySinh.getMonth();
                let retMonth = dThoiDiemNghi.getFullYear() * 12 + dThoiDiemNghi.getMonth();
                rawThangConLai = (dobMonth + tran * 12) - retMonth;
            }
            if (rawThangConLai < 0) rawThangConLai = 0;

            let monthsCongTac = (dThoiDiemNghi && dNhapNgu) ? calcThang(dThoiDiemNghi, dNhapNgu) : 0;
            if (monthsCongTac < 0) monthsCongTac = 0;
            let rawCot11 = calcNamLamTron(monthsCongTac);
            let cot11 = rawCot11;

            // Cột 10: Số tháng thôi việc (khống chế tối đa 60 tháng theo NĐ 178; trường hợp thời gian đóng BHXH dưới 5 năm/60 tháng thì bằng đúng thời gian công tác thực tế có đóng BHXH)
            let maxThangThoiViec = (monthsCongTac > 0 && monthsCongTac < 60) ? monthsCongTac : 60;
            let rawCot10 = rawThangConLai > 0 ? Math.min(rawThangConLai, maxThangThoiViec) : maxThangThoiViec;
            let actualC10 = data.cot10Actual != null ? data.cot10Actual : (data.rawCols ? data.rawCols[10] : null);
            let c10Num = (typeof actualC10 === 'number') ? actualC10 : (actualC10 != null ? parseFloat(String(actualC10).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
            let isCapped60 = ((rawThangConLai >= 60 || rawCot10 >= 60) && c10Num === 60);
            let cot10 = isCapped60 ? 60 : rawCot10;
            let thangConLai = rawThangConLai;

            let isEligibleByAge = thangConLai >= 24;
            let isOver2Years = isEligibleByAge;

            let distance8_7 = (dThoiDiemNghi && dSapNhap) ? calcThang(dThoiDiemNghi, dSapNhap) : 0;
            let isWithin12Months = (!dSapNhap || distance8_7 <= 12);

            let luong = Number(data.luongThang) || 0;
            let cot12 = 0, cot13 = 0, cot14 = 0, cot15 = 0, cot16 = 0, cot17 = 0;

            if (isEligibleByAge) {
                if (isWithin12Months) {
                    cot12 = cot10 * 0.8 * luong;
                    cot13 = cot11 * 1.5 * luong;
                    cot14 = 3 * luong;
                } else {
                    cot15 = cot10 * 0.4 * luong;
                    cot16 = cot11 * 1.5 * luong;
                    cot17 = 3 * luong;
                }
            }

            let cot18 = cot12 + cot13 + cot14 + cot15 + cot16 + cot17;

            return {
                cot10, cot11, cot12, cot13, cot14, cot15, cot16, cot17, cot18,
                total: cot18, tran, rawThangConLai, rawCot10, monthsCongTac, maxThangThoiViec, isOver2Years, isEligibleByAge, isWithin12Months, distance8_7
            };
        }
    };

    // 3. Phụ lục I.3 (Nghỉ hưu NĐ 177)
    const PLI3Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu) || 58;
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);

            let diffMonths10 = (dThoiDiemNghi && dNhapNgu) ? calcThang(dThoiDiemNghi, dNhapNgu) : 0;
            if (diffMonths10 < 0) diffMonths10 = 0;
            let rawCot10 = calcNamLamTron(diffMonths10);
            let cot10 = rawCot10;

            let rawCot11 = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let expYear = dNgaySinh.getFullYear() + tran;
                let expMonth = dNgaySinh.getMonth();
                let retYear = dThoiDiemNghi.getFullYear();
                let retMonth = dThoiDiemNghi.getMonth();
                let diff11 = (expYear * 12 + expMonth) - (retYear * 12 + retMonth);
                if (diff11 > 0) {
                    rawCot11 = calcNamLamTron(diff11);
                }
            }
            let cot11 = rawCot11;

            let luong = Number(data.luongThang) || 0;
            let cot12 = cot11 * 5 * luong;
            let cot13 = 5 * luong;

            let nghiTruoc172025 = true;
            if (data.thoiDiemNghi && data.thoiDiemNghi >= new Date(2025, 6, 1)) {
                nghiTruoc172025 = false;
            }

            let moc14 = nghiTruoc172025 ? 20 : 15;
            let cot14 = 0;
            if (cot10 > moc14) {
                cot14 = (cot10 - moc14) * 0.5 * luong;
            }

            let cot15 = cot12 + cot13 + cot14;

            return {
                cot10, cot11, cot12, cot13, cot14, cot15,
                total: cot15, tran, moc14, nghiTruoc172025
            };
        }
    };

    // 4. Phụ lục I.5 (Lương tháng hiện hưởng làm căn cứ tính hưởng chế độ - 26.9.PHU_LUC_SUA.xlsx)
    const PLI5Calculator = {
        calculateExpected(data) {
            const LCS = 2340000; // 2.340.000 VNĐ

            // Cột 13 = Cột 7 * 2.340.000đ (Chênh lệch bảo lưu)
            let rawC7 = data.heSoChenhLechBaoLuu != null ? data.heSoChenhLechBaoLuu : (data.rawCols && data.rawCols[7]);
            let hsBaoLuu = Number(rawC7) || 0;
            let cot13 = Math.round(hsBaoLuu * LCS);

            // Cột 14 = Cột 6 * 2.340.000đ (Lương ngạch bậc)
            let rawC6 = data.heSoLuong != null ? data.heSoLuong : (data.rawCols && data.rawCols[6]);
            let hsLuong = Number(rawC6) || 0;
            let cot14 = Math.round(hsLuong * LCS);

            // Cột 15 = Cột 8 * 2.340.000đ (Phụ cấp chức vụ)
            let rawC8 = data.heSoChucVu != null ? data.heSoChucVu : (data.rawCols && data.rawCols[8]);
            let hsChucVu = Number(rawC8) || 0;
            let cot15 = Math.round(hsChucVu * LCS);

            // Cột 16 = [((Cột 10 – 1 tháng) – Cột 9) đơn vị tính năm] * (Cột 14 + Cột 15)
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi || data.cot10);
            let dNhapNgu = toDateObj(data.nhapNgu || data.cot9);
            let diffMonths = 0;
            if (dThoiDiemNghi && dNhapNgu) {
                let dNghiMinus1 = new Date(dThoiDiemNghi.getFullYear(), dThoiDiemNghi.getMonth() - 1, 1);
                diffMonths = calcThang(dNghiMinus1, dNhapNgu);
                if (diffMonths < 0) diffMonths = 0;
            }

            let soNamNguyen = Math.floor(diffMonths / 12);
            let tiLeThamNien = Math.max(0, soNamNguyen / 100); // Mỗi năm thâm niên nghề hưởng 1%
            let base14_15 = cot14 + cot15;
            let cot16 = Math.round(base14_15 * tiLeThamNien);

            // Cột 17 = Phụ cấp thâm niên vượt khung (đọc từ Excel nếu có)
            let rawC17 = data.phuCapThamNienVuotKhung != null ? data.phuCapThamNienVuotKhung : (data.rawCols && data.rawCols[17]);
            let cot17 = Number(rawC17) || 0;

            // Căn cứ tính Cột 18, 19, 20: (Cột 14 + Cột 15 + Cột 16)
            let base14_15_16 = cot14 + cot15 + cot16;

            // Cột 18 = Cột 11 * (Cột 14 + Cột 15 + Cột 16)
            let rawC11 = data.tiLePhuCapTrachNhiem != null ? data.tiLePhuCapTrachNhiem : (data.rawCols && data.rawCols[11]);
            let valC11 = Number(rawC11) || 0;
            let tiLeC11 = (valC11 > 1.0) ? (valC11 / 100) : valC11;
            let cot18 = Math.round(base14_15_16 * tiLeC11);

            // Cột 19 = 25% * (Cột 14 + Cột 15 + Cột 16)
            let cot19 = Math.round(base14_15_16 * 0.25);

            // Cột 20 = Cột 12 * (Cột 14 + Cột 15 + Cột 16)
            let rawC12 = data.tiLePhuCapDacThu != null ? data.tiLePhuCapDacThu : (data.rawCols && data.rawCols[12]);
            let valC12 = Number(rawC12) || 0;
            let tiLeC12 = (valC12 > 1.0) ? (valC12 / 100) : valC12;
            let cot20 = Math.round(base14_15_16 * tiLeC12);

            // Cột 21 = Được nhận khác (đọc từ Excel nếu có)
            let rawC21 = data.duocNhanKhac != null ? data.duocNhanKhac : (data.rawCols && data.rawCols[21]);
            let cot21 = Number(rawC21) || 0;

            // Cột 22 = SUM(Cột 13 : Cột 21)
            let cot22 = cot13 + cot14 + cot15 + cot16 + cot17 + cot18 + cot19 + cot20 + cot21;

            return {
                cot13, cot14, cot15, cot16, cot17, cot18, cot19, cot20, cot21, cot22,
                total: cot22,
                diffMonths,
                soNamThamNien: soNamNguyen,
                tiLeThamNien,
                tiLeC11,
                tiLeC12,
                base14_15,
                base14_15_16
            };
        }
    };

    // ============================================================
    // BỘ PHÂN TÍCH VÀ ĐỐI CHIẾU DỮ LIỆU EXCEL (CELL STATUS & PARSER)
    // ============================================================
    const CellStatus = Object.freeze({
        VALUE: 'VALUE',
        FORMULA_CACHED: 'FORMULA_CACHED',
        FORMULA_EVALUATED: 'FORMULA_EVALUATED',
        FORMULA_EMPTY: 'FORMULA_EMPTY',
        FORMULA_NO_RESULT: 'FORMULA_NO_RESULT',
        FORMULA_ERROR: 'FORMULA_ERROR',
        BLANK: 'BLANK',
        INVALID_TYPE: 'INVALID_TYPE'
    });

    const ExcelParser = {
        buildXmlFormulaIndex(buffer) {
            const xmlIndex = {};
            if (!buffer) return xmlIndex;
            try {
                let CFB = null;
                if (typeof XLSX !== 'undefined' && XLSX && XLSX.CFB) {
                    CFB = XLSX.CFB;
                } else if (typeof window !== 'undefined' && window.XLSX && window.XLSX.CFB) {
                    CFB = window.XLSX.CFB;
                } else if (typeof globalThis !== 'undefined' && globalThis.XLSX && globalThis.XLSX.CFB) {
                    CFB = globalThis.XLSX.CFB;
                } else if (typeof global !== 'undefined' && global.XLSX && global.XLSX.CFB) {
                    CFB = global.XLSX.CFB;
                } else if (typeof require === 'function') {
                    try {
                        const xlsxStatic = require('./bqp-validate-data/src/main/resources/static/xlsx.full.min.js');
                        if (xlsxStatic && xlsxStatic.CFB) CFB = xlsxStatic.CFB;
                    } catch (e1) {
                        try {
                            const xlsxRoot = require('./xlsx.full.min.js');
                            if (xlsxRoot && xlsxRoot.CFB) CFB = xlsxRoot.CFB;
                        } catch (e2) {}
                    }
                }
                if (!CFB) return xmlIndex;

                let zip;
                const isBuffer = buffer && (
                    (typeof Buffer !== 'undefined' && Buffer.isBuffer && Buffer.isBuffer(buffer)) ||
                    (buffer.constructor && buffer.constructor.name === 'Buffer') ||
                    buffer._isBuffer
                );
                const isArrayBuffer = buffer instanceof ArrayBuffer || (buffer && buffer.constructor && buffer.constructor.name === 'ArrayBuffer');
                const isUint8 = buffer instanceof Uint8Array || (buffer && buffer.constructor && buffer.constructor.name === 'Uint8Array');

                if (isBuffer) {
                    zip = CFB.read(buffer, { type: 'buffer' });
                } else if (isArrayBuffer) {
                    const u8 = (buffer.buffer && buffer.byteLength !== undefined) ? new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength) : new Uint8Array(buffer);
                    zip = CFB.read(u8, { type: 'array' });
                } else if (isUint8) {
                    zip = CFB.read(buffer, { type: 'array' });
                } else if (typeof buffer === 'string') {
                    zip = CFB.read(buffer, { type: 'binary' });
                } else {
                    try {
                        zip = CFB.read(new Uint8Array(buffer), { type: 'array' });
                    } catch (eFallback) {
                        zip = CFB.read(buffer, { type: 'binary' });
                    }
                }
                if (!zip) return xmlIndex;

                const findZipEntry = (p) => {
                    const cleanP = p.replace(/^\/?(Root Entry\/)?/, '');
                    const baseName = cleanP.split('/').pop();
                    return CFB.find(zip, 'Root Entry/' + cleanP)
                        || CFB.find(zip, cleanP)
                        || CFB.find(zip, 'Root Entry/' + baseName)
                        || CFB.find(zip, baseName);
                };

                const getEntryText = (p) => {
                    const entry = findZipEntry(p);
                    if (!entry || !entry.content) return null;
                    if (typeof Buffer !== 'undefined' && (Buffer.isBuffer(entry.content) || (entry.content.constructor && entry.content.constructor.name === 'Buffer'))) {
                        return entry.content.toString('utf8');
                    }
                    if (entry.content instanceof Uint8Array || (entry.content && entry.content.constructor && entry.content.constructor.name === 'Uint8Array') || Array.isArray(entry.content)) {
                        if (typeof TextDecoder !== 'undefined') {
                            return new TextDecoder('utf-8').decode(new Uint8Array(entry.content));
                        }
                        let str = '';
                        for (let i = 0; i < entry.content.length; i++) {
                            str += String.fromCharCode(entry.content[i]);
                        }
                        return decodeURIComponent(escape(str));
                    }
                    return String(entry.content);
                };

                const wbXml = getEntryText('xl/workbook.xml');
                if (!wbXml) return xmlIndex;

                const sheetMap = {};
                const sheetMatches = wbXml.matchAll(/<sheet\s+[^>]*name="([^"]+)"[^>]*r:id="([^"]+)"/g);
                for (const m of sheetMatches) {
                    sheetMap[m[2]] = m[1];
                }

                const relsXml = getEntryText('xl/_rels/workbook.xml.rels');
                if (!relsXml) return xmlIndex;

                const fileToSheetName = {};
                const relMatches = relsXml.matchAll(/<Relationship\s+[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"/g);
                for (const m of relMatches) {
                    const rId = m[1];
                    const target = m[2];
                    const sheetName = sheetMap[rId];
                    if (sheetName) {
                        const normPath = 'xl/' + target.replace(/^\/?xl\//, '').replace(/^\//, '');
                        fileToSheetName[normPath] = sheetName;
                    }
                }

                for (const fp in fileToSheetName) {
                    const sName = fileToSheetName[fp];
                    const sheetXml = getEntryText(fp);
                    if (!sheetXml) continue;

                    const sheetIndex = {};
                    const cellRegex = /<c\s+r="([A-Z0-9]+)"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g;
                    let match;
                    while ((match = cellRegex.exec(sheetXml)) !== null) {
                        const addr = match[1];
                        const body = match[3];
                        if (body && (body.includes('<f') || body.includes('<f>'))) {
                            const hasV = body.includes('<v>') || body.includes('<v/>') || body.includes('<v ');
                            let isEmptyV = false;
                            if (hasV) {
                                if (body.includes('<v/>')) {
                                    isEmptyV = true;
                                } else {
                                    const vm = body.match(/<v>([\s\S]*?)<\/v>/);
                                    if (vm && vm[1].trim() === '') {
                                        isEmptyV = true;
                                    }
                                }
                            }
                            sheetIndex[addr] = {
                                hasFormula: true,
                                hasV: hasV,
                                isEmptyV: isEmptyV
                            };
                        }
                    }
                    xmlIndex[sName] = sheetIndex;
                }
            } catch (err) {
                console.warn('[ExcelParser] buildXmlFormulaIndex warning:', err);
            }
            return xmlIndex;
        },
        readCellValue(cell, expectedType = null, options = {}) {
            const sheetName = options.sheetName || (cell && cell.worksheet ? cell.worksheet.name : '') || '';
            const cellAddress = cell ? (cell.address || '') : '';
            
            const res = {
                sheetName,
                cellAddress,
                formula: null,
                value: null,
                valueType: 'null',
                status: CellStatus.BLANK,
                valueSource: 'none',
                errorCode: null,
                rawText: ''
            };

            if (!cell || cell.value == null) {
                res.status = CellStatus.BLANK;
                res.rawText = '';
                return res;
            }

            const v = cell.value;
            const isFormula = (typeof cell.type === 'number' && cell.type === 6)
                || (v && typeof v === 'object' && !(v instanceof Date) && ('formula' in v || 'sharedFormula' in v));

            if (isFormula) {
                res.formula = (v && typeof v === 'object' && v.formula) ? String(v.formula) : (cell.formula ? String(cell.formula) : null);
                
                // 1. Kiểm tra lỗi ô trực tiếp trong cell.value
                if (v && typeof v === 'object' && v.error) {
                    res.status = CellStatus.FORMULA_ERROR;
                    res.errorCode = String(v.error);
                    res.valueSource = 'none';
                    res.rawText = `[Lỗi ô: ${res.errorCode}]`;
                    return res;
                }

                // 2. Lấy kết quả lưu sẵn (result)
                const cachedRes = (cell.result !== undefined ? cell.result : null) ?? (v && typeof v === 'object' ? v.result : null);
                
                if (cachedRes !== undefined && cachedRes !== null) {
                    if (typeof cachedRes === 'object' && cachedRes.error) {
                        res.status = CellStatus.FORMULA_ERROR;
                        res.errorCode = String(cachedRes.error);
                        res.valueSource = 'none';
                        res.rawText = `[Lỗi ô: ${res.errorCode}]`;
                        return res;
                    }
                    if (typeof cachedRes === 'string' && /^#(?:VALUE!|REF!|NAME\?|DIV\/0!|N\/A|NUM!|NULL!)$/i.test(cachedRes.trim())) {
                        res.status = CellStatus.FORMULA_ERROR;
                        res.errorCode = cachedRes.trim();
                        res.valueSource = 'none';
                        res.rawText = `[Lỗi ô: ${res.errorCode}]`;
                        return res;
                    }
                    if (cachedRes === '') {
                        res.status = CellStatus.FORMULA_EMPTY;
                        res.value = '';
                        res.valueType = 'string';
                        res.valueSource = 'cached';
                        res.rawText = '';
                        return res;
                    }
                    if (typeof cachedRes === 'number') {
                        res.status = CellStatus.FORMULA_CACHED;
                        res.value = isNaN(cachedRes) ? null : cachedRes;
                        res.valueType = 'number';
                        res.valueSource = 'cached';
                        res.rawText = String(cachedRes);
                        return res;
                    }
                    if (cachedRes instanceof Date) {
                        res.status = CellStatus.FORMULA_CACHED;
                        res.value = cachedRes;
                        res.valueType = 'date';
                        res.valueSource = 'cached';
                        res.rawText = cachedRes.toISOString();
                        return res;
                    }
                    if (typeof cachedRes === 'boolean') {
                        res.status = CellStatus.FORMULA_CACHED;
                        res.value = cachedRes;
                        res.valueType = 'boolean';
                        res.valueSource = 'cached';
                        res.rawText = String(cachedRes);
                        return res;
                    }
                    const s = String(cachedRes);
                    if (s.trim() === '') {
                        res.status = CellStatus.FORMULA_EMPTY;
                        res.value = '';
                        res.valueType = 'string';
                        res.valueSource = 'cached';
                        res.rawText = '';
                    } else {
                        res.status = CellStatus.FORMULA_CACHED;
                        res.value = s;
                        res.valueType = 'string';
                        res.valueSource = 'cached';
                        res.rawText = s;
                        if (expectedType === 'number') {
                            const n = BQPNormalization.number(s);
                            if (n != null) {
                                res.value = n;
                                res.valueType = 'number';
                            }
                        }
                    }
                    return res;
                }

                // cachedRes is null or undefined: Tra cứu chỉ mục XML chuẩn xác (không suy đoán bằng regex)
                const xmlIndex = options.xmlIndex 
                    || (cell && cell.worksheet && cell.worksheet.workbook ? cell.worksheet.workbook._xmlIndex : null)
                    || (cell && cell.workbook ? cell.workbook._xmlIndex : null);
                
                const getSheetXmlIndex = (idx, name) => {
                    if (!idx || !name) return null;
                    if (idx[name]) return idx[name];
                    const clean = s => String(s || '').toLowerCase().replace(/\s+/g, '').replace(/phụlục|phuluc|pl/g, '').replace(/\./g, '');
                    const target = clean(name);
                    for (const k of Object.keys(idx)) {
                        if (clean(k) === target) return idx[k];
                    }
                    return null;
                };

                const sheetXmlIndex = getSheetXmlIndex(xmlIndex, sheetName);
                const xmlCell = (sheetXmlIndex && cellAddress) ? sheetXmlIndex[cellAddress] : null;

                if (xmlCell) {
                    if (xmlCell.hasV && xmlCell.isEmptyV) {
                        res.status = CellStatus.FORMULA_EMPTY;
                        res.value = '';
                        res.valueType = 'string';
                        res.valueSource = 'cached';
                        res.rawText = '';
                        return res;
                    }
                    if (!xmlCell.hasV) {
                        res.status = CellStatus.FORMULA_NO_RESULT;
                        res.value = null;
                        res.valueSource = 'none';
                        res.rawText = '[Lỗi ô: Chưa tính kết quả]';
                        return res;
                    }
                }

                // Hoàn toàn không có kết quả cache
                res.status = CellStatus.FORMULA_NO_RESULT;
                res.valueSource = 'none';
                res.rawText = '[Lỗi ô: Chưa tính kết quả]';
                return res;
            }

            // Giá trị thường (Literal)
            if (typeof v === 'object' && !(v instanceof Date)) {
                if (v.error) {
                    res.status = CellStatus.FORMULA_ERROR;
                    res.errorCode = String(v.error);
                    res.rawText = `[Lỗi ô: ${res.errorCode}]`;
                    return res;
                }
                if (v.richText && Array.isArray(v.richText)) {
                    const txt = v.richText.map(rt => rt.text || '').join('');
                    if (!txt.trim()) {
                        res.status = CellStatus.BLANK;
                        return res;
                    }
                    res.status = CellStatus.VALUE;
                    res.value = txt;
                    res.valueType = 'string';
                    res.valueSource = 'literal';
                    res.rawText = txt;
                    return res;
                }
                if (v.text != null) {
                    const txt = String(v.text);
                    if (!txt.trim()) {
                        res.status = CellStatus.BLANK;
                        return res;
                    }
                    res.status = CellStatus.VALUE;
                    res.value = txt;
                    res.valueType = 'string';
                    res.valueSource = 'literal';
                    res.rawText = txt;
                    return res;
                }
            }

            if (typeof v === 'number') {
                res.status = CellStatus.VALUE;
                res.value = isNaN(v) ? null : v;
                res.valueType = 'number';
                res.valueSource = 'literal';
                res.rawText = String(v);
                return res;
            }

            if (v instanceof Date) {
                res.status = CellStatus.VALUE;
                res.value = v;
                res.valueType = 'date';
                res.valueSource = 'literal';
                res.rawText = v.toISOString();
                return res;
            }

            if (typeof v === 'boolean') {
                res.status = CellStatus.VALUE;
                res.value = v;
                res.valueType = 'boolean';
                res.valueSource = 'literal';
                res.rawText = String(v);
                return res;
            }

            const strVal = String(v || '').trim();
            if (!strVal) {
                res.status = CellStatus.BLANK;
                return res;
            }

            res.status = CellStatus.VALUE;
            res.value = strVal;
            res.valueType = 'string';
            res.valueSource = 'literal';
            res.rawText = strVal;

            if (expectedType === 'number') {
                const n = BQPNormalization.number(strVal);
                if (n != null) {
                    res.value = n;
                    res.valueType = 'number';
                } else {
                    res.status = CellStatus.INVALID_TYPE;
                    res.errorCode = 'INVALID_TYPE';
                    res.value = null;
                }
            }

            return res;
        },

        readNumber(cell, options = {}) {
            return this.readCellValue(cell, 'number', options);
        },

        getCellText(cell) {
            if (!cell || cell.value == null) return '';
            try {
                const res = this.readCellValue(cell);
                if (res.status === CellStatus.FORMULA_ERROR) {
                    return `[Lỗi ô: ${res.errorCode || 'Lỗi công thức'}]`;
                }
                if (res.status === CellStatus.FORMULA_NO_RESULT) {
                    return '[Lỗi ô: Chưa tính kết quả]';
                }
                if (res.status === CellStatus.FORMULA_EMPTY || res.status === CellStatus.BLANK) {
                    return '';
                }
                if (res.value instanceof Date) {
                    return formatDate(res.value);
                }
                return cleanString(res.rawText || (res.value != null ? String(res.value) : ''));
            } catch (e) {
                return `[Lỗi ô: ${e.message || 'Lỗi đọc'}]`;
            }
        },

        parseNumber(cell, defaultValue = null) {
            if (!cell || cell.value == null) return defaultValue;
            try {
                const res = this.readCellValue(cell, 'number');
                if (res.status === CellStatus.FORMULA_ERROR || res.status === CellStatus.FORMULA_NO_RESULT) {
                    return null;
                }
                if (res.status === CellStatus.FORMULA_EMPTY || res.status === CellStatus.BLANK) {
                    return defaultValue;
                }
                if (typeof res.value === 'number' && !isNaN(res.value)) {
                    return res.value;
                }
                if (res.status === CellStatus.INVALID_TYPE) {
                    return null;
                }
                const n = BQPNormalization.number(res.rawText);
                return n == null ? defaultValue : n;
            } catch (e) {
                return defaultValue;
            }
        },

        parseDateCell(cell) {
            try {
                if (!cell || cell.value == null) return null;
                const res = this.readCellValue(cell, 'date');
                if (res.status === CellStatus.FORMULA_ERROR || res.status === CellStatus.FORMULA_NO_RESULT) {
                    return null;
                }
                if (res.status === CellStatus.FORMULA_EMPTY || res.status === CellStatus.BLANK) {
                    return null;
                }
                if (res.value instanceof Date) {
                    let val = new Date(res.value.getTime());
                    let y = val.getFullYear();
                    if (y > 2040) val.setFullYear(y - 100);
                    return val;
                }
                if (typeof res.value === 'number' && res.value >= 10000 && res.value <= 70000) {
                    let dateObj = new Date(Math.round((res.value - 25569) * 86400 * 1000));
                    if (!isNaN(dateObj.getTime())) {
                        let y = dateObj.getFullYear();
                        if (y > 2040) dateObj.setFullYear(y - 100);
                        return dateObj;
                    }
                }
                let rawText = res.rawText || this.getCellText(cell);
                if (!rawText || !rawText.trim() || rawText.includes('[Lỗi')) return null;

                // Xử lý chuỗi chứa nhiều mốc ngày kèm tiền tố (TD: mm/yyyy, NN: mm/yyyy, BH: mm/yyyy, SQDB: mm/yyyy...)
                let dateMatches = [];
                let regex = /(?:(?:(\d{1,2})[\/\.,-])?(\d{1,2})[\/\.,-](\d{4}))/g;
                let mMatch;
                while ((mMatch = regex.exec(rawText)) !== null) {
                    let d = mMatch[1] ? parseInt(mMatch[1], 10) : 1;
                    let m = parseInt(mMatch[2], 10);
                    let y = parseInt(mMatch[3], 10);
                    if (y < 100) y = y <= 45 ? 2000 + y : 1900 + y;
                    if (y > 2040) y -= 100;
                    if (m >= 1 && m <= 12 && y >= 1940 && y <= 2040) {
                        dateMatches.push(new Date(y, m - 1, d));
                    }
                }
                if (dateMatches.length === 0) {
                    let regex2 = /(?:(?:(\d{1,2})[\/\.,-])?(\d{1,2})[\/\.,-](\d{2}))\b/g;
                    while ((mMatch = regex2.exec(rawText)) !== null) {
                        let d = mMatch[1] ? parseInt(mMatch[1], 10) : 1;
                        let m = parseInt(mMatch[2], 10);
                        let yy = parseInt(mMatch[3], 10);
                        let y = yy <= 45 ? 2000 + yy : 1900 + yy;
                        if (m >= 1 && m <= 12) {
                            dateMatches.push(new Date(y, m - 1, d));
                        }
                    }
                }
                if (dateMatches.length > 0) {
                    dateMatches.sort((a, b) => a.getTime() - b.getTime());
                    return dateMatches[0];
                }

                let text = rawText.replace(/[-.,]/g, '/').replace(/\s+/g, '');
                let parts = text.split('/');
                if (parts.length === 2) {
                    let m = parseInt(parts[0], 10);
                    let y = parseInt(parts[1], 10);
                    if (y < 100) y = y <= 45 ? 2000 + y : 1900 + y;
                    if (y > 2040) y -= 100;
                    if (m >= 1 && m <= 12) return new Date(y, m - 1, 1);
                } else if (parts.length === 3) {
                    let d = parseInt(parts[0], 10);
                    let m = parseInt(parts[1], 10);
                    let y = parseInt(parts[2], 10);
                    if (parts[0].length === 4) {
                        y = parseInt(parts[0], 10);
                        m = parseInt(parts[1], 10);
                        d = parseInt(parts[2], 10);
                    }
                    if (y < 100) y = y <= 45 ? 2000 + y : 1900 + y;
                    if (y > 2040) y -= 100;
                    if (m >= 1 && m <= 12) return new Date(y, m - 1, d);
                } else if (/^\d{4}$/.test(text)) {
                    let y = parseInt(text, 10);
                    if (y > 2040) y -= 100;
                    return new Date(y, 0, 1);
                }
                return null;
            } catch (dateErr) {
                return null;
            }
        },

        generateFormulaReport(workbook, options = {}) {
            const report = {
                totalFormulas: 0,
                cachedFormulas: 0,
                evaluatedFormulas: 0,
                emptyFormulas: 0,
                noResultFormulas: 0,
                errorFormulas: 0,
                suspectedOldCacheFormulas: 0,
                sheetSummary: {},
                issues: []
            };

            if (!workbook || !workbook.eachSheet) return report;
            const xmlIndex = options.xmlIndex || (workbook ? workbook._xmlIndex : null) || null;

            workbook.eachSheet((ws) => {
                const sheetStats = {
                    sheetName: ws.name,
                    total: 0,
                    cached: 0,
                    evaluated: 0,
                    empty: 0,
                    noResult: 0,
                    error: 0
                };

                ws.eachRow((row, r) => {
                    row.eachCell((cell, c) => {
                        const isFormula = (typeof cell.type === 'number' && cell.type === 6)
                            || (cell.value && typeof cell.value === 'object' && !(cell.value instanceof Date) && ('formula' in cell.value || 'sharedFormula' in cell.value));
                        
                        if (isFormula) {
                            report.totalFormulas++;
                            sheetStats.total++;

                            const readRes = this.readCellValue(cell, null, { sheetName: ws.name, xmlIndex });
                            switch (readRes.status) {
                                case CellStatus.FORMULA_CACHED:
                                    report.cachedFormulas++;
                                    sheetStats.cached++;
                                    break;
                                case CellStatus.FORMULA_EVALUATED:
                                    report.evaluatedFormulas++;
                                    sheetStats.evaluated++;
                                    break;
                                case CellStatus.FORMULA_EMPTY:
                                    report.emptyFormulas++;
                                    sheetStats.empty++;
                                    break;
                                case CellStatus.FORMULA_NO_RESULT:
                                    report.noResultFormulas++;
                                    sheetStats.noResult++;
                                    report.issues.push({
                                        sheet: ws.name,
                                        address: cell.address || `R${r}C${c}`,
                                        formula: readRes.formula,
                                        status: readRes.status,
                                        errorCode: 'NO_RESULT',
                                        row: r,
                                        col: c
                                    });
                                    break;
                                case CellStatus.FORMULA_ERROR:
                                    report.errorFormulas++;
                                    sheetStats.error++;
                                    report.issues.push({
                                        sheet: ws.name,
                                        address: cell.address || `R${r}C${c}`,
                                        formula: readRes.formula,
                                        status: readRes.status,
                                        errorCode: readRes.errorCode,
                                        row: r,
                                        col: c
                                    });
                                    break;
                            }
                        }
                    });
                });

                if (sheetStats.total > 0) {
                    report.sheetSummary[ws.name] = sheetStats;
                }
            });

            return report;
        },

        findColMap(worksheet) {
            const adapted = (typeof BQPNormalization !== 'undefined' && BQPNormalization && BQPNormalization.findColMap) ? BQPNormalization.findColMap(worksheet) : null;
            if (adapted) return adapted;
            let colMap = {};
            let headerRowIdx = -1;
            let rowCount = Math.min(15, worksheet.rowCount);

            for (let r = 1; r <= rowCount; r++) {
                let row = worksheet.getRow(r);
                let rowMap = {};
                for (let c = 1; c <= 35; c++) {
                    let txt = this.getCellText(row.getCell(c));
                    if (txt.includes('/') || /^\d+[-–]\d+$/.test(txt.trim())) continue;
                    let m = txt.match(/^[\(\[]?(\d{1,2})/);
                    if (m) {
                        let num = parseInt(m[1], 10);
                        if (num >= 1 && num <= 35 && !rowMap[num]) {
                            rowMap[num] = c;
                        }
                    }
                }
                if (Object.keys(rowMap).length >= 7 && rowMap[1] && rowMap[2] && rowMap[3] && rowMap[4] && rowMap[5]) {
                    headerRowIdx = r;
                    // Nhận diện cột Họ và tên nằm ở số nào (1 hay 2)
                    let colHoTenNum = -1;
                    for (let num in rowMap) {
                        let cIdx = rowMap[num];
                        for (let pr = Math.max(1, r - 5); pr < r; pr++) {
                            let prevRow = worksheet.getRow(pr);
                            let hText = this.getCellText(prevRow.getCell(cIdx)).toLowerCase().replace(/\s+/g, ' ').trim();
                            if (hText.includes('họ và tên') || hText.includes('ho va ten') || hText.includes('họ tên')) {
                                colHoTenNum = parseInt(num, 10);
                                break;
                            }
                        }
                        if (colHoTenNum !== -1) break;
                    }

                    // Nhận diện cột Lương nằm ở số nào (9 hay 10)
                    let colLuongNum = -1;
                    for (let num in rowMap) {
                        let cIdx = rowMap[num];
                        for (let pr = Math.max(1, r - 5); pr < r; pr++) {
                            let prevRow = worksheet.getRow(pr);
                            let hText = this.getCellText(prevRow.getCell(cIdx)).toLowerCase().replace(/\s+/g, ' ').trim();
                            if (hText.includes('lương tháng') || hText.includes('luong thang') || hText.includes('tiền lương')) {
                                colLuongNum = parseInt(num, 10);
                                break;
                            }
                        }
                        if (colLuongNum !== -1) break;
                    }

                    if (colLuongNum === 10 && colHoTenNum === 2) {
                        // Mẫu cũ có thêm cột "Đánh giá xếp loại cán bộ"
                        colMap[1] = rowMap[1]; // STT
                        colMap[2] = rowMap[2]; // Họ tên
                        colMap[3] = rowMap[3]; // Ngày sinh
                        colMap[4] = rowMap[4]; // Cấp bậc
                        colMap[5] = rowMap[5]; // Chức vụ
                        colMap[6] = rowMap[6]; // Nhập ngũ
                        colMap[7] = rowMap[8]; // Sáp nhập
                        colMap[8] = rowMap[9]; // Nghỉ hưu
                        colMap[9] = rowMap[10]; // Lương
                        for (let k = 10; k <= 35; k++) {
                            if (rowMap[k + 1]) colMap[k] = rowMap[k + 1];
                        }
                    } else if (colHoTenNum === 1 && colLuongNum === 8) {
                        // Mẫu không có STT (Họ tên ở cột 1)
                        colMap[2] = rowMap[1]; // Họ tên
                        colMap[3] = rowMap[2]; // Ngày sinh
                        colMap[4] = rowMap[3]; // Cấp bậc
                        colMap[5] = rowMap[4]; // Chức vụ
                        colMap[6] = rowMap[5]; // Nhập ngũ
                        colMap[7] = rowMap[6]; // Sáp nhập
                        colMap[8] = rowMap[7]; // Nghỉ
                        colMap[9] = rowMap[8]; // Lương
                        for (let k = 10; k <= 35; k++) {
                            if (rowMap[k - 1]) colMap[k] = rowMap[k - 1];
                        }
                    } else {
                        // Mẫu chuẩn mới 26.9.PHU_LUC_SUA.xlsx (1: STT, 2: Họ tên, 3: Ngày sinh, 4: Cấp bậc, 5: Chức vụ, 6: Nhập ngũ, 7: Sáp nhập, 8: Nghỉ, 9: Lương, 10..35: Chế độ)
                        for (let k in rowMap) {
                            colMap[k] = rowMap[k];
                        }
                    }
                    break;
                }
            }
            return { colMap, headerRowIdx };
        }
    };

    // ============================================================
    // DỊCH VỤ ĐỐI CHIẾU VÀ PHÁT HIỆN LỖI TỪNG CỘT
    // ============================================================
    const ValidationService = {
        validateRow(sheetType, rowData) {
            let exp = {};
            let comparisons = [];
            let errorDetails = [];

            try {
                // Kiểm tra các ô bị lỗi phát hiện trực tiếp từ Excel
                if (rowData && rowData.cellErrors && typeof rowData.cellErrors === 'object') {
                    for (let colIdx in rowData.cellErrors) {
                        let errTxt = rowData.cellErrors[colIdx];
                        errorDetails.push(`Cột ${colIdx}: Ô chứa dữ liệu/công thức lỗi (${errTxt}).`);
                        comparisons.push({
                            col: `Cột ${colIdx}`,
                            title: `Lỗi ô Cột ${colIdx}`,
                            actual: errTxt,
                            expected: 'Số hợp lệ',
                            diff: 'Lỗi',
                            hasErr: true,
                            formula: 'Dữ liệu ô bị lỗi trong file Excel'
                        });
                    }
                }

                if (rowData && typeof rowData.ngaySinh === 'string' && rowData.ngaySinh.startsWith('[Lỗi')) {
                    errorDetails.push(`Cột Ngày sinh: Dữ liệu ô bị lỗi (${rowData.ngaySinh}).`);
                    comparisons.push({
                        col: 'Cột 3',
                        title: 'Ngày tháng năm sinh',
                        actual: rowData.ngaySinh,
                        expected: 'Ngày hợp lệ',
                        diff: 'Lỗi',
                        hasErr: true,
                        formula: 'Định dạng ngày sinh bị lỗi'
                    });
                }
                if (rowData && typeof rowData.nhapNgu === 'string' && rowData.nhapNgu.startsWith('[Lỗi')) {
                    errorDetails.push(`Cột Ngày nhập ngũ: Dữ liệu ô bị lỗi (${rowData.nhapNgu}).`);
                    comparisons.push({
                        col: 'Cột 6',
                        title: 'Thời điểm nhập ngũ',
                        actual: rowData.nhapNgu,
                        expected: 'Ngày hợp lệ',
                        diff: 'Lỗi',
                        hasErr: true,
                        formula: 'Định dạng ngày nhập ngũ bị lỗi'
                    });
                }
                if (rowData && typeof rowData.thoiDiemNghi === 'string' && rowData.thoiDiemNghi.startsWith('[Lỗi')) {
                    errorDetails.push(`Cột Thời điểm nghỉ: Dữ liệu ô bị lỗi (${rowData.thoiDiemNghi}).`);
                    comparisons.push({
                        col: 'Cột 8',
                        title: 'Thời điểm nghỉ',
                        actual: rowData.thoiDiemNghi,
                        expected: 'Ngày hợp lệ',
                        diff: 'Lỗi',
                        hasErr: true,
                        formula: 'Định dạng thời điểm nghỉ bị lỗi'
                    });
                }
                if (rowData && typeof rowData.hoTen === 'string' && rowData.hoTen.startsWith('[Lỗi')) {
                    errorDetails.push(`Cột Họ và tên: Dữ liệu ô bị lỗi (${rowData.hoTen}).`);
                }
                if (rowData && typeof rowData.capBac === 'string' && rowData.capBac.startsWith('[Lỗi')) {
                    errorDetails.push(`Cột Cấp bậc: Dữ liệu ô bị lỗi (${rowData.capBac}).`);
                }

                // KIỂM TRA ĐIỀU KIỆN TIÊN QUYẾT (PREREQUISITE) TRƯỚC KHI THẨM ĐỊNH
                const missingPrereqs = [];
                if (!rowData.hoTen || String(rowData.hoTen).startsWith('[Lỗi')) missingPrereqs.push('Họ và tên');
                if (!rowData.capBac || String(rowData.capBac).startsWith('[Lỗi')) missingPrereqs.push('Cấp bậc');
                if (sheetType !== 'I.5') {
                    if (!rowData.ngaySinh || !(rowData.ngaySinh instanceof Date) || isNaN(rowData.ngaySinh.getTime())) {
                        missingPrereqs.push('Ngày sinh');
                    }
                } else {
                    if (rowData.heSoLuong == null || rowData.heSoLuong <= 0) {
                        missingPrereqs.push('Hệ số lương');
                    }
                }
                if (!rowData.nhapNgu || !(rowData.nhapNgu instanceof Date) || isNaN(rowData.nhapNgu.getTime())) {
                    missingPrereqs.push('Ngày nhập ngũ');
                }
                if (!rowData.thoiDiemNghi || !(rowData.thoiDiemNghi instanceof Date) || isNaN(rowData.thoiDiemNghi.getTime())) {
                    missingPrereqs.push('Thời điểm nghỉ');
                }
                if (rowData.luongThang == null || rowData.luongThang <= 0) {
                    missingPrereqs.push(sheetType === 'I.5' ? 'Tiền lương & phụ cấp (Cột 22)' : 'Tiền lương tháng bình quân (Cột 9)');
                }

                if (missingPrereqs.length > 0) {
                    const prereqMsg = `Hồ sơ chưa đủ điều kiện tiên quyết: Thiếu hoặc lỗi các trường bắt buộc [${missingPrereqs.join(', ')}]. Ô công thức chưa được tính hoặc dữ liệu nguồn bị lỗi.`;
                    errorDetails.unshift(prereqMsg);
                    return {
                        status: 'INCOMPLETE_INPUT',
                        exp: {},
                        comparisons: comparisons,
                        errorDetails: errorDetails,
                        actualTotal: null,
                        expectedTotal: null,
                        diff: null,
                        hasErrors: true,
                        hasDiff24Months: false,
                        isIncomplete: true
                    };
                }

                if (sheetType === 'I.1') {
                    exp = PLI1Calculator.calculateExpected(rowData);
                    let raw = rowData.rawCols || {};

                // Cột 10: Tháng nghỉ hưu trước tuổi
                let c10Act = raw[10];
                let c10Num = (typeof c10Act === 'number') ? c10Act : (c10Act != null ? parseFloat(String(c10Act).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
                let isC10Correct60 = (exp.rawCot10 >= 60 && c10Num === 60);
                let c10Exp = isC10Correct60 ? (c10Act != null ? c10Act : 60) : exp.cot10;
                let c10Err = (c10Act != null && !isC10Correct60 && (c10Num !== exp.cot10));
                let fDesc10 = isC10Correct60
                    ? `Thẩm định ${exp.rawCot10} tháng ≥ 60 tháng -> Ghi 60 tháng đúng theo mức khống chế tối đa NĐ 178 (Đạt chuẩn)`
                    : `(${formatDate(rowData.ngaySinh)} + ${exp.tran} tuổi) - ${formatDate(rowData.thoiDiemNghi)} + 1 tháng (tính cả 2 đầu tháng)`;
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Số tháng nghỉ hưu trước tuổi',
                    actual: c10Act != null ? c10Act + ' tháng' : 'Chưa nhập',
                    expected: c10Exp + ' tháng',
                    diff: c10Err ? `${c10Act - c10Exp > 0 ? '+' : ''}${c10Act - c10Exp} tháng` : '0 tháng',
                    hasErr: c10Err,
                    formula: fDesc10
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Số tháng nghỉ trước tuổi): File ghi ${c10Act} tháng, Chuẩn tính lại ${c10Exp} tháng (lệch ${c10Act - c10Exp} tháng). Căn cứ trần tuổi ${exp.tran} và thời điểm nghỉ ${formatDate(rowData.thoiDiemNghi)}.`);
                }

                // Cột 11: Năm nghỉ hưu trước tuổi
                let c11Act = raw[11];
                let c11Num = (typeof c11Act === 'number') ? c11Act : (c11Act != null ? parseFloat(String(c11Act).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Num, exp.cot11));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Số năm nghỉ hưu trước tuổi',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `${exp.rawCot10} tháng / 12 = ${(exp.rawCot10 / 12).toFixed(2)} năm -> làm tròn theo quy tắc`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Số năm nghỉ trước tuổi): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                // Cột 12: Thời gian đóng BHXH
                let c12Act = raw[12];
                let c12Exp = exp.cot12;
                let c12Err = (c12Act != null && !isEqualTime(c12Act, c12Exp));
                comparisons.push({
                    col: 'Cột 12',
                    title: 'Thời gian đóng BHXH',
                    actual: c12Act != null ? fmtNum(c12Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c12Exp) + ' năm',
                    diff: c12Err ? `${c12Act - c12Exp > 0 ? '+' : ''}${fmtNum(c12Act - c12Exp)} năm` : '0 năm',
                    hasErr: c12Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn theo quy tắc`
                });
                if (c12Err) {
                    errorDetails.push(`Cột 12 (Thời gian đóng BHXH): File ghi ${fmtNum(c12Act)} năm, Chuẩn tính lại ${fmtNum(c12Exp)} năm.`);
                }

                // Cột 13..22: Tiền trợ cấp
                const colDefs = [
                    { k: 13, expKey: 'cot13', title: 'Trợ cấp nghỉ sớm (HS 1.0)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 1.0 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: 'Trợ cấp nghỉ sớm (HS 0.9)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.9 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 15, expKey: 'cot15', title: 'Trợ cấp nghỉ sớm (HS 0.5)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 16, expKey: 'cot16', title: 'Trợ cấp nghỉ sớm (HS 0.45)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.45 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 17, expKey: 'cot17', title: 'Trợ cấp tuổi đời (Nhóm 2 - 5 năm)', f: `${fmtNum(exp.cot11)} năm × 5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 18, expKey: 'cot18', title: `Trợ cấp BHXH (${exp.mocBHXH} năm đầu)`, f: `${exp.hsBHXH} tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 19, expKey: 'cot19', title: `Trợ cấp BHXH (vượt ${exp.mocBHXH} năm)`, f: `0.5 × (${fmtNum(exp.cot12)} - ${exp.mocBHXH}) × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 20, expKey: 'cot20', title: 'Trợ cấp tuổi đời (Nhóm 5 - 10 năm)', f: `${fmtNum(exp.cot11)} năm × 4 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 21, expKey: 'cot21', title: `Trợ cấp BHXH (${exp.mocBHXH} năm đầu)`, f: `${exp.hsBHXH} tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 22, expKey: 'cot22', title: `Trợ cấp BHXH (vượt ${exp.mocBHXH} năm)`, f: `0.5 × (${fmtNum(exp.cot12)} - ${exp.mocBHXH}) × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                // Kiểm tra đơn vị cộng gộp Cột 18 + 19 hoặc Cột 21 + 22 theo quy định (chuẩn Java)
                let c18Act = raw[18] || 0;
                let c19Act = raw[19] || 0;
                let exp18 = exp.cot18 || 0;
                let exp19 = exp.cot19 || 0;
                let exp18Plus19 = exp18 + exp19;
                let isMerged18_19 = (c19Act === 0) && c18Act !== 0 && isEqual(c18Act, exp18Plus19);

                let c21Act = raw[21] || 0;
                let c22Act = raw[22] || 0;
                let exp21 = exp.cot21 || 0;
                let exp22 = exp.cot22 || 0;
                let exp21Plus22 = exp21 + exp22;
                let isMerged21_22 = (c22Act === 0) && c21Act !== 0 && isEqual(c21Act, exp21Plus22);

                colDefs.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;

                    if ((def.k === 18 || def.k === 19) && isMerged18_19) {
                        if (def.k === 18) {
                            comparisons.push({
                                col: 'Cột 18',
                                title: def.title + ' (Cộng gộp Cột 18 + 19)',
                                actual: fmtMoney(actVal) + ' đ',
                                expected: fmtMoney(exp18Plus19) + ' đ',
                                diff: '0 đ',
                                hasErr: false,
                                formula: `Đơn vị cộng gộp Cột 18 (${fmtMoney(exp18)} đ) + Cột 19 (${fmtMoney(exp19)} đ) -> HỢP LỆ`
                            });
                        } else {
                            comparisons.push({
                                col: 'Cột 19',
                                title: def.title + ' (Đã cộng gộp vào Cột 18)',
                                actual: '0 đ',
                                expected: 'Đã gộp',
                                diff: '0 đ',
                                hasErr: false,
                                formula: 'Đã cộng gộp vào Cột 18 -> HỢP LỆ'
                            });
                        }
                        return;
                    }

                    if ((def.k === 21 || def.k === 22) && isMerged21_22) {
                        if (def.k === 21) {
                            comparisons.push({
                                col: 'Cột 21',
                                title: def.title + ' (Cộng gộp Cột 21 + 22)',
                                actual: fmtMoney(actVal) + ' đ',
                                expected: fmtMoney(exp21Plus22) + ' đ',
                                diff: '0 đ',
                                hasErr: false,
                                formula: `Đơn vị cộng gộp Cột 21 (${fmtMoney(exp21)} đ) + Cột 22 (${fmtMoney(exp22)} đ) -> HỢP LỆ`
                            });
                        } else {
                            comparisons.push({
                                col: 'Cột 22',
                                title: def.title + ' (Đã cộng gộp vào Cột 21)',
                                actual: '0 đ',
                                expected: 'Đã gộp',
                                diff: '0 đ',
                                hasErr: false,
                                formula: 'Đã cộng gộp vào Cột 21 -> HỢP LỆ'
                            });
                        }
                        return;
                    }

                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh chi trả (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                // Cột 23: Tổng cộng số tiền
                let actualTotal = raw[23] != null ? (typeof raw[23] === 'number' ? raw[23] : (BQPNormalization.number(raw[23]) ?? raw[23])) : null;
                let expectedTotal = exp.total;
                let totalErr = (actualTotal == null) || !isEqual(actualTotal, expectedTotal);
                let diffVal = (actualTotal != null && expectedTotal != null) ? (expectedTotal - actualTotal) : null;

                comparisons.push({
                    col: 'Cột 23',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: actualTotal != null ? fmtMoney(actualTotal) + ' đ' : (rowData.cellErrors && rowData.cellErrors[23] ? rowData.cellErrors[23] : 'Chưa tính kết quả'),
                    expected: expectedTotal != null ? fmtMoney(expectedTotal) + ' đ' : 'Chưa xác định',
                    diff: diffVal != null ? (diffVal !== 0 ? (diffVal > 0 ? '+' : '') + fmtMoney(diffVal) + ' đ' : '0 đ') : 'Chưa xác định',
                    hasErr: totalErr,
                    formula: 'Cột 13 + 14 + 15 + 16 + 17 + 18 + 19 + 20 + 21 + 22'
                });
                if (totalErr) {
                    if (actualTotal != null) {
                        errorDetails.push(`Cột 23 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                    } else {
                        errorDetails.push(`Cột 23 (Tổng cộng số tiền): File chưa tính hoặc lỗi ô công thức (${rowData.cellErrors && rowData.cellErrors[23] ? rowData.cellErrors[23] : 'Chưa tính'}).`);
                    }
                }

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: diffVal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            } else if (sheetType === 'I.2') {
                exp = PLI2Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                // Cột 10: Số tháng thôi việc
                let c10Act = raw[10];
                let c10Num = (typeof c10Act === 'number') ? c10Act : (c10Act != null ? parseFloat(String(c10Act).replace(/,/g, '.').replace(/[^0-9.-]/g, '')) : null);
                let isC10Correct60 = ((exp.rawThangConLai >= 60 || exp.cot10 >= 60 || (exp.rawCot10 != null && exp.rawCot10 >= 60)) && c10Num === 60);
                let c10Exp = isC10Correct60 ? (c10Act != null ? c10Act : 60) : exp.cot10;
                let c10Err = (c10Act != null && !isC10Correct60 && (c10Num !== exp.cot10));
                let fDesc10 = isC10Correct60
                    ? `Thẩm định tuổi đời trước trần ${exp.rawThangConLai} tháng ≥ 60 tháng -> Ghi 60 tháng đúng theo mức khống chế tối đa NĐ 178 (Đạt chuẩn)`
                    : ((exp.monthsCongTac > 0 && exp.monthsCongTac < 60)
                        ? `Khống chế theo thời gian công tác ${exp.monthsCongTac} tháng (< 60 tháng theo NĐ 178)`
                        : `min(Tuổi đời trước trần ${exp.rawThangConLai} tháng, 60 tháng)`);
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Số tháng thôi việc',
                    actual: c10Act != null ? c10Act + ' tháng' : 'Chưa nhập',
                    expected: c10Exp + ' tháng',
                    diff: c10Err ? `${c10Act - c10Exp > 0 ? '+' : ''}${c10Act - c10Exp} tháng` : '0 tháng',
                    hasErr: c10Err,
                    formula: fDesc10
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Số tháng thôi việc): File ghi ${c10Act} tháng, Chuẩn tính lại ${c10Exp} tháng.`);
                }

                // Cột 11: Thời gian đóng BHXH
                let c11Act = raw[11];
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Act, c11Exp));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Thời gian đóng BHXH',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Thời gian đóng BHXH): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                const colDefs2 = [
                    { k: 12, expKey: 'cot12', title: 'Trợ cấp thôi việc ngay (HS 0.8)', f: `${exp.cot10} tháng × 0.8 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 13, expKey: 'cot13', title: 'Trợ cấp theo BHXH (1.5 tháng)', f: `${fmtNum(exp.cot11)} năm × 1.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: 'Trợ cấp tìm việc làm', f: `3 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 15, expKey: 'cot15', title: 'Trợ cấp sau 12 tháng (HS 0.4)', f: `${exp.cot10} tháng × 0.4 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 16, expKey: 'cot16', title: 'Trợ cấp theo BHXH (1.5 tháng)', f: `${fmtNum(exp.cot11)} năm × 1.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 17, expKey: 'cot17', title: 'Trợ cấp tìm việc làm', f: `3 tháng × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                colDefs2.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;
                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[18] != null ? (typeof raw[18] === 'number' ? raw[18] : (BQPNormalization.number(raw[18]) ?? raw[18])) : null;
                let expectedTotal = exp.total;
                let totalErr = (actualTotal == null) || !isEqual(actualTotal, expectedTotal);
                let diffVal = (actualTotal != null && expectedTotal != null) ? (expectedTotal - actualTotal) : null;

                comparisons.push({
                    col: 'Cột 18',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: actualTotal != null ? fmtMoney(actualTotal) + ' đ' : (rowData.cellErrors && rowData.cellErrors[18] ? rowData.cellErrors[18] : 'Chưa tính kết quả'),
                    expected: expectedTotal != null ? fmtMoney(expectedTotal) + ' đ' : 'Chưa xác định',
                    diff: diffVal != null ? (diffVal !== 0 ? (diffVal > 0 ? '+' : '') + fmtMoney(diffVal) + ' đ' : '0 đ') : 'Chưa xác định',
                    hasErr: totalErr,
                    formula: 'Cột 12 + 13 + 14 + 15 + 16 + 17'
                });
                if (totalErr) {
                    if (actualTotal != null) {
                        errorDetails.push(`Cột 18 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                    } else {
                        errorDetails.push(`Cột 18 (Tổng cộng số tiền): File chưa tính hoặc lỗi ô công thức (${rowData.cellErrors && rowData.cellErrors[18] ? rowData.cellErrors[18] : 'Chưa tính'}).`);
                    }
                }

                // Cột 19 & Cột 20 (Tổng phụ Cột 18 + Cột 19)
                let c19Act = raw[19] || 0;
                let c20Act = raw[20];
                if (c20Act != null && c20Act !== 0 && expectedTotal != null) {
                    let exp20 = expectedTotal + c19Act;
                    let c20Err = !isEqual(c20Act, exp20);
                    comparisons.push({
                        col: 'Cột 20',
                        title: 'TỔNG CỘNG CHUNG (CỘT 18 + CỘT 19)',
                        actual: fmtMoney(c20Act) + ' đ',
                        expected: fmtMoney(exp20) + ' đ',
                        diff: c20Err ? (exp20 - c20Act > 0 ? '+' : '') + fmtMoney(exp20 - c20Act) + ' đ' : '0 đ',
                        hasErr: c20Err,
                        formula: `Cột 18 (${fmtMoney(expectedTotal)} đ) + Cột 19 (${fmtMoney(c19Act)} đ)`
                    });
                    if (c20Err) {
                        errorDetails.push(`Cột 20 (Tổng cộng chung): File tính ${fmtMoney(c20Act)} đ, Chuẩn tính lại ${fmtMoney(exp20)} đ (lệch ${fmtMoney(exp20 - c20Act)} đ).`);
                    }
                }

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: diffVal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            } else if (sheetType === 'I.3') {
                // I.3
                exp = PLI3Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                // Cột 10: Thời gian đóng BHXH
                let c10Act = raw[10];
                let c10Exp = exp.cot10;
                let c10Err = (c10Act != null && !isEqualTime(c10Act, c10Exp));
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Thời gian đóng BHXH',
                    actual: c10Act != null ? fmtNum(c10Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c10Exp) + ' năm',
                    diff: c10Err ? `${c10Act - c10Exp > 0 ? '+' : ''}${fmtNum(c10Act - c10Exp)} năm` : '0 năm',
                    hasErr: c10Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn`
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Thời gian đóng BHXH): File ghi ${fmtNum(c10Act)} năm, Chuẩn tính lại ${fmtNum(c10Exp)} năm.`);
                }

                // Cột 11: Thời gian nghỉ hưu trước tuổi
                let c11Act = raw[11];
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Act, c11Exp));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Thời gian nghỉ hưu trước tuổi',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `(${formatDate(rowData.ngaySinh)} + ${exp.tran} tuổi) - ${formatDate(rowData.thoiDiemNghi)} -> làm tròn`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Thời gian nghỉ trước tuổi): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                const colDefs3 = [
                    { k: 12, expKey: 'cot12', title: 'Trợ cấp nghỉ trước tuổi', f: `${fmtNum(exp.cot11)} năm × 5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 13, expKey: 'cot13', title: `Trợ cấp ${exp.moc14} năm đầu`, f: `5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: `Trợ cấp vượt ${exp.moc14} năm BHXH`, f: `(${fmtNum(exp.cot10)} - ${exp.moc14}) × 0.5 × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                colDefs3.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;
                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[15] != null ? (typeof raw[15] === 'number' ? raw[15] : (BQPNormalization.number(raw[15]) ?? raw[15])) : null;
                let expectedTotal = exp.total;
                let totalErr = (actualTotal == null) || !isEqual(actualTotal, expectedTotal);
                let diffVal = (actualTotal != null && expectedTotal != null) ? (expectedTotal - actualTotal) : null;

                comparisons.push({
                    col: 'Cột 15',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: actualTotal != null ? fmtMoney(actualTotal) + ' đ' : (rowData.cellErrors && rowData.cellErrors[15] ? rowData.cellErrors[15] : 'Chưa tính kết quả'),
                    expected: expectedTotal != null ? fmtMoney(expectedTotal) + ' đ' : 'Chưa xác định',
                    diff: diffVal != null ? (diffVal !== 0 ? (diffVal > 0 ? '+' : '') + fmtMoney(diffVal) + ' đ' : '0 đ') : 'Chưa xác định',
                    hasErr: totalErr,
                    formula: 'Cột 12 + 13 + 14'
                });
                if (totalErr) {
                    if (actualTotal != null) {
                        errorDetails.push(`Cột 15 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                    } else {
                        errorDetails.push(`Cột 15 (Tổng cộng số tiền): File chưa tính hoặc lỗi ô công thức (${rowData.cellErrors && rowData.cellErrors[15] ? rowData.cellErrors[15] : 'Chưa tính'}).`);
                    }
                }

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: diffVal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            } else if (sheetType === 'I.5') {
                exp = PLI5Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                const colDefs5 = [
                    { k: 13, expKey: 'cot13', title: 'Chênh lệch bảo lưu (tiền)', f: `Cột 7 (${fmtNum(rowData.heSoChenhLechBaoLuu || raw[7])}) × 2.340.000 đ` },
                    { k: 14, expKey: 'cot14', title: 'Tiền lương ngạch bậc', f: `Cột 6 (${fmtNum(rowData.heSoLuong || raw[6])}) × 2.340.000 đ` },
                    { k: 15, expKey: 'cot15', title: 'Phụ cấp chức vụ', f: `Cột 8 (${fmtNum(rowData.heSoChucVu || raw[8])}) × 2.340.000 đ` },
                    { k: 16, expKey: 'cot16', title: 'Phụ cấp thâm niên nghề', f: `Thâm niên ${exp.soNamThamNien} năm (${fmtNum(exp.tiLeThamNien * 100)}%) × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)}) đ` },
                    { k: 17, expKey: 'cot17', title: 'Phụ cấp thâm niên vượt khung', f: `Đọc từ file Excel` },
                    { k: 18, expKey: 'cot18', title: 'Phụ cấp trách nhiệm nghề', f: `${fmtNum(exp.tiLeC11 * 100)}% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 19, expKey: 'cot19', title: 'Phụ cấp công vụ', f: `25% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 20, expKey: 'cot20', title: 'Phụ cấp đặc thù quân sự cơ yếu', f: `${fmtNum(exp.tiLeC12 * 100)}% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 21, expKey: 'cot21', title: 'Được nhận khác', f: `Đọc từ file Excel` },
                    { k: 22, expKey: 'cot22', title: 'TỔNG CỘNG LƯƠNG & PHỤ CẤP', f: `SUM(Cột 13 : Cột 21)` }
                ];

                colDefs5.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;

                    if ((def.k === 17 || def.k === 21) && expVal === 0 && actVal > 0) {
                        expVal = actVal;
                        exp[def.expKey] = actVal;
                    }

                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: def.f
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[22] != null ? (typeof raw[22] === 'number' ? raw[22] : (BQPNormalization.number(raw[22]) ?? raw[22])) : null;
                let expectedTotal = exp.total;
                let totalErr = (actualTotal == null) || !isEqual(actualTotal, expectedTotal);
                let diffVal = (actualTotal != null && expectedTotal != null) ? (expectedTotal - actualTotal) : null;

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: diffVal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            }

            return {
                exp: exp || {},
                comparisons: comparisons || [],
                errorDetails: errorDetails || [],
                actualTotal: (rowData && rowData.tongTienThucTe) || 0,
                expectedTotal: 0,
                diff: 0,
                hasErrors: (errorDetails && errorDetails.length > 0),
                hasDiff24Months: false
            };
        } catch (err) {
            console.error("Lỗi khi đối chiếu/thẩm định dòng:", err, rowData);
            errorDetails.push(`Lỗi tính toán thẩm định dòng: ${err.message || err}`);
            comparisons.push({
                col: 'Toàn dòng',
                title: 'Lỗi thẩm định',
                actual: 'Lỗi ô/dữ liệu',
                expected: 'Chuẩn',
                diff: '0 đ',
                hasErr: true,
                formula: err.message || 'Lỗi thẩm định'
            });
            return {
                exp: exp || {},
                comparisons,
                errorDetails,
                actualTotal: (rowData && rowData.tongTienThucTe) || 0,
                expectedTotal: 0,
                diff: 0,
                hasErrors: true,
                hasDiff24Months: false
            };
        }
    }
};


    function normalizeErrorDetails(errors) {
        if (!errors) return [];
        const rawList = Array.isArray(errors) ? errors : [errors];
        const result = [];
        const seen = new Set();
        rawList.forEach(err => {
            if (!err) return;
            let str = '';
            if (typeof err === 'string') {
                str = err.trim();
            } else if (typeof err === 'object') {
                if (err.message) {
                    str = String(err.message).trim();
                } else if (err.columnNumber != null || err.col) {
                    const col = err.col || ('Cột ' + err.columnNumber);
                    const desc = err.errorCode || err.errorDesc || err.reason || 'Sai lệch dữ liệu';
                    str = `${col}: ${desc}`;
                } else {
                    try { str = JSON.stringify(err); } catch (e) { str = String(err); }
                }
            } else {
                str = String(err).trim();
            }
            if (str && !seen.has(str)) {
                seen.add(str);
                result.push(str);
            }
        });
        return result;
    }

    function parseToValidDate(val) {
        if (!val) return null;
        if (val instanceof Date && !isNaN(val.getTime())) return val;
        if (typeof val === 'number') {
            if (val > 100000000000) return new Date(val);
            const d = new Date(Math.round((val - 25569) * 86400 * 1000));
            return !isNaN(d.getTime()) ? d : null;
        }
        if (typeof val === 'string') {
            const s = val.trim();
            if (/^\d{4}[-/]\d{1,2}[-/]\d{1,2}/.test(s)) {
                const parts = s.split(/[-/]/);
                const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                if (!isNaN(d.getTime())) return d;
            }
            if (/^\d{1,2}[-/]\d{1,2}[-/]\d{4}/.test(s)) {
                const parts = s.split(/[-/]/);
                const d = new Date(parseInt(parts[2], 10), parseInt(parts[1], 10) - 1, parseInt(parts[0], 10));
                if (!isNaN(d.getTime())) return d;
            }
            if (/^\d{1,2}[-/]\d{4}$/.test(s)) {
                const parts = s.split(/[-/]/);
                const d = new Date(parseInt(parts[1], 10), parseInt(parts[0], 10) - 1, 1);
                if (!isNaN(d.getTime())) return d;
            }
            const d = new Date(s);
            if (!isNaN(d.getTime())) return d;
        }
        return null;
    }

    function buildRawExcelComparisons(sheetType, rawCols, rec) {
        let comparisons = [];
        let raw = rawCols || (rec && (rec.rawCols || rec.rawColumns)) || {};
        let sType = sheetType || (rec && (rec.sheet || rec.sheetType)) || 'I.1';

        let fmtMoneyHelper = (v) => {
            if (v == null || v === '') return '0 đ';
            if (typeof v === 'number') return BQPValidation.fmtMoney(v) + ' đ';
            let num = (typeof BQPNormalization !== 'undefined' && BQPNormalization.number) ? BQPNormalization.number(v) : null;
            return (num != null) ? BQPValidation.fmtMoney(num) + ' đ' : String(v);
        };

        let fmtNumHelper = (v, unit = '') => {
            if (v == null || v === '') return '0' + (unit ? ' ' + unit : '');
            return String(v) + (unit ? ' ' + unit : '');
        };

        if (sType === 'I.1') {
            const defs = [
                { k: 10, title: 'Số tháng nghỉ hưu trước tuổi', type: 'month' },
                { k: 11, title: 'Số năm nghỉ hưu trước tuổi', type: 'year' },
                { k: 12, title: 'Thời gian đóng BHXH', type: 'year' },
                { k: 13, title: 'Trợ cấp nghỉ sớm (HS 1.0)', type: 'money' },
                { k: 14, title: 'Trợ cấp nghỉ sớm (HS 0.9)', type: 'money' },
                { k: 15, title: 'Trợ cấp nghỉ sớm (HS 0.5)', type: 'money' },
                { k: 16, title: 'Trợ cấp nghỉ sớm (HS 0.45)', type: 'money' },
                { k: 17, title: 'Trợ cấp tuổi đời (Nhóm 2 - 5 năm)', type: 'money' },
                { k: 18, title: 'Trợ cấp BHXH (20 năm đầu)', type: 'money' },
                { k: 19, title: 'Trợ cấp BHXH (vượt 20 năm)', type: 'money' },
                { k: 20, title: 'Trợ cấp tuổi đời (Nhóm 5 - 10 năm)', type: 'money' },
                { k: 21, title: 'Trợ cấp BHXH (20 năm đầu)', type: 'money' },
                { k: 22, title: 'Trợ cấp BHXH (vượt 20 năm)', type: 'money' },
                { k: 23, title: 'TỔNG CỘNG SỐ TIỀN', type: 'money' }
            ];
            defs.forEach(d => {
                let val = raw[d.k];
                let actStr = '0 đ';
                if (d.type === 'month') actStr = val != null ? val + ' tháng' : 'Chưa nhập';
                else if (d.type === 'year') actStr = val != null ? fmtNumHelper(val, 'năm') : 'Chưa nhập';
                else if (d.type === 'money') actStr = val != null ? fmtMoneyHelper(val) : '0 đ';
                comparisons.push({
                    col: 'Cột ' + d.k,
                    title: d.title,
                    actual: actStr,
                    expected: actStr,
                    diff: '---',
                    hasErr: false,
                    formula: 'Số liệu ghi trong file Excel'
                });
            });
        } else if (sType === 'I.2') {
            const defs = [
                { k: 10, title: 'Số tháng thôi việc', type: 'month' },
                { k: 11, title: 'Thời gian đóng BHXH', type: 'year' },
                { k: 12, title: 'Trợ cấp thôi việc ngay (HS 0.8)', type: 'money' },
                { k: 13, title: 'Trợ cấp theo BHXH (1.5 tháng)', type: 'money' },
                { k: 14, title: 'Trợ cấp tìm việc làm', type: 'money' },
                { k: 15, title: 'Trợ cấp sau 12 tháng (HS 0.4)', type: 'money' },
                { k: 16, title: 'Trợ cấp theo BHXH (1.5 tháng)', type: 'money' },
                { k: 17, title: 'Trợ cấp tìm việc làm', type: 'money' },
                { k: 18, title: 'TỔNG CỘNG SỐ TIỀN', type: 'money' }
            ];
            defs.forEach(d => {
                let val = raw[d.k];
                let actStr = '0 đ';
                if (d.type === 'month') actStr = val != null ? val + ' tháng' : 'Chưa nhập';
                else if (d.type === 'year') actStr = val != null ? fmtNumHelper(val, 'năm') : 'Chưa nhập';
                else if (d.type === 'money') actStr = val != null ? fmtMoneyHelper(val) : '0 đ';
                comparisons.push({
                    col: 'Cột ' + d.k,
                    title: d.title,
                    actual: actStr,
                    expected: actStr,
                    diff: '---',
                    hasErr: false,
                    formula: 'Số liệu ghi trong file Excel'
                });
            });
            if (raw[19] != null || raw[20] != null) {
                if (raw[19] != null) comparisons.push({ col: 'Cột 19', title: 'Kinh phí đào tạo chuyển đổi nghề', actual: fmtMoneyHelper(raw[19]), expected: fmtMoneyHelper(raw[19]), diff: '---', hasErr: false, formula: 'Số liệu ghi trong file Excel' });
                if (raw[20] != null) comparisons.push({ col: 'Cột 20', title: 'TỔNG CỘNG CHUNG (CỘT 18 + CỘT 19)', actual: fmtMoneyHelper(raw[20]), expected: fmtMoneyHelper(raw[20]), diff: '---', hasErr: false, formula: 'Số liệu ghi trong file Excel' });
            }
        } else if (sType === 'I.3') {
            const defs = [
                { k: 10, title: 'Thời gian đóng BHXH', type: 'year' },
                { k: 11, title: 'Thời gian nghỉ hưu trước tuổi', type: 'year' },
                { k: 12, title: 'Trợ cấp nghỉ trước tuổi', type: 'money' },
                { k: 13, title: 'Trợ cấp 14/20 năm đầu', type: 'money' },
                { k: 14, title: 'Trợ cấp vượt 14/20 năm BHXH', type: 'money' },
                { k: 15, title: 'TỔNG CỘNG SỐ TIỀN', type: 'money' }
            ];
            defs.forEach(d => {
                let val = raw[d.k];
                let actStr = '0 đ';
                if (d.type === 'year') actStr = val != null ? fmtNumHelper(val, 'năm') : 'Chưa nhập';
                else if (d.type === 'money') actStr = val != null ? fmtMoneyHelper(val) : '0 đ';
                comparisons.push({
                    col: 'Cột ' + d.k,
                    title: d.title,
                    actual: actStr,
                    expected: actStr,
                    diff: '---',
                    hasErr: false,
                    formula: 'Số liệu ghi trong file Excel'
                });
            });
        } else if (sType === 'I.5') {
            const defs = [
                { k: 13, title: 'Chênh lệch bảo lưu (tiền)', type: 'money' },
                { k: 14, title: 'Tiền lương ngạch bậc', type: 'money' },
                { k: 15, title: 'Phụ cấp chức vụ', type: 'money' },
                { k: 16, title: 'Phụ cấp thâm niên nghề', type: 'money' },
                { k: 17, title: 'Phụ cấp thâm niên vượt khung', type: 'money' },
                { k: 18, title: 'Phụ cấp trách nhiệm nghề', type: 'money' },
                { k: 19, title: 'Phụ cấp công vụ', type: 'money' },
                { k: 20, title: 'Phụ cấp đặc thù quân sự cơ yếu', type: 'money' },
                { k: 21, title: 'Được nhận khác', type: 'money' },
                { k: 22, title: 'TỔNG CỘNG LƯƠNG & PHỤ CẤP', type: 'money' }
            ];
            defs.forEach(d => {
                let val = raw[d.k];
                let actStr = val != null ? fmtMoneyHelper(val) : '0 đ';
                comparisons.push({
                    col: 'Cột ' + d.k,
                    title: d.title,
                    actual: actStr,
                    expected: actStr,
                    diff: '---',
                    hasErr: false,
                    formula: 'Số liệu ghi trong file Excel'
                });
            });
        } else {
            for (let colIdx in raw) {
                let numIdx = parseInt(colIdx);
                if (!isNaN(numIdx) && numIdx >= 9) {
                    let v = raw[colIdx];
                    let isMoney = typeof v === 'number' && v > 1000;
                    comparisons.push({
                        col: 'Cột ' + numIdx,
                        title: 'Chỉ tiêu Cột ' + numIdx,
                        actual: isMoney ? fmtMoneyHelper(v) : String(v != null ? v : '---'),
                        expected: isMoney ? fmtMoneyHelper(v) : String(v != null ? v : '---'),
                        diff: '---',
                        hasErr: false,
                        formula: 'Số liệu ghi trong file Excel'
                    });
                }
            }
        }
        return comparisons;
    }

    function ensureRecordValidationDetail(rec, isExcelOnly = false) {
        if (!rec) return rec;

        // 1. Chuẩn hóa rawColumns / rawColumnsJson -> rawCols
        if (!rec.rawCols && rec.rawColumns) {
            rec.rawCols = rec.rawColumns;
        }
        if (!rec.rawCols && rec.rawColumnsJson) {
            try {
                rec.rawCols = typeof rec.rawColumnsJson === 'string' ? JSON.parse(rec.rawColumnsJson) : rec.rawColumnsJson;
            } catch (e) {}
        }
        if (!rec.rawCols) rec.rawCols = {};

        // Chuẩn hóa input
        if (!rec.input && rec.inputJson) {
            try {
                rec.input = typeof rec.inputJson === 'string' ? JSON.parse(rec.inputJson) : rec.inputJson;
            } catch (e) {}
        }

        function extractPlainText(v) {
            if (!v) return '';
            if (typeof v === 'string') return v.trim();
            if (typeof v === 'object') {
                if (Array.isArray(v.richText)) return v.richText.map(t => t.text).join('').trim();
                if (v.text != null) return String(v.text).trim();
                if (v.result != null) return String(v.result).trim();
            }
            return String(v).trim();
        }

        // Chuẩn hóa các trường định danh cơ bản
        rec.sheet = rec.sheet || rec.sheetType || rec.sheetSource || 'I.1';
        rec.sheetType = rec.sheet;
        rec.sheetSource = rec.sheet;
        rec.hoTen = extractPlainText(rec.hoTen || rec.fullName || (rec.input && (rec.input.hoTen || rec.input.fullName)) || '');
        rec.fullName = rec.hoTen;
        rec.capBac = extractPlainText(rec.capBac || rec.rank || (rec.input && (rec.input.capBac || rec.input.rank)) || '');
        rec.rank = rec.capBac;
        rec.chucVu = extractPlainText(rec.chucVu || rec.position || (rec.input && (rec.input.chucVu || rec.input.position)) || '');
        rec.position = rec.chucVu;
        const rawNs = rec.ngaySinh || rec.birthDate || (rec.input && (rec.input.ngaySinh || rec.input.birthDate)) || null;
        rec.ngaySinh = rawNs ? formatDate(rawNs) : null;
        rec.birthDate = rec.ngaySinh;
        const rawNn = rec.nhapNgu || rec.enlistmentDate || (rec.input && (rec.input.nhapNgu || rec.input.enlistmentDate)) || null;
        rec.nhapNgu = rawNn ? formatDate(rawNn) : null;
        rec.enlistmentDate = rec.nhapNgu;
        const rawSn = rec.sapNhap || rec.mergerDate || (rec.input && (rec.input.sapNhap || rec.input.mergerDate)) || null;
        rec.sapNhap = rawSn ? formatDate(rawSn) : null;
        rec.mergerDate = rec.sapNhap;
        const rawNg = rec.thoiDiemNghi || rec.retirementDate || (rec.input && (rec.input.thoiDiemNghi || rec.input.retirementDate)) || null;
        rec.thoiDiemNghi = rawNg ? formatDate(rawNg) : null;
        rec.retirementDate = rec.thoiDiemNghi;
        rec.luongThang = rec.luongThang != null ? rec.luongThang : (rec.monthlySalary != null ? rec.monthlySalary : ((rec.input && rec.input.luongThang) || 0));
        rec.monthlySalary = rec.luongThang;

        // Chuẩn hóa rowIndex từ sourceRow nếu thiếu
        if (rec.rowIndex == null && rec.sourceRow != null) {
            rec.rowIndex = rec.sourceRow;
        }
        if (rec.sourceRow == null && rec.rowIndex != null) {
            rec.sourceRow = rec.rowIndex;
        }

        // Chuẩn hóa các trường tiền
        rec.tongTienThucTe = rec.tongTienThucTe != null ? rec.tongTienThucTe : (rec.actualTotal != null ? rec.actualTotal : (rec.rawCols ? (rec.rawCols[23] || rec.rawCols[18] || rec.rawCols[15] || rec.rawCols[22] || 0) : 0));
        rec.tongTienTinhLai = rec.tongTienTinhLai != null ? rec.tongTienTinhLai : (rec.calculatedTotal != null ? rec.calculatedTotal : (rec.result && rec.result.tongTien != null ? rec.result.tongTien : rec.tongTienThucTe));
        rec.diff = rec.diff != null ? rec.diff : (rec.difference != null ? rec.difference : (rec.tongTienTinhLai - rec.tongTienThucTe));
        rec.difference = rec.diff;

        // 2. Nếu đã có comparisons đầy đủ trong bộ nhớ: giữ nguyên
        if (Array.isArray(rec.comparisons) && rec.comparisons.length > 0) {
            rec.errorDetails = normalizeErrorDetails(rec.errorDetails);
            return rec;
        }

        // 3. Đọc từ validationSnapshot đã lưu trong resultJson / result
        let resultObj = null;
        if (rec.resultJson && typeof rec.resultJson === 'string') {
            try { resultObj = JSON.parse(rec.resultJson); } catch (e) {}
        } else if (rec.result && typeof rec.result === 'object') {
            resultObj = rec.result;
        }

        const snapshot = (resultObj && resultObj.validationSnapshot)
            || (rec.valRes && rec.valRes.validationSnapshot);

        if (snapshot && Array.isArray(snapshot.comparisons) && snapshot.comparisons.length > 0) {
            rec.comparisons = snapshot.comparisons;
            if (!rec.expected || Object.keys(rec.expected).length === 0) {
                rec.expected = snapshot.expected || {};
            }
            if (!rec.cellErrors || Object.keys(rec.cellErrors).length === 0) {
                rec.cellErrors = snapshot.cellErrors || {};
            }
            rec.errorDetails = normalizeErrorDetails(snapshot.errorDetails || rec.errorDetails);
            if (snapshot.hasErrors != null) rec.hasErrors = Boolean(snapshot.hasErrors);
            if (snapshot.calculatedTotal != null) {
                rec.tongTienTinhLai = snapshot.calculatedTotal;
                rec.calculatedTotal = snapshot.calculatedTotal;
            }
            if (snapshot.diff != null) {
                rec.diff = snapshot.diff;
                rec.difference = snapshot.diff;
                rec.chenhLech = snapshot.diff;
            }
            return rec;
        }

        const isRawSource = Boolean(isExcelOnly || rec.source === 'excel' || (rec.id && String(rec.id).startsWith('excel_')));

        // 4. Nếu là hồ sơ cũ chưa có snapshot hoặc hồ sơ RAW: Tái tạo qua ValidationService.validateRow với Date hợp lệ
        if (typeof ValidationService !== 'undefined' && typeof ValidationService.validateRow === 'function') {
            try {
                const validationInput = {
                    ...rec,
                    rawCols: rec.rawCols || {},
                    ngaySinh: parseToValidDate(rawNs),
                    nhapNgu: parseToValidDate(rawNn),
                    sapNhap: parseToValidDate(rawSn),
                    thoiDiemNghi: parseToValidDate(rawNg),
                    luongThang: rec.luongThang != null ? Number(rec.luongThang) : 0,
                    heSoLuong: rec.heSoLuong != null ? Number(rec.heSoLuong) : (rec.rawCols ? Number(rec.rawCols[6] || 0) : 0)
                };

                const valRes = ValidationService.validateRow(rec.sheet, validationInput);
                if (valRes && Array.isArray(valRes.comparisons) && valRes.comparisons.length > 0) {
                    const storedExpected = rec.tongTienTinhLai != null ? rec.tongTienTinhLai : rec.calculatedTotal;
                    let canUseRecalculated = true;
                    if (isRawSource) {
                        // Nguồn RAW: Luôn chấp nhận comparisons để hiển thị bảng các cột từ file Excel
                        canUseRecalculated = true;
                    } else if (storedExpected != null && valRes.expectedTotal != null) {
                        if (Math.abs(Math.round(valRes.expectedTotal) - Math.round(storedExpected)) > 1) {
                            console.warn('[ValidationDetail] Tổng tính lại (' + valRes.expectedTotal + ') lệch tổng đã lưu (' + storedExpected + ') của hồ sơ ' + (rec.hoTen || rec.id));
                            canUseRecalculated = false;
                        }
                    }

                    if (canUseRecalculated) {
                        rec.comparisons = valRes.comparisons;
                        rec.expected = valRes.exp || {};
                        rec.valRes = valRes;
                        if (!rec.errorDetails || rec.errorDetails.length === 0) {
                            rec.errorDetails = normalizeErrorDetails(valRes.errorDetails);
                        }
                        if (valRes.hasErrors && !isRawSource) {
                            rec.hasErrors = true;
                        }
                        if (valRes.cellErrors && (!rec.cellErrors || Object.keys(rec.cellErrors).length === 0)) {
                            rec.cellErrors = valRes.cellErrors;
                        }
                    }
                }
            } catch (recalcErr) {
                console.warn('[ValidationDetail] Lỗi tái tạo đối chiếu dòng:', recalcErr);
            }
        }

        // 5. Fallback tuyệt đối cho hồ sơ RAW khi validateRow chưa tạo comparisons (ví dụ thiếu ngày tháng):
        if (isRawSource && (!rec.comparisons || rec.comparisons.length === 0) && rec.rawCols && Object.keys(rec.rawCols).length > 0) {
            rec.comparisons = buildRawExcelComparisons(rec.sheet, rec.rawCols, rec);
        }

        if (rec.comparisons && rec.comparisons.length > 0) {
            if (!rec.result || typeof rec.result !== 'object') rec.result = {};
            if (!rec.result.validationSnapshot) {
                rec.result.validationSnapshot = {
                    comparisons: rec.comparisons,
                    expected: rec.expected || {},
                    cellErrors: rec.cellErrors || {},
                    errorDetails: rec.errorDetails || []
                };
            }
        }

        rec.errorDetails = normalizeErrorDetails(rec.errorDetails);
        return rec;
    }

    function buildLevel2Resolver(units, selectedUnitId) {
        if (!units || !Array.isArray(units) || units.length === 0) {
            throw new Error('Danh mục đơn vị rỗng, không thể tổng hợp theo đơn vị cấp 2.');
        }
        const unitById = new Map();
        units.forEach(u => {
            if (u && u.id != null) unitById.set(String(u.id), u);
        });

        const selectedIdStr = String(selectedUnitId);
        const selectedUnit = unitById.get(selectedIdStr);
        if (!selectedUnit) {
            throw new Error(`Đơn vị cấp 1 đã chọn (ID: ${selectedUnitId}) không tồn tại trong danh mục đơn vị.`);
        }

        const cache = new Map();

        function resolveUnit(unitId) {
            if (!unitId) {
                return { ok: false, error: 'Thiếu mã đơn vị (unitId)' };
            }
            const idStr = String(unitId);
            if (cache.has(idStr)) return cache.get(idStr);

            const u = unitById.get(idStr);
            if (!u) {
                const res = { ok: false, error: `Mã đơn vị "${idStr}" không tồn tại trong danh mục` };
                cache.set(idStr, res);
                return res;
            }

            const path = [];
            const visited = new Set();
            let curr = u;
            while (curr) {
                const cid = String(curr.id);
                if (visited.has(cid)) {
                    const res = { ok: false, error: `Phát hiện vòng lặp cây đơn vị tại "${curr.name || cid}"` };
                    cache.set(idStr, res);
                    return res;
                }
                visited.add(cid);
                path.push(curr);
                if (!curr.parentId) break;
                const parent = unitById.get(String(curr.parentId));
                if (!parent) {
                    const res = { ok: false, error: `Đơn vị "${curr.name || cid}" có đơn vị cha "${curr.parentId}" không tồn tại` };
                    cache.set(idStr, res);
                    return res;
                }
                curr = parent;
            }

            const chain = path.reverse();
            const root = chain[0];

            if (String(root.id) !== selectedIdStr) {
                const res = { ok: false, error: `Đơn vị "${u.name || idStr}" không thuộc nhánh của đơn vị cấp 1 đã chọn ("${selectedUnit.name || selectedIdStr}")` };
                cache.set(idStr, res);
                return res;
            }

            if (root.level != null && root.level !== '') {
                const rLvl = parseInt(String(root.level), 10);
                if (!isNaN(rLvl) && rLvl !== 1) {
                    const res = { ok: false, error: `Đơn vị gốc "${root.name}" có cấp quy định là ${rLvl} thay vì cấp 1` };
                    cache.set(idStr, res);
                    return res;
                }
            }

            if (chain.length < 2) {
                const res = { ok: false, error: `Hồ sơ gắn trực tiếp vào đơn vị cấp 1 "${root.name}", không có đơn vị cấp 2 trực thuộc` };
                cache.set(idStr, res);
                return res;
            }

            const l2 = chain[1];
            if (l2.level != null && l2.level !== '') {
                const l2Lvl = parseInt(String(l2.level), 10);
                if (!isNaN(l2Lvl) && l2Lvl !== 2) {
                    const res = { ok: false, error: `Đơn vị "${l2.name}" nằm ở vị trí cấp 2 nhưng thuộc tính cấp lại là ${l2Lvl}` };
                    cache.set(idStr, res);
                    return res;
                }
            }

            const order = (l2.orderIndex != null) ? l2.orderIndex : ((l2.displayOrder != null) ? l2.displayOrder : 0);
            const res = {
                ok: true,
                l2Unit: {
                    id: String(l2.id),
                    name: l2.name || `Đơn vị ${l2.id}`,
                    displayOrder: Number(order) || 0
                }
            };
            cache.set(idStr, res);
            return res;
        }

        return { resolveUnit, selectedUnit };
    }

    async function exportValidatedWorkbook(baseWb, baseFileName, recordsToExport, isFiltered = false, suffix = 'Tat_Ca', allRecords = null, exportContext = null) {
        const recordType = rec => {
            const name = String(rec.sheet || rec.sheetType || rec.sheetSource || 'I.1').trim();
            const match = name.match(/(?:^|\s)I\.([1-5])$/i);
            return match ? 'I.' + match[1] : name.toUpperCase();
        };

        if (!recordsToExport || recordsToExport.length === 0) {
            if (typeof notifyWarning === 'function') notifyWarning('Không có hồ sơ nào phù hợp với bộ lọc hiện tại để xuất file.');
            return;
        }

        const isLevel1Export = Boolean(
            exportContext &&
            exportContext.summaryLevel === 2 &&
            exportContext.selectedUnitId &&
            Array.isArray(exportContext.units) &&
            exportContext.units.length > 0
        );

        let level2Resolver = null;
        if (isLevel1Export) {
            level2Resolver = buildLevel2Resolver(exportContext.units, exportContext.selectedUnitId);
            const unmappable = [];
            recordsToExport.forEach(r => {
                let sh = recordType(r);
                if (!['I.1', 'I.2', 'I.3'].includes(sh)) return;
                const res = level2Resolver.resolveUnit(r.unitId);
                if (!res.ok) {
                    unmappable.push({ record: r, error: res.error });
                }
            });

            if (unmappable.length > 0) {
                const sampleErrors = unmappable.slice(0, 3).map(u => {
                    const name = u.record.hoTen || (u.record.input && u.record.input.hoTen) || u.record.id || 'Chưa rõ tên';
                    return `• ${name}: ${u.error}`;
                }).join('\n');
                const errMsg = `Không thể xuất báo cáo cấp 1: Phát hiện ${unmappable.length} hồ sơ không thể ánh xạ về đơn vị cấp 2 hợp lệ.\n${sampleErrors}\n\nVui lòng kiểm tra lại gán đơn vị hoặc cây danh mục trước khi xuất.`;
                if (typeof notifyError === 'function') notifyError(errMsg);
                throw new Error(errMsg);
            }
        }

        const allRecs = (Array.isArray(allRecords) && allRecords.length > 0) ? allRecords : recordsToExport;

        // Chuẩn hóa và phục hồi đối chiếu chi tiết cho toàn bộ hồ sơ
        recordsToExport.forEach(r => ensureRecordValidationDetail(r));
        if (allRecs !== recordsToExport) {
            allRecs.forEach(r => ensureRecordValidationDetail(r));
        }
        if (exportContext && exportContext.rawMode) {
            // RAW: ensureRecordValidationDetail tự thẩm định lại từng dòng, nên phải bỏ kết quả sau khi chuẩn hóa
            recordsToExport.forEach(r => {
                r.comparisons = []; r.cellErrors = {}; r.errorDetails = []; r.hasErrors = false; r.valRes = null;
                r.tongTienTinhLai = r.tongTienThucTe; r.calculatedTotal = r.tongTienThucTe;
                r.diff = 0; r.difference = 0;
            });
        }

        const missingComparisons = recordsToExport.filter(r => (r.hasErrors || Math.abs(r.diff || 0) > 1) && (!r.comparisons || r.comparisons.length === 0));
        if (missingComparisons.length > 0) {
            console.warn('[CẢNH BÁO XUẤT EXCEL] Có ' + missingComparisons.length + ' hồ sơ có sai lệch nhưng không thể phục hồi đối chiếu chi tiết từng cột.');
        }

        let baseName = (baseFileName || 'Bao_cao').replace(/\.[^/.]+$/, '');

        if (typeof BQPLoader !== 'undefined') {
            BQPLoader.show('Đang xuất file Excel báo cáo thẩm định...', 'Đang tạo các sheet phụ lục chi tiết, gộp ô và định dạng màu sắc chuẩn...', 'Đang kết xuất báo cáo');
            await new Promise(r => setTimeout(r, 60));
        }

        try {
            // Chuyển công thức dùng chung thành công thức độc lập trước khi điền dữ liệu.
            const sanitizeSharedFormulas = (workbook) => {
                if (!workbook || !workbook.worksheets) return;
                workbook.worksheets.forEach(ws => {
                    ws.eachRow({ includeEmpty: true }, (row) => {
                        row.eachCell({ includeEmpty: true }, (cell) => {
                            if (cell.value && typeof cell.value === 'object') {
                                if (cell.value.sharedFormula) {
                                    const formula = cell.formula;
                                    cell.value = formula ? { formula, result: cell.result ?? cell.value.result }
                                        : (cell.value.result != null ? cell.value.result : '');
                                } else if (cell.value.shareType === 'shared') {
                                    cell.value = { formula: cell.value.formula, result: cell.result ?? cell.value.result };
                                }
                            }
                        });
                    });
                });
            };

            // Đánh dấu ô bị lỗi khi xuất file thay vì làm gián đoạn tiến trình xuất báo cáo
            const markCellExportError = (cell, err, fallbackVal) => {
                if (!cell) return;
                try {
                    if (fallbackVal !== undefined) {
                        cell.value = fallbackVal;
                    } else if (cell.value && typeof cell.value === 'object') {
                        cell.value = cell.value.result != null ? cell.value.result : (cell.value.formula || '[Lỗi ô]');
                    }
                    cell.fill = {
                        type: 'pattern',
                        pattern: 'solid',
                        fgColor: { argb: 'FFFFC7CE' } // Màu hồng nhạt cảnh báo chuẩn Excel
                    };
                    cell.font = Object.assign({}, cell.font || {}, {
                        color: { argb: 'FF9C0006' },
                        bold: true
                    });
                    // Bỏ cell.note để không hiện tam giác đỏ theo yêu cầu
                } catch(e) {}
            };

            // Đánh dấu dòng bị lỗi khi xuất file
            const markRowExportError = (row, noteCol, err) => {
                if (!row) return;
                try {
                    const errMsg = err ? (err.message || String(err)) : 'Lỗi không xác định';
                    if (noteCol) {
                        const noteCell = row.getCell(noteCol);
                        const curVal = noteCell.value ? String(noteCell.value) : '';
                        noteCell.value = (curVal ? curVal + '\n' : '') + `[LỖI XUẤT DÒNG: ${errMsg}]`;
                        noteCell.fill = {
                            type: 'pattern',
                            pattern: 'solid',
                            fgColor: { argb: 'FFFFC7CE' }
                        };
                        noteCell.font = {
                            name: 'Arial',
                            size: 8,
                            bold: true,
                            color: { argb: 'FF9C0006' }
                        };
                        noteCell.alignment = { wrapText: true, vertical: 'top' };
                    }
                    row.eachCell({ includeEmpty: false }, (c) => {
                        try {
                            if (!c.fill || !c.fill.fgColor) {
                                c.fill = {
                                    type: 'pattern',
                                    pattern: 'solid',
                                    fgColor: { argb: 'FFFFF1F2' }
                                };
                            }
                        } catch(e2) {}
                    });
                } catch(e) {}
            };

            // Workbook đầu vào chỉ cung cấp hồ sơ; layout luôn lấy từ mẫu báo cáo đã xác minh.
            const reportTemplate = exportContext?.reportTemplateWorkbook ||
                (baseWb?.__bqpReportTemplate === true ? baseWb : null);
            const templateVerified = exportContext?.reportTemplateWorkbook
                ? exportContext.reportTemplateVerified === true
                : reportTemplate?.__bqpReportTemplate === true;
            if (!reportTemplate || !templateVerified) {
                throw new Error('Chưa tải được file mẫu đã xác minh. Vui lòng tải lại mẫu trước khi xuất báo cáo.');
            }
            const clone = value => {
                if (value == null || typeof value !== 'object') return value;
                if (value instanceof Date) return new Date(value.getTime());
                if (Array.isArray(value)) return value.map(clone);
                if (ArrayBuffer.isView(value)) return value.slice();
                return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, clone(item)]));
            };
            const exportWb = new ExcelJS.Workbook();
            const templateModel = clone(reportTemplate.model);
            templateModel.worksheets.forEach(sheet => {
                sheet.mergeCells = sheet.merges || [];
                const source = reportTemplate.getWorksheet(sheet.name);
                const rowNumbers = new Set(sheet.rows.map(row => row.number));
                source.eachRow({ includeEmpty: true }, row => {
                    if (rowNumbers.has(row.number) || !Object.keys(row.style || {}).length) return;
                    // ExcelJS bỏ hàng chỉ có row style khi lấy model: vật hóa các ô trống trên bản sao.
                    const cells = source.columns.map((column, index) => ({
                        address: column.letter + row.number, type: 0,
                        style: clone({ ...(column.style || {}), ...(row.style || {}) })
                    }));
                    sheet.rows.push({ number: row.number, cells, min: 1, max: cells.length,
                        style: clone(row.style), height: row.height, hidden: row.hidden, outlineLevel: row.outlineLevel });
                });
                sheet.rows.sort((a, b) => a.number - b.number);
            });
            exportWb.model = templateModel;
            function restoreModelStyles(ws, model) {
                model.rows.forEach(row => {
                    ws.getRow(row.number).style = clone(row.style || {});
                    row.cells.forEach(cell => { ws.getCell(cell.address).style = clone(cell.style || {}); });
                });
            }
            templateModel.worksheets.forEach(model => restoreModelStyles(exportWb.getWorksheet(model.name), model));
            sanitizeSharedFormulas(exportWb);

            const cellText = cell => BQPValidation.ExcelParser.getCellText(cell).trim();
            const detailSizes = { 'I.1': 23, 'I.2': 18, 'I.3': 15, 'I.4': 16, 'I.5': 22 };
            function getTableLayout(ws, type = null) {
                const columnExtent = Math.max(ws.columnCount, ws.columns.length);
                let headerRowIdx = -1;
                let colMap = {};
                const size = type ? detailSizes[type] : 10;
                if (type) {
                    if (/^Phụ lục I\.[1-5]$/.test(ws.name)) {
                        const found = BQPValidation.ExcelParser.findColMap(ws);
                        headerRowIdx = found.headerRowIdx;
                    } else {
                        for (let r = 1; r <= Math.min(20, ws.rowCount); r++) {
                            let count = 0;
                            ws.getRow(r).eachCell(cell => { if (/^\d+(?:\s*=|$)/.test(cellText(cell))) count++; });
                            if (count >= size) { headerRowIdx = r; break; }
                        }
                    }
                    // Hàng số cột của mẫu là nguồn chính xác, kể cả bảng lương I.5.
                    const numeric = {};
                    ws.getRow(headerRowIdx).eachCell(cell => {
                        const m = cellText(cell).match(/^\(?([0-9]{1,2})\)?(?:$|\s*=)/);
                        if (m && Number(m[1]) <= size) numeric[Number(m[1])] = cell.col;
                    });
                    if (Object.keys(numeric).length !== size) {
                        throw new Error(ws.name + ': file mẫu thiếu hàng số cột 1–' + size + '.');
                    }
                    colMap = numeric;
                    for (let n = 1; n <= size; n++) {
                        if (!colMap[n]) throw new Error(ws.name + ': mẫu thiếu cột ' + n + '.');
                    }
                    colMap = Object.fromEntries(Object.entries(colMap).filter(([n]) => Number(n) <= size));
                } else {
                    for (let r = 1; r <= Math.min(20, ws.rowCount); r++) {
                        const row = ws.getRow(r);
                        if (cellText(row.getCell(1)) === 'A' && cellText(row.getCell(2)) === 'B' &&
                            [1, 2, 3, 4, 5, 6].every(n => cellText(row.getCell(n + 2)) === String(n)) &&
                            /^7/.test(cellText(row.getCell(9))) && /^8/.test(cellText(row.getCell(10)))) {
                            headerRowIdx = r; break;
                        }
                    }
                }
                if (headerRowIdx < 1) throw new Error(ws.name + ': không nhận diện được tiêu đề mẫu.');
                let totalRow = 0, noteRow = 0;
                const nameCol = type ? colMap[2] : 2;
                for (let r = headerRowIdx + 1; r <= ws.rowCount; r++) {
                    const row = ws.getRow(r);
                    if (!totalRow && /^(?:tổng\s+)?cộng$/i.test(cellText(row.getCell(nameCol)))) totalRow = r;
                    row.eachCell(cell => {
                        if (!noteRow && /^ghi chú\s*:/i.test(cellText(cell))) noteRow = r;
                    });
                    if (totalRow && noteRow) break;
                }
                const footerRow = totalRow || noteRow;
                if (!footerRow || footerRow <= headerRowIdx) throw new Error(ws.name + ': mẫu thiếu dòng cộng hoặc ghi chú.');
                const dataStart = headerRowIdx + 1;
                const bodyRows = [];
                for (let r = dataStart; r < footerRow; r++) bodyRows.push(ws.getRow(r));
                const dataRow = bodyRows.find(row => /^\d+$/.test(cellText(row.getCell(type ? colMap[1] : 1))) &&
                    cellText(row.getCell(nameCol))) || bodyRows[0];
                const unitRow = bodyRows.find(row => /^[IVXLCDM]+$/i.test(cellText(row.getCell(type ? colMap[1] : 1))));
                const categories = bodyRows.filter(row => /^[AB]$/.test(cellText(row.getCell(type ? colMap[1] : 1))));
                const bands = bodyRows.filter(row => !cellText(row.getCell(1)) &&
                    /nghị định|đối tượng/i.test(cellText(row.getCell(nameCol))));
                const snapshot = row => row && ({ number: row.number, height: row.height, styles: Array.from({ length: Math.max(ws.columnCount, size) },
                    (_, c) => clone(row.getCell(c + 1).style)), values: clone(row.values) });
                return { headerRowIdx, colMap, size, columnExtent, nameCol, dataStart, footerRow, totalRow, noteRow,
                    dataStyle: snapshot(dataRow), unitStyle: snapshot(unitRow || dataRow),
                    categories: categories.map(snapshot), bands: bands.map(snapshot),
                    totalStyle: snapshot(totalRow ? ws.getRow(totalRow) : dataRow) };
            }
            // Dịch cả model để giữ đúng vị trí merge, ghi chú và style khi thêm hàng.
            function shiftTemplateRows(ws, at, count) {
                if (!count) return;
                const model = clone(ws.model);
                const moveAddress = address => address.replace(/([A-Z]+)([0-9]+)/g, (_, col, row) =>
                    col + (Number(row) >= at ? Number(row) + count : Number(row)));
                model.rows.forEach(row => {
                    if (row.number >= at) {
                        row.number += count;
                        row.cells.forEach(cell => { cell.address = moveAddress(cell.address); });
                    }
                });
                model.merges = (model.merges || []).map(moveAddress);
                for (const merge of ws.model.merges || []) ws.unMergeCells(merge);
                model.mergeCells = model.merges;
                ws.model = model;
                restoreModelStyles(ws, model);
            }
            function clearCellNote(cell) {
                if (!cell) return;
                delete cell.note;
                delete cell._comment;
                if (cell._value && cell._value.model) {
                    delete cell._value.model.comment;
                }
            }
            function prepareTemplateBody(ws, layout, needed) {
                if (!layout.totalRow) {
                    shiftTemplateRows(ws, layout.footerRow, 1);
                    layout.totalRow = layout.footerRow;
                    if (layout.noteRow) layout.noteRow++;
                    ws.getRow(layout.totalRow).getCell(layout.nameCol).value = 'TỔNG CỘNG';
                }
                const extra = Math.max(0, needed - (layout.footerRow - layout.dataStart));
                if (extra) {
                    shiftTemplateRows(ws, layout.footerRow, extra);
                    layout.footerRow += extra;
                    layout.totalRow += extra;
                    if (layout.noteRow) layout.noteRow += extra;
                }
                for (let r = layout.dataStart; r < layout.footerRow; r++) {
                    ws.getRow(r).eachCell({ includeEmpty: true }, cell => {
                        if (!cell.isMerged || cell.master.address === cell.address) cell.value = null;
                        clearCellNote(cell);
                    });
                }
                return layout;
            }
            function applyTemplateRow(row, prototype, width) {
                if (!prototype) return;
                row.height = prototype.height;
                for (let c = 1; c <= width; c++) row.getCell(c).style = clone(prototype.styles[c - 1] || {});
            }
            const layouts = new Map();
            for (const prefix of ['I', 'II', 'III', 'IV']) {
                const ws = exportWb.getWorksheet('Phụ lục ' + prefix);
                if (!ws) throw new Error('File mẫu thiếu sheet "Phụ lục ' + prefix + '".');
                layouts.set(ws.name, getTableLayout(ws));
            }
            for (const type of Object.keys(detailSizes)) {
                const ws = exportWb.getWorksheet('Phụ lục ' + type);
                if (!ws && ['I.1', 'I.2', 'I.3'].includes(type)) throw new Error('File mẫu thiếu sheet "Phụ lục ' + type + '".');
                if (ws) layouts.set(ws.name, getTableLayout(ws, type));
            }
            for (const rec of recordsToExport) {
                const type = recordType(rec);
                if (!detailSizes[type] || !exportWb.getWorksheet('Phụ lục ' + type)) {
                    throw new Error('File mẫu không có phụ lục phù hợp cho hồ sơ ' + (rec.hoTen || rec.id || '') + ': ' + type + '.');
                }
            }

                        // =====================================================================
            // XỬ LÝ CÁC SHEET GỐC (Phụ lục I.1, I.2, I.3, I.5)
            // =====================================================================

            function populateChildSheet(ws, sType, recordsForSheet) {
                if (!ws) return;
                const fmtMoney = BQPValidation.fmtMoney;
                const layout = layouts.get(ws.name) || getTableLayout(ws, sType);
                const { colMap, headerRowIdx: hRow } = layout;
                const maxCol = Math.max(...Object.values(colMap));
                const noteCol = layout.columnExtent + 1;
                const isRawMode = exportContext && exportContext.rawMode;

                if (!isRawMode) {
                    // Header ghi chú thẩm định
                    let headerCell = ws.getRow(hRow).getCell(noteCol);
                    headerCell.value = 'Ghi chú thẩm định';
                    headerCell.style = {
                        font: { name: 'Arial', size: 8.5, bold: true, color: { argb: 'FFFF0000' } },
                        alignment: { vertical: 'middle', horizontal: 'center', wrapText: true },
                        border: { top: { style: 'thin' }, left: { style: 'thin' }, bottom: { style: 'thin' }, right: { style: 'thin' } }
                    };
                    ws.getColumn(noteCol).width = 46;
                    ws.getColumn(noteCol).hidden = false;
                }

                const formatDobSafe = (val) => {
                    if (!val) return '';
                    if (val instanceof Date && !isNaN(val)) {
                        let m = val.getMonth() + 1;
                        let y = val.getFullYear();
                        return m + '/' + y;
                    }
                    return String(val).trim().replace(/^[0]+/g, '');
                };

                const formatDateSafe = (val) => {
                    if (!val) return '';
                    if (val instanceof Date && !isNaN(val)) {
                        let d = val.getDate();
                        let m = val.getMonth() + 1;
                        let y = val.getFullYear();
                        return (d < 10 ? '0' + d : d) + '/' + (m < 10 ? '0' + m : m) + '/' + y;
                    }
                    return String(val).trim();
                };

                function applyRecordToRow(row, rec, sttNum) {
                    const nameVal = rec.hoTen || (rec.input && rec.input.hoTen) || '';
                    const dobVal = rec.ngaySinh != null ? rec.ngaySinh : ((rec.input && rec.input.ngaySinh) || '');
                    const cbVal = rec.capBac || (rec.input && rec.input.capBac) || '';
                    const cvVal = rec.chucVu || (rec.input && rec.input.chucVu) || '';
                    const nnVal = rec.nhapNgu || rec.enlistmentDate || rec.input?.nhapNgu || rec.rawCols?.[6] || '';

                    if (sType === 'I.4' || sType === 'I.5') {
                        for (let c = 1; c <= layout.size; c++) {
                            if (rec.rawCols?.[c] != null) row.getCell(colMap[c]).value = rec.rawCols[c];
                        }
                    }
                    row.getCell(colMap[1] || 1).value = sttNum;
                    row.getCell(colMap[1] || 1).style = Object.assign({}, row.getCell(colMap[1] || 1).style); row.getCell(colMap[1] || 1).alignment = { horizontal: 'center', vertical: 'middle' };
                    row.getCell(colMap[2] || 2).value = nameVal;

                    if (sType === 'I.5') {
                        if (colMap[3]) row.getCell(colMap[3]).value = rec.donVi || rec.unitName || rec.input?.donVi || '';
                        if (colMap[4]) row.getCell(colMap[4]).value = cvVal;
                        if (colMap[5]) row.getCell(colMap[5]).value = cbVal;
                        const date9 = rec.nhapNgu || rec.enlistmentDate || rec.input?.nhapNgu || rec.rawCols?.[9] || '';
                        if (colMap[9] && date9) {
                            row.getCell(colMap[9]).value = formatDateSafe(date9) || String(date9);
                            row.getCell(colMap[9]).numFmt = '@';
                            row.getCell(colMap[9]).alignment = { horizontal: 'center', vertical: 'middle' };
                        }
                        const date10 = rec.thoiDiemNghi || rec.retirementDate || rec.input?.thoiDiemNghi || rec.rawCols?.[10] || '';
                        if (colMap[10] && date10) {
                            row.getCell(colMap[10]).value = formatDateSafe(date10) || String(date10);
                            row.getCell(colMap[10]).numFmt = '@';
                            row.getCell(colMap[10]).alignment = { horizontal: 'center', vertical: 'middle' };
                        }
                    }

                    if (sType !== 'I.4' && sType !== 'I.5') {
                    if (colMap[3]) {
                        row.getCell(colMap[3]).value = formatDobSafe(dobVal) || '';
                        row.getCell(colMap[3]).numFmt = '@';
                        row.getCell(colMap[3]).alignment = { horizontal: 'center', vertical: 'middle' };
                    }
                    if (colMap[4]) row.getCell(colMap[4]).value = cbVal;
                    if (colMap[5]) row.getCell(colMap[5]).value = cvVal;
                    if (colMap[6] && nnVal) {
                        row.getCell(colMap[6]).value = formatDateSafe(nnVal) || String(nnVal);
                        row.getCell(colMap[6]).numFmt = '@';
                        row.getCell(colMap[6]).alignment = { horizontal: 'center', vertical: 'middle' };
                    }

                    if (rec.rawCols) {
                        for (let c = 7; c <= maxCol; c++) {
                            if (rec.rawCols[c] != null && colMap[c]) {
                                row.getCell(colMap[c]).value = rec.rawCols[c];
                            }
                        }
                    }
                    for (const [logical, field] of [[7, 'sapNhap'], [8, 'thoiDiemNghi'], [9, 'luongThang']]) {
                        if (colMap[logical] && rec.rawCols?.[logical] == null && rec[field] != null) {
                            row.getCell(colMap[logical]).value = logical === 9 ? rec[field] : formatDateSafe(rec[field]);
                        }
                    }
                    }

                    const totalColIdx = ['I.1', 'I.2', 'I.3'].includes(sType) ? detailSizes[sType] : null;
                    const actV = rec.tongTienThucTe != null ? rec.tongTienThucTe : 0;
                    const expV = rec.tongTienTinhLai != null ? rec.tongTienTinhLai : actV;
                    const diffV = rec.diff != null ? rec.diff : (expV - actV);

                    if (colMap[totalColIdx] && row.getCell(colMap[totalColIdx]).value == null) {
                        row.getCell(colMap[totalColIdx]).value = actV;
                        row.getCell(colMap[totalColIdx]).numFmt = '#,##0';
                    }

                    let hasCellErr = rec.cellErrors && Object.keys(rec.cellErrors).length > 0;
                    let isErr = rec.hasErrors || (rec.errorDetails && rec.errorDetails.length > 0) || hasCellErr;
                    let hasDiff = actV > 0 && Math.abs(diffV) > 1000;
                    let isRecIncomplete = (rec.valRes && (rec.valRes.status === 'INCOMPLETE_INPUT' || rec.valRes.isIncomplete)) || (rec.tongTienThucTe == null && rec.hasErrors);

                    let noteVal = '';
                    let noteColor = 'FF166534';

                    if (isRecIncomplete) {
                        let detail = (rec.errorDetails && rec.errorDetails.length > 0) ? rec.errorDetails.join('\n') : 'Chưa đủ điều kiện tiên quyết';
                        noteVal = '[CHƯA ĐỦ DỮ LIỆU - CẦN TÍNH LẠI CÔNG THỨC TRÊN EXCEL]\n' + detail;
                        noteColor = 'FFB45309';
                    } else if (isErr || hasDiff) {
                        noteColor = 'FFFF0000';
                        let diffPrefix = '';
                        if (hasCellErr) {
                            diffPrefix = '[LỖI ĐỌC DỮ LIỆU ĐẦU VÀO]\n';
                        } else if (diffV > 1000) {
                            diffPrefix = '[Sai thiếu: Cấp thiếu +' + fmtMoney(diffV) + ' đ]\n';
                        } else if (diffV < -1000) {
                            diffPrefix = '[Sai thừa: Cấp thừa -' + fmtMoney(Math.abs(diffV)) + ' đ]\n';
                        }
                        let detailStr = (rec.errorDetails && rec.errorDetails.length > 0) ? rec.errorDetails.join('\n') : 'Lệch chuẩn';
                        noteVal = diffPrefix + detailStr;
                    } else {
                        noteVal = 'Đạt chuẩn';
                    }

                    if (!isRawMode) {
                        const noteCell = row.getCell(noteCol);
                        noteCell.value = noteVal;
                        noteCell.style = {
                            font: { name: 'Arial', size: 7.5, color: { argb: noteColor }, bold: noteVal !== 'Đạt chuẩn' },
                            alignment: { wrapText: true, vertical: 'top' },
                            border: {
                                top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                            }
                        };
                    }

                    if (hasCellErr && rec.cellErrors) {
                        for (let errColIdx in rec.cellErrors) {
                            let targetCol = colMap[errColIdx] || parseInt(errColIdx, 10);
                            if (targetCol) {
                                try {
                                    let cellToMark = row.getCell(targetCol);
                                    cellToMark.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFFFC7CE' } };
                                    cellToMark.font = Object.assign({}, cellToMark.font || {}, { color: { argb: 'FF9C0006' }, bold: true });
                                    // Bỏ cellToMark.note để không hiện tam giác đỏ
                                } catch(eF) {}
                            }
                        }
                    }

                    let has2Line = false;
                    if (rec.comparisons && rec.comparisons.length > 0) {
                        rec.comparisons.forEach(comp => {
                            if (comp.hasErr) {
                                try {
                                    let cMatch = comp.col ? comp.col.match(/(\d+)/) : null;
                                    if (cMatch) {
                                        let cNum = parseInt(cMatch[1], 10);
                                        if ((['I.1', 'I.2', 'I.3'].includes(sType) && cNum === 9) || cNum >= 10) {
                                            let cellCol = colMap[cNum] || cNum;
                                            if (cellCol) {
                                                let targetCell = row.getCell(cellCol);
                                                let rawStr = '';
                                                let expStr = '';
                                                const parseCompVal = (val) => {
                                                    if (val == null) return '';
                                                    // Chuỗi kiểu "277.739.280 đ": dấu chấm là phân cách nghìn, không phải thập phân
                                                    let num = typeof val === 'number' ? val : BQPNormalization.number(String(val).replace(/\s*(đồng|đ|tháng|năm)\s*$/i, ''));
                                                    if (num != null && !isNaN(num) && num > 0) {
                                                        if (cNum === 10) return String(Math.round(num));
                                                        if (cNum === 11 || cNum === 12) return String(num);
                                                        return fmtMoney(Math.round(num));
                                                    }
                                                    return String(val).trim();
                                                };

                                                if (comp.actual != null) rawStr = parseCompVal(comp.actual);
                                                if (comp.expected != null) expStr = parseCompVal(comp.expected);
                                                if (!rawStr && targetCell.value != null) rawStr = parseCompVal(targetCell.value);

                                                rawStr = rawStr.replace(/\s*(đ|tháng|năm)$/i, '').trim();
                                                expStr = expStr.replace(/\s*(đ|tháng|năm)$/i, '').trim();

                                                const cleanNum = s => String(s || '').replace(/[.,\s]/g, '').trim();
                                                if (rawStr && expStr && cleanNum(rawStr) !== cleanNum(expStr)) {
                                                    targetCell.value = {
                                                        richText: [
                                                            { text: rawStr + '\n', font: { name: 'Times New Roman', size: 9.5, strike: true, color: { argb: 'FFDC2626' } } },
                                                            { text: expStr, font: { name: 'Times New Roman', size: 11, bold: true, strike: false, color: { argb: 'FF000000' } } }
                                                        ]
                                                    };
                                                    targetCell.style = Object.assign({}, targetCell.style); targetCell.alignment = {
                                                        wrapText: true,
                                                        vertical: 'middle',
                                                        horizontal: cNum <= 12 ? 'center' : 'right'
                                                    };
                                                    has2Line = true;
                                                }
                                            }
                                        }
                                    }
                                } catch(eComp) {}
                            }
                        });
                    }

                    row.height = has2Line ? Math.max(36, row.height || 0) : (row.height || 25.2);

                    for (let c = 1; c <= maxCol; c++) {
                        const cell = row.getCell(c);
                        const sampleBorder = layout.dataStyle.styles[c - 1]?.border || {};
                        const fallback = { style: 'thin' };
                        cell.border = Object.fromEntries(['top', 'left', 'bottom', 'right'].map(side =>
                            [side, clone(sampleBorder[side]?.style ? sampleBorder[side] : fallback)]));
                    }
                }

                // Giữ các hàng nhóm/đơn vị của mẫu, thay các nhãn ví dụ bằng nhóm hồ sơ thật.
                const buckets = new Map();
                recordsForSheet.forEach(rec => {
                    const categoryLabel = rec.categoryCode || rec.categoryName || rec.category || rec.input?.categoryCode || rec.input?.category || '';
                    const category = categoryLabel ? (/QNCN|chuyên nghiệp/i.test(categoryLabel) ? 'QNCN' : 'SQ')
                        : (MilitaryRankHelper.checkIsQNCN(String(rec.capBac || rec.input?.capBac || ''),
                            String(rec.chucVu || rec.input?.chucVu || '')) ? 'QNCN' : 'SQ');
                    const policyLabel = String(rec.policyCode || rec.policy || rec.policyGroup || rec.nghiDinh ||
                        rec.input?.policyCode || rec.input?.policy || rec.input?.nghiDinh || '');
                    const policy = /177/.test(policyLabel) ? '177' : '178';
                    const unit = rec.group || rec.donVi || rec.unitName || exportContext?.units?.find(u => u.id === rec.unitId)?.name || 'Toàn đơn vị';
                    
                    let l2Name = '';
                    let l2Order = 0;
                    if (level2Resolver) {
                        const res = level2Resolver.resolveUnit(rec.unitId);
                        if (res.ok && res.l2Unit) {
                            l2Name = res.l2Unit.name;
                            l2Order = res.l2Unit.displayOrder || 0;
                        }
                    }

                    const key = policy + '\u0000' + category + '\u0000' + l2Name + '\u0000' + unit;
                    if (!buckets.has(key)) buckets.set(key, { policy, category, unit, l2Name, l2Order, records: [] });
                    buckets.get(key).records.push(rec);
                });
                const descriptors = [];
                let previousCategory = null, previousPolicy = null, previousL2 = null, unitNumber = 0;
                
                const sortedBuckets = [...buckets.values()].sort((a, b) =>
                    b.policy.localeCompare(a.policy) || 
                    (a.category === 'SQ' ? 0 : 1) - (b.category === 'SQ' ? 0 : 1) ||
                    (a.l2Order - b.l2Order) ||
                    String(a.l2Name || '').localeCompare(String(b.l2Name || ''), 'vi') ||
                    String(a.unit || '').localeCompare(String(b.unit || ''), 'vi')
                );
                
                const roman = value => {
                    let result = '';
                    for (const [amount, letter] of [[1000, 'M'], [900, 'CM'], [500, 'D'], [400, 'CD'], [100, 'C'],
                        [90, 'XC'], [50, 'L'], [40, 'XL'], [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']]) {
                        while (value >= amount) { result += letter; value -= amount; }
                    }
                    return result;
                };
                for (const bucket of sortedBuckets) {
                    if (layout.bands.length && previousPolicy !== bucket.policy) {
                        const bandIndex = Math.max(0, layout.bands.findIndex(band => String(band.values[colMap[2]]).includes(bucket.policy)));
                        for (const sample of layout.bands.slice(0, bandIndex + 1)) {
                            if (!descriptors.some(descriptor => descriptor.label === sample.values[colMap[2]])) {
                                descriptors.push({ prototype: sample, label: sample.values[colMap[2]] });
                            }
                        }
                        previousPolicy = bucket.policy;
                        previousCategory = null;
                        previousL2 = null;
                    }
                    if (layout.categories.length && previousCategory !== bucket.category) {
                        const qncn = /QNCN|chuyên nghiệp/i.test(bucket.category);
                        const sample = layout.categories.find(category => category.values[colMap[1]] === (qncn ? 'B' : 'A')) || layout.categories[0];
                        descriptors.push({ prototype: sample, category: qncn ? 'B' : 'A',
                            label: qncn ? 'Quân nhân chuyên nghiệp' : 'Sĩ quan' });
                        previousCategory = bucket.category;
                        previousL2 = null;
                        unitNumber = 0;
                    }

                    if (level2Resolver && bucket.l2Name && previousL2 !== bucket.l2Name) {
                        descriptors.push({
                            prototype: layout.l2Style || layout.unitStyle,
                            l2: bucket.l2Name,
                            isL2Header: true
                        });
                        previousL2 = bucket.l2Name;
                        unitNumber = 0;
                    }

                    if (layout.unitStyle) descriptors.push({ prototype: layout.unitStyle, unit: bucket.unit, number: roman(++unitNumber) });
                    bucket.records.forEach(rec => descriptors.push({ prototype: layout.dataStyle, rec }));
                }
                if (descriptors.length) {
                    for (const band of layout.bands) {
                        if (!descriptors.some(descriptor => descriptor.label === band.values[colMap[2]])) {
                            descriptors.push({ prototype: band, label: band.values[colMap[2]] });
                        }
                    }
                }
                prepareTemplateBody(ws, layout, descriptors.length);
                // Với phụ lục rỗng, giữ nhãn phân loại chính thức; tên đơn vị và quân nhân mẫu đã xóa.
                if (!descriptors.length) {
                    [...layout.categories, ...layout.bands].forEach(sample => {
                        const row = ws.getRow(sample.number);
                        applyTemplateRow(row, sample, maxCol);
                        row.getCell(colMap[1]).value = sample.values[colMap[1]] || null;
                        row.getCell(colMap[2]).value = sample.values[colMap[2]] || null;
                    });
                }
                let sttCounter = 1;
                descriptors.forEach((descriptor, i) => {
                    const row = ws.getRow(layout.dataStart + i);
                    applyTemplateRow(row, descriptor.prototype, maxCol);
                    
                    if (descriptor.isL2Header) {
                        for (let c = 1; c <= maxCol; c++) {
                            const cCell = row.getCell(colMap[c] || c);
                            if (c === 2) {
                                cCell.value = '=== ' + descriptor.l2 + ' ===';
                                cCell.font = { name: 'Times New Roman', size: 12, bold: true, italic: true };
                                cCell.alignment = { horizontal: 'left', vertical: 'middle' };
                            } else {
                                cCell.value = null;
                            }
                            cCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFDBE5F1' } };
                        }
                    } else if (descriptor.rec) {
                        applyRecordToRow(row, descriptor.rec, sttCounter++);
                    } else {
                        row.getCell(colMap[1]).value = descriptor.category || descriptor.number || null;
                        row.getCell(colMap[2]).value = descriptor.label || descriptor.unit;
                    }
                });
                const total = ws.getRow(layout.totalRow);
                for (let c = 3; c <= maxCol; c++) {
                    if (!total.getCell(c).value?.formula) total.getCell(c).value = null;
                }
                if (['I.1', 'I.2', 'I.3', 'I.5'].includes(sType)) {
                    const raw = recordsForSheet.reduce((sum, rec) => sum + (rec.tongTienThucTe || 0), 0);
                    const expected = recordsForSheet.reduce((sum, rec) => sum + (rec.tongTienTinhLai ?? rec.tongTienThucTe ?? 0), 0);
                    const cell = total.getCell(colMap[detailSizes[sType]]);
                    cell.value = Math.abs(expected - raw) > 1000 ? { richText: [
                        { text: BQPValidation.fmtMoney(raw) + '\n', font: { name: 'Times New Roman', size: 9.5, strike: true, color: { argb: 'FFDC2626' } } },
                        { text: BQPValidation.fmtMoney(expected), font: { name: 'Times New Roman', size: 11, bold: true, color: { argb: 'FF000000' } } }
                    ] } : expected;
                    if (Math.abs(expected - raw) > 1000) {
                        cell.alignment = { ...cell.alignment, wrapText: true, vertical: 'middle', horizontal: 'right' };
                        total.height = Math.max(36, total.height || 0);
                    }
                }
            }

            exportWb.eachSheet((ws) => {
                let type = BQPNormalization.sheetType(ws);
                if (!Object.hasOwn(detailSizes, type)) return;
                const recsInThisSheet = recordsToExport.filter(r => recordType(r) === type);
                populateChildSheet(ws, type, recsInThisSheet);
            });
            // =====================================================================
            // BƯỚC BỔ SUNG: Tạo các Sheet II.x (Đạt chuẩn), III.x (Cấp thừa), IV.x (Cấp thiếu)
            // =====================================================================
            const classifyRec = (r) => {
                let hasCellErr = r.cellErrors && Object.keys(r.cellErrors).length > 0;
                if (hasCellErr) return 'data_err'; // Dòng lỗi đọc dữ liệu tuyệt đối không vào Phụ lục II.x (Đạt chuẩn)
                let act = r.tongTienThucTe != null ? r.tongTienThucTe : 0;
                let exp2 = r.tongTienTinhLai != null ? r.tongTienTinhLai : act;
                let diff2 = r.diff != null ? r.diff : (exp2 - act);
                let hasCompErr = r.comparisons && r.comparisons.some(c => c.hasErr);
                let isErr2 = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0) || hasCompErr || (act > 0 && Math.abs(diff2) > 1000);

                // II.1, II.2, II.3 CHỈ lấy ra những người hoàn toàn ĐÚNG ở I.1, I.2, I.3
                if (!isErr2) return 'correct';

                // Đối tượng có sai phạm:
                if (diff2 < -1000) return 'over';  // Phụ lục III.x (Cấp thừa)
                if (diff2 > 1000) return 'under';  // Phụ lục IV.x (Cấp thiếu)
                return 'other_err';               // Sai tiêu chí/số tháng nhưng không lệch tiền: lưu vết ở sheet gốc I.x, không đưa vào II.x
            };

            const correctRecs = recordsToExport.filter(r => classifyRec(r) === 'correct');
            const overRecs    = recordsToExport.filter(r => classifyRec(r) === 'over');
            const underRecs   = recordsToExport.filter(r => classifyRec(r) === 'under');

            const subGroups = [
                { recs: correctRecs, prefix: 'II',  label: 'Đạt chuẩn' },
                { recs: overRecs,    prefix: 'III', label: 'Cấp thừa' },
                { recs: underRecs,   prefix: 'IV',  label: 'Cấp thiếu' }
            ];

            const templatePl1 = exportWb.getWorksheet('Phụ lục I');
            if (!templatePl1) {
                const errMsg = 'File mẫu không chứa sheet "Phụ lục I" để làm mẫu tổng hợp.';
                if (typeof notifyError === 'function') notifyError(errMsg);
                throw new Error(errMsg);
            }

            function buildSummarySheet(exportWb, templateWs, prefix, label, recs, existingWs = null, resolver = null) {
                const sheetName = 'Phụ lục ' + prefix;
                const ws = existingWs || exportWb.getWorksheet(sheetName);
                if (!ws) throw new Error('File mẫu thiếu sheet "' + sheetName + '".');
                const layout = layouts.get(sheetName);
                const borderCell = { top: { style: 'thin' }, left: { style: 'thin' },
                    bottom: { style: 'thin' }, right: { style: 'thin' } };
                // Chỉ bổ sung cột đối chiếu ngoài bảng A:J, giữ nguyên tiêu đề/merge/width mẫu.
                const noteCol = layout.columnExtent + 1;
                const isRawMode = exportContext && exportContext.rawMode;
                if (!isRawMode) {
                    ws.getColumn(noteCol).width = 30;
                    ws.getRow(layout.headerRowIdx).getCell(noteCol).value = 'Ghi chú thẩm định';
                    ws.getRow(layout.headerRowIdx).getCell(noteCol).style = clone(ws.getRow(layout.headerRowIdx).getCell(10).style);
                }

                // Thống kê theo từng đơn vị: theo dõi cả số tiền raw từ excel và số tiền sau thẩm định
                const unitStats = new Map();
                if (resolver) {
                    recs.forEach(r => {
                        let sh = recordType(r);
                        if (!['I.1', 'I.2', 'I.3'].includes(sh)) return;
                        const res = resolver.resolveUnit(r.unitId);
                        if (!res.ok || !res.l2Unit) return;
                        const l2 = res.l2Unit;
                        const key = l2.id;
                        if (!unitStats.has(key)) {
                            unitStats.set(key, {
                                unitId: l2.id,
                                unitName: l2.name,
                                displayOrder: l2.displayOrder,
                                'I.1': { count: 0, rawMoney: 0, expMoney: 0 },
                                'I.2': { count: 0, rawMoney: 0, expMoney: 0 },
                                'I.3': { count: 0, rawMoney: 0, expMoney: 0 }
                            });
                        }
                        const st = unitStats.get(key);
                        st[sh].count++;
                        let rawAmt = r.tongTienThucTe != null ? r.tongTienThucTe : 0;
                        let expAmt = r.tongTienTinhLai != null ? r.tongTienTinhLai : rawAmt;
                        st[sh].rawMoney += rawAmt;
                        st[sh].expMoney += expAmt;
                    });
                } else {
                    recs.forEach(r => {
                        let sh = recordType(r);
                        if (!['I.1', 'I.2', 'I.3'].includes(sh)) return;
                        let u = r.group || r.donVi || r.unitName || exportContext?.units?.find(unit => unit.id === r.unitId)?.name || 'Toàn đơn vị';
                        if (typeof BQPNormalization !== 'undefined' && BQPNormalization.normalizeUnitName) {
                            u = BQPNormalization.normalizeUnitName(u);
                        }
                        if (!unitStats.has(u)) {
                            unitStats.set(u, {
                                unitId: null,
                                unitName: u,
                                displayOrder: 0,
                                'I.1': { count: 0, rawMoney: 0, expMoney: 0 },
                                'I.2': { count: 0, rawMoney: 0, expMoney: 0 },
                                'I.3': { count: 0, rawMoney: 0, expMoney: 0 }
                            });
                        }
                        const st = unitStats.get(u);
                        st[sh].count++;
                        let rawAmt = r.tongTienThucTe != null ? r.tongTienThucTe : 0;
                        let expAmt = r.tongTienTinhLai != null ? r.tongTienTinhLai : rawAmt;
                        st[sh].rawMoney += rawAmt;
                        st[sh].expMoney += expAmt;
                    });
                }

                const fontData = { name: 'Times New Roman', size: 12, bold: false };
                const fontTotal = { name: 'Times New Roman', size: 12, bold: true };

                let stt = 0;
                const startRow = layout.dataStart;
                let grandRaw1 = 0, grandExp1 = 0;
                let grandRaw2 = 0, grandExp2 = 0;
                let grandRaw3 = 0, grandExp3 = 0;
                let grandCount1 = 0, grandCount2 = 0, grandCount3 = 0;

                let unitList = Array.from(unitStats.values());
                if (resolver) {
                    unitList.sort((a, b) => {
                        if (a.displayOrder !== b.displayOrder) return a.displayOrder - b.displayOrder;
                        const nameCmp = String(a.unitName || '').localeCompare(String(b.unitName || ''), 'vi');
                        if (nameCmp !== 0) return nameCmp;
                        return String(a.unitId || '').localeCompare(String(b.unitId || ''));
                    });
                }

                prepareTemplateBody(ws, layout, unitList.length || (resolver ? 0 : 1));
                for (const st of unitList) {
                    const u = st.unitName;
                    stt++;
                    const r = startRow + stt - 1;
                    const row = ws.getRow(r);
                    applyTemplateRow(row, layout.dataStyle, 10);
                    let rowHas2Line = false;

                    let c1Count = st['I.1'].count || 0;
                    let c1Raw = st['I.1'].rawMoney || 0;
                    let c1Exp = st['I.1'].expMoney || 0;

                    let c2Count = st['I.2'].count || 0;
                    let c2Raw = st['I.2'].rawMoney || 0;
                    let c2Exp = st['I.2'].expMoney || 0;

                    let c3Count = st['I.3'].count || 0;
                    let c3Raw = st['I.3'].rawMoney || 0;
                    let c3Exp = st['I.3'].expMoney || 0;

                    grandCount1 += c1Count;
                    grandRaw1 += c1Raw;
                    grandExp1 += c1Exp;

                    grandCount2 += c2Count;
                    grandRaw2 += c2Raw;
                    grandExp2 += c2Exp;

                    grandCount3 += c3Count;
                    grandRaw3 += c3Raw;
                    grandExp3 += c3Exp;

                    let uTotalCount = c1Count + c2Count + c3Count;
                    let uTotalRaw = c1Raw + c2Raw + c3Raw;
                    let uTotalExp = c1Exp + c2Exp + c3Exp;

                    row.getCell(1).value = stt;
                    row.getCell(2).value = u;
                    row.getCell(3).value = c1Count;
                    row.getCell(5).value = c2Count;
                    row.getCell(7).value = c3Count;
                    row.getCell(9).value = uTotalCount;

                    // Định dạng ô tiền: Nếu có sai lệch thì hiển thị 2 giá trị (raw bên trên gạch đỏ, thẩm định bên dưới in đậm)
                    const setMoneyCell = (cell, rawM, expM) => {
                        try {
                            let hasErr = Math.abs(rawM - expM) > 1000;
                            if (hasErr) {
                                let rawStr = BQPValidation.fmtMoney(Math.round(rawM));
                                let expStr = BQPValidation.fmtMoney(Math.round(expM));
                                cell.value = {
                                    richText: [
                                        {
                                            text: rawStr + '\n',
                                            font: {
                                                name: 'Times New Roman',
                                                size: 9.5,
                                                strike: true,
                                                color: { argb: 'FFDC2626' }
                                            }
                                        },
                                        {
                                            text: expStr,
                                            font: {
                                                name: 'Times New Roman',
                                                size: 11,
                                                bold: true,
                                                strike: false,
                                                color: { argb: 'FF000000' }
                                            }
                                        }
                                    ]
                                };
                                cell.alignment = { wrapText: true, vertical: 'middle', horizontal: 'right' };
                                rowHas2Line = true;
                            } else {
                                cell.value = expM || 0;
                                cell.numFmt = '#,##0';
                                cell.font = clone(cell.font || fontData);
                                cell.alignment = { horizontal: 'right', vertical: 'middle' };
                            }
                        } catch (err) {
                            console.warn("Lỗi setMoneyCell:", err);
                            markCellExportError(cell, err, rawM);
                        }
                    };

                    setMoneyCell(row.getCell(4), c1Raw, c1Exp);
                    setMoneyCell(row.getCell(6), c2Raw, c2Exp);
                    setMoneyCell(row.getCell(8), c3Raw, c3Exp);
                    setMoneyCell(row.getCell(10), uTotalRaw, uTotalExp);

                    if (!isRawMode) {
                        let uDiff = Math.abs(uTotalExp - uTotalRaw) > 1000 ? Math.round(uTotalExp - uTotalRaw) : 0;
                        row.getCell(noteCol).value = uDiff;
                        row.getCell(noteCol).numFmt = '#,##0';
                        row.getCell(noteCol).font = fontData;
                        row.getCell(noteCol).alignment = { horizontal: 'right', vertical: 'middle' };
                    }

                    row.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                    row.getCell(2).alignment = { horizontal: 'left', vertical: 'middle', wrapText: true };
                    [3, 5, 7, 9].forEach(cN => {
                        let c = row.getCell(cN);
                        c.alignment = { horizontal: 'right', vertical: 'middle' };
                        c.numFmt = '#,##0';
                        c.font = clone(c.font || fontData);
                    });
                    for (let colN = 1; colN <= 10; colN++) {
                        row.getCell(colN).border = clone(layout.dataStyle.styles[colN - 1]?.border || borderCell);
                        if (!row.getCell(colN).font) row.getCell(colN).font = fontData;
                    }

                    row.height = rowHas2Line ? Math.max(36, row.height || 0) : (row.height || 25.2);
                }

                if (stt === 0) {
                    if (!resolver) {
                        stt = 1;
                        const r = startRow;
                        const row = ws.getRow(r);
                        applyTemplateRow(row, layout.dataStyle, 10);
                        row.getCell(1).value = 1;
                        row.getCell(2).value = 'Toàn đơn vị';
                        for (let colN = 3; colN <= 10; colN++) {
                            row.getCell(colN).value = 0;
                            row.getCell(colN).numFmt = '#,##0';
                            row.getCell(colN).alignment = { horizontal: 'right', vertical: 'middle' };
                        }
                        if (!isRawMode) {
                            row.getCell(noteCol).value = 0;
                            row.getCell(noteCol).numFmt = '#,##0';
                            row.getCell(noteCol).alignment = { horizontal: 'right', vertical: 'middle' };
                        }
                        for (let colN = 1; colN <= 10; colN++) {
                            row.getCell(colN).font = clone(row.getCell(colN).font || fontData);
                            row.getCell(colN).border = clone(layout.dataStyle.styles[colN - 1]?.border || borderCell);
                        }
                    }
                }

                const totalRowIdx = layout.totalRow;
                const totalRow = ws.getRow(totalRowIdx);
                applyTemplateRow(totalRow, layout.totalStyle, 10);
                let totalRowHas2Line = false;

                totalRow.getCell(1).value = null;
                totalRow.getCell(2).value = 'TỔNG CỘNG';
                totalRow.getCell(3).value = grandCount1;
                totalRow.getCell(5).value = grandCount2;
                totalRow.getCell(7).value = grandCount3;
                totalRow.getCell(9).value = grandCount1 + grandCount2 + grandCount3;

                const setTotalMoneyCell = (cell, rawM, expM) => {
                    try {
                        let hasErr = Math.abs(rawM - expM) > 1000;
                        if (hasErr) {
                            let rawStr = BQPValidation.fmtMoney(Math.round(rawM));
                            let expStr = BQPValidation.fmtMoney(Math.round(expM));
                            cell.value = {
                                richText: [
                                    {
                                        text: rawStr + '\n',
                                        font: {
                                            name: 'Times New Roman',
                                            size: 9.5,
                                            strike: true,
                                            color: { argb: 'FFDC2626' }
                                        }
                                    },
                                    {
                                        text: expStr,
                                        font: {
                                            name: 'Times New Roman',
                                            size: 11,
                                            bold: true,
                                            strike: false,
                                            color: { argb: 'FF000000' }
                                        }
                                    }
                                ]
                            };
                            cell.alignment = { wrapText: true, vertical: 'middle', horizontal: 'right' };
                            totalRowHas2Line = true;
                        } else {
                            cell.value = expM || 0;
                            cell.numFmt = '#,##0';
                            cell.font = clone(cell.font || fontTotal);
                            cell.alignment = { horizontal: 'right', vertical: 'middle' };
                        }
                    } catch (err) {
                        console.warn("Lỗi setTotalMoneyCell:", err);
                        markCellExportError(cell, err, rawM);
                    }
                };

                let grandTotalRaw = grandRaw1 + grandRaw2 + grandRaw3;
                let grandTotalExp = grandExp1 + grandExp2 + grandExp3;

                setTotalMoneyCell(totalRow.getCell(4), grandRaw1, grandExp1);
                setTotalMoneyCell(totalRow.getCell(6), grandRaw2, grandExp2);
                setTotalMoneyCell(totalRow.getCell(8), grandRaw3, grandExp3);
                setTotalMoneyCell(totalRow.getCell(10), grandTotalRaw, grandTotalExp);

                if (!isRawMode) {
                    let grandDiff = Math.abs(grandTotalExp - grandTotalRaw) > 1000 ? Math.round(grandTotalExp - grandTotalRaw) : 0;
                    totalRow.getCell(noteCol).value = grandDiff;
                    totalRow.getCell(noteCol).numFmt = '#,##0';
                    totalRow.getCell(noteCol).font = fontTotal;
                    totalRow.getCell(noteCol).alignment = { horizontal: 'right', vertical: 'middle' };
                }

                totalRow.getCell(2).alignment = { horizontal: 'center', vertical: 'middle' };
                [3, 5, 7, 9].forEach(cN => {
                    let cell = totalRow.getCell(cN);
                    cell.alignment = { horizontal: 'right', vertical: 'middle' };
                    cell.numFmt = '#,##0';
                    cell.font = clone(cell.font || fontTotal);
                });
                for (let colN = 1; colN <= 10; colN++) {
                    totalRow.getCell(colN).border = clone(layout.totalStyle.styles[colN - 1]?.border || borderCell);
                    if (!totalRow.getCell(colN).font) totalRow.getCell(colN).font = fontTotal;
                }

                totalRow.height = totalRowHas2Line ? Math.max(36, totalRow.height || 0) : (totalRow.height || 34.5);


                return ws;
            }

            // Sheet Thống kê/Tổng hợp: tổng hợp theo tháng hoặc đơn vị, đối chiếu số kê khai với số thẩm định.
            function buildThongKeSheet(records) {
                const normName = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
                    .replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/\s+/g, '').toLowerCase();
                const findSummaryWs = workbook => {
                    if (!workbook || !workbook.getWorksheet) return null;
                    return workbook.getWorksheet('Tổng hợp') || workbook.getWorksheet('Thống kê') ||
                        (workbook.worksheets || []).find(sheet => {
                            const key = normName(sheet.name);
                            return key === 'tonghop' || key === 'thongke' || key.includes('phuluctonghop');
                        }) || null;
                };
                const ws = findSummaryWs(exportWb);
                if (!ws) return;
                // Khi chưa có file đơn vị, baseWb chính là template (có số liệu mẫu) — không đối chiếu với nó.
                const rawWs = baseWb && baseWb.__bqpReportTemplate ? null : findSummaryWs(baseWb);
                const layout = summaryLayout(ws);
                const rawLayout = summaryLayout(rawWs);
                let { dataStart, totalRowIdx, noteRowIdx } = layout;
                const officerRanks = ['daita', 'thuongta', 'trungta', 'thieuta', 'daiuy', 'thuonguy', 'trunguy', 'thieuuy'];
                function findRankColumns(sheet, headerRow) {
                    const columns = new Map();
                    for (let r = Math.max(1, headerRow - 2); r < headerRow; r++) {
                        sheet.getRow(r).eachCell(cell => {
                            const key = normName(cellText(cell));
                            if (officerRanks.includes(key)) columns.set(key, cell.col);
                            else if (key.includes('quannhanchuyennghiep') || key.includes('qncn')) columns.set('other', cell.col);
                        });
                    }
                    return columns;
                }
                const rankColumns = findRankColumns(ws, layout.hdrRow);
                const rawRankColumns = rawLayout ? findRankColumns(rawWs, rawLayout.hdrRow) : new Map();
                const tableEndCol = Math.max(6, ...rankColumns.values());
                const rawRankCell = (row, key, col) => {
                    const rawCol = rawRankColumns.size ? rawRankColumns.get(key) : col;
                    return row && rawCol ? row.getCell(rawCol) : null;
                };

                const borderAll = { top: { style: 'thin' }, left: { style: 'thin' }, bottom: { style: 'thin' }, right: { style: 'thin' } };
                const fontBold13 = { name: 'Times New Roman', size: 13, bold: true, color: { argb: 'FF000000' } };
                const fontNormal11 = { name: 'Times New Roman', size: 11, bold: false, color: { theme: 1 } };
                const numFmt = '#,##0;[Red]#,##0';

                function rankCol(rec) {
                    const capBac = String(rec.capBac || rec.input?.capBac || '');
                    const category = normName(rec.categoryCode || rec.categoryName || rec.category || rec.input?.categoryCode || rec.input?.category || '');
                    const otherCol = rankColumns.get('other');
                    const position = ['sq', 'siquan'].includes(category) ? '' : String(rec.chucVu || rec.input?.chucVu || '');
                    if (/qncn|chuyennghiep|cnvcqp|cnqp|vcqp|ldhd|congnhan|vienchuc|laodong/.test(category) ||
                        MilitaryRankHelper.checkIsQNCN(capBac, position)) return otherCol;
                    const rank = normName(capBac);
                    const namedRank = officerRanks.find(key => rank.includes(key));
                    if (namedRank) return rankColumns.get(namedRank) || otherCol;
                    const code = capBac.match(/(?<!\d)([1-4])\s*(\/\/|\/)(?!\/|\d)/);
                    if (code) {
                        const keys = code[2] === '//' ? ['thieuta', 'trungta', 'thuongta', 'daita'] : ['thieuuy', 'trunguy', 'thuonguy', 'daiuy'];
                        return rankColumns.get(keys[Number(code[1]) - 1]) || otherCol;
                    }
                    return otherCol;
                }

                // Kỳ nghỉ (năm + tháng) của hồ sơ; null nếu không xác định được.
                function extractPeriod(rec) {
                    const raw = rec.thoiDiemNghi || rec.retirementDate || rec.input?.thoiDiemNghi || rec.input?.retirementDate || rec.rawCols?.[8] || '';
                    // Số nhỏ (VD số tháng nghỉ sớm) không phải ngày Excel: tránh bị đọc thành tháng 1-2/1900.
                    if (typeof raw === 'number' && raw < 10000) return null;
                    const d = parseToValidDate(raw);
                    return d ? { year: d.getFullYear(), month: d.getMonth() + 1 } : null;
                }
                function extractMonth(rec) {
                    const p = extractPeriod(rec);
                    return p ? p.month : 0;
                }

                function getEffectiveLevel(unitId, units) {
                    if (!unitId || !Array.isArray(units)) return null;
                    const byId = new Map();
                    units.forEach(u => { if (u) byId.set(String(u.id), u); });
                    const target = byId.get(String(unitId));
                    if (!target) return null;
                    if (target.level != null && target.level !== '') {
                        const lvl = parseInt(String(target.level), 10);
                        if (!isNaN(lvl) && lvl > 0) return lvl;
                    }
                    let curr = target, depth = 0;
                    const visited = new Set();
                    while (curr) {
                        const id = String(curr.id);
                        if (visited.has(id)) return null;
                        visited.add(id);
                        depth++;
                        if (!curr.parentId) break;
                        curr = byId.get(String(curr.parentId));
                    }
                    return depth || null;
                }

                const effLevel = getEffectiveLevel(exportContext?.selectedUnitId, exportContext?.units);
                const hasUnits = exportContext?.selectedUnitId && Array.isArray(exportContext?.units) && exportContext.units.length > 0;
                let childResolverFn = null;
                if (hasUnits && effLevel === 1 && level2Resolver) {
                    childResolverFn = unitId => {
                        const res = level2Resolver.resolveUnit(unitId);
                        return res.ok ? res.l2Unit : null;
                    };
                } else if (hasUnits && effLevel === 2) {
                    const byId = new Map();
                    exportContext.units.forEach(u => { if (u && u.id != null) byId.set(String(u.id), u); });
                    const selIdStr = String(exportContext.selectedUnitId);
                    const resolveCache = new Map();
                    childResolverFn = unitId => {
                        if (!unitId) return null;
                        const idStr = String(unitId);
                        if (resolveCache.has(idStr)) return resolveCache.get(idStr);
                        if (idStr === selIdStr) { resolveCache.set(idStr, null); return null; }
                        const u = byId.get(idStr);
                        if (!u) { resolveCache.set(idStr, null); return null; }
                        const path = [];
                        const visited = new Set();
                        let curr = u;
                        while (curr) {
                            const cid = String(curr.id);
                            if (visited.has(cid)) { resolveCache.set(idStr, null); return null; }
                            visited.add(cid);
                            path.push(curr);
                            if (cid === selIdStr) break;
                            if (!curr.parentId) { resolveCache.set(idStr, null); return null; }
                            curr = byId.get(String(curr.parentId));
                            if (!curr) { resolveCache.set(idStr, null); return null; }
                        }
                        path.reverse();
                        if (path.length < 2 || String(path[0].id) !== selIdStr) { resolveCache.set(idStr, null); return null; }
                        const child = path[1];
                        const order = child.orderIndex ?? child.displayOrder ?? 0;
                        const result = { id: String(child.id), name: child.name || ('Đơn vị ' + child.id), displayOrder: Number(order) || 0 };
                        resolveCache.set(idStr, result);
                        return result;
                    };
                }

                function mkRow() { return { i1: 0, i2: 0, i3: 0, ranks: Object.fromEntries([...rankColumns.values()].map(col => [col, 0])) }; }
                // Hồ sơ không đưa được vào bảng thống kê: loại khỏi mọi dòng (đơn vị, tháng, tổng cộng)
                // và ghi danh sách riêng dưới dòng Ghi chú để đối chiếu với Phụ lục I.
                const uncounted = new Map();
                const markUncounted = (rec, type, reason) => {
                    if (!uncounted.has(rec)) uncounted.set(rec, { rec, type, reasons: [] });
                    const reasons = uncounted.get(rec).reasons;
                    if (!reasons.includes(reason)) reasons.push(reason);
                };
                const monthReason = rec => {
                    const raw = rec.thoiDiemNghi || rec.retirementDate || rec.input?.thoiDiemNghi || rec.input?.retirementDate || '';
                    return raw ? `Thời điểm nghỉ không hợp lệ ("${raw}"), không xác định được tháng nghỉ`
                        : 'Thiếu thời điểm nghỉ, không xác định được tháng nghỉ';
                };
                const isRecIncomplete = rec => (rec.valRes && (rec.valRes.status === 'INCOMPLETE_INPUT' || rec.valRes.isIncomplete)) || (rec.tongTienThucTe == null && rec.hasErrors);
                // Hồ sơ chưa đủ dữ liệu tính toán hoặc lỗi đọc dữ liệu đầu vào: không thống kê, đưa vào danh sách rà soát.
                const dataIssueReasons = rec => {
                    const reasons = [];
                    const details = (rec.errorDetails || []).map(String);
                    const prereq = details.find(text => /điều kiện tiên quyết/i.test(text)) || '';
                    // Hồ sơ nạp lại từ CSDL có thể mất valRes.status, nên nhận diện thêm qua thông báo thiếu điều kiện tiên quyết.
                    if (isRecIncomplete(rec) || prereq) {
                        const fields = prereq.match(/\[([^\]]+)\]/);
                        reasons.push('[CHƯA ĐỦ DỮ LIỆU TÍNH TOÁN] ' + (fields ? 'Thiếu hoặc lỗi: ' + fields[1] : (details[0] || 'Chưa đủ điều kiện tiên quyết')));
                    }
                    const cellErrors = rec.cellErrors && typeof rec.cellErrors === 'object' ? Object.entries(rec.cellErrors) : [];
                    if (cellErrors.length > 0) {
                        reasons.push('[LỖI ĐỌC DỮ LIỆU ĐẦU VÀO] ' + cellErrors.map(([col, msg]) => `Cột ${col}: ${msg}`).join('; '));
                    }
                    return reasons;
                };
                const useUnits = !!childResolverFn;

                // Số liệu theo tháng được tách theo NĂM nghỉ, mỗi năm một khối 12 tháng.
                const monthsByYear = new Map();
                const monthRowsOf = year => {
                    if (!monthsByYear.has(year)) {
                        const rows = [];
                        for (let m = 1; m <= 12; m++) {
                            const rd = mkRow();
                            rows.push({ tt: m, label: 'Tháng ' + m, i1: rd.i1, i2: rd.i2, i3: rd.i3, ranks: rd.ranks, _isMonth: true, _month: m });
                        }
                        monthsByYear.set(year, rows);
                    }
                    return monthsByYear.get(year);
                };
                // Chỉ một năm 2025: giữ bố cục cũ. Nhiều năm (hoặc năm khác 2025): mỗi năm có dòng tiêu đề và nhãn "Tháng n/năm".
                const buildMonthBlocks = titleOf => {
                    const years = [...monthsByYear.keys()].sort((a, b) => a - b);
                    if (years.length === 0) years.push(2025);
                    const showYear = years.length > 1 || years[0] !== 2025;
                    const out = [];
                    for (const y of years) {
                        const rows = monthRowsOf(y);
                        if (showYear) {
                            out.push({ _isSeparator: true, label: titleOf(y) });
                            rows.forEach(r => { r.label = `Tháng ${r._month}/${y}`; });
                        }
                        out.push(...rows);
                    }
                    return out;
                };

                let dataRows;
                if (useUnits) {
                    const unitMap = new Map();
                    for (const rec of records) {
                        const type = recordType(rec);
                        if (!['I.1', 'I.2', 'I.3'].includes(type)) continue;
                        const child = childResolverFn(rec.unitId);
                        const period = extractPeriod(rec);
                        const month = period ? period.month : 0;
                        const unitOk = !!child || effLevel === 2;
                        const monthOk = month >= 1 && month <= 12;
                        if (!unitOk) markUncounted(rec, type, 'Không xác định được đơn vị trực thuộc để thống kê theo đơn vị');
                        const issues = dataIssueReasons(rec);
                        issues.forEach(reason => markUncounted(rec, type, reason));
                        if (!monthOk && issues.length === 0) markUncounted(rec, type, monthReason(rec));
                        if (!unitOk || !monthOk || issues.length > 0) continue;
                        const col = rankCol(rec);
                        const targets = [monthRowsOf(period.year)[month - 1]];
                        if (child) {
                            if (!unitMap.has(child.id)) {
                                const rd = mkRow();
                                unitMap.set(child.id, { label: child.name, order: child.displayOrder, i1: rd.i1, i2: rd.i2, i3: rd.i3, ranks: rd.ranks });
                            }
                            targets.push(unitMap.get(child.id));
                        }
                        for (const row of targets) {
                            if (type === 'I.1') row.i1++;
                            else if (type === 'I.2') row.i2++;
                            else row.i3++;
                            if (col != null) row.ranks[col]++;
                        }
                    }
                    dataRows = [...unitMap.values()].sort((a, b) => {
                        if (a.order !== b.order) return a.order - b.order;
                        return String(a.label).localeCompare(String(b.label), 'vi');
                    });
                    dataRows.forEach((r, i) => r.tt = i + 1);
                    if (effLevel === 2) {
                        dataRows = buildMonthBlocks(y => `NĂM ${y}`);
                    } else {
                        const blocks = buildMonthBlocks(y => `THỐNG KÊ THEO THÁNG - NĂM ${y}`);
                        if (!blocks.some(b => b._isSeparator)) dataRows.push({ _isSeparator: true, label: 'THỐNG KÊ THEO THÁNG' });
                        dataRows.push(...blocks);
                    }
                } else {
                    for (const rec of records) {
                        const type = recordType(rec);
                        if (!['I.1', 'I.2', 'I.3'].includes(type)) continue;
                        const issues = dataIssueReasons(rec);
                        issues.forEach(reason => markUncounted(rec, type, reason));
                        const period = extractPeriod(rec);
                        const month = period ? period.month : 0;
                        if ((month < 1 || month > 12) && issues.length === 0) markUncounted(rec, type, monthReason(rec));
                        if (issues.length > 0 || month < 1 || month > 12) continue;
                        const col = rankCol(rec);
                        const row = monthRowsOf(period.year)[month - 1];
                        if (type === 'I.1') row.i1++;
                        else if (type === 'I.2') row.i2++;
                        else row.i3++;
                        if (col != null) row.ranks[col]++;
                    }
                    dataRows = buildMonthBlocks(y => `NĂM ${y}`);
                }

                function summaryLayout(sheet) {
                    if (!sheet) return null;
                    let hdrRow = 7;
                    for (let r = 1; r <= Math.min(15, sheet.rowCount); r++) {
                        if (cellText(sheet.getRow(r).getCell(1)) === 'A' && cellText(sheet.getRow(r).getCell(2)) === 'B') { hdrRow = r; break; }
                    }
                    const dataStart = hdrRow + 1;
                    let totalRowIdx = 0, noteRowIdx = 0;
                    for (let r = dataStart; r <= sheet.rowCount; r++) {
                        const bVal = cellText(sheet.getRow(r).getCell(2));
                        if (!totalRowIdx && /tổng\s*cộng/i.test(bVal)) totalRowIdx = r;
                        sheet.getRow(r).eachCell(cell => {
                            if (!noteRowIdx && /^ghi chú/i.test(cellText(cell))) noteRowIdx = r;
                        });
                        if (totalRowIdx && noteRowIdx) break;
                    }
                    if (!totalRowIdx) totalRowIdx = noteRowIdx || (sheet.rowCount + 1);
                    return { hdrRow, dataStart, totalRowIdx, noteRowIdx };
                }
                const rawMap = new Map();
                if (rawLayout) {
                    for (let r = rawLayout.dataStart; r < rawLayout.totalRowIdx; r++) {
                        const label = cellText(rawWs.getRow(r).getCell(2)).trim();
                        if (label) rawMap.set(normName(label), rawWs.getRow(r));
                    }
                    if (rawLayout.totalRowIdx) rawMap.set('__total__', rawWs.getRow(rawLayout.totalRowIdx));
                }

                const neededRows = dataRows.length;
                const availableRows = totalRowIdx - dataStart;
                if (neededRows > availableRows) {
                    shiftTemplateRows(ws, totalRowIdx, neededRows - availableRows);
                    totalRowIdx += neededRows - availableRows;
                    if (noteRowIdx) noteRowIdx += neededRows - availableRows;
                }

                for (let r = dataStart; r < totalRowIdx; r++) {
                    const row = ws.getRow(r);
                    for (let c = 1; c <= tableEndCol; c++) {
                        const cell = row.getCell(c);
                        if (!cell.isMerged || cell.master.address === cell.address) cell.value = null;
                        clearCellNote(cell);
                    }
                }

                const toNumber = value => {
                    if (value == null || value === '') return null;
                    if (value && value.richText) value = value.richText.map(part => part.text).join('');
                    if (value && value.formula) value = value.result;
                    if (typeof value === 'number') return isNaN(value) ? null : value;
                    const text = String(value).replace(/\s+/g, '').trim();
                    if (!text) return null;
                    const n = (typeof BQPNormalization !== 'undefined' && BQPNormalization.number) ? BQPNormalization.number(text) : Number(text.replace(/[.,]/g, ''));
                    return n == null || isNaN(n) ? null : n;
                };
                const rawText = value => {
                    if (value == null || value === '') return '';
                    if (value && value.richText) return value.richText.map(part => part.text).join('').trim();
                    if (value && value.formula) value = value.result;
                    return String(value).trim();
                };
                const fmtCount = value => BQPValidation.fmtMoney(Math.round(Number(value || 0)));
                const setCheckedCount = (cell, rawCell, expected, font, alignment = {}) => {
                    const rawVal = rawCell ? rawCell.value : null;
                    const rawNum = toNumber(rawVal);
                    const rawStr = rawText(rawVal);
                    const expNum = Number(expected || 0);
                    const hasDeclared = rawNum != null || rawStr !== '';
                    const hasDiff = hasDeclared && (rawNum != null ? Math.round(rawNum) !== Math.round(expNum) : rawStr !== String(Math.round(expNum || 0)));
                    if (hasDiff) {
                        cell.value = {
                            richText: [
                                { text: (rawNum != null ? fmtCount(rawNum) : rawStr) + '\n', font: { name: 'Times New Roman', size: 9.5, strike: true, color: { argb: 'FFDC2626' } } },
                                { text: fmtCount(expNum), font: { name: 'Times New Roman', size: 11, bold: true, strike: false, color: { argb: 'FF000000' } } }
                            ]
                        };
                        cell.alignment = { horizontal: 'right', vertical: 'middle', wrapText: true, ...alignment };
                        return true;
                    }
                    cell.value = expNum || null;
                    cell.numFmt = numFmt;
                    cell.alignment = { horizontal: 'right', vertical: 'middle', wrapText: true, ...alignment };
                    cell.font = clone(font);
                    return false;
                };

                const totals = mkRow();
                const hasUnitRows = dataRows.some(r => !r._isMonth && !r._isSeparator);
                for (let i = 0; i < dataRows.length; i++) {
                    const r = dataStart + i;
                    const d = dataRows[i];
                    const row = ws.getRow(r);

                    if (d._isSeparator) {
                        row.height = 28;
                        row.getCell(1).value = null;
                        row.getCell(2).value = d.label;
                        row.getCell(2).font = { name: 'Times New Roman', size: 13, bold: true, italic: true };
                        row.getCell(2).alignment = { horizontal: 'left', vertical: 'middle' };
                        for (let c = 1; c <= tableEndCol; c++) {
                            row.getCell(c).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFDBE5F1' } };
                            row.getCell(c).border = clone(borderAll);
                        }
                        continue;
                    }

                    const rawRow = rawMap.get(normName(d.label));
                    row.height = 25.15;
                    const total = d.i1 + d.i2 + d.i3;
                    let rowHasDiff = false;

                    row.getCell(1).value = d.tt;
                    row.getCell(1).font = clone(fontBold13);
                    row.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                    row.getCell(1).border = clone(borderAll);

                    row.getCell(2).value = d.label;
                    row.getCell(2).font = clone(fontBold13);
                    row.getCell(2).alignment = { horizontal: 'left', vertical: 'middle', wrapText: true };
                    row.getCell(2).border = clone(borderAll);

                    for (const [c, v] of [[3, d.i1], [4, d.i2], [5, d.i3], [6, total]]) {
                        const cell = row.getCell(c);
                        rowHasDiff = setCheckedCount(cell, rawRow ? rawRow.getCell(c) : null, v, fontBold13) || rowHasDiff;
                        cell.border = clone(borderAll);
                    }
                    for (const [key, c] of rankColumns) {
                        const cell = row.getCell(c);
                        rowHasDiff = setCheckedCount(cell, rawRankCell(rawRow, key, c), d.ranks[c] || 0, fontNormal11) || rowHasDiff;
                        cell.border = clone(borderAll);
                    }
                    row.height = rowHasDiff ? Math.max(36, row.height || 0) : row.height;

                    if (!d._isMonth || !hasUnitRows) {
                        totals.i1 += d.i1;
                        totals.i2 += d.i2;
                        totals.i3 += d.i3;
                        for (const c of rankColumns.values()) totals.ranks[c] += d.ranks[c];
                    }
                }

                for (let r = dataStart + neededRows; r < totalRowIdx; r++) {
                    const row = ws.getRow(r);
                    for (let c = 1; c <= tableEndCol; c++) {
                        const cell = row.getCell(c);
                        cell.value = null;
                        cell.border = {};
                    }
                }

                const tRow = ws.getRow(totalRowIdx);
                const rawTotalRow = rawMap.get('__total__');
                tRow.height = 25.15;
                const grandTotal = totals.i1 + totals.i2 + totals.i3;
                let totalHasDiff = false;
                tRow.getCell(1).value = null;
                tRow.getCell(1).font = clone(fontBold13);
                tRow.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                tRow.getCell(1).border = clone(borderAll);
                tRow.getCell(2).value = 'TỔNG CỘNG';
                tRow.getCell(2).font = clone(fontBold13);
                tRow.getCell(2).alignment = { horizontal: 'center', vertical: 'middle' };
                tRow.getCell(2).border = clone(borderAll);
                for (const [c, v] of [[3, totals.i1], [4, totals.i2], [5, totals.i3], [6, grandTotal]]) {
                    const cell = tRow.getCell(c);
                    totalHasDiff = setCheckedCount(cell, rawTotalRow ? rawTotalRow.getCell(c) : null, v, fontBold13) || totalHasDiff;
                    cell.border = clone(borderAll);
                }
                for (const [key, c] of rankColumns) {
                    const cell = tRow.getCell(c);
                    totalHasDiff = setCheckedCount(cell, rawRankCell(rawTotalRow, key, c), totals.ranks[c] || 0, fontBold13) || totalHasDiff;
                    cell.border = clone(borderAll);
                }
                tRow.height = totalHasDiff ? Math.max(36, tRow.height || 0) : tRow.height;

                if (exportContext?.selectedUnitId && Array.isArray(exportContext?.units)) {
                    const selUnit = exportContext.units.find(u => String(u.id) === String(exportContext.selectedUnitId));
                    if (selUnit) {
                        ws.getCell('A2').value = selUnit.name || 'Đơn vị';
                        if (selUnit.parentId) {
                            const parentUnit = exportContext.units.find(u => String(u.id) === String(selUnit.parentId));
                            if (parentUnit) ws.getCell('A1').value = parentUnit.name || 'Đơn vị cấp trên';
                        }
                    }
                }

                if (uncounted.size > 0) {
                    const reasonCol = Math.min(7, tableEndCol);
                    const safeMerge = (r, c1, c2) => {
                        if (c2 <= c1) return;
                        try { ws.mergeCells(r, c1, r, c2); } catch (mergeErr) { /* vùng đã gộp sẵn trong mẫu */ }
                    };
                    let r = (noteRowIdx || totalRowIdx) + 2;
                    // Mẫu còn sót vùng gộp rỗng bên dưới (VD C23:E23, C29:E29): gỡ để không vỡ bảng danh sách.
                    const listEnd = r + 1 + uncounted.size;
                    (ws.model.merges || []).forEach(range => {
                        const [a, b = a] = String(range).split(':');
                        const top = ws.getCell(a).row, bottom = ws.getCell(b).row;
                        if (bottom >= r && top <= listEnd) {
                            try { ws.unMergeCells(range); } catch (unmergeErr) { /* bỏ qua */ }
                        }
                    });
                    const titleRow = ws.getRow(r);
                    titleRow.getCell(1).value = `Danh sách hồ sơ không thống kê được (${uncounted.size} người, chưa tính vào dòng TỔNG CỘNG) — cần rà soát, sửa dữ liệu`;
                    titleRow.getCell(1).font = { name: 'Times New Roman', size: 13, bold: true, color: { argb: 'FFC00000' } };
                    titleRow.getCell(1).alignment = { horizontal: 'left', vertical: 'middle' };
                    safeMerge(r, 1, tableEndCol);
                    titleRow.height = 22;
                    r++;

                    const headers = ['TT', 'Họ và tên', 'Đơn vị', 'Phụ lục / dòng gốc', 'Cấp bậc', 'Thời điểm nghỉ', 'Lý do không thống kê được'];
                    const headerRow = ws.getRow(r);
                    for (let c = 1; c <= tableEndCol; c++) {
                        const cell = headerRow.getCell(c);
                        cell.value = c <= reasonCol ? headers[c - 1] : null;
                        cell.font = { name: 'Times New Roman', size: 11, bold: true };
                        cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
                        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFFFF3CD' } };
                        cell.border = clone(borderAll);
                    }
                    safeMerge(r, reasonCol, tableEndCol);
                    headerRow.height = 30;
                    r++;

                    let tt = 0;
                    for (const { rec, type, reasons } of uncounted.values()) {
                        const unitName = rec.donVi || rec.unitName || exportContext?.units?.find(u => String(u.id) === String(rec.unitId))?.name || '';
                        const srcRow = rec.rowIndex ?? rec.sourceRow;
                        const values = [
                            ++tt,
                            rec.hoTen || rec.fullName || rec.input?.hoTen || '',
                            unitName,
                            'Phụ lục ' + type + (srcRow != null ? ' – dòng ' + srcRow : ''),
                            rec.capBac || rec.input?.capBac || '',
                            rec.thoiDiemNghi || rec.retirementDate || rec.input?.thoiDiemNghi || '',
                            reasons.join('; ')
                        ];
                        const row = ws.getRow(r);
                        for (let c = 1; c <= tableEndCol; c++) {
                            const cell = row.getCell(c);
                            cell.value = c <= reasonCol ? values[c - 1] : null;
                            cell.numFmt = '@';
                            cell.font = clone(fontNormal11);
                            cell.alignment = { horizontal: [1, 4, 5, 6].includes(c) ? 'center' : 'left', vertical: 'middle', wrapText: true };
                            cell.border = clone(borderAll);
                        }
                        safeMerge(r, reasonCol, tableEndCol);
                        row.height = 30;
                        r++;
                    }
                }
            }

            // Sheet Phụ lục I: Cập nhật tổng hợp toàn bộ hồ sơ thẩm định (2 giá trị cùng ô nếu sai lệch)
            if (templatePl1) {
                const existingPl1 = exportWb.getWorksheet('Phụ lục I');
                buildSummarySheet(exportWb, templatePl1, 'I', '', recordsToExport, existingPl1, level2Resolver);
            }
            buildThongKeSheet(recordsToExport);

            if (!exportContext?.rawMode) {
                        // Với mỗi nhóm (correct/over/under), xử lý sheet tổng hợp và các phụ lục chi tiết
                for (const grp of subGroups) {
                    if (templatePl1) {
                        buildSummarySheet(exportWb, templatePl1, grp.prefix, grp.label, grp.recs, null, isLevel1Export ? level2Resolver : null);
                    }

                    const subNums = ['1', '2', '3'];
                    for (const subNum of subNums) {
                        const sType = 'I.' + subNum;
                        const targetSheetName = 'Phụ lục ' + grp.prefix + '.' + subNum;
                        let destWs = exportWb.getWorksheet(targetSheetName);

                        if (!destWs) {
                            const srcWs = reportTemplate.getWorksheet('Phụ lục I.' + subNum);
                            if (!srcWs) throw new Error('File mẫu thiếu phụ lục chi tiết I.' + subNum + '.');
                            destWs = exportWb.addWorksheet(targetSheetName);
                            const model = clone(templateModel.worksheets.find(sheet => sheet.name === srcWs.name));
                            model.id = destWs.id;
                            model.name = targetSheetName;
                            model.mergeCells = model.merges || [];
                            destWs.model = model;
                            restoreModelStyles(destWs, model);
                            destWs.getCell('C1').value = targetSheetName;
                        }

                        if (destWs) {
                            const recsForThisChild = grp.recs.filter(r => recordType(r) === sType);
                            populateChildSheet(destWs, sType, recsForThisChild);


                        }
                    }
                }

                // Tạo sheet "Danh sách lỗi" nếu có lỗi thẩm định
                const errorsList = [];
                recordsToExport.forEach((rec, idx) => {
                    if (rec.cellErrors && Object.keys(rec.cellErrors).length > 0) {
                        for (let colIdx in rec.cellErrors) {
                            errorsList.push({
                                stt: errorsList.length + 1,
                                sheet: rec.sheet || rec.sheetName || 'N/A',
                                row: rec.rowIndex || (idx + 10),
                                col: 'Cột ' + colIdx,
                                name: rec.hoTen || rec.fullName || rec.name || rec.C2 || rec.C3 || '',
                                errorType: 'Lỗi ô dữ liệu',
                                description: rec.cellErrors[colIdx]
                            });
                        }
                    }
                    if (rec.errorDetails && rec.errorDetails.length > 0) {
                        rec.errorDetails.forEach(errDesc => {
                            errorsList.push({
                                stt: errorsList.length + 1,
                                sheet: rec.sheet || rec.sheetName || 'N/A',
                                row: rec.rowIndex || (idx + 10),
                                col: 'Thẩm định',
                                name: rec.hoTen || rec.fullName || rec.name || rec.C2 || rec.C3 || '',
                                errorType: 'Sai lệch thẩm định',
                                description: errDesc
                            });
                        });
                    }
                });

                if (errorsList.length > 0) {
                    const errSheet = exportWb.addWorksheet('Danh sách lỗi');
                    errSheet.getRow(1).values = ['STT', 'Tên Sheet', 'Dòng', 'Cột', 'Họ tên', 'Loại lỗi', 'Mô tả chi tiết'];
                    errSheet.getRow(1).font = { bold: true };
                    errSheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFD9E1F2' } };

                    errorsList.forEach((err, i) => {
                        errSheet.getRow(i + 2).values = [
                            err.stt, err.sheet, err.row, err.col, err.name, err.errorType, err.description
                        ];
                    });

                    errSheet.getColumn(1).width = 5;
                    errSheet.getColumn(2).width = 15;
                    errSheet.getColumn(3).width = 8;
                    errSheet.getColumn(4).width = 8;
                    errSheet.getColumn(5).width = 25;
                    errSheet.getColumn(6).width = 15;
                    errSheet.getColumn(7).width = 50;
                }
            } else {
                // rawMode: giữ sheet tổng hợp/thống kê và 6 sheet I, I.1-I.5; xóa tất cả còn lại
                const keepSheets = new Set(['Thống kê', 'Tổng hợp', 'Phụ lục I', 'Phụ lục I.1', 'Phụ lục I.2', 'Phụ lục I.3', 'Phụ lục I.4', 'Phụ lục I.5']);
                const sheetsToRemove = [];
                exportWb.eachSheet(ws => { if (!keepSheets.has(ws.name)) sheetsToRemove.push(ws); });
                sheetsToRemove.forEach(ws => exportWb.removeWorksheet(ws.id));
            }

            // Sắp xếp thứ tự các sheet theo chuẩn logic báo cáo
            const sheetPriority = exportContext?.rawMode
                ? ['Thống kê', 'Tổng hợp', 'Phụ lục I', 'Phụ lục I.1', 'Phụ lục I.2', 'Phụ lục I.3', 'Phụ lục I.4', 'Phụ lục I.5']
                : [
                    'Thống kê', 'Tổng hợp',
                    'Phụ lục I', 'Phụ lục I.1', 'Phụ lục I.2', 'Phụ lục I.3',
                    'Phụ lục II', 'Phụ lục II.1', 'Phụ lục II.2', 'Phụ lục II.3',
                    'Phụ lục III', 'Phụ lục III.1', 'Phụ lục III.2', 'Phụ lục III.3',
                    'Phụ lục IV', 'Phụ lục IV.1', 'Phụ lục IV.2', 'Phụ lục IV.3',
                    'Đối chiếu nguồn', 'Danh sách lỗi'
                ];
            let currentOrder = 1;
            sheetPriority.forEach(shName => {
                const s = exportWb.getWorksheet(shName);
                if (s) s.orderNo = currentOrder++;
            });
            exportWb.eachSheet(s => {
                if (!sheetPriority.includes(s.name)) {
                    s.orderNo = currentOrder++;
                }
            });

            // Đảm bảo cấu trúc file 100% chuẩn OpenXML (ECMA-376) cho Microsoft Excel
            exportWb.eachSheet((ws) => {
                if (ws.properties) {
                    ws.properties.outlineProperties = undefined;
                }
                ws.eachRow({ includeEmpty: false }, row => {
                    row.eachCell({ includeEmpty: false }, cell => {
                        clearCellNote(cell);
                    });
                });
            });

            sanitizeSharedFormulas(exportWb);
            let outBuffer;
            try {
                outBuffer = await exportWb.xlsx.writeBuffer();
            } catch (writeErr) {
                console.warn("Phát hiện lỗi khi đóng gói file Excel. Đang tự động đánh dấu ô/dòng lỗi và phục hồi dữ liệu xuất...", writeErr);

                // 1. Nếu lỗi chỉ đích danh địa chỉ ô (ví dụ 'for cell N27', 'cell X99')
                const cellMatches = writeErr.message ? writeErr.message.match(/cell\s+([A-Z]+[0-9]+)/gi) : null;
                if (cellMatches) {
                    cellMatches.forEach(cm => {
                        const m = cm.match(/([A-Z]+[0-9]+)/i);
                        if (m) {
                            const badAddr = m[1].toUpperCase();
                            exportWb.eachSheet(ws => {
                                try {
                                    const c = ws.getCell(badAddr);
                                    if (c) {
                                        markCellExportError(c, writeErr, c.value?.result != null ? c.value.result : '[LỖI CÔNG THỨC]');
                                    }
                                } catch(e) {}
                            });
                        }
                    });
                }

                // 2. Chuyển đổi an toàn tuyệt đối: Quét toàn bộ workbook, biến toàn bộ công thức hỏng thành giá trị tĩnh
                exportWb.eachSheet(ws => {
                    ws.eachRow({ includeEmpty: true }, (row) => {
                        row.eachCell({ includeEmpty: true }, (cell) => {
                            try {
                                if (cell.value && typeof cell.value === 'object') {
                                    if (cell.value.formula || cell.value.sharedFormula) {
                                        cell.value = cell.value.result != null ? cell.value.result : '';
                                    }
                                }
                            } catch(e) {}
                        });
                    });
                });

                // Thử ghi buffer lần 2
                try {
                    outBuffer = await exportWb.xlsx.writeBuffer();
                    console.log("Đã tự động khắc phục và xuất file thành công sau khi đánh dấu các ô lỗi!");
                } catch (secondErr) {
                    console.warn("Thử xuất lần 3 (Lược bỏ thành phần bổ trợ gây xung đột):", secondErr);
                    exportWb.eachSheet(ws => {
                        try { delete ws._drawings; } catch(e) {}
                        try { delete ws._comments; } catch(e) {}
                        try { ws.views = []; } catch(e) {}
                    });
                    outBuffer = await exportWb.xlsx.writeBuffer();
                }
            }
            const blob = new Blob([outBuffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;

            // Thêm nhãn cấp khi xuất báo cáo cấp 1
            let unitLabel = '';
            if (exportContext?.summaryLevel === 2 && exportContext?.selectedUnitId) {
                const selectedUnit = exportContext.units?.find(u => u.id === exportContext.selectedUnitId);
                if (selectedUnit && selectedUnit.name) {
                    const cleanName = selectedUnit.name.trim()
                        .replace(/[<>:"/\\|?*]/g, '_')
                        .replace(/\s+/g, '_')
                        .substring(0, 30);
                    unitLabel = `_${cleanName}_cap1`;
                } else {
                    unitLabel = '_cap1';
                }
            }

            let fileName;
            if (exportContext?.rawMode) {
                fileName = `${baseName}_RAW_chua_tham_dinh.xlsx`;
            } else {
                fileName = (isFiltered && recordsToExport.length < allRecs.length)
                    ? `${baseName}_Tham_dinh_${suffix}${unitLabel}_${recordsToExport.length}dc.xlsx`
                    : `${baseName}_tham_dinh_BQP${unitLabel}.xlsx`;
            }
            a.download = fileName;
            a.click();
            URL.revokeObjectURL(url);

            if (typeof notifySuccess === 'function') {
                notifySuccess('Đã tạo và tải file Excel báo cáo thẩm định thành công.');
            }

            return { success: true, fileName, buffer: outBuffer };
        } catch (e) {
            console.error("Lỗi xuất Excel:", e);
            if (typeof notifyError === 'function') {
                notifyError("Có lỗi khi tạo file Excel: " + e.message);
            }
            throw e;
        } finally {
            if (typeof BQPLoader !== 'undefined') {
                BQPLoader.hide();
            }
        }
    }

    return {
        LCS,
        CellStatus,
        MilitaryRankHelper,
        PLI1Calculator,
        PLI2Calculator,
        PLI3Calculator,
        PLI5Calculator,
        ExcelParser,
        ValidationService,
        fmtMoney,
        fmtNum,
        formatDate,
        calcThang,
        calcNamLamTron,
        isEqual,
        isEqualTime,
        readNumberToVietnameseWords,
        normalizeErrorDetails,
        ensureRecordValidationDetail,
        buildRawExcelComparisons,
        buildLevel2Resolver,
        exportValidatedWorkbook
    };
})();

// A report template is loaded separately from the workbook uploaded for validation.
// Both export entry points share this promise; exports clone the verified workbook.
window.BQPReportTemplate = window.BQPReportTemplate || {
    fileName: 'PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx',
    fallbackSources: [],
    _loadPromise: null,

    validate(workbook) {
        const parser = BQPValidation.ExcelParser;
        const sheetKey = name => String(name || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/^(?:phu luc|pl)\s*/i, '')
            .replace(/\s+/g, '').toUpperCase();
        const sheets = new Map(workbook.worksheets.map(sheet => [sheetKey(sheet.name), sheet]));
        const sizes = { 'I.1': 23, 'I.2': 18, 'I.3': 15 };
        for (const [key, size] of Object.entries(sizes)) {
            const sheet = sheets.get(key);
            if (!sheet) throw new Error(`Template thiếu sheet ${key}.`);
            const { colMap, headerRowIdx } = parser.findColMap(sheet);
            if (headerRowIdx < 1) throw new Error(`Template: không nhận diện được tiêu đề sheet ${key}.`);
            for (let column = 1; column <= size; column++) {
                const physicalColumn = colMap[column];
                const text = physicalColumn ? parser.getCellText(sheet.getRow(headerRowIdx).getCell(physicalColumn)) : '';
                const number = text.match(/^[\(\[]?(\d{1,2})(?:[\)\]]|\s|=|$)/);
                if (!number || Number(number[1]) !== column) {
                    throw new Error(`Template: sheet ${key} thiếu cột ${column}.`);
                }
            }
        }
        for (const key of ['I', 'II', 'III', 'IV']) {
            const sheet = sheets.get(key);
            if (!sheet) throw new Error(`Template thiếu sheet tổng hợp ${key}.`);
            let hasColumns = false;
            for (let rowNumber = 1; rowNumber <= Math.min(15, sheet.rowCount); rowNumber++) {
                const labels = new Set();
                sheet.getRow(rowNumber).eachCell(cell => {
                    const text = parser.getCellText(cell).trim().toUpperCase();
                    const number = text.match(/^[\(\[]?(\d{1,2})(?:[\)\]]|\s|=|$)/);
                    labels.add(number ? String(Number(number[1])) : text);
                });
                if (['A', 'B', '1', '2', '3', '4', '5', '6', '7', '8'].every(label => labels.has(label))) {
                    hasColumns = true;
                    break;
                }
            }
            if (!hasColumns) throw new Error(`Template: sheet tổng hợp ${key} thiếu cột tên đơn vị hoặc cột kinh phí.`);
        }
        return workbook;
    },

    load() {
        if (!this._loadPromise) {
            this._loadPromise = (async () => {
                if (typeof ExcelJS === 'undefined') throw new Error('Thư viện ExcelJS chưa sẵn sàng.');
                const sources = ['/' + encodeURIComponent(this.fileName), ...this.fallbackSources];
                const failures = [];
                for (const source of sources) {
                    try {
                        const response = await fetch(source, { cache: 'no-cache' }); // luôn lấy file mẫu mới nhất sau khi cập nhật
                        if (!response.ok) throw new Error(`HTTP ${response.status}`);
                        const workbook = new ExcelJS.Workbook();
                        await workbook.xlsx.load(await response.arrayBuffer());
                        this.validate(workbook);
                        workbook.__bqpReportTemplate = true;
                        workbook.__bqpReportTemplateSource = source;
                        return { workbook, source, fileName: decodeURIComponent(source.slice(1)) };
                    } catch (error) {
                        failures.push(`${decodeURIComponent(source.slice(1))}: ${error.message}`);
                    }
                }
                throw new Error(`Không thể tải template tương thích. ${failures.join(' | ')}`);
            })().catch(error => {
                this._loadPromise = null;
                throw error;
            });
        }
        return this._loadPromise;
    },

    async createExportContext(context = null) {
        const template = await this.load();
        return {
            ...(context || {}),
            reportTemplateWorkbook: template.workbook,
            reportTemplateSource: template.source,
            reportTemplateVerified: true
        };
    }
};

window.CellStatus = BQPValidation.CellStatus;
window.ExcelParser = BQPValidation.ExcelParser;
_bqpGlobal.CellStatus = BQPValidation.CellStatus;
_bqpGlobal.ExcelParser = BQPValidation.ExcelParser;
  window.normalizeErrorDetails = BQPValidation.normalizeErrorDetails;
  window.ensureRecordValidationDetail = BQPValidation.ensureRecordValidationDetail;
  _bqpGlobal.normalizeErrorDetails = BQPValidation.normalizeErrorDetails;
  _bqpGlobal.ensureRecordValidationDetail = BQPValidation.ensureRecordValidationDetail;

// ============================================================
// GIAO DIỆN THẨM ĐỊNH VÀ ĐỐI CHIẾU DỮ LIỆU (UI CONTROLLER)
// ============================================================
window.ValidationUI = {
    currentFile: null,
    currentResult: null,
    currentRecords: [],
    currentFiltered: [],
    currentModalIndex: -1,

    crossCheckI5Salary(allRecords) {
        if (!allRecords || !allRecords.length) return;
        const i5Records = allRecords.filter(r => r.sheet === 'I.5');
        if (!i5Records.length) return;

        const cleanStr = s => BQPNormalization.clean(s || '').toLowerCase();
        const parseAmt = val => {
            if (typeof val === 'number') return isNaN(val) ? 0 : val;
            if (!val) return 0;
            return BQPNormalization.number(val) || 0;
        };

        const detailRecords = allRecords.filter(r => ['I.1', 'I.2', 'I.3'].includes(r.sheet));

        detailRecords.forEach(rec => {
            const recName = cleanStr(rec.hoTen);
            if (!recName || recName.startsWith('[lỗi')) return;

            const recUnit = cleanStr(rec.donVi || rec.unitName || rec.group);
            const candidates = i5Records.filter(i5 => {
                const i5Name = cleanStr(i5.hoTen);
                if (i5Name !== recName) return false;
                const i5Unit = cleanStr(i5.donVi || i5.unitName || i5.group);
                if (recUnit && i5Unit && !recUnit.includes(i5Unit) && !i5Unit.includes(recUnit)) {
                    // Unit differs
                }
                return true;
            });

            if (candidates.length === 1) {
                const matchedI5 = candidates[0];
                const fmt = BQPValidation.fmtMoney;
                const i5Declared = (matchedI5.rawCols && matchedI5.rawCols[22] != null)
                    ? parseAmt(matchedI5.rawCols[22])
                    : parseAmt(matchedI5.tongTienThucTe);
                // Chuẩn là C22 do BQP tính lại từ I.5; chỉ dùng số kê khai khi I.5 chưa đủ dữ liệu để tính
                const i5Recalc = (matchedI5.valRes && !matchedI5.valRes.isIncomplete && matchedI5.tongTienTinhLai > 0)
                    ? Math.round(matchedI5.tongTienTinhLai) : 0;
                const i5Salary = i5Recalc || i5Declared;

                let c9Declared = 0;
                if (rec.rawCols && rec.rawCols[9] != null) {
                    c9Declared = parseAmt(rec.rawCols[9]);
                } else if (rec.monthlySalary != null) {
                    c9Declared = parseAmt(rec.monthlySalary);
                } else if (rec.luongThang != null) {
                    c9Declared = parseAmt(rec.luongThang);
                }

                if (i5Salary > 0 && c9Declared > 0 && Math.round(c9Declared) !== Math.round(i5Salary)) {
                    const i5Desc = i5Recalc
                        ? `Cột 22 Phụ lục I.5 chuẩn tính lại ${fmt(i5Recalc)} đ` + (Math.round(i5Declared) !== i5Recalc ? ` (file I.5 ghi ${fmt(i5Declared)} đ)` : '')
                        : `Cột 22 Phụ lục I.5 ${fmt(i5Declared)} đ`;
                    const errStr = `Cột 9 (Lương tháng hiện hưởng): File ghi ${fmt(c9Declared)} đ, khác ${i5Desc}.`;
                    const compC9 = {
                        col: 'Cột 9',
                        title: 'Lương tháng hiện hưởng (đối chiếu Cột 22 Phụ lục I.5)',
                        actual: fmt(c9Declared) + ' đ',
                        expected: fmt(i5Salary) + ' đ',
                        diff: `${i5Salary - c9Declared > 0 ? '+' : ''}${fmt(i5Salary - c9Declared)} đ`,
                        hasErr: true,
                        formula: i5Recalc ? 'Cột 22 Phụ lục I.5 = SUM(Cột 13 : Cột 21) theo chuẩn' : 'Cột 22 Phụ lục I.5 (kê khai)'
                    };

                    // Tính lại I.1/I.2/I.3 theo lương chuẩn; giữ nguyên rawCols[9] là số kê khai gốc
                    rec.luongThang = i5Salary;
                    rec.monthlySalary = i5Salary;
                    const reVal = BQPValidation.ValidationService.validateRow(rec.sheet, rec);
                    if (reVal && reVal.exp) {
                        rec.valRes = reVal;
                        rec.expected = reVal.exp;
                        rec.tongTienTinhLai = reVal.expectedTotal;
                        rec.diff = reVal.expectedTotal - (rec.tongTienThucTe || 0);
                        rec.comparisons = [compC9, ...(reVal.comparisons || []).filter(c => c.col !== 'Cột 9')];
                        rec.errorDetails = [errStr, ...(reVal.errorDetails || [])];
                    } else {
                        rec.comparisons = [compC9, ...(rec.comparisons || []).filter(c => c.col !== 'Cột 9')];
                        rec.errorDetails = [errStr, ...(rec.errorDetails || [])];
                    }
                    rec.hasErrors = true;
                }
            } else if (candidates.length > 1) {
                rec.errorDetails = rec.errorDetails || [];
                const warnStr = 'Không đủ căn cứ ghép lương I.5 (Có nhiều hơn 1 bản ghi I.5 trùng tên)';
                if (!rec.errorDetails.includes(warnStr)) rec.errorDetails.push(warnStr);
            }
        });
    },

    async handleFileUpload(event) {
        let file = null;
        if (typeof File !== 'undefined' && event instanceof File) {
            file = event;
        } else if (typeof Blob !== 'undefined' && event instanceof Blob) {
            file = event;
        } else if (event && event.target && event.target.files) {
            file = event.target.files[0];
        } else if (event && event.dataTransfer && event.dataTransfer.files) {
            file = event.dataTransfer.files[0];
        }
        if (!file) return;

        let loadingEl = document.getElementById("validate_loading");
        let dropzone = document.getElementById("validate_dropzone");
        if (loadingEl) loadingEl.style.display = "block";
        if (dropzone) dropzone.style.display = "none";

        if (typeof BQPLoader !== 'undefined') {
            BQPLoader.show('Đang nạp file Excel...', 'Hệ thống đang tải dữ liệu và phân tích cấu trúc các bảng tính...', 'Bước 1/3: Nạp tệp tin');
            await new Promise(r => setTimeout(r, 60));
        }

        try {
            this.currentFile = file;
            this.readReport = null;
            this.renderReadReport();
            const arrayBuffer = await file.arrayBuffer();

            // 1. Lập chỉ mục XML trực tiếp từ file nạp gốc để phân biệt chính xác ô có/không có <v>
            let xmlIndex = {};
            try {
                xmlIndex = BQPValidation.ExcelParser.buildXmlFormulaIndex(arrayBuffer);
            } catch (xmlErr) {
                console.warn('[BQPValidation] buildXmlFormulaIndex error:', xmlErr);
            }

            let workbook = new ExcelJS.Workbook();
            await workbook.xlsx.load(arrayBuffer);
            workbook._xmlIndex = xmlIndex;
            this.rawWb = workbook;

            // 2. Báo cáo công thức file nguồn (sourceFormulaReport) TRƯỚC KHI chuẩn hóa
            try {
                this.sourceFormulaReport = BQPValidation.ExcelParser.generateFormulaReport(workbook, { xmlIndex });
            } catch (fSrcErr) {
                console.warn('[BQPValidation] Không thể tạo báo cáo công thức nguồn:', fSrcErr);
            }

            // Tự động nhận diện và chuẩn hóa cấu trúc về Biểu mẫu 26.9 chuẩn
            let hasI123 = false;
            workbook.eachSheet((ws) => {
                let st = BQPNormalization.sheetType(ws);
                if (st === 'I.1' || st === 'I.2' || st === 'I.3') hasI123 = true;
            });

            let isNormalized = false;
            let normCounts = null;

            if (hasI123 && typeof BQPNormalization !== 'undefined' && BQPNormalization.normalizeWorkbook) {
                if (typeof BQPLoader !== 'undefined') {
                    BQPLoader.update('Đang chuẩn hóa cấu trúc biểu mẫu...', 'Tự động đối soát và khớp biểu mẫu 26.9 chuẩn...', 'Bước 2/3: Chuẩn hóa');
                    await new Promise(r => setTimeout(r, 40));
                }
                try {
                    const normRes = await BQPNormalization.normalizeWorkbook(workbook, ExcelJS);
                    if (normRes && normRes.workbook) {
                        workbook = normRes.workbook;
                        workbook._xmlIndex = xmlIndex; // Kế thừa xmlIndex
                        isNormalized = true;
                        normCounts = normRes.counts;
                    }
                } catch (normErr) {
                    console.warn("Tự động chuẩn hóa bỏ qua (tiếp tục với file gốc):", normErr);
                }
            }

            this.currentWb = workbook;
            this.isNormalized = isNormalized;
            this.normCounts = normCounts;

            // 3. Báo cáo công thức sau chuẩn hóa và kết luận tổng thể
            try {
                this.normalizedFormulaReport = BQPValidation.ExcelParser.generateFormulaReport(workbook, { xmlIndex });
                this.currentFormulaReport = this.sourceFormulaReport || this.normalizedFormulaReport;
                if (this.currentFormulaReport && (this.currentFormulaReport.errorFormulas > 0 || this.currentFormulaReport.noResultFormulas > 0)) {
                    console.warn(`[BQPValidation] Phát hiện ${this.currentFormulaReport.errorFormulas} ô công thức lỗi và ${this.currentFormulaReport.noResultFormulas} ô công thức chưa tính kết quả.`);
                    this.showFormulaWarningBanner(this.currentFormulaReport);
                } else {
                    this.hideFormulaWarningBanner();
                }
            } catch (fRepErr) {
                console.warn('[BQPValidation] Không thể tạo báo cáo công thức:', fRepErr);
            }

            if (typeof BQPLoader !== 'undefined' && typeof BQPLoader.update === 'function') {
                BQPLoader.update('Đang thẩm định dữ liệu chính sách...', 'Kiểm tra chi tiết từng chế độ theo Nghị định 178 & Nghị định 177...', 'Bước 3/3: Thẩm định');
                await new Promise(r => setTimeout(r, 40));
            }

            let allRecords = [];
            let sheetStats = { i1: 0, i2: 0, i3: 0, i5: 0 };
            const readAudit = BQPNormalization.createReadAudit();

            workbook.eachSheet((worksheet) => {
                let type = BQPNormalization.sheetType(worksheet);

                if (!['I.1', 'I.2', 'I.3', 'I.5'].includes(type)) return;

                const { colMap, headerRowIdx } = BQPValidation.ExcelParser.findColMap(worksheet);
                let startRow = headerRowIdx !== -1 ? headerRowIdx + 1 : (type === 'I.5' ? 6 : 11);
                let currentGroup = worksheet.name;
                let currentPolicy = (type === 'I.3' ? 'ND177' : 'ND178');
                let currentCategory = (type === 'I.3' ? 'QNCN' : 'SQ');
                let currentCategoryName = (type === 'I.3' ? 'Quân nhân chuyên nghiệp' : 'Sĩ quan');
                let currentCategoryLetter = (type === 'I.3' ? 'B' : 'A');
                let currentUnit = currentGroup;
                let currentUnitRoman = 'I';

                for (let r = startRow; r <= worksheet.rowCount; r++) {
                    try {
                        let row = worksheet.getRow(r);
                        let col2Idx = colMap[2] || 2;
                        let hoten = BQPValidation.ExcelParser.getCellText(row.getCell(col2Idx));
                        if (!hoten && colMap[1]) {
                            hoten = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[1]));
                        }
                        if (!hoten) continue;

                        let col1Idx = colMap[1] || 1;
                        let sttRaw = BQPValidation.ExcelParser.getCellText(row.getCell(col1Idx));
                        let sttNum = BQPValidation.ExcelParser.parseNumber(row.getCell(col1Idx));
                        let hasStt = (sttNum != null && sttNum > 0) || (sttRaw && /^\d+$/.test(sttRaw.trim()));

                        // Bằng chứng dữ liệu được ưu tiên hơn việc đoán theo chữ trong ô tên: dòng có STT số và có ngày/lương/cấp bậc
                        // luôn là người. Ô gộp (slave) không tính vì chỉ lặp lại giá trị của ô chính.
                        const cellTxtReal = k => {
                            const c = row.getCell(colMap[k] || k);
                            if (c.isMerged && c.master && c.master.address !== c.address) return '';
                            return BQPValidation.ExcelParser.getCellText(c);
                        };
                        const rowHasData = type === 'I.5'
                            ? (!!cellTxtReal(6) || !!cellTxtReal(9))
                            : (!!cellTxtReal(3) || !!cellTxtReal(4) || !!cellTxtReal(6) || !!cellTxtReal(9));
                        const personEvidence = BQPNormalization.isPersonEvidence(hasStt, rowHasData);

                        if (!personEvidence && BQPNormalization.isNoteRow(row, colMap)) {
                            readAudit.skip(type, r, hoten, 'Dòng ghi chú', false);
                            continue;
                        }

                        let lower = hoten.toLowerCase();
                        if (!personEvidence && BQPNormalization.isTotal(hoten)) {
                            readAudit.total(type, r, hoten);
                            readAudit.skip(type, r, hoten, 'Dòng tổng cộng của file', false);
                            continue;
                        }
                        if (!personEvidence) readAudit.heading(type, r, hoten);

                        let rowData = null;
                        let cellErrors = {};

                        // A. Bỏ qua dòng dấu chấm lửng minh họa ('…', '...')
                        if (BQPNormalization.isEllipsis(sttRaw, hoten)) continue;

                        // B. Nhận diện dòng khối Nghị định (Policy Level)
                        if (BQPNormalization.isPolicy(hoten) || BQPNormalization.isPolicy(sttRaw)) {
                            let pTxt = hoten || sttRaw;
                            if (pTxt.includes('177')) currentPolicy = 'ND177';
                            else if (pTxt.includes('178')) currentPolicy = 'ND178';
                            continue;
                        }

                        if (!hoten && hasStt) {
                            hoten = '[Lỗi: Thiếu họ tên]';
                        }

                        if (type === 'I.5') {
                            let donVi = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[3] || 3));
                            let chucVu = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[4] || 4));
                            let capBac = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[5] || 5));

                            let c6 = row.getCell(colMap[6] || 6);
                            let txt6 = BQPValidation.ExcelParser.getCellText(c6);
                            let heSoLuong = 0;
                            if (txt6 && (txt6.startsWith('[Lỗi') || txt6.includes('#VALUE!') || txt6.includes('#REF!'))) {
                                cellErrors[6] = txt6;
                            } else {
                                heSoLuong = BQPValidation.ExcelParser.parseNumber(c6);
                            }

                            let hasDateOrSalary = (heSoLuong > 0) || !!cellErrors[6] || !!BQPValidation.ExcelParser.getCellText(row.getCell(colMap[9] || 9));

                            // Nhận diện dòng Nhóm đối tượng (A, B...)
                            const isCatText = BQPNormalization.isCategory(hoten);
                            const isCatLetter = BQPNormalization.isLetterCategory(sttRaw);
                            if (isCatText || (isCatLetter && !capBac && !hasDateOrSalary)) {
                                if (BQPNormalization.clean(hoten).includes('quan nhan') || BQPNormalization.clean(hoten).includes('qncn') || sttRaw.trim().toUpperCase() === 'B') {
                                    currentCategory = 'QNCN';
                                    currentCategoryName = 'Quân nhân chuyên nghiệp';
                                    currentCategoryLetter = 'B';
                                } else {
                                    currentCategory = 'SQ';
                                    currentCategoryName = 'Sĩ quan';
                                    currentCategoryLetter = 'A';
                                }
                                continue;
                            }

                            // Nhận diện dòng Đơn vị (I, II...)
                            const isRoman = BQPNormalization.isRomanNumeral(sttRaw);
                            const isUnitMarker = isRoman || /^(?:ĐV|DV)$/i.test(sttRaw.trim());
                            if (isUnitMarker && !capBac && !hasDateOrSalary && hoten && !BQPNormalization.isTotal(hoten)) {
                                currentUnit = BQPNormalization.normalizeUnitName(hoten);
                                currentUnitRoman = isRoman ? sttRaw.trim().toUpperCase() : sttRaw.trim();
                                currentGroup = currentUnit;
                                continue;
                            }

                            let isSoldier = !BQPNormalization.isCategory(hoten) && !isUnitMarker && (!!capBac || hasStt || hasDateOrSalary);
                            if (!isSoldier) {
                                if (!BQPNormalization.isCategory(hoten) && isNaN(hoten)) {
                                    currentGroup = hoten;
                                }
                                continue;
                            }

                            if (!capBac) {
                                capBac = '[Lỗi: Thiếu cấp bậc]';
                                cellErrors[5] = 'Thiếu hoặc lỗi ô Cấp bậc';
                            }
                            if (hoten.startsWith('[Lỗi')) {
                                cellErrors[2] = 'Thiếu hoặc lỗi ô Họ và tên';
                            }
                            if (heSoLuong <= 0 && !cellErrors[6]) {
                                cellErrors[6] = 'Hệ số lương phải > 0';
                            }

                            let c7 = row.getCell(colMap[7] || 7);
                            let txt7 = BQPValidation.ExcelParser.getCellText(c7);
                            let heSoChenhLechBaoLuu = 0;
                            if (txt7 && txt7.startsWith('[Lỗi')) cellErrors[7] = txt7;
                            else heSoChenhLechBaoLuu = BQPValidation.ExcelParser.parseNumber(c7);

                            let c8 = row.getCell(colMap[8] || 8);
                            let txt8 = BQPValidation.ExcelParser.getCellText(c8);
                            let heSoChucVu = 0;
                            if (txt8 && txt8.startsWith('[Lỗi')) cellErrors[8] = txt8;
                            else heSoChucVu = BQPValidation.ExcelParser.parseNumber(c8);

                            let c9 = row.getCell(colMap[9] || 9);
                            let txt9 = BQPValidation.ExcelParser.getCellText(c9);
                            let nhapNgu = BQPValidation.ExcelParser.parseDateCell(c9);
                            if (txt9 && (txt9.startsWith('[Lỗi') || txt9.includes('#VALUE!'))) {
                                cellErrors[9] = txt9;
                            } else if (txt9 && !nhapNgu) {
                                cellErrors[9] = `Lỗi định dạng ngày nhập ngũ (${txt9})`;
                            } else if (!txt9 && !nhapNgu) {
                                cellErrors[9] = 'Thiếu ngày tháng nhập ngũ';
                            }

                            let c10 = row.getCell(colMap[10] || 10);
                            let txt10 = BQPValidation.ExcelParser.getCellText(c10);
                            let thoiDiemNghi = BQPValidation.ExcelParser.parseDateCell(c10);
                            if (txt10 && (txt10.startsWith('[Lỗi') || txt10.includes('#VALUE!'))) {
                                cellErrors[10] = txt10;
                            } else if (txt10 && !thoiDiemNghi) {
                                cellErrors[10] = `Lỗi định dạng thời điểm nghỉ (${txt10})`;
                            } else if (!txt10 && !thoiDiemNghi) {
                                cellErrors[10] = 'Thiếu thời điểm nghỉ';
                            }

                            let c11 = row.getCell(colMap[11] || 11);
                            let txt11 = BQPValidation.ExcelParser.getCellText(c11);
                            let tiLePhuCapTrachNhiem = 0;
                            if (txt11 && txt11.startsWith('[Lỗi')) cellErrors[11] = txt11;
                            else tiLePhuCapTrachNhiem = BQPValidation.ExcelParser.parseNumber(c11);

                            let c12 = row.getCell(colMap[12] || 12);
                            let txt12 = BQPValidation.ExcelParser.getCellText(c12);
                            let tiLePhuCapDacThu = 0;
                            if (txt12 && txt12.startsWith('[Lỗi')) cellErrors[12] = txt12;
                            else tiLePhuCapDacThu = BQPValidation.ExcelParser.parseNumber(c12);

                            let rawCols = {};
                            for (let k = 6; k <= 22; k++) {
                                if (colMap[k]) {
                                    if (k === 9 || k === 10) {
                                        let dateVal = k === 9 ? nhapNgu : thoiDiemNghi;
                                        let txtDate = k === 9 ? txt9 : txt10;
                                        rawCols[k] = (dateVal instanceof Date && !isNaN(dateVal.getTime())) ? ((dateVal.getDate() < 10 ? '0' + dateVal.getDate() : dateVal.getDate()) + '/' + (dateVal.getMonth() + 1 < 10 ? '0' + (dateVal.getMonth() + 1) : (dateVal.getMonth() + 1)) + '/' + dateVal.getFullYear()) : (dateVal ? String(dateVal).trim() : (txtDate ? String(txtDate).trim() : null));
                                    } else {
                                        let ck = row.getCell(colMap[k]);
                                        let readk = BQPValidation.ExcelParser.readCellValue(ck, 'number', { sheetName: worksheet.name });
                                        if (readk.status === CellStatus.FORMULA_ERROR || readk.status === CellStatus.FORMULA_NO_RESULT || readk.status === CellStatus.INVALID_TYPE) {
                                            cellErrors[k] = readk.rawText || (readk.status === CellStatus.FORMULA_ERROR ? `[Lỗi: ${readk.errorCode}]` : '[Lỗi: Chưa tính]');
                                            rawCols[k] = null; // TUYỆT ĐỐI KHÔNG GÁN 0 CHO Ô LỖI HOẶC THIẾU KẾT QUẢ
                                        } else if (readk.status === CellStatus.FORMULA_EMPTY || readk.status === CellStatus.BLANK) {
                                            rawCols[k] = null;
                                        } else {
                                            rawCols[k] = readk.value;
                                        }
                                    }
                                }
                            }

                            rowData = {
                                id: 'val_' + Date.now() + '_' + Math.floor(Math.random() * 100000),
                                rowIndex: r,
                                group: donVi || currentGroup,
                                sheet: type,
                                fileName: file.name,
                                hoTen: hoten,
                                categoryCode: currentCategory || 'SQ',
                                categoryName: currentCategoryName || 'Sĩ quan',
                                unitName: currentUnit || currentGroup,
                                unitRoman: currentUnitRoman || 'I',
                                policyCode: currentPolicy || 'ND178',
                                capBac: capBac,
                                chucVu: chucVu,
                                donVi: donVi,
                                heSoLuong: heSoLuong,
                                heSoChenhLechBaoLuu: heSoChenhLechBaoLuu,
                                heSoChucVu: heSoChucVu,
                                ngaySinh: null,
                                nhapNgu: nhapNgu,
                                sapNhap: null,
                                thoiDiemNghi: thoiDiemNghi,
                                tiLePhuCapTrachNhiem: tiLePhuCapTrachNhiem,
                                tiLePhuCapDacThu: tiLePhuCapDacThu,
                                luongThang: (cellErrors[22] || rawCols[22] == null) ? null : rawCols[22],
                                rawCols: rawCols,
                                cellErrors: cellErrors
                            };
                        } else {
                            let c4 = row.getCell(colMap[4] || 4);
                            let capBac = BQPValidation.ExcelParser.getCellText(c4);
                            let c3 = row.getCell(colMap[3] || 3);
                            let txt3 = BQPValidation.ExcelParser.getCellText(c3);
                            let c9 = row.getCell(colMap[9] || 9);
                            let txt9 = BQPValidation.ExcelParser.getCellText(c9);
                            let hasDateOrMoney = !!txt3 || !!txt9 || !!BQPValidation.ExcelParser.getCellText(row.getCell(colMap[6] || 6));

                            // Nhận diện dòng Nhóm đối tượng (A, B...)
                            const isCatText2 = BQPNormalization.isCategory(hoten);
                            const isCatLetter2 = BQPNormalization.isLetterCategory(sttRaw);
                            if (isCatText2 || (isCatLetter2 && !capBac && !hasDateOrMoney)) {
                                if (BQPNormalization.clean(hoten).includes('quan nhan') || BQPNormalization.clean(hoten).includes('qncn') || sttRaw.trim().toUpperCase() === 'B') {
                                    currentCategory = 'QNCN';
                                    currentCategoryName = 'Quân nhân chuyên nghiệp';
                                    currentCategoryLetter = 'B';
                                } else {
                                    currentCategory = 'SQ';
                                    currentCategoryName = 'Sĩ quan';
                                    currentCategoryLetter = 'A';
                                }
                                continue;
                            }

                            // Nhận diện dòng Đơn vị (I, II...)
                            const isRoman2 = BQPNormalization.isRomanNumeral(sttRaw);
                            const isUnitMarker2 = isRoman2 || /^(?:ĐV|DV)$/i.test(sttRaw.trim());
                            if (isUnitMarker2 && !capBac && !hasDateOrMoney && hoten && !BQPNormalization.isTotal(hoten)) {
                                currentUnit = BQPNormalization.normalizeUnitName(hoten);
                                currentUnitRoman = isRoman2 ? sttRaw.trim().toUpperCase() : sttRaw.trim();
                                currentGroup = currentUnit;
                                continue;
                            }

                            const isMergedC4 = c4 ? c4.isMerged : false;
                            let isSoldier = !BQPNormalization.isCategory(hoten) && !isUnitMarker2 && (personEvidence || !BQPNormalization.isTotal(hoten)) && (!!capBac || personEvidence) && (!isMergedC4 || personEvidence);
                            if (!isSoldier) {
                                // Dòng có STT hoặc dữ liệu hợp lệ mà không nhận là hồ sơ: ghi lại để rà soát, không bỏ qua im lặng.
                                const nameNorm = BQPNormalization.clean(hoten);
                                const sampleRow = /^nguyen van [a-z]$/.test(nameNorm) || /^\d+$/.test(nameNorm);
                                const strongData = !!BQPValidation.ExcelParser.parseDateCell(c3) || (BQPValidation.ExcelParser.parseNumber(c9) > 0);
                                if (!sampleRow && !isUnitMarker2 && (hasStt || strongData)) {
                                    readAudit.skip(type, r, hoten, hasStt ? 'Có STT nhưng thiếu dữ liệu (ngày sinh, nhập ngũ, lương...) nên không nhận là hồ sơ' : 'Có ngày/lương nhưng thiếu STT nên không nhận là hồ sơ', true);
                                }
                                if (!BQPNormalization.isCategory(hoten) && !BQPNormalization.isTotal(hoten) && isNaN(hoten)) {
                                    currentGroup = hoten;
                                    currentUnit = hoten;
                                }
                                continue;
                            }

                            if (!capBac) {
                                capBac = '[Lỗi: Thiếu cấp bậc]';
                                cellErrors[4] = 'Thiếu hoặc lỗi ô Cấp bậc';
                            }
                            if (hoten.startsWith('[Lỗi')) {
                                cellErrors[2] = 'Thiếu hoặc lỗi ô Họ và tên';
                            }

                            // Ngày đọc được nhưng năm vô lý (VD "09/195" -> năm 195, "9/1795", "01/122025") là lỗi gõ của đơn vị:
                            // báo lỗi đọc dữ liệu đầu vào thay vì tính tiếp từ ngày sai.
                            const yearOf = d => (d instanceof Date && !isNaN(d.getTime())) ? d.getFullYear() : null;
                            const outOfRange = (d, lo, hi) => { const y = yearOf(d); return y != null && (y < lo || y > hi); };
                            let ngaySinh = BQPValidation.ExcelParser.parseDateCell(c3);
                            if (txt3 && (txt3.startsWith('[Lỗi') || txt3.includes('#VALUE!') || txt3.includes('#REF!'))) {
                                cellErrors[3] = txt3;
                                ngaySinh = '[Lỗi: ' + txt3 + ']';
                            } else if (txt3 && !ngaySinh) {
                                cellErrors[3] = `Lỗi định dạng ngày sinh (${txt3})`;
                                ngaySinh = '[Lỗi định dạng]';
                            } else if (!txt3 && !ngaySinh) {
                                cellErrors[3] = 'Thiếu ngày tháng năm sinh';
                                ngaySinh = '[Lỗi: Thiếu ngày sinh]';
                            } else if (outOfRange(ngaySinh, 1930, 2010)) {
                                cellErrors[3] = `Năm sinh không hợp lệ (${txt3})`;
                                ngaySinh = '[Lỗi định dạng]';
                            }

                            let chucVu = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[5] || 5));

                            let c6 = row.getCell(colMap[6] || 6);
                            let txt6 = BQPValidation.ExcelParser.getCellText(c6);
                            let nhapNgu = BQPValidation.ExcelParser.parseDateCell(c6);
                            if (txt6 && (txt6.startsWith('[Lỗi') || txt6.includes('#VALUE!'))) {
                                cellErrors[6] = txt6;
                            } else if (txt6 && !nhapNgu) {
                                cellErrors[6] = `Lỗi định dạng ngày nhập ngũ (${txt6})`;
                            } else if (!txt6 && !nhapNgu) {
                                cellErrors[6] = 'Thiếu ngày tháng nhập ngũ';
                            } else if (outOfRange(nhapNgu, 1950, 2026)) {
                                cellErrors[6] = `Năm nhập ngũ không hợp lệ (${txt6})`;
                                nhapNgu = null;
                            }

                            let sapNhap = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[7] || 7));

                            let c8 = row.getCell(colMap[8] || 8);
                            let txt8 = BQPValidation.ExcelParser.getCellText(c8);
                            let thoiDiemNghi = BQPValidation.ExcelParser.parseDateCell(c8);
                            if (txt8 && (txt8.startsWith('[Lỗi') || txt8.includes('#VALUE!'))) {
                                cellErrors[8] = txt8;
                            } else if (txt8 && !thoiDiemNghi) {
                                cellErrors[8] = `Lỗi định dạng thời điểm nghỉ (${txt8})`;
                            } else if (!txt8 && !thoiDiemNghi) {
                                cellErrors[8] = 'Thiếu thời điểm nghỉ';
                            } else if (outOfRange(thoiDiemNghi, 2024, 2027)) {
                                cellErrors[8] = `Thời điểm nghỉ không hợp lệ (${txt8})`;
                                thoiDiemNghi = null;
                            }

                            // Thứ tự thời gian phải hợp lý: sinh < nhập ngũ < nghỉ. Sai thứ tự thì không đủ tin cậy để tính chế độ.
                            if (!cellErrors[6] && ngaySinh instanceof Date && nhapNgu instanceof Date && nhapNgu < ngaySinh) {
                                cellErrors[6] = `Ngày nhập ngũ (${txt6}) trước ngày sinh (${txt3})`;
                                nhapNgu = null;
                            }
                            if (!cellErrors[8] && nhapNgu instanceof Date && thoiDiemNghi instanceof Date && thoiDiemNghi < nhapNgu) {
                                cellErrors[8] = `Thời điểm nghỉ (${txt8}) trước ngày nhập ngũ (${txt6})`;
                                thoiDiemNghi = null;
                            }

                            let luongThang = 0;
                            if (txt9 && (txt9.startsWith('[Lỗi') || txt9.includes('#VALUE!') || txt9.includes('#REF!'))) {
                                cellErrors[9] = txt9;
                            } else {
                                luongThang = BQPValidation.ExcelParser.parseNumber(c9);
                                if (luongThang <= 0) {
                                    cellErrors[9] = 'Tiền lương tháng bình quân phải > 0';
                                }
                            }

                            let rawCols = {};
                            let endCol = type === 'I.1' ? 23 : (type === 'I.2' ? 18 : 15);
                            for (let k = 10; k <= endCol + 2; k++) {
                                if (colMap[k]) {
                                    let ck = row.getCell(colMap[k]);
                                    let readk = BQPValidation.ExcelParser.readCellValue(ck, 'number', { sheetName: worksheet.name });
                                    if (readk.status === CellStatus.FORMULA_ERROR || readk.status === CellStatus.FORMULA_NO_RESULT || readk.status === CellStatus.INVALID_TYPE) {
                                        cellErrors[k] = readk.rawText || (readk.status === CellStatus.FORMULA_ERROR ? `[Lỗi: ${readk.errorCode}]` : '[Lỗi: Chưa tính]');
                                        rawCols[k] = null; // TUYỆT ĐỐI KHÔNG GÁN 0 CHO Ô LỖI HOẶC THIẾU KẾT QUẢ
                                    } else if (readk.status === CellStatus.FORMULA_EMPTY || readk.status === CellStatus.BLANK) {
                                        rawCols[k] = null;
                                    } else {
                                        rawCols[k] = readk.value;
                                    }
                                }
                            }
                            // Giữ số kê khai C9 gốc: luongThang có thể bị thay bằng lương chuẩn từ Phụ lục I.5
                            if (!cellErrors[9] && luongThang > 0) rawCols[9] = luongThang;

                            rowData = {
                                id: 'val_' + Date.now() + '_' + Math.floor(Math.random() * 100000),
                                rowIndex: r,
                                group: currentGroup,
                                sheet: type,
                                fileName: file.name,
                                hoTen: hoten,
                                categoryCode: currentCategory || (type === 'I.3' ? 'QNCN' : 'SQ'),
                                categoryName: currentCategoryName || (currentCategory === 'QNCN' ? 'Quân nhân chuyên nghiệp' : 'Sĩ quan'),
                                unitName: currentUnit || currentGroup,
                                unitRoman: currentUnitRoman || 'I',
                                policyCode: currentPolicy || (type === 'I.3' ? 'ND177' : 'ND178'),
                                donVi: currentUnit || currentGroup,
                                capBac: capBac,
                                chucVu: chucVu,
                                ngaySinh: ngaySinh,
                                nhapNgu: nhapNgu,
                                sapNhap: sapNhap,
                                thoiDiemNghi: thoiDiemNghi,
                                luongThang: luongThang,
                                rawCols: rawCols,
                                cellErrors: cellErrors
                            };
                        }

                        let valRes = BQPValidation.ValidationService.validateRow(type, rowData);

                        rowData.valRes = valRes;
                        rowData.hasErrors = valRes.hasErrors || Object.keys(cellErrors).length > 0;
                        rowData.hasDiff24Months = valRes.hasDiff24Months || false;
                        rowData.errorDetails = valRes.errorDetails;
                        rowData.comparisons = valRes.comparisons;
                        rowData.expected = valRes.exp;
                        const colTotal = (type === 'I.1' ? 23 : (type === 'I.2' ? 18 : (type === 'I.3' ? 15 : (type === 'I.5' ? 22 : 23))));
                        let rawTotalVal = (rowData && (!rowData.cellErrors || !rowData.cellErrors[colTotal]) && rowData.rawCols && rowData.rawCols[colTotal] != null) ? rowData.rawCols[colTotal] : null;
                        if (rawTotalVal != null && typeof rawTotalVal !== 'number') {
                            rawTotalVal = BQPNormalization.number(rawTotalVal) ?? rawTotalVal;
                        }
                        rowData.tongTienThucTe = (valRes.actualTotal != null) ? valRes.actualTotal : (typeof rawTotalVal === 'number' ? rawTotalVal : null);
                        rowData.tongTienTinhLai = valRes.expectedTotal;
                        rowData.diff = valRes.diff;

                        allRecords.push(rowData);
                        readAudit.person(type);

                        if (type === 'I.1') sheetStats.i1++;
                        else if (type === 'I.2') sheetStats.i2++;
                        else if (type === 'I.3') sheetStats.i3++;
                        else if (type === 'I.5') sheetStats.i5 = (sheetStats.i5 || 0) + 1;
                    } catch (rowErr) {
                        console.error(`Lỗi đọc/thẩm định dòng ${r} (${worksheet.name}):`, rowErr);
                        let fallbackRecord = {
                            id: 'val_err_' + Date.now() + '_' + r + '_' + Math.floor(Math.random() * 1000),
                            rowIndex: r,
                            group: currentGroup || worksheet.name,
                            sheet: type,
                            fileName: file.name,
                            hoTen: `[Dòng ${r}: Lỗi ô]`,
                            capBac: '[Lỗi]',
                            chucVu: '',
                            ngaySinh: '[Lỗi]',
                            nhapNgu: '[Lỗi]',
                            sapNhap: null,
                            thoiDiemNghi: '[Lỗi]',
                            luongThang: 0,
                            rawCols: {},
                            cellErrors: { 0: rowErr.message || 'Lỗi ô' },
                            hasErrors: true,
                            hasDiff24Months: false,
                            errorDetails: [`Dòng ${r} gặp lỗi khi đọc ô dữ liệu: ${rowErr.message || rowErr}`],
                            comparisons: [{
                                col: 'Toàn dòng',
                                title: 'Lỗi ô dữ liệu',
                                actual: 'Lỗi ô',
                                expected: 'Chuẩn',
                                diff: '0 đ',
                                hasErr: true,
                                formula: rowErr.message || 'Lỗi ô'
                            }],
                            expected: {},
                            tongTienThucTe: 0,
                            tongTienTinhLai: 0,
                            diff: 0
                        };
                        fallbackRecord.valRes = {
                            exp: {},
                            comparisons: fallbackRecord.comparisons,
                            errorDetails: fallbackRecord.errorDetails,
                            actualTotal: 0,
                            expectedTotal: 0,
                            diff: 0,
                            hasErrors: true,
                            hasDiff24Months: false
                        };
                        allRecords.push(fallbackRecord);
                    }
                }
            });

            this.crossCheckI5Salary(allRecords);

            this.readReport = readAudit.finish();
            this.renderReadReport();

            this.currentRecords = allRecords;
            this.currentFiltered = allRecords;
            this.currentResult = {
                records: allRecords,
                sheetStats: sheetStats
            };

            let wrap = document.getElementById("validate_result_wrap");
            if (wrap) wrap.style.display = "block";

            let bannerEl = document.getElementById("v_norm_banner");
            let detailsEl = document.getElementById("v_norm_details");
            if (bannerEl) {
                if (this.isNormalized && this.normCounts) {
                    bannerEl.style.display = "flex";
                    if (detailsEl) {
                        let parts = [];
                        if (this.normCounts['I.1']) parts.push(`Phụ lục I.1: ${this.normCounts['I.1'].toLocaleString('vi-VN')} người`);
                        if (this.normCounts['I.2']) parts.push(`Phụ lục I.2: ${this.normCounts['I.2'].toLocaleString('vi-VN')} người`);
                        if (this.normCounts['I.3']) parts.push(`Phụ lục I.3: ${this.normCounts['I.3'].toLocaleString('vi-VN')} người`);
                        detailsEl.textContent = `(${parts.join(', ') || '0 đối tượng'})`;
                    }
                } else {
                    bannerEl.style.display = "none";
                }
            }

            this.renderTable();

        } catch (err) {
            console.error("Lỗi khi đọc file Excel:", err);
            notifyError("Có lỗi khi đọc file Excel: " + err.message);
            if (dropzone) dropzone.style.display = "block";
        } finally {
            if (typeof BQPLoader !== 'undefined') {
                BQPLoader.hide();
            }
            if (loadingEl) loadingEl.style.display = "none";
            if (event && event.target) event.target.value = "";
        }
    },

    setFilterStatus(status) {
        let sel = document.getElementById('vfilter_status');
        if (sel) sel.value = status;
        this.renderTable();
    },

    renderTable() {
        let container = document.getElementById("vtable_container");
        if (!container) return;

        let records = this.currentRecords || [];

        let filterSheet = document.getElementById('vfilter_sheet') ? document.getElementById('vfilter_sheet').value : '';
        let filterStatus = document.getElementById('vfilter_status') ? document.getElementById('vfilter_status').value : '';
        let filterSearch = document.getElementById('vfilter_search') ? document.getElementById('vfilter_search').value.toLowerCase().trim() : '';

        // Thống kê tổng số lượng theo từng nhóm trạng thái
        let totalCount = records.length;
        let validCount = 0;
        let errCount = 0;
        let underCount = 0;
        let underMoney = 0;
        let overCount = 0;
        let overMoney = 0;
        let otherCount = 0;

        records.forEach(r => {
            let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
            let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
            if (!isErr) {
                validCount++;
            } else {
                errCount++;
                if (diff > 1000) {
                    underCount++;
                    underMoney += diff;
                } else if (diff < -1000) {
                    overCount++;
                    overMoney += Math.abs(diff);
                } else {
                    otherCount++;
                }
            }
        });

        let filtered = records.filter(r => {
            if (filterSheet && r.sheet !== filterSheet) return false;
            let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
            let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
            let isDiffUnder = isErr && diff > 1000;
            let isDiffOver = isErr && diff < -1000;
            let isDiffOther = isErr && !isDiffUnder && !isDiffOver;

            if ((filterStatus === 'valid' || filterStatus === 'ok') && isErr) return false;
            if ((filterStatus === 'error' || filterStatus === 'err') && !isErr) return false;
            if (filterStatus === 'error_under' && (!isErr || !isDiffUnder)) return false;
            if (filterStatus === 'error_over' && (!isErr || !isDiffOver)) return false;
            if (filterStatus === 'error_other' && (!isErr || !isDiffOther)) return false;

            if (filterSearch) {
                let name = (r.hoTen || '').toLowerCase();
                let rank = (r.capBac || '').toLowerCase();
                let unit = (r.group || '').toLowerCase();
                if (!name.includes(filterSearch) && !rank.includes(filterSearch) && !unit.includes(filterSearch)) return false;
            }
            return true;
        });

        this.currentFiltered = filtered;

        if (filtered.length === 0) {
            container.innerHTML = `
                <div style="padding:40px 20px; text-align:center; color:#64748b; background:#ffffff; border:1px solid #e2e8f0; border-radius:6px;">
                    <div style="font-size:15px; font-weight:600; color:#1e293b; margin-bottom:4px;">Không tìm thấy hồ sơ phù hợp</div>
                    <div style="font-size:13px;">Hãy thử thay đổi điều kiện lọc hoặc từ khóa tìm kiếm.</div>
                </div>`;
            let btnExport = document.querySelector('button[onclick="ValidationUI.exportExcel()"]');
            if (btnExport) btnExport.textContent = 'Xuất file Excel (0 hồ sơ)';
            return;
        }

        // Nhóm theo Đơn vị
        let groups = {};
        filtered.forEach(r => {
            let g = r.group || 'Đơn vị không xác định';
            if (!groups[g]) groups[g] = [];
            groups[g].push(r);
        });

        let html = `
            <div style="margin-bottom:12px; font-size:13px; color:#475569;">
                Hiển thị <strong>${filtered.length}</strong> / ${totalCount} hồ sơ thẩm định (${errCount} hồ sơ có sai sót)
            </div>

            <table class="records-table" style="width:100%; border-collapse:collapse; background:#ffffff; border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                <thead>
                    <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-size:12.5px; text-transform:uppercase;">
                        <th style="width:45px; text-align:center; padding:10px 8px;">STT</th>
                        <th style="width:75px; text-align:center; padding:10px 8px;">Phụ lục</th>
                        <th style="padding:10px 14px; text-align:left;">Họ và tên quân nhân</th>
                        <th style="width:150px; padding:10px 14px; text-align:left;">Cấp bậc / Chức vụ</th>
                        <th style="width:140px; text-align:center; padding:10px 8px;">Trạng thái thẩm định</th>
                        <th style="min-width:140px; text-align:right; padding:10px 14px; white-space:nowrap;">Tiền trên Excel</th>
                        <th style="min-width:140px; text-align:right; padding:10px 14px; white-space:nowrap;">Chuẩn tính lại</th>
                        <th style="min-width:155px; text-align:right; padding:10px 14px; white-space:nowrap;">Chênh lệch</th>
                        <th style="width:100px; text-align:center; padding:10px 8px;">Thao tác</th>
                    </tr>
                </thead>
                <tbody>`;

        let stt = 1;
        for (let groupName in groups) {
            let groupRecords = groups[groupName];

            html += `
                <tr style="background:#f1f5f9; border-top:2px solid #cbd5e1; border-bottom:1px solid #cbd5e1;">
                    <td colspan="9" style="padding:9px 14px;">
                        <div style="font-weight:700; color:#0f172a; font-size:13.5px;">
                            Đơn vị: ${escapeHtml(groupName)}
                        </div>
                    </td>
                </tr>`;

            groupRecords.forEach(r => {
                let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
                let diffStr = diff === 0 ? '---' : (diff > 0 ? '+' + BQPValidation.fmtMoney(diff) + '&nbsp;đ' : BQPValidation.fmtMoney(diff) + '&nbsp;đ');
                let diffColor = diff === 0 ? '#64748b' : '#b91c1c';

                let isIncomplete = (r.valRes && (r.valRes.status === 'INCOMPLETE_INPUT' || r.valRes.isIncomplete)) || (r.tongTienThucTe == null && r.hasErrors);
                let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
                let statusBadge = '';
                if (isIncomplete) {
                    statusBadge = `<span style="display:inline-block; padding:3px 8px; border-radius:4px; font-size:11.5px; font-weight:600; background:#fffbeb; color:#b45309; border:1px solid #fde68a;">Chưa đủ dữ liệu</span>`;
                } else if (isErr) {
                    statusBadge = `<span style="display:inline-block; padding:3px 8px; border-radius:4px; font-size:11.5px; font-weight:600; background:#fef2f2; color:#b91c1c; border:1px solid #fecaca;">Lệch chuẩn</span>`;
                } else {
                    statusBadge = `<span style="display:inline-block; padding:3px 8px; border-radius:4px; font-size:11.5px; font-weight:600; background:#f0fdf4; color:#15803d; border:1px solid #bbf7d0;">Đạt chuẩn</span>`;
                }

                let nsStr = BQPValidation.formatDate(r.ngaySinh) || '---';
                let nnStr = BQPValidation.formatDate(r.nhapNgu) || '---';

                let cellValHelper = (val) => {
                    if (val === null || val === undefined || val === '') return '---';
                    let s = String(val);
                    if (s.includes('[Lỗi')) {
                        return `<span style="color:#b91c1c; font-weight:600; background:#fef2f2; padding:1px 5px; border-radius:3px; border:1px solid #fecaca;" title="${escapeAttr(s)}">${escapeHtml(s)}</span>`;
                    }
                    return escapeHtml(s);
                };

                html += `
                    <tr style="border-bottom:1px solid #f1f5f9; transition:background 0.15s;">
                        <td style="text-align:center; color:#64748b; font-size:12.5px; padding:10px 8px;">${stt++}</td>
                        <td style="text-align:center; padding:10px 8px;">
                            <span style="font-size:11.5px; font-weight:700; color:#1e40af; background:#eff6ff; border:1px solid #bfdbfe; padding:2px 6px; border-radius:4px;">${escapeHtml(r.sheet)}</span>
                        </td>
                        <td style="text-align:left; padding:10px 14px;">
                            <div style="font-weight:700; color:#0f172a; font-size:13.5px; margin-bottom:2px;">${cellValHelper(r.hoTen)}</div>
                            <div style="font-size:11.5px; color:#64748b;">Sinh: ${cellValHelper(nsStr)} &nbsp;•&nbsp; Nhập ngũ: ${cellValHelper(nnStr)}</div>
                        </td>
                        <td style="text-align:left; padding:10px 14px;">
                            <div style="font-weight:600; color:#334155; font-size:12.5px;">${cellValHelper(r.capBac)}</div>
                            <div style="font-size:11.5px; color:#64748b;">${cellValHelper(r.chucVu)}</div>
                        </td>
                        <td style="text-align:center; padding:10px 8px;">
                            ${statusBadge}
                        </td>
                        <td style="text-align:right; font-weight:600; color:#334155; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${r.tongTienThucTe != null ? BQPValidation.fmtMoney(r.tongTienThucTe) + '&nbsp;đ' : '<span style="color:#94a3b8; font-size:12px;">Chưa xác định</span>'}
                        </td>
                        <td style="text-align:right; font-weight:700; color:#15803d; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${r.tongTienTinhLai != null ? BQPValidation.fmtMoney(r.tongTienTinhLai) + '&nbsp;đ' : '<span style="color:#94a3b8; font-size:12px;">Chưa xác định</span>'}
                        </td>
                        <td style="text-align:right; font-weight:700; color:${diffColor}; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${(r.tongTienThucTe != null && r.tongTienTinhLai != null) ? diffStr : '<span style="color:#94a3b8;">---</span>'}
                        </td>
                        <td style="text-align:center; padding:10px 8px;">
                            <button type="button" onclick="ValidationUI.openCompareModal('${escapeJs(r.id)}')" title="Đối chiếu chi tiết từng cột" style="padding:4px 12px; font-size:12px; font-weight:600; background:#f8fafc; color:#1e3a5f; border:1px solid #cbd5e1; border-radius:4px; cursor:pointer;">
                                Đối chiếu
                            </button>
                        </td>
                    </tr>`;
            });
        }

        html += `</tbody></table>`;
        container.innerHTML = html;

        let btnExport = document.querySelector('button[onclick="ValidationUI.exportExcel()"]');
        if (btnExport) {
            if (filtered.length < totalCount) {
                btnExport.textContent = `Xuất file Excel (${filtered.length})`;
                btnExport.title = `Xuất ${filtered.length} hồ sơ đã lọc ra file Excel`;
            } else {
                btnExport.textContent = 'Xuất file Excel báo cáo';
            }
        }
    },

    openCompareModal(recordOrId, isExcelOnly = false, options = {}) {
        let r = null;
        let list = null;
        let idx = -1;

        if (typeof recordOrId === 'object' && recordOrId !== null) {
            r = recordOrId;
            list = (options && Array.isArray(options.contextList) && options.contextList.length > 0)
                ? options.contextList
                : [r];
            idx = (options && options.contextIndex != null)
                ? options.contextIndex
                : list.findIndex(x => x.id === r.id);
            if (idx === -1) idx = 0;
            this.currentModalIndex = idx;
        } else if (typeof recordOrId === 'number') {
            idx = recordOrId;
            this.currentModalIndex = idx;
            if (this._modalContext && Array.isArray(this._modalContext.list)) {
                list = this._modalContext.list;
                r = list[idx];
                isExcelOnly = (this._modalContext.isExcelOnly != null) ? this._modalContext.isExcelOnly : isExcelOnly;
                options = this._modalContext.options || options;
            } else {
                list = (this.currentFiltered && this.currentFiltered.length > 0) ? this.currentFiltered : (this.currentRecords || []);
                r = list[idx];
            }
        } else {
            const recordId = recordOrId;
            if (options && Array.isArray(options.contextList)) {
                list = options.contextList;
                idx = list.findIndex(x => x.id === recordId);
                if (idx !== -1) r = list[idx];
            }
            if (!r) {
                list = (this.currentFiltered && this.currentFiltered.length > 0) ? this.currentFiltered : (this.currentRecords || []);
                idx = list.findIndex(x => x.id === recordId);
                if (idx !== -1) r = list[idx];
            }
            if (!r && typeof StorageManager !== 'undefined') {
                let src = isExcelOnly ? 'excel' : 'validated';
                let recs = StorageManager.getRecordsBySource(src);
                r = recs.find(x => x.id === recordId);
                if (r) {
                    list = recs;
                    idx = list.findIndex(x => x.id === recordId);
                }
            }
            this.currentModalIndex = idx >= 0 ? idx : 0;
        }

        if (!r) {
            notifyWarning('Không tìm thấy thông tin hồ sơ.');
            return;
        }

        this._modalContext = {
            list: list || [r],
            isExcelOnly: isExcelOnly,
            options: options || {}
        };

        // Đảm bảo đối tượng được hydrate đầy đủ chi tiết đối chiếu
        if (typeof ensureRecordValidationDetail === 'function') {
            ensureRecordValidationDetail(r, isExcelOnly);
        } else if (typeof BQPValidation !== 'undefined' && typeof BQPValidation.ensureRecordValidationDetail === 'function') {
            BQPValidation.ensureRecordValidationDetail(r, isExcelOnly);
        }

        let modal = document.getElementById("valErrorModal");
        let bodyEl = document.getElementById("valModalBody");
        let titleEl = document.getElementById("valModalTitle");
        let navEl = document.getElementById("valModalNav");
        if (!modal || !bodyEl) return;

        let totalRecords = (list && list.length > 0) ? list.length : 1;
        idx = this.currentModalIndex;

        let sheetKey = r.sheet || r.sheetSource || 'I.1';

        if (titleEl) {
            titleEl.textContent = isExcelOnly
                ? `THÔNG TIN CHI TIẾT HỒ SƠ QUÂN NHÂN: ${r.hoTen} (${r.capBac || '---'}) — Phụ lục ${sheetKey}`
                : `BẢNG ĐỐI CHIẾU CHI TIẾT CHỈ TIÊU & SAI SÓT: ${r.hoTen} (${r.capBac || '---'}) — Phụ lục ${sheetKey}`;
        }

        // Tái tạo hoặc lấy bảng so sánh từng cột
        let comparisons = Array.isArray(r.comparisons) ? r.comparisons : [];
        let errorDetails = Array.isArray(r.errorDetails) ? r.errorDetails : [];

        if (!comparisons || comparisons.length === 0) {
            let valRes = null;
            let rawNs = r.ngaySinh || r.birthDate || (r.input && (r.input.ngaySinh || r.input.birthDate)) || null;
            let rawNn = r.nhapNgu || r.enlistmentDate || (r.input && (r.input.nhapNgu || r.input.enlistmentDate)) || null;
            let rawSn = r.sapNhap || r.mergerDate || (r.input && (r.input.sapNhap || r.input.mergerDate)) || null;
            let rawNg = r.thoiDiemNghi || r.retirementDate || (r.input && (r.input.thoiDiemNghi || r.input.retirementDate)) || null;

            let validationInput = {
                ...r,
                rawCols: r.rawCols || {},
                ngaySinh: (typeof parseToValidDate === 'function') ? parseToValidDate(rawNs) : rawNs,
                nhapNgu: (typeof parseToValidDate === 'function') ? parseToValidDate(rawNn) : rawNn,
                sapNhap: (typeof parseToValidDate === 'function') ? parseToValidDate(rawSn) : rawSn,
                thoiDiemNghi: (typeof parseToValidDate === 'function') ? parseToValidDate(rawNg) : rawNg,
                luongThang: r.luongThang != null ? Number(r.luongThang) : 0,
                heSoLuong: r.heSoLuong != null ? Number(r.heSoLuong) : (r.rawCols ? Number(r.rawCols[6] || 0) : 0)
            };
            if (typeof ValidationService !== 'undefined' && typeof ValidationService.validateRow === 'function') {
                valRes = ValidationService.validateRow(sheetKey, validationInput);
            } else if (typeof BQPValidation !== 'undefined' && BQPValidation.ValidationService && typeof BQPValidation.ValidationService.validateRow === 'function') {
                valRes = BQPValidation.ValidationService.validateRow(sheetKey, validationInput);
            }
            if (valRes && Array.isArray(valRes.comparisons) && valRes.comparisons.length > 0) {
                comparisons = valRes.comparisons;
                r.comparisons = comparisons;
                if (!isExcelOnly) errorDetails = valRes.errorDetails || errorDetails;
            }
        }

        // Fallback dựng trực tiếp từ rawCols nếu comparisons vẫn rỗng (CHỈ CHO HỒ SƠ RAW)
        if (isExcelOnly && (!comparisons || comparisons.length === 0) && r.rawCols && Object.keys(r.rawCols).length > 0) {
            if (typeof buildRawExcelComparisons === 'function') {
                comparisons = buildRawExcelComparisons(sheetKey, r.rawCols, r);
                r.comparisons = comparisons;
            } else if (typeof BQPValidation !== 'undefined' && typeof BQPValidation.buildRawExcelComparisons === 'function') {
                comparisons = BQPValidation.buildRawExcelComparisons(sheetKey, r.rawCols, r);
                r.comparisons = comparisons;
            }
        }

        let dNgaySinh = BQPValidation.formatDate(r.ngaySinh) || '---';
        let dNhapNgu = BQPValidation.formatDate(r.nhapNgu) || '---';
        let dSapNhap = BQPValidation.formatDate(r.sapNhap) || '---';
        let dNghi = BQPValidation.formatDate(r.thoiDiemNghi) || '---';
        let tranTuoi = r.expected ? r.expected.tran : BQPValidation.MilitaryRankHelper.getTran(r.capBac, r.chucVu);
        let displayRow = r.rowIndex != null ? r.rowIndex : (r.sourceRow != null ? r.sourceRow : '-');

        // Tổng hợp kiểm tra lỗi trung thực
        let hasAnyError = Boolean(
            r.hasErrors ||
            (errorDetails && errorDetails.length > 0) ||
            (comparisons && comparisons.some(c => c.hasErr)) ||
            (r.cellErrors && Object.keys(r.cellErrors).length > 0) ||
            r.hasDiff24Months
        );

        const normFn = (typeof normalizeErrorDetails === 'function')
            ? normalizeErrorDetails
            : ((typeof BQPValidation !== 'undefined' && typeof BQPValidation.normalizeErrorDetails === 'function')
                ? BQPValidation.normalizeErrorDetails
                : (errs) => (Array.isArray(errs) ? errs.map(e => typeof e === 'object' && e !== null ? (e.message || e.col || JSON.stringify(e)) : String(e)) : (errs ? [String(errs)] : [])));
        let normalizedErrors = normFn(errorDetails);
        if (comparisons && comparisons.length > 0) {
            comparisons.forEach(c => {
                if (c.hasErr && c.diff && c.diff !== '0 đ' && c.diff !== '0 tháng' && c.diff !== '0 năm' && c.diff !== '---') {
                    const colDesc = `${c.col} (${c.title}): File ghi ${c.actual}, Chuẩn ${c.expected} (lệch ${c.diff})`;
                    if (!normalizedErrors.some(e => e.includes(c.col))) {
                        normalizedErrors.push(colDesc);
                    }
                }
            });
        }

        let snapshotNoticeHtml = '';
        if (!isExcelOnly && r.linkedRawStatus === 'from_snapshot') {
            snapshotNoticeHtml = `
                <div style="background:#eff6ff; border:1px solid #bfdbfe; border-left:4px solid #3b82f6; border-radius:4px; padding:8px 14px; margin-bottom:12px; font-size:12.5px; color:#1e40af;">
                    ℹ <strong>Nguồn dữ liệu:</strong> Hồ sơ hiển thị từ bản ghi snapshot lưu trữ. Bản ghi gốc RAW đã bị xóa hoặc không tìm thấy.
                </div>`;
        }

                // Khung cảnh báo sai lệch (Chỉ hiển thị khi thẩm định / đối chiếu, ẩn khi xem hồ sơ Excel)
        let alertBoxHtml = '';
        if (!isExcelOnly) {
            if (r.linkedRawStatus === 'missing' && (!comparisons || comparisons.length === 0)) {
                alertBoxHtml = `
                    <div style="background:#fffbeb; border:1px solid #fde68a; border-left:4px solid #f59e0b; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#b45309; font-size:13.5px; margin-bottom:4px;">
                            Không tìm thấy bản RAW liên kết với hồ sơ sau thẩm định này
                        </div>
                        <div style="color:#78350f; font-size:12.5px; line-height:1.5;">
                            Không tìm thấy bản ghi dữ liệu gốc từ file Excel (mã liên kết không tồn tại). Chưa đủ dữ liệu để đối chiếu chi tiết.
                        </div>
                    </div>`;
            } else if (r.linkedRawStatus === 'inconsistent') {
                alertBoxHtml = `
                    <div style="background:#fffbeb; border:1px solid #fde68a; border-left:4px solid #f59e0b; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#b45309; font-size:13.5px; margin-bottom:4px;">
                            Dữ liệu nguồn không nhất quán
                        </div>
                        <div style="color:#78350f; font-size:12.5px; line-height:1.5;">
                            Thông tin định danh của bản RAW liên kết khác với hồ sơ sau thẩm định. Vui lòng kiểm tra lại đợt nhập dữ liệu.
                        </div>
                    </div>`;
            } else if (hasAnyError) {
                if (normalizedErrors.length > 0) {
                    alertBoxHtml = `
                        <div style="background:#fef2f2; border:1px solid #fecaca; border-left:4px solid #b91c1c; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                            <div style="font-weight:700; color:#991b1b; font-size:13.5px; margin-bottom:6px;">
                                Phát hiện ${normalizedErrors.length} điểm sai lệch so với chuẩn:
                            </div>
                            <ul style="margin:0; padding-left:18px; color:#991b1b; font-size:12.5px; line-height:1.6;">
                                ${normalizedErrors.map(err => `<li><strong>${escapeHtml(err)}</strong></li>`).join('')}
                            </ul>
                        </div>`;
                } else {
                    alertBoxHtml = `
                        <div style="background:#fef2f2; border:1px solid #fecaca; border-left:4px solid #b91c1c; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                            <div style="font-weight:700; color:#991b1b; font-size:13.5px;">
                                Hồ sơ có lỗi; chưa có đầy đủ chi tiết lỗi từ đợt thẩm định trước.
                            </div>
                        </div>`;
                }
            } else if (!comparisons || comparisons.length === 0) {
                alertBoxHtml = `
                    <div style="background:#fffbeb; border:1px solid #fde68a; border-left:4px solid #f59e0b; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#b45309; font-size:13.5px; margin-bottom:4px;">
                            Chưa đủ dữ liệu để đối chiếu chi tiết cho hồ sơ này
                        </div>
                        <div style="color:#78350f; font-size:12.5px; line-height:1.5;">
                            Hồ sơ này không có snapshot đối chiếu hoặc thiếu dữ liệu cột gốc từ Excel để tái lập bảng chi tiết. Vui lòng thẩm định lại file Excel để lưu đầy đủ thông tin chi tiết.
                        </div>
                    </div>`;
            } else {
                alertBoxHtml = `
                    <div style="background:#f0fdf4; border:1px solid #bbf7d0; border-left:4px solid #16a34a; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#166534; font-size:13.5px;">
                            Không phát hiện sai lệch giữa RAW và kết quả sau thẩm định đã lưu (Đạt chuẩn 100%).
                        </div>
                    </div>`;
            }
        }

        let totalExpected = r.tongTienTinhLai != null ? r.tongTienTinhLai : (r.calculatedTotal != null ? r.calculatedTotal : 0);
        let totalActual = r.tongTienThucTe != null ? r.tongTienThucTe : (r.actualTotal != null ? r.actualTotal : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || r.rawCols[22] || r.rawCols[20] || 0) : 0));
        let totalDiff = totalExpected - totalActual;

        // Khối tổng kinh phí
        let summaryMoneyHtml = '';
        if (isExcelOnly) {
            summaryMoneyHtml = `
                <div style="background:#f0fdf4; border:1px solid #86efac; border-radius:6px; padding:14px; text-align:center; margin-bottom:12px;">
                    <div style="font-size:12px; color:#15803d; font-weight:700; text-transform:uppercase; letter-spacing:0.5px;">Tổng kinh phí (File Excel)</div>
                    <div style="font-size:22px; font-weight:800; color:#15803d; margin-top:4px;">${BQPValidation.fmtMoney(totalActual)} đ</div>
                </div>
            `;
        } else {
            summaryMoneyHtml = `
                <div style="display:grid; grid-template-columns:repeat(3, 1fr); gap:12px; margin-bottom:16px;">
                    <div style="background:#f8fafc; border:1px solid #cbd5e1; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:#64748b; font-weight:600;">TỔNG TIỀN RAW (FILE EXCEL)</div>
                        <div style="font-size:17px; font-weight:700; color:#334155; margin-top:4px;">${BQPValidation.fmtMoney(totalActual)} đ</div>
                    </div>
                    <div style="background:#f0fdf4; border:1px solid #86efac; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:#15803d; font-weight:600;">THẨM ĐỊNH TÍNH LẠI</div>
                        <div style="font-size:17px; font-weight:700; color:#15803d; margin-top:4px;">${BQPValidation.fmtMoney(totalExpected)} đ</div>
                    </div>
                    <div style="background:${totalDiff !== 0 ? '#fef2f2' : '#f8fafc'}; border:1px solid ${totalDiff !== 0 ? '#fca5a5' : '#cbd5e1'}; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:${totalDiff !== 0 ? '#b91c1c' : '#64748b'}; font-weight:600;">CHÊNH LỆCH (Sau thẩm định − RAW)</div>
                        <div style="font-size:17px; font-weight:700; color:${totalDiff !== 0 ? '#b91c1c' : '#64748b'}; margin-top:4px;">
                            ${totalDiff === 0 ? '0 đ' : (totalDiff > 0 ? '+' + BQPValidation.fmtMoney(totalDiff) + ' đ' : BQPValidation.fmtMoney(totalDiff) + ' đ')}
                        </div>
                    </div>
                </div>
            `;
        }

        // Bằng chữ
        let chuTienHtml = '';
        if (isExcelOnly) {
            chuTienHtml = `
                <div style="font-size:13px; color:#475569; margin-bottom:14px;">
                    Số tiền bằng chữ: <strong style="color:#0f172a;">${BQPValidation.readNumberToVietnameseWords(totalActual)}</strong>
                </div>
            `;
        } else {
            chuTienHtml = `
                <div style="font-size:13px; color:#475569; margin-bottom:16px;">
                    Số tiền bằng chữ (Thẩm định): <strong style="color:#0f172a;">${BQPValidation.readNumberToVietnameseWords(totalExpected)}</strong>
                </div>
            `;
        }

        // Bảng dữ liệu: Nếu là Hồ sơ Excel -> chỉ hiển thị các cột nhập từ Excel; Nếu là Thẩm định -> bảng đối chiếu 6 cột
        let tableBlockHtml = '';
        if (isExcelOnly) {
            let tableRowsHtml = (comparisons && comparisons.length > 0) ? comparisons.map(c => {
                let isTotal = c.col === 'Cột 23' || c.col === 'Cột 22' || c.col === 'Cột 20' || c.col === 'Cột 18' || c.col === 'Cột 15' || (c.title && c.title.toLowerCase().includes('tổng cộng'));
                return `
                    <tr style="${isTotal ? 'background:#f0fdf4; font-weight:700;' : ''} border-bottom:1px solid #f1f5f9;">
                        <td style="text-align:center; font-weight:700; color:#1e3a5f; padding:9px 10px; width:75px;">${escapeHtml(c.col)}</td>
                        <td style="padding:9px 10px; color:#1e293b; font-weight:${isTotal ? '700' : '500'};">${escapeHtml(c.title)}</td>
                        <td style="text-align:right; padding:9px 14px; font-weight:${isTotal ? '800' : '600'}; color:${isTotal ? '#15803d' : '#334155'}; font-size:${isTotal ? '13.5' : '13'}px; width:220px;">${escapeHtml(c.actual)}</td>
                    </tr>`;
            }).join('') : `<tr><td colspan="3" style="text-align:center; padding:24px 16px; color:#64748b; font-size:13px;">Không có dữ liệu chi tiết các cột nhập từ Excel cho hồ sơ này.</td></tr>`;

            tableBlockHtml = `
                <div style="border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                    <table style="width:100%; border-collapse:collapse; font-size:12.5px;">
                        <thead>
                            <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-weight:700; font-size:12px; text-transform:uppercase;">
                                <th style="width:75px; text-align:center; padding:9px 10px;">Cột</th>
                                <th style="padding:9px 10px; text-align:left;">Nội dung chỉ tiêu chế độ</th>
                                <th style="width:220px; text-align:right; padding:9px 14px;">Số tiền / Giá trị (File Excel)</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRowsHtml}
                        </tbody>
                    </table>
                </div>
            `;
        } else {
            let tableRowsHtml = (comparisons && comparisons.length > 0) ? comparisons.map(c => {
                let rowStyle = c.hasErr ? 'background:#fff8f8; border-left:3px solid #ef4444;' : '';
                return `
                    <tr style="${rowStyle} border-bottom:1px solid #e2e8f0;">
                        <td style="text-align:center; font-weight:700; color:#1e3a5f; padding:8px 10px;">${escapeHtml(c.col)}</td>
                        <td style="padding:8px 10px; font-weight:600; color:#1e293b;">${escapeHtml(c.title)}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:600; color:${c.hasErr ? '#b91c1c' : '#334155'}; font-size:13px; white-space:nowrap;">${escapeHtml(c.actual)}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:700; color:#15803d; font-size:13px; white-space:nowrap;">${escapeHtml(c.expected)}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:700; color:${c.hasErr ? '#b91c1c' : '#64748b'}; font-size:12.5px; white-space:nowrap;">${escapeHtml(c.diff)}</td>
                        <td style="padding:8px 10px; font-size:12px; color:#475569;">${escapeHtml(c.formula)}</td>
                    </tr>`;
            }).join('') : `<tr><td colspan="6" style="text-align:center; padding:24px 16px; color:#64748b; font-size:13px;">Không có dữ liệu đối chiếu chi tiết cho hồ sơ này.</td></tr>`;

            tableBlockHtml = `
                <div style="border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                    <table style="width:100%; border-collapse:collapse; font-size:12.5px;">
                        <thead>
                            <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-weight:700;">
                                <th style="width:75px; text-align:center; padding:9px 10px;">Cột</th>
                                <th style="padding:9px 10px; text-align:left;">Nội dung chỉ tiêu chế độ</th>
                                <th style="width:145px; text-align:right; padding:9px 10px; white-space:nowrap;">File Excel (RAW)</th>
                                <th style="width:145px; text-align:right; padding:9px 10px; white-space:nowrap;">Sau thẩm định</th>
                                <th style="width:130px; text-align:right; padding:9px 10px; white-space:nowrap;">Chênh lệch</th>
                                <th style="padding:9px 10px; text-align:left;">Công thức quy định</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRowsHtml}
                        </tbody>
                    </table>
                </div>
            `;
        }

        bodyEl.innerHTML = `
            ${snapshotNoticeHtml}
            ${alertBoxHtml}

            <!-- Thẻ thông tin quân nhân -->
            <div style="background:#f8fafc; border:1px solid #cbd5e1; border-radius:6px; padding:12px 16px; margin-bottom:14px;">
                ${sheetKey === 'I.5' ? `
                <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:10px; font-size:12.5px;">
                    <div>Họ và tên: <strong style="color:#0f172a;">${escapeHtml(r.hoTen)}</strong></div>
                    <div>Cấp bậc: <strong>${escapeHtml(r.capBac || '---')}</strong></div>
                    <div>Chức vụ: <strong>${escapeHtml(r.chucVu || '---')}</strong></div>
                    <div>Đơn vị: <strong>${escapeHtml(r.donVi || r.group || '---')}</strong></div>

                    <div>Hệ số lương: <strong style="color:#1e3a5f;">${BQPValidation.fmtNum(r.heSoLuong || (r.rawCols && r.rawCols[6]))}</strong></div>
                    <div>Hệ số chênh lệch: <strong>${BQPValidation.fmtNum(r.heSoChenhLechBaoLuu || (r.rawCols && r.rawCols[7]))}</strong></div>
                    <div>Hệ số chức vụ: <strong>${BQPValidation.fmtNum(r.heSoChucVu || (r.rawCols && r.rawCols[8]))}</strong></div>
                    <div>Mức lương cơ sở: <strong>2.340.000 đ</strong></div>

                    <div>Thời điểm nhập ngũ: <strong>${dNhapNgu}</strong></div>
                    <div>Thời điểm nghỉ hưu: <strong>${dNghi}</strong></div>
                    <div>% PC trách nhiệm: <strong>${BQPValidation.fmtNum(((r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) > 1 ? (r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) : (r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) * 100))}%</strong></div>
                    <div>% PC đặc thù: <strong>${BQPValidation.fmtNum(((r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) > 1 ? (r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) : (r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) * 100))}%</strong></div>
                </div>
                ` : `
                <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:10px; font-size:12.5px;">
                    <div>Họ và tên: <strong style="color:#0f172a;">${escapeHtml(r.hoTen)}</strong></div>
                    <div>Cấp bậc: <strong>${escapeHtml(r.capBac || '---')}</strong></div>
                    <div>Chức vụ: <strong>${escapeHtml(r.chucVu || '---')}</strong></div>
                    <div>Đơn vị: <strong>${escapeHtml(r.group || '---')}</strong></div>

                    <div>Tháng năm sinh: <strong>${dNgaySinh}</strong></div>
                    ${!isExcelOnly ? `<div>Trần tuổi áp dụng: <strong style="color:#1e3a5f;">${tranTuoi} tuổi</strong></div>` : `<div>Mức lương tháng: <strong style="color:#15803d;">${BQPValidation.fmtMoney(r.luongThang)} đ</strong></div>`}
                    <div>Thời điểm nhập ngũ: <strong>${dNhapNgu}</strong></div>
                    <div>Thời điểm nghỉ: <strong>${dNghi}</strong></div>

                    <div>Sáp nhập / Giải thể: <strong>${dSapNhap}</strong></div>
                    ${!isExcelOnly ? `<div>Lương tháng hưởng: <strong style="color:#15803d;">${BQPValidation.fmtMoney(r.luongThang)} đ</strong></div>` : `<div>Dòng trong Excel: <strong>Dòng ${displayRow}</strong></div>`}
                    ${!isExcelOnly ? `<div>Dòng trong Excel: <strong>Dòng ${displayRow}</strong></div>` : `<div>Biểu mẫu: <strong style="color:#1e3a5f;">Phụ lục ${sheetKey}</strong></div>`}
                    ${!isExcelOnly ? `<div>Loại phụ lục: <strong style="color:#1e3a5f;">Phụ lục ${sheetKey}</strong></div>` : `<div>Trạng thái: <strong style="color:#0369a1;">Hồ sơ gốc từ Excel</strong></div>`}
                </div>
                `}
            </div>

            <!-- Tổng kết tiền tệ -->
            ${summaryMoneyHtml}

            <!-- Số tiền bằng chữ -->
            ${chuTienHtml}

            <!-- Bảng dữ liệu -->
            ${tableBlockHtml}
        `;

        // Thanh điều hướng (Trước / Sau)
        if (navEl && idx !== -1) {
            let prevDisabled = idx <= 0 ? 'disabled' : '';
            let nextDisabled = idx >= totalRecords - 1 ? 'disabled' : '';
            let paramBool = isExcelOnly ? 'true' : 'false';
            navEl.innerHTML = `
                <button type="button" class="btn-top" onclick="ValidationUI.openCompareModal(${idx - 1}, ${paramBool})" ${prevDisabled} style="padding:5px 12px; font-size:12px; font-weight:600; cursor:pointer;">
                    Đối tượng trước
                </button>
                <span style="font-size:12.5px; color:#64748b;">
                    Hồ sơ <strong>${idx + 1}</strong> / ${totalRecords}
                </span>
                <button type="button" class="btn-top" onclick="ValidationUI.openCompareModal(${idx + 1}, ${paramBool})" ${nextDisabled} style="padding:5px 12px; font-size:12px; font-weight:600; cursor:pointer;">
                    Đối tượng tiếp theo
                </button>
            `;
        }

        modal.classList.add('open');
    },

    closeErrorModal() {
        let modal = document.getElementById("valErrorModal");
        if (modal) modal.classList.remove('open');
    },

    openSaveModal() {
        let modal = document.getElementById("valSaveModal");
        if (modal) {
            let records = this.currentRecords || [];
            let units = (typeof StorageManager !== 'undefined') ? StorageManager.getUnits() : [];
            if (typeof UnitTreeManager !== 'undefined') {
                let flat = (typeof UnitTreeManager.buildFlatTree === 'function') 
                    ? UnitTreeManager.buildFlatTree(units)
                    : units;
                let activeParents = flat.filter(u => u.isActive !== false && (u.level || u.computedLevel || 1) <= 3);
                let sel = document.getElementById("val_save_parent_select");
                if (sel) {
                    sel.innerHTML = '<option value="">-- Chọn Đơn vị cha tiếp nhận (Cấp 1 - 3) --</option>' +
                        activeParents.map(u => {
                            const lvl = u.level || u.computedLevel || 1;
                            const prefix = '— '.repeat(u.depth || 0);
                            const safeName = typeof escapeHtml === 'function' ? escapeHtml(u.name) : u.name;
                            const safeId = typeof escapeAttr === 'function' ? escapeAttr(u.id) : u.id;
                            return `<option value="${safeId}">${prefix}C${lvl}: ${safeName}</option>`;
                        }).join('');
                }
            }

            // Điền tên đơn vị theo file mặc định từ tên file Excel (bỏ đuôi mở rộng)
            let fileUnitInput = document.getElementById("val_save_file_unit_name");
            if (fileUnitInput) {
                let defaultName = '';
                if (this.currentFile && this.currentFile.name) {
                    defaultName = this.currentFile.name.replace(/\.[^/.]+$/, '').trim();
                } else {
                    defaultName = 'Dữ liệu thẩm định ' + new Date().toLocaleDateString('vi-VN');
                }
                fileUnitInput.value = defaultName;
            }

            // Cập nhật số lượng đối tượng dự kiến ghi vào cả 2 danh sách
            let countEl = document.getElementById("val_save_record_count_text");
            if (countEl) {
                countEl.textContent = `${records.length} đối tượng × 2 danh sách = ${records.length * 2} bản ghi`;
            }
            let btnSave = document.getElementById("btn_execute_save_records");
            if (btnSave) {
                btnSave.disabled = false;
                btnSave.textContent = '⚡ Lưu đồng thời vào 2 danh sách';
            }

            modal.classList.add('open');
        } else {
            // Fallback nếu không có modal
            this.saveRecords('all');
        }
    },

    mapValidationRecordToRawRecord(record, context = {}) {
        let r = record || {};
        let uName = context.unitName || ((r && r.group) || 'BQP').trim();
        let actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
        let roundAct = Math.round(Number(actMoney) || 0);

        // Chỉ tính lỗi đọc nguồn (như cellErrors do đọc công thức/parse lỗi), KHÔNG lấy lỗi thẩm định nghiệp vụ
        let hasReadErr = Boolean(r.cellErrors && Object.keys(r.cellErrors).length > 0);

        let birthDate = BQPValidation.formatDate(r.ngaySinh || (r.input && r.input.ngaySinh));
        let enlistDate = BQPValidation.formatDate(r.nhapNgu || (r.input && r.input.nhapNgu));
        let mergerDate = BQPValidation.formatDate(r.sapNhap || (r.input && r.input.sapNhap) || (r.input && r.input.thoiGianDonViSapNhapGiaiThe));
        let retireDate = BQPValidation.formatDate(r.thoiDiemNghi || (r.input && r.input.thoiDiemNghi));
        // Danh sách RAW lưu lương kê khai C9, không lấy lương chuẩn đã đối chiếu với Phụ lục I.5
        const declaredC9 = (r.sheet !== 'I.5' && r.rawCols && typeof r.rawCols[9] === 'number') ? r.rawCols[9] : null;
        const rawSalary = Math.round(Number(declaredC9 != null ? declaredC9 : (r.luongThang || (r.input && r.input.luongThang) || 0)));

        return {
            id: 'excel_' + (r.id ? String(r.id).replace(/^val_/, '') : (Date.now() + '_' + Math.random().toString(36).substr(2, 6))),
            source: 'excel',
            sourceRow: r.rowIndex || r.sourceRow || null,
            unitName: uName,
            unit: uName,
            unitId: context.unitId || null,
            sheetType: r.sheet || r.sheetSource || r.sheetType || 'I.1',
            sheet: r.sheet || r.sheetSource || r.sheetType || 'I.1',
            categoryCode: r.categoryCode || 'SQ',
            policyCode: r.policyCode || 'ND178',
            classificationSource: 'EXCEL_ORIGINAL',
            classificationConfidence: 1.0,
            fullName: r.hoTen || (r.input && r.input.hoTen) || '[Lỗi ô: Thiếu họ tên]',
            hoTen: r.hoTen || (r.input && r.input.hoTen) || '[Lỗi ô: Thiếu họ tên]',
            rank: r.capBac || (r.input && r.input.capBac) || '',
            capBac: r.capBac || (r.input && r.input.capBac) || '',
            position: r.chucVu || (r.input && r.input.chucVu) || '',
            chucVu: r.chucVu || (r.input && r.input.chucVu) || '',
            birthDate: birthDate,
            ngaySinh: birthDate,
            enlistmentDate: enlistDate,
            nhapNgu: enlistDate,
            mergerDate: mergerDate,
            sapNhap: mergerDate,
            recruitmentDate: mergerDate,
            retirementDate: retireDate,
            thoiDiemNghi: retireDate,
            demobilizationDate: retireDate,
            monthlySalary: rawSalary,
            luongThang: rawSalary,
            actualTotal: roundAct,
            tongTienThucTe: roundAct,
            calculatedTotal: 0,
            tongTienTinhLai: 0,
            difference: 0,
            diff: 0,
            hasErrors: hasReadErr,
            rawColumnsJson: JSON.stringify(r.rawCols || {}),
            rawCols: r.rawCols || {},
            inputJson: JSON.stringify(r.input || {
                hoTen: r.hoTen,
                capBac: r.capBac,
                chucVu: r.chucVu,
                ngaySinh: birthDate,
                nhapNgu: enlistDate,
                sapNhap: mergerDate,
                thoiDiemNghi: retireDate,
                luongThang: rawSalary
            }),
            input: r.input || {
                hoTen: r.hoTen,
                capBac: r.capBac,
                chucVu: r.chucVu,
                ngaySinh: birthDate,
                nhapNgu: enlistDate,
                sapNhap: mergerDate,
                thoiDiemNghi: retireDate,
                luongThang: rawSalary
            },
            resultJson: JSON.stringify({ tongTien: roundAct }),
            result: { tongTien: roundAct },
            createdAt: context.nowIso || new Date().toISOString()
        };
    },

    mapValidationRecordToValidatedRecord(record, context = {}) {
        let r = record || {};
        let uName = context.unitName || ((r && r.group) || 'BQP').trim();
        let actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
        let expMoney = r.tongTienTinhLai != null ? r.tongTienTinhLai : actMoney;
        let roundAct = Math.round(Number(actMoney) || 0);
        let roundExp = Math.round(Number(expMoney) || 0);
        let hasErr = Boolean(r.hasErrors || (r.errorDetails && r.errorDetails.length > 0) || (r.cellErrors && Object.keys(r.cellErrors).length > 0));

        let birthDate = BQPValidation.formatDate(r.ngaySinh || (r.input && r.input.ngaySinh));
        let enlistDate = BQPValidation.formatDate(r.nhapNgu || (r.input && r.input.nhapNgu));
        let mergerDate = BQPValidation.formatDate(r.sapNhap || (r.input && r.input.sapNhap) || (r.input && r.input.thoiGianDonViSapNhapGiaiThe));
        let retireDate = BQPValidation.formatDate(r.thoiDiemNghi || (r.input && r.input.thoiDiemNghi));

        let valSnapshot = {
            schemaVersion: '1.0',
            ruleVersion: '2024_ND178',
            comparisons: r.comparisons || (r.valRes && r.valRes.comparisons) || [],
            expected: r.expected || (r.valRes && r.valRes.exp) || {},
            errorDetails: BQPValidation.normalizeErrorDetails(r.errorDetails || (r.valRes && r.valRes.errorDetails)),
            cellErrors: r.cellErrors || (r.valRes && r.valRes.cellErrors) || {}
        };
        let resObj = Object.assign({}, r.result || { tongTien: roundExp }, {
            tongTien: roundExp,
            validationSnapshot: valSnapshot
        });
        let errModels = (r.errorDetails || []).map((err) => {
            if (typeof err === 'object' && err !== null) {
                return {
                    recordId: r.id,
                    columnNumber: err.columnNumber || null,
                    errorCode: err.errorCode || 'VAL_DIFF',
                    ruleCode: err.ruleCode || 'BQP_VAL_RULE',
                    message: err.message || JSON.stringify(err),
                    actualValue: err.actualValue != null ? String(err.actualValue) : null,
                    expectedValue: err.expectedValue != null ? String(err.expectedValue) : null
                };
            }
            let colNum = null;
            let matchCol = String(err).match(/Cột\s*(\d+)/i);
            if (matchCol) colNum = parseInt(matchCol[1], 10);
            return {
                recordId: r.id,
                columnNumber: colNum,
                errorCode: 'VAL_DIFF',
                ruleCode: 'BQP_VAL_RULE',
                message: String(err),
                actualValue: null,
                expectedValue: null
            };
        });

        return {
            id: r.id || ('val_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
            source: 'validated',
            sourceRow: r.rowIndex || r.sourceRow || null,
            unitName: uName,
            unit: uName,
            unitId: context.unitId || null,
            sheetType: r.sheet || r.sheetSource || r.sheetType || 'I.1',
            sheet: r.sheet || r.sheetSource || r.sheetType || 'I.1',
            categoryCode: r.categoryCode || 'SQ',
            policyCode: r.policyCode || 'ND178',
            classificationSource: 'VALIDATED_EXCEL',
            classificationConfidence: 1.0,
            fullName: r.hoTen || (r.input && r.input.hoTen) || '[Lỗi ô: Thiếu họ tên]',
            hoTen: r.hoTen || (r.input && r.input.hoTen) || '[Lỗi ô: Thiếu họ tên]',
            rank: r.capBac || (r.input && r.input.capBac) || '',
            capBac: r.capBac || (r.input && r.input.capBac) || '',
            position: r.chucVu || (r.input && r.input.chucVu) || '',
            chucVu: r.chucVu || (r.input && r.input.chucVu) || '',
            birthDate: birthDate,
            ngaySinh: birthDate,
            enlistmentDate: enlistDate,
            nhapNgu: enlistDate,
            mergerDate: mergerDate,
            sapNhap: mergerDate,
            recruitmentDate: mergerDate,
            retirementDate: retireDate,
            thoiDiemNghi: retireDate,
            demobilizationDate: retireDate,
            monthlySalary: Math.round(Number(r.luongThang || (r.input && r.input.luongThang) || 0)),
            luongThang: Math.round(Number(r.luongThang || (r.input && r.input.luongThang) || 0)),
            actualTotal: roundAct,
            tongTienThucTe: roundAct,
            calculatedTotal: roundExp,
            tongTienTinhLai: roundExp,
            tongTien: roundExp,
            difference: roundExp - roundAct,
            diff: roundExp - roundAct,
            hasErrors: hasErr,
            rawColumnsJson: JSON.stringify(r.rawCols || {}),
            rawCols: r.rawCols || {},
            inputJson: JSON.stringify(r.input || {
                hoTen: r.hoTen,
                capBac: r.capBac,
                chucVu: r.chucVu,
                ngaySinh: birthDate,
                nhapNgu: enlistDate,
                sapNhap: mergerDate,
                thoiDiemNghi: retireDate,
                luongThang: r.luongThang || 0
            }),
            input: r.input || {
                hoTen: r.hoTen,
                capBac: r.capBac,
                chucVu: r.chucVu,
                ngaySinh: birthDate,
                nhapNgu: enlistDate,
                sapNhap: mergerDate,
                thoiDiemNghi: retireDate,
                luongThang: r.luongThang || 0
            },
            resultJson: JSON.stringify(resObj),
            result: resObj,
            expected: r.expected || (r.valRes && r.valRes.exp) || {},
            comparisons: r.comparisons || (r.valRes && r.valRes.comparisons) || [],
            errorDetails: errModels,
            cellErrors: r.cellErrors || {},
            createdAt: context.nowIso || new Date().toISOString()
        };
    },

    closeSaveModal() {
        let modal = document.getElementById("valSaveModal");
        if (modal) modal.classList.remove('open');
    },

    onUnitModeRadioChange() {
        // Toggle UI modes if needed
    },

    openQuickAddUnitDialog() {
        const modal = document.getElementById('valQuickAddUnitModal');
        if (!modal) return;

        const nameInput = document.getElementById('val_quick_unit_name');
        const errEl = document.getElementById('val_quick_unit_error');
        const btnSubmit = document.getElementById('btn_confirm_quick_unit');
        const parentSel = document.getElementById('val_quick_unit_parent');

        if (nameInput) {
            nameInput.value = '';
            nameInput.style.borderColor = 'var(--border)';
        }
        if (errEl) {
            errEl.textContent = '';
            errEl.style.display = 'none';
        }
        if (btnSubmit) {
            btnSubmit.disabled = false;
        }

        // Nạp danh sách đơn vị cha đang hoạt động (chỉ cấp 1 & 2 để đơn vị mới nằm trong cấp 1-3)
        if (parentSel && typeof StorageManager !== 'undefined') {
            const units = StorageManager.getUnits().filter(u => u.isActive !== false);
            const validParents = units.filter(u => !u.parentId || (units.find(p => p.id === u.parentId && !p.parentId)));
            parentSel.innerHTML = '<option value="">Cấp 1 (Đơn vị độc lập - Cấp cao nhất)</option>' +
                validParents.map(u => `<option value="${u.id}">${typeof escapeHtml === 'function' ? escapeHtml(u.name) : u.name} (Cấp ${u.level || (u.parentId ? 2 : 1)})</option>`).join('');

            const saveParentSel = document.getElementById('val_save_parent_select');
            if (saveParentSel && saveParentSel.value) {
                parentSel.value = saveParentSel.value;
            }
        }

        modal.classList.add('open');

        setTimeout(() => {
            if (nameInput) nameInput.focus();
        }, 50);

        if (!this._quickAddKeyBound) {
            this._quickAddKeyBound = true;
            modal.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    this.confirmQuickAddUnit();
                } else if (e.key === 'Escape') {
                    e.preventDefault();
                    e.stopPropagation();
                    this.closeQuickAddUnitDialog();
                }
            });
        }
    },

    async saveRecords(mode = 'all', saveMode = 'append') {
        let btnSave = document.getElementById("btn_execute_save_records");
        try {
            let records = this.currentRecords || [];
            if (records.length === 0) return;

            let toSave = records;
            if (toSave.length === 0) {
                notifyWarning('Không có hồ sơ nào thỏa mãn điều kiện lưu.');
                return;
            }

            if (typeof StorageManager === 'undefined') {
                notifyError('Hệ thống lưu trữ chưa sẵn sàng.');
                return;
            }

            if (btnSave) {
                btnSave.disabled = true;
                btnSave.textContent = 'Đang lưu đồng thời 2 danh sách...';
            }

            // Lấy thông tin đơn vị được chọn từ form lưu nếu có
            let parentUnitId = null;
            let parentSel = document.getElementById("val_save_parent_select");
            if (parentSel && parentSel.value) parentUnitId = parentSel.value;

            let units = StorageManager.getUnits();
            let targetParent = parentUnitId ? units.find(u => u.id === parentUnitId) : units.find(u => !u.parentId && u.isActive !== false);
            if (!targetParent && units.length > 0) {
                targetParent = units.find(u => u.isActive !== false) || units[0];
            }
            if (!targetParent || targetParent.isActive === false) {
                notifyWarning("Vui lòng chọn hoặc tạo đơn vị tiếp nhận đang hoạt động trước khi lưu!");
                if (btnSave) {
                    btnSave.disabled = false;
                    btnSave.textContent = '⚡ Lưu đồng thời vào 2 danh sách';
                }
                return;
            }

            // Lấy tên đơn vị theo file (Cấp 2)
            let fileUnitName = '';
            let fileUnitInput = document.getElementById("val_save_file_unit_name");
            if (fileUnitInput && fileUnitInput.value && fileUnitInput.value.trim()) {
                fileUnitName = fileUnitInput.value.trim();
            } else if (this.currentFile && this.currentFile.name) {
                fileUnitName = this.currentFile.name.replace(/\\.[^/.]+$/, '').trim();
            } else {
                fileUnitName = 'Dữ liệu thẩm định';
            }

            const uniqueGroupNames = [...new Set(toSave.map(r => ((r && r.group) || 'BQP').trim()))];
            const nowIso = new Date().toISOString();

            // Ánh xạ dữ liệu qua 2 mapper thuần túy
            const validatedRecords = toSave.map(r => this.mapValidationRecordToValidatedRecord(r, {
                unitName: ((r && r.group) || 'BQP').trim(),
                nowIso: nowIso
            }));

            const excelRecords = toSave.map(r => this.mapValidationRecordToRawRecord(r, {
                unitName: ((r && r.group) || 'BQP').trim(),
                nowIso: nowIso
            }));

            // Nếu đang kết nối Backend SQLite: sử dụng giao dịch nguyên tử ImportService.commitBatch (atomic: true)
            if (typeof BqpStorageAdapter !== 'undefined' && BqpStorageAdapter.isSqlite) {
                let fileName = (this.currentFile && this.currentFile.name) || 'danh_sach_tham_dinh.xlsx';
                let fileHash = '';

                if (this.currentFile) {
                    try {
                        const arrayBuffer = await this.currentFile.arrayBuffer();
                        if (window.crypto && window.crypto.subtle) {
                            const hashBuf = await crypto.subtle.digest('SHA-256', arrayBuffer);
                            fileHash = Array.from(new Uint8Array(hashBuf))
                                .map(b => b.toString(16).padStart(2, '0'))
                                .join('')
                                .toLowerCase();
                        }
                    } catch (hashErr) {
                        console.warn('Lỗi tính fileHash:', hashErr);
                    }
                }
                if (!fileHash) {
                    fileHash = 'VAL_' + fileName + '_' + toSave.length;
                }

                let duplicateAction = 'NEW';
                try {
                    // Kiểm tra cả 2 hash (validated & excel)
                    const [chkVal, chkExc] = await Promise.all([
                        fetch(`/api/imports/by-hash/${encodeURIComponent(fileHash)}`),
                        fetch(`/api/imports/by-hash/${encodeURIComponent(fileHash + '_EXCEL')}`)
                    ]);

                    let existingVal = chkVal.ok ? await chkVal.json().catch(() => null) : null;
                    let existingExc = chkExc.ok ? await chkExc.json().catch(() => null) : null;

                    if ((existingVal && existingVal.id) || (existingExc && existingExc.id)) {
                        let replaceChoice = await SafeConfirmModal.choose({
                            title: 'Tệp tin đã từng được lưu',
                            message: `Tệp tin "${fileName}" đã từng được lưu vào hệ thống trước đó.\nChọn phương án xử lý cặp hồ sơ cũ:`,
                            options: [
                                { value: 'REPLACE', label: 'Thay thế (REPLACE) cả 2 danh sách cũ', variant: 'danger' },
                                { value: 'SKIP', label: 'Bỏ qua (Hủy lưu)', variant: 'secondary' }
                            ],
                            dismissValue: 'SKIP'
                        });
                        if (replaceChoice !== 'REPLACE') {
                            this.closeSaveModal();
                            return;
                        }
                        duplicateAction = 'REPLACE';
                    }
                } catch (chkErr) {
                    console.warn('Không thể kiểm tra file hash:', chkErr);
                }

                const internalUnitsDto = uniqueGroupNames.map(name => ({ name: name, code: null }));

                const filesToCommit = [
                    {
                        fileName: fileName,
                        fileHash: fileHash,
                        displayUnitName: fileUnitName,
                        parentUnitId: targetParent.id,
                        parserMode: 'STANDARD_CTC',
                        templateSignature: 'VAL_26_9',
                        duplicateAction: duplicateAction,
                        internalUnits: internalUnitsDto,
                        records: validatedRecords
                    },
                    {
                        fileName: fileName + ' [Gốc]',
                        fileHash: fileHash ? (fileHash + '_EXCEL') : ('EXCEL_' + Date.now()),
                        displayUnitName: fileUnitName,
                        parentUnitId: targetParent.id,
                        parserMode: 'STANDARD_CTC',
                        templateSignature: 'VAL_26_9',
                        duplicateAction: duplicateAction,
                        internalUnits: internalUnitsDto,
                        records: excelRecords
                    }
                ];

                const batchPayload = {
                    atomic: true,
                    operationType: 'DUAL_SAVE_VALIDATION',
                    commonParentUnitId: targetParent.id,
                    files: filesToCommit
                };

                const resp = await fetch('/api/imports/commit-batch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(batchPayload)
                });

                if (!resp.ok) {
                    const err = await resp.json().catch(() => ({}));
                    throw new Error(err.message || 'Lỗi server khi commit batch: ' + resp.status);
                }

                const batchResult = await resp.json();
                if (batchResult.failedCount > 0 || !batchResult.committed) {
                    const failMsgs = (batchResult.fileResults || []).filter(f => f.status === 'FAILED').map(f => f.message).join('\n');
                    throw new Error('Giao dịch lưu đồng thời thất bại (đã hoàn tác toàn bộ): ' + (failMsgs || batchResult.message));
                }

                // Đồng bộ lại toàn bộ dữ liệu từ SQLite
                await BqpStorageAdapter.syncUnitsFromSqlite();
                await BqpStorageAdapter.syncRecordsFromSqlite();
                await BqpStorageAdapter.updateRecordCountsFromSqlite();

            } else {
                // Chế độ Offline Fallback (LocalStorage / In-Memory): Bảo đảm tính nguyên tử (Rollback nếu lỗi)
                const snapshotValidated = JSON.parse(JSON.stringify(StorageManager.getRecordsBySource('validated') || []));
                const snapshotExcel = JSON.parse(JSON.stringify(StorageManager.getRecordsBySource('excel') || []));

                let effectiveParentId = targetParent ? targetParent.id : null;
                if (fileUnitName && targetParent && targetParent.name !== fileUnitName) {
                    try {
                        effectiveParentId = await StorageManager.addUnit(fileUnitName, targetParent.id);
                    } catch (fErr) {
                        console.warn("Lỗi tạo file unit offline:", fErr);
                    }
                }
                const groupUnitMap = new Map();
                for (const uName of uniqueGroupNames) {
                    try {
                        let uId = await StorageManager.addUnit(uName, effectiveParentId);
                        groupUnitMap.set(uName, uId);
                    } catch (uErr) {
                        console.warn("Lỗi chuẩn bị đơn vị:", uName, uErr);
                    }
                }

                try {
                    validatedRecords.forEach(r => {
                        let uName = r.unitName || 'BQP';
                        r.unitId = groupUnitMap.get(uName) || effectiveParentId;
                        r.donVi = targetParent ? `${targetParent.name} > ${uName}` : uName;
                    });
                    excelRecords.forEach(r => {
                        let uName = r.unitName || 'BQP';
                        r.unitId = groupUnitMap.get(uName) || effectiveParentId;
                        r.donVi = targetParent ? `${targetParent.name} > ${uName}` : uName;
                    });

                    await StorageManager.addRecordsBySource('validated', validatedRecords, saveMode);
                    await StorageManager.addRecordsBySource('excel', excelRecords, saveMode);
                } catch (offlineErr) {
                    // Rollback snapshot cả 2 danh sách
                    await StorageManager.addRecordsBySource('validated', snapshotValidated, 'replace');
                    await StorageManager.addRecordsBySource('excel', snapshotExcel, 'replace');
                    throw new Error('Lỗi lưu offline: ' + offlineErr.message + ' (Đã khôi phục trạng thái ban đầu)');
                }
            }

            if (typeof updateTabCount === 'function') updateTabCount();
            if (typeof refreshAllUnitSelects === 'function') refreshAllUnitSelects();
            if (typeof RecordsUI !== 'undefined') RecordsUI.render();

            this.closeSaveModal();

            notifySuccess(`Đã lưu đồng thời ${toSave.length} hồ sơ Chưa thẩm định (RAW) và ${toSave.length} hồ sơ Sau thẩm định.`, {
                title: 'Lưu hồ sơ thành công',
                duration: 6000,
                actionLabel: 'Xem danh sách',
                onAction: () => {
                    activateMainTab('pane_list');
                    if (typeof RecordsUI !== 'undefined' && RecordsUI.switchSource) {
                        RecordsUI.switchSource('validated');
                    }
                }
            });
        } catch (err) {
            console.error("Lỗi khi lưu vào phần mềm:", err);
            notifyError("Có lỗi trong quá trình lưu hồ sơ: " + err.message);
        } finally {
            if (btnSave) {
                btnSave.disabled = false;
                btnSave.textContent = '⚡ Lưu đồng thời vào 2 danh sách';
            }
        }
    },


        executeSaveToRecords() {
        this.saveRecords('all', 'append');
    },

    closeQuickAddUnitDialog() {
        const modal = document.getElementById('valQuickAddUnitModal');
        if (modal) modal.classList.remove('open');
        const btnOpen = document.getElementById('btn_val_quick_add_unit') || document.querySelector('button[onclick*="openQuickAddUnitDialog"]');
        if (btnOpen) btnOpen.focus();
    },

    async confirmQuickAddUnit() {
        const nameInput = document.getElementById('val_quick_unit_name');
        const errEl = document.getElementById('val_quick_unit_error');
        const btnSubmit = document.getElementById('btn_confirm_quick_unit');
        const parentSel = document.getElementById('val_quick_unit_parent');

        const name = nameInput ? nameInput.value.trim() : '';
        const parentId = (parentSel && parentSel.value) ? parentSel.value : null;

        if (!name) {
            if (errEl) {
                errEl.textContent = 'Vui lòng nhập tên đơn vị.';
                errEl.style.display = 'block';
            }
            if (nameInput) {
                nameInput.style.borderColor = 'var(--danger)';
                nameInput.focus();
            }
            return;
        }

        if (typeof StorageManager === 'undefined') {
            notifyError('Hệ thống lưu trữ chưa sẵn sàng.');
            return;
        }

        // Kiểm tra tên trùng trong cùng cấp trực thuộc
        const existingUnits = StorageManager.getUnits();
        const isDuplicate = existingUnits.some(u => 
            u.name.trim().toLowerCase() === name.toLowerCase() && 
            (u.parentId || null) === (parentId || null)
        );
        if (isDuplicate) {
            if (errEl) {
                errEl.textContent = 'Đơn vị với tên này đã tồn tại trong cùng cấp trực thuộc.';
                errEl.style.display = 'block';
            }
            if (nameInput) {
                nameInput.style.borderColor = 'var(--danger)';
                nameInput.focus();
            }
            return;
        }

        if (btnSubmit) btnSubmit.disabled = true;

        try {
            const newId = await StorageManager.addUnit(name, parentId);
            if (typeof refreshAllUnitSelects === 'function') refreshAllUnitSelects();

            const sel = document.getElementById("val_save_parent_select");
            if (sel) {
                const units = StorageManager.getUnits().filter(u => u.isActive !== false);
                const parentUnits = units.filter(u => !u.parentId);
                sel.innerHTML = '<option value="">-- Chọn Đơn vị Cấp 1 tiếp nhận --</option>' +
                    parentUnits.map(u => `<option value="${u.id}">${typeof escapeHtml === 'function' ? escapeHtml(u.name) : u.name}</option>`).join('');
                sel.value = parentId || newId;
            }

            this.closeQuickAddUnitDialog();
            notifySuccess(`Đã tạo đơn vị "${name}" thành công.`);
        } catch (err) {
            console.error('[QuickAddUnit] Lỗi tạo đơn vị:', err);
            if (errEl) {
                errEl.textContent = 'Lỗi lưu đơn vị: ' + err.message;
                errEl.style.display = 'block';
            }
            notifyError('Lỗi khi tạo đơn vị: ' + err.message);
        } finally {
            if (btnSubmit) btnSubmit.disabled = false;
        }
    },

    async saveSingleRecord(recordId) {
        try {
            let r = this.currentRecords.find(x => x.id === recordId);
            if (!r) return;

            if (typeof StorageManager === 'undefined') return;

            let uName = (r && r.group) || 'BQP';
            let unitId = null;
            try {
                unitId = await StorageManager.addUnit(uName, null);
            } catch (uErr) {
                console.warn("Lỗi tạo đơn vị:", uErr);
            }

            let actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
            let expMoney = r.tongTienTinhLai != null ? r.tongTienTinhLai : actMoney;
            let hasErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0) || (r.cellErrors && Object.keys(r.cellErrors).length > 0);

            let recToSave = {
                id: r.id || ('val_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
                unitId: unitId,
                donVi: uName,
                group: uName,
                sheetSource: r.sheet || 'I.1',
                sheet: r.sheet || 'I.1',
                hoTen: r.hoTen || '[Lỗi ô: Thiếu họ tên]',
                capBac: r.capBac || '',
                chucVu: r.chucVu || '',
                ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                sapNhap: BQPValidation.formatDate(r.sapNhap),
                thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                luongThang: r.luongThang || 0,
                tongTienThucTe: actMoney,
                tongTienTinhLai: expMoney,
                tongTien: expMoney,
                diff: expMoney - actMoney,
                input: {
                    hoTen: r.hoTen,
                    capBac: r.capBac,
                    chucVu: r.chucVu,
                    ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                    nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                    sapNhap: BQPValidation.formatDate(r.sapNhap),
                    thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                    luongThang: r.luongThang || 0
                },
                result: {
                    tongTien: expMoney
                },
                hasErrors: hasErr,
                errorDetails: r.errorDetails || [],
                comparisons: r.comparisons || [],
                cellErrors: r.cellErrors || {},
                expected: r.expected || {},
                createdAt: new Date().toISOString()
            };

            await StorageManager.addRecordsBySource('validated', [recToSave]);

            if (typeof updateTabCount === 'function') updateTabCount();
            if (typeof RecordsUI !== 'undefined') RecordsUI.render();

            notifySuccess(`Đã lưu quân nhân "${recToSave.hoTen}" vào Danh sách đối tượng.`);
        } catch (err) {
            console.error("Lỗi khi lưu 1 hồ sơ:", err);
            notifyError("Có lỗi khi lưu hồ sơ: " + err.message);
        }
    },

    async deleteRecord(recordId) {
        const accepted = await SafeConfirmModal.confirm({
            title: 'Xóa đối tượng khỏi danh sách thẩm định',
            message: 'Xác nhận xóa đối tượng này khỏi danh sách thẩm định hiện tại?',
            tone: 'danger',
            confirmLabel: 'Xóa đối tượng',
            cancelLabel: 'Giữ lại'
        });
        if (!accepted) return;
        this.currentRecords = this.currentRecords.filter(r => r.id !== recordId);
        this.renderTable();
        notifySuccess('Đã xóa đối tượng khỏi danh sách thẩm định.');
    },

    async exportExcel() {
        if (!this.currentRecords || this.currentRecords.length === 0) {
            notifyWarning('Không có dữ liệu thẩm định để xuất file.');
            return;
        }

        let filterStatus = document.getElementById('vfilter_status') ? document.getElementById('vfilter_status').value : '';
        let filterSheet = document.getElementById('vfilter_sheet') ? document.getElementById('vfilter_sheet').value : '';
        let filterSearch = document.getElementById('vfilter_search') ? document.getElementById('vfilter_search').value.trim() : '';

        let isFiltered = Boolean(filterStatus || filterSheet || filterSearch || (this.currentFiltered && this.currentFiltered.length > 0 && this.currentFiltered.length < this.currentRecords.length));
        let recordsToExport = (this.currentFiltered && this.currentFiltered.length > 0) ? this.currentFiltered : this.currentRecords;

        if (recordsToExport.length === 0) {
            notifyWarning('Không có hồ sơ nào phù hợp với bộ lọc hiện tại để xuất file.');
            return;
        }

        const incompleteList = recordsToExport.filter(r => (r.valRes && (r.valRes.status === 'INCOMPLETE_INPUT' || r.valRes.isIncomplete)) || (r.tongTienThucTe == null && r.hasErrors));
        if (incompleteList.length > 0 && typeof window !== 'undefined' && window.ConfirmModal && typeof window.ConfirmModal.confirm === 'function') {
            const confirmed = await SafeConfirmModal.confirm({
                title: 'Cảnh báo hồ sơ chưa đủ dữ liệu',
                message: `Có ${incompleteList.length} hồ sơ chưa đủ dữ liệu hoặc ô công thức chưa được tính (thiếu kết quả cache) trong file Excel. Các hồ sơ này sẽ được ghi chú riêng trong file xuất và không có kết luận thừa/thiếu tiền. Bạn có muốn tiếp tục xuất file không?`,
                confirmText: 'Vẫn xuất file',
                cancelText: 'Xem lại',
                danger: false
            });
            if (!confirmed) return;
        }

        let suffix = 'Tat_Ca';
        if (isFiltered && recordsToExport.length < this.currentRecords.length) {
            if (filterStatus === 'valid') {
                suffix = 'Tinh_Dung';
            } else if (filterStatus === 'error_under') {
                suffix = 'Sai_Thieu';
            } else if (filterStatus === 'error_over') {
                suffix = 'Sai_Thua';
            } else if (filterStatus === 'error') {
                suffix = 'Lech_Chuan';
            } else {
                suffix = 'Da_Loc';
            }
            if (filterSheet) {
                suffix += '_' + filterSheet.replace('.', '');
            }
        }

        let exportContext;
        try {
            exportContext = await window.BQPReportTemplate.createExportContext();
        } catch (error) {
            notifyError(error.message);
            return { success: false, error: error.message };
        }

        return await BQPValidation.exportValidatedWorkbook(
            this.currentWb,
            this.currentFile?.name || 'Bao_cao.xlsx',
            recordsToExport,
            isFiltered,
            suffix,
            this.currentRecords,
            exportContext
        );
    },
    // Báo cáo đọc file: đối chiếu số người đọc được với số file tự khai và liệt kê dòng bị bỏ qua đáng ngờ.
    renderReadReport() {
        const el = document.getElementById('v_read_report');
        if (!el) return;
        const rep = this.readReport;
        if (!rep) { el.style.display = 'none'; el.innerHTML = ''; return; }
        const fmt = n => Number(n).toLocaleString('vi-VN');
        const perSheet = Object.entries(rep.persons).filter(([, n]) => n > 0).map(([k, n]) => `${k}: ${fmt(n)}`).join(' · ');
        const box = (bg, border, color, body) => `<div style="background:${bg}; border:1px solid ${border}; border-radius:6px; padding:10px 16px; font-size:13px; color:${color};">${body}</div>`;
        const head = `<strong>Đọc được ${fmt(rep.totalPersons)} hồ sơ</strong> <span style="opacity:.85;">(${escapeHtml(perSheet || 'không có')})</span>`;

        if (rep.ok) {
            el.innerHTML = rep.checked > 0
                ? box('#f0fdf4', '#bbf7d0', '#166534', `✓ ${head} — khớp với số người (đ/c) file tự khai ở ${fmt(rep.checked)} mục tiêu đề/dòng cộng.`)
                : box('#f8fafc', '#e2e8f0', '#475569', `${head} — file không ghi số người (đ/c) ở tiêu đề nên chưa có số để đối chiếu.`);
            el.style.display = 'block';
            return;
        }

        const limit = 12;
        const rowsOf = (items, cols) => items.slice(0, limit).map(x => `<tr>${cols(x).map(c => `<td style="padding:3px 8px; border-top:1px solid #fde68a;">${c}</td>`).join('')}</tr>`).join('')
            + (items.length > limit ? `<tr><td colspan="6" style="padding:3px 8px; border-top:1px solid #fde68a; font-style:italic;">… và ${items.length - limit} mục khác</td></tr>` : '');
        let body = `<div style="margin-bottom:6px;">⚠ ${head} — <strong>cần rà soát</strong>: có chỗ số người đọc được không khớp số file tự khai hoặc có dòng bị bỏ qua đáng ngờ.</div>`;
        if (rep.mismatches.length) {
            body += `<div style="font-weight:700; margin:6px 0 2px;">Lệch số người (${rep.mismatches.length})</div>
                <table style="border-collapse:collapse; font-size:12.5px; width:100%;"><thead><tr style="text-align:left;"><th style="padding:3px 8px;">Phụ lục</th><th style="padding:3px 8px;">Dòng</th><th style="padding:3px 8px;">Mục</th><th style="padding:3px 8px; text-align:right;">File ghi</th><th style="padding:3px 8px; text-align:right;">Đọc được</th><th style="padding:3px 8px; text-align:right;">Chênh</th></tr></thead><tbody>${rowsOf(rep.mismatches, m => [escapeHtml(m.sheet), m.row, escapeHtml(m.label), `<div style="text-align:right;">${fmt(m.declared)}</div>`, `<div style="text-align:right;">${fmt(m.counted)}</div>`, `<div style="text-align:right; font-weight:700;">${m.counted - m.declared > 0 ? '+' : ''}${fmt(m.counted - m.declared)}</div>`])}</tbody></table>`;
        }
        if (rep.suspicious.length) {
            body += `<div style="font-weight:700; margin:8px 0 2px;">Dòng bị bỏ qua đáng ngờ (${rep.suspicious.length})</div>
                <table style="border-collapse:collapse; font-size:12.5px; width:100%;"><tbody>${rowsOf(rep.suspicious, x => [escapeHtml(x.sheet), x.row, escapeHtml(x.label), escapeHtml(x.reason)])}</tbody></table>`;
        }
        body += `<div style="margin-top:6px; opacity:.85;">“Dòng” là số dòng trong sheet của file Excel đã nạp.</div>`;
        el.innerHTML = box('#fffbeb', '#fde68a', '#92400e', body);
        el.style.display = 'block';
        const n = rep.mismatches.length + rep.suspicious.length;
        notifyWarning(`Số người đọc được chưa khớp hoàn toàn với file: ${rep.mismatches.length} mục lệch, ${rep.suspicious.length} dòng bị bỏ qua đáng ngờ. Xem khung “Đọc được … hồ sơ” phía trên bảng.`);
    },
    showFormulaWarningBanner(report) {
        if (!report || (report.noResultFormulas === 0 && report.errorFormulas === 0)) return;
        notifyWarning(`Phát hiện ${report.noResultFormulas} ô công thức chưa tính kết quả và ${report.errorFormulas} ô bị lỗi. Xem hướng dẫn trên màn hình.`);
        
        if (typeof document === 'undefined' || !document.createElement || !document.getElementById) return;
        let banner = document.getElementById('formula_quality_banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'formula_quality_banner';
            const resultWrap = document.getElementById('validate_result_wrap');
            if (resultWrap && resultWrap.parentNode) {
                resultWrap.parentNode.insertBefore(banner, resultWrap);
            }
        }
        if (banner) {
            banner.style.display = 'block';
            banner.innerHTML = `
                <div style="margin-bottom:16px; padding:14px 18px; background:#fffbeb; border:1px solid #f59e0b; border-radius:6px; color:#92400e; font-size:13px; display:flex; align-items:center; justify-content:space-between; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
                    <div style="display:flex; align-items:flex-start; gap:12px;">
                        <span style="font-size:20px; line-height:1; font-weight:700;">⚠</span>
                        <div>
                            <div style="font-weight:700; font-size:13.5px; color:#78350f;">
                                CẢNH BÁO CHẤT LƯỢNG FILE EXCEL: Phát hiện ${report.noResultFormulas} ô công thức chưa được tính và ${report.errorFormulas} ô bị lỗi
                            </div>
                            <div style="font-size:12px; margin-top:3px; color:#b45309; line-height:1.4;">
                                Khuyến nghị: Mở file bằng <strong>Microsoft Excel</strong>, nhấn tổ hợp phím <strong>Ctrl + Alt + F9</strong> để tính toán lại toàn bộ bảng tính, rồi nhấn <strong>Ctrl + S</strong> để lưu file trước khi tải lại vào phần mềm.
                            </div>
                        </div>
                    </div>
                    <button type="button" onclick="ValidationUI.openFormulaIssuesModal()" style="padding:6px 14px; font-size:12px; font-weight:600; background:#fef3c7; color:#92400e; border:1px solid #d97706; border-radius:4px; cursor:pointer; white-space:nowrap; margin-left:16px;">
                        Xem chi tiết ${report.issues ? report.issues.length : 0} ô
                    </button>
                </div>
            `;
        }
    },
    hideFormulaWarningBanner() {
        if (typeof document === 'undefined' || !document.getElementById) return;
        let banner = document.getElementById('formula_quality_banner');
        if (banner) banner.style.display = 'none';
    },
    openFormulaIssuesModal() {
        const report = this.currentFormulaReport;
        if (!report || !report.issues || report.issues.length === 0) {
            notifyInfo('Không có ô công thức nào bị lỗi hoặc chưa tính kết quả.');
            return;
        }

        let modal = document.getElementById('formula_issues_modal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'formula_issues_modal';
            modal.style.cssText = 'position:fixed; top:0; left:0; width:100vw; height:100vh; background:rgba(15,23,42,0.6); z-index:99999; display:flex; align-items:center; justify-content:center; backdrop-filter:blur(2px);';
            document.body.appendChild(modal);
        }

        let rowsHtml = report.issues.map((iss, idx) => `
            <tr style="border-bottom:1px solid #e2e8f0; font-size:12px;">
                <td style="padding:8px 10px; text-align:center; color:#64748b;">${idx + 1}</td>
                <td style="padding:8px 12px; font-weight:600; color:#1e40af;">${iss.sheet || '---'}</td>
                <td style="padding:8px 12px; font-family:monospace; font-weight:700; color:#0f172a;">${iss.address || '---'}</td>
                <td style="padding:8px 12px; font-family:monospace; color:#334155; max-width:240px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${iss.formula || ''}">${iss.formula || '---'}</td>
                <td style="padding:8px 12px; text-align:center;">
                    <span style="display:inline-block; padding:2px 8px; border-radius:4px; font-size:11px; font-weight:600; background:${iss.status === 'FORMULA_NO_RESULT' ? '#fffbeb' : '#fef2f2'}; color:${iss.status === 'FORMULA_NO_RESULT' ? '#b45309' : '#b91c1c'}; border:1px solid ${iss.status === 'FORMULA_NO_RESULT' ? '#fde68a' : '#fecaca'};">
                        ${iss.status === 'FORMULA_NO_RESULT' ? 'Chưa tính' : (iss.errorCode || 'Lỗi ô')}
                    </span>
                </td>
            </tr>
        `).join('');

        modal.innerHTML = `
            <div style="background:#ffffff; width:720px; max-width:92vw; max-height:85vh; border-radius:8px; display:flex; flex-direction:column; box-shadow:0 20px 25px -5px rgba(0,0,0,0.2); overflow:hidden;">
                <div style="padding:14px 18px; background:#f8fafc; border-bottom:1px solid #e2e8f0; display:flex; justify-content:space-between; align-items:center;">
                    <div style="font-weight:700; font-size:14px; color:#0f172a;">
                        DANH SÁCH Ô CÔNG THỨC CHƯA TÍNH HOẶC BỊ LỖI (${report.issues.length} ô)
                    </div>
                    <button type="button" onclick="document.getElementById('formula_issues_modal').style.display='none'" style="border:none; background:transparent; font-size:20px; color:#64748b; cursor:pointer; line-height:1;">&times;</button>
                </div>
                <div style="padding:14px 18px; font-size:12.5px; color:#475569; background:#fffbeb; border-bottom:1px solid #fde68a;">
                    Các ô dưới đây không có kết quả cache trong file Excel. Vui lòng mở file bằng Excel, nhấn <strong>Ctrl + Alt + F9</strong> rồi Lưu lại.
                </div>
                <div style="flex:1; overflow-y:auto; padding:0;">
                    <table style="width:100%; border-collapse:collapse;">
                        <thead>
                            <tr style="background:#f1f5f9; border-bottom:1px solid #cbd5e1; font-size:11.5px; text-transform:uppercase; color:#475569;">
                                <th style="padding:8px 10px; width:45px; text-align:center;">STT</th>
                                <th style="padding:8px 12px; width:130px; text-align:left;">Biểu mẫu</th>
                                <th style="padding:8px 12px; width:80px; text-align:left;">Ô Excel</th>
                                <th style="padding:8px 12px; text-align:left;">Công thức</th>
                                <th style="padding:8px 12px; width:110px; text-align:center;">Trạng thái</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                </div>
                <div style="padding:10px 18px; background:#f8fafc; border-top:1px solid #e2e8f0; text-align:right;">
                    <button type="button" onclick="document.getElementById('formula_issues_modal').style.display='none'" style="padding:6px 16px; font-size:12.5px; font-weight:600; background:#0284c7; color:#ffffff; border:none; border-radius:4px; cursor:pointer;">
                        Đóng
                    </button>
                </div>
            </div>
        `;
        modal.style.display = 'flex';
    },
    reset() {
        this.hideFormulaWarningBanner();
        this.currentFile = null;
        this.currentWb = null;
        this.rawWb = null;
        this.isNormalized = false;
        this.normCounts = null;
        this.currentRecords = [];
        this.currentFiltered = [];
        this.currentResult = null;

        let fileInput = document.getElementById("validate_file_input");
        if (fileInput) fileInput.value = "";

        let bannerEl = document.getElementById("v_norm_banner");
        if (bannerEl) bannerEl.style.display = "none";

        let wrap = document.getElementById("validate_result_wrap");
        let dropzone = document.getElementById("validate_dropzone");
        if (wrap) wrap.style.display = "none";
        if (dropzone) dropzone.style.display = "block";
    }
};

if (typeof document !== 'undefined' && document.addEventListener) {
    document.addEventListener("DOMContentLoaded", function () {
        const dropzone = document.getElementById("validate_dropzone");
        if (dropzone) {
            dropzone.addEventListener("dragover", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.add("dragover");
            });
            dropzone.addEventListener("dragleave", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove("dragover");
            });
            dropzone.addEventListener("drop", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove("dragover");
                if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    ValidationUI.handleFileUpload(e);
                }
            });
        }
    });
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        BQPValidation: typeof BQPValidation !== 'undefined' ? BQPValidation : null,
        ValidationUI: typeof ValidationUI !== 'undefined' ? ValidationUI : null,
        CellStatus: (typeof BQPValidation !== 'undefined' && BQPValidation) ? BQPValidation.CellStatus : null,
        ExcelParser: (typeof BQPValidation !== 'undefined' && BQPValidation) ? BQPValidation.ExcelParser : null
    };
}

