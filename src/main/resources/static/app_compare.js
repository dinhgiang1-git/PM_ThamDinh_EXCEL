/*
 * BQP COMPARE — Module so sánh hai lần nộp file (Lần 1 ↔ Lần 2)
 *
 * - Lần 1: file gốc, được lưu lại (IndexedDB) làm mốc.
 * - Lần 2: file cập nhật; nạp xong hệ thống tự so sánh với Lần 1.
 * - Mỗi file được đọc + thẩm định bằng chính ValidationUI.handleFileUpload (chế độ headless),
 *   nên cách nhận diện người, cột và kết quả thẩm định giống hệt tab "Thẩm định Excel".
 */
(function () {
    'use strict';

    const DB_NAME = 'BQP_Compare_DB';
    const STORE = 'files';
    const PAGE_SIZE = 300;
    const SHEETS = ['I.1', 'I.2', 'I.3', 'I.5'];
    const SHEET_NAMES = {
        'I.1': 'Phụ lục I.1 (Nghỉ hưu NĐ 178)',
        'I.2': 'Phụ lục I.2 (Thôi việc NĐ 178)',
        'I.3': 'Phụ lục I.3 (Nghỉ hưu NĐ 177)',
        'I.5': 'Phụ lục I.5 (Lương & Phụ cấp)'
    };

    // Tên ngắn của các cột số liệu theo Phụ lục kèm theo hướng dẫn
    const COL_LABELS = {
        'I.1': {
            9: 'Lương tháng hiện hưởng',
            10: 'Số tháng nghỉ hưu trước tuổi', 11: 'Số năm nghỉ hưu trước tuổi', 12: 'Số năm công tác đóng BHXH',
            13: 'TC nghỉ sớm 12 tháng đầu (còn ≤5 năm)', 14: 'TC nghỉ sớm 12 tháng đầu (còn 5–10 năm)',
            15: 'TC nghỉ sớm từ tháng 13 (còn ≤5 năm)', 16: 'TC nghỉ sớm từ tháng 13 (còn 5–10 năm)',
            17: 'TC trước tuổi (2–5 năm): 5 tháng lương/năm nghỉ sớm', 18: 'TC trước tuổi (2–5 năm): 20(15) năm đầu',
            19: 'TC trước tuổi (2–5 năm): từ năm 21(16)', 20: 'TC trước tuổi (5–10 năm): 4 tháng lương/năm nghỉ sớm',
            21: 'TC trước tuổi (5–10 năm): 20(15) năm đầu', 22: 'TC trước tuổi (5–10 năm): từ năm 21(16)',
            23: 'Tổng cộng (Cột 23)'
        },
        'I.2': {
            9: 'Lương tháng hiện hưởng',
            10: 'Số tháng thôi việc', 11: 'Số năm hưởng trợ cấp',
            12: '12 tháng đầu: TC theo số tháng đóng BHXH', 13: '12 tháng đầu: TC theo số năm đóng BHXH', 14: '12 tháng đầu: TC tạo việc làm',
            15: 'Từ tháng 13: TC theo số tháng đóng BHXH', 16: 'Từ tháng 13: TC theo số năm đóng BHXH', 17: 'Từ tháng 13: TC tạo việc làm',
            18: 'Tổng cộng (Cột 18)'
        },
        'I.3': {
            9: 'Lương tháng hiện hưởng',
            10: 'Số năm công tác đóng BHXH', 11: 'Số năm nghỉ sớm hưởng trợ cấp',
            12: '5 tháng lương/năm nghỉ sớm', 13: '5 tháng lương cho 20(15) năm đầu', 14: 'Từ năm thứ 21(16) trở đi',
            15: 'Tổng cộng (Cột 15)'
        },
        'I.5': {
            11: 'Tỉ lệ % phụ cấp trách nhiệm', 12: 'Tỉ lệ % phụ cấp đặc thù',
            13: 'Hệ số chênh lệch bảo lưu (tiền)', 14: 'Tiền lương theo ngạch bậc', 15: 'Phụ cấp chức vụ',
            16: 'Phụ cấp thâm niên nghề', 17: 'Phụ cấp thâm niên vượt khung', 18: 'Phụ cấp trách nhiệm theo nghề',
            19: 'Phụ cấp công vụ', 20: 'Phụ cấp đặc thù', 21: 'Được nhận khác', 22: 'Lương tháng (Cột 22)'
        }
    };

    const KIND = {
        added: { label: 'Thêm mới', cls: 'cmp-b-added' },
        removed: { label: 'Bị loại', cls: 'cmp-b-removed' },
        changed: { label: 'Có thay đổi', cls: 'cmp-b-changed' },
        same: { label: 'Không đổi', cls: 'cmp-b-same' }
    };

    // ---------------------------------------------------------------- tiện ích
    const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const fmtNum = n => (typeof n === 'number' && isFinite(n)) ? n.toLocaleString('vi-VN', { maximumFractionDigits: 4 }) : '';
    const fmtMoney = n => (typeof n === 'number' && isFinite(n)) ? Math.round(n).toLocaleString('vi-VN') : '—';
    const fmtSigned = n => (n > 0 ? '+' : '') + Math.round(n).toLocaleString('vi-VN');
    const pad = n => String(n).padStart(2, '0');
    const isDate = v => v instanceof Date && !isNaN(v.getTime());
    const fmtDate = d => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
    const fmtTime = t => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())} ${fmtDate(d)}`; };
    const normText = s => String(s == null ? '' : s).normalize('NFC').toLowerCase().replace(/\s+/g, ' ').trim();

    function notify(kind, msg) {
        const fn = { error: 'notifyError', warning: 'notifyWarning', success: 'notifySuccess' }[kind];
        if (typeof window[fn] === 'function') window[fn](msg);
        else if (typeof globalThis[fn] === 'function') globalThis[fn](msg);
        else console.log(`[BQPCompare] ${msg}`);
    }

    function displayValue(v) {
        if (v == null || v === '') return '(trống)';
        if (isDate(v)) return fmtDate(v);
        if (typeof v === 'number') return fmtNum(v);
        return String(v);
    }

    function sameValue(a, b) {
        const emptyA = a == null || a === '';
        const emptyB = b == null || b === '';
        if (emptyA || emptyB) return emptyA && emptyB;
        if (isDate(a) || isDate(b)) return isDate(a) && isDate(b) && fmtDate(a) === fmtDate(b);
        if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) < 0.5 || Math.abs(a - b) < 1e-6 * Math.max(1, Math.abs(a));
        return normText(a) === normText(b);
    }

    function statusOf(r) {
        const isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
        if (!isErr) return 'Đạt chuẩn';
        const diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
        if (diff > 1000) return 'Sai – cấp thiếu';
        if (diff < -1000) return 'Sai – cấp thừa';
        return 'Sai chỉ tiêu / thiếu dữ liệu';
    }

    const unitOf = r => r.donVi || r.unitName || r.group || '';
    // I.5 không có ngày sinh: dùng ngày nhập ngũ để phân biệt người trùng tên
    const identityDate = r => {
        const v = r.sheet === 'I.5' ? r.nhapNgu : r.ngaySinh;
        if (isDate(v)) return fmtDate(v);
        return normText(r.ngaySinhRaw || '');
    };

    // ------------------------------------------------------------- so sánh
    function fieldList(a, b) {
        const sheet = b.sheet || a.sheet;
        const out = [];
        const push = (label, va, vb, group) => {
            if (!sameValue(va, vb)) out.push({ label, from: displayValue(va), to: displayValue(vb), group });
        };
        push('Họ và tên', a.hoTen, b.hoTen, 'info');
        push('Phụ lục', a.sheet, b.sheet, 'info');
        push('Đơn vị', unitOf(a), unitOf(b), 'info');
        push('Cấp bậc', a.capBac, b.capBac, 'info');
        push('Chức vụ', a.chucVu, b.chucVu, 'info');
        if (sheet !== 'I.5') {
            // So cả nội dung ô: "11/1971" → "11/1971N" vẫn được hiểu là cùng ngày nhưng chữ N (nữ) làm đổi tuổi trần
            const rawA = String(a.ngaySinhRaw || '').trim(), rawB = String(b.ngaySinhRaw || '').trim();
            if (rawA !== rawB) push('Ngày sinh (nội dung ô)', rawA, rawB, 'info');
            else push('Ngày sinh', isDate(a.ngaySinh) ? a.ngaySinh : a.ngaySinhRaw, isDate(b.ngaySinh) ? b.ngaySinh : b.ngaySinhRaw, 'info');
            push('Nhập ngũ', a.nhapNgu, b.nhapNgu, 'info');
            push('Thời gian sáp nhập, giải thể', a.sapNhap, b.sapNhap, 'info');
        } else {
            push('Hệ số lương', a.heSoLuong, b.heSoLuong, 'info');
            push('Hệ số chênh lệch bảo lưu', a.heSoChenhLechBaoLuu, b.heSoChenhLechBaoLuu, 'info');
            push('Hệ số chức vụ', a.heSoChucVu, b.heSoChucVu, 'info');
            push('Nhập ngũ', a.nhapNgu, b.nhapNgu, 'info');
        }
        push('Thời điểm nghỉ', a.thoiDiemNghi, b.thoiDiemNghi, 'info');

        if (a.sheet !== b.sheet) {
            // Khác phụ lục thì các cột không cùng ý nghĩa: chỉ so tổng tiền
            push('Tổng tiền kê khai', a.tongTienThucTe, b.tongTienThucTe, 'money');
            return out;
        }
        const labels = COL_LABELS[sheet] || {};
        const firstCol = sheet === 'I.5' ? 11 : 9;   // I.5: cột 6–10 đã so ở phần thông tin
        const ra = a.rawCols || {}, rb = b.rawCols || {};
        const ea = a.cellErrors || {}, eb = b.cellErrors || {};
        const keys = Array.from(new Set([...Object.keys(ra), ...Object.keys(rb), ...Object.keys(ea), ...Object.keys(eb)]))
            .map(Number).filter(k => k >= firstCol).sort((x, y) => x - y);
        keys.forEach(k => {
            const va = ea[k] ? `[Lỗi] ${ea[k]}` : ra[k];
            const vb = eb[k] ? `[Lỗi] ${eb[k]}` : rb[k];
            push(`Cột ${k} – ${labels[k] || 'Số liệu'}`, va, vb, 'money');
        });
        // Lần 1 chỉ là mốc: không so kết quả thẩm định, chỉ so số liệu file ghi
        return out;
    }

    function matchRecords(listA, listB) {
        const pairs = [];
        let restA = listA.slice(), restB = listB.slice();
        const passes = [
            r => `${r.sheet}|${normText(r.hoTen)}|${identityDate(r)}`,
            r => `${normText(r.hoTen)}|${identityDate(r)}`,   // chuyển phụ lục
            r => `${r.sheet}|${normText(r.hoTen)}`            // sửa ngày sinh
        ];
        passes.forEach(keyFn => {
            const pool = new Map();
            restB.forEach(r => {
                const k = keyFn(r);
                if (!pool.has(k)) pool.set(k, []);
                pool.get(k).push(r);
            });
            const nextA = [];
            restA.forEach(a => {
                const bucket = pool.get(keyFn(a));
                if (bucket && bucket.length) pairs.push([a, bucket.shift()]);
                else nextA.push(a);
            });
            restA = nextA;
            restB = Array.from(pool.values()).flat();
        });
        // Sửa chính tả họ tên: cùng phụ lục và trùng cả 3 mốc ngày (sinh/nhập ngũ, nhập ngũ, nghỉ), duy nhất ở cả hai bên
        const dateKey = r => {
            const ds = [r.sheet === 'I.5' ? null : r.ngaySinh, r.nhapNgu, r.thoiDiemNghi];
            if (r.sheet !== 'I.5' && !isDate(ds[0])) return null;
            if (!isDate(ds[1]) || !isDate(ds[2])) return null;
            return `${r.sheet}|${ds.map(d => d ? fmtDate(d) : '').join('|')}`;
        };
        const groupBy = list => {
            const m = new Map();
            list.forEach(r => { const k = dateKey(r); if (k) { if (!m.has(k)) m.set(k, []); m.get(k).push(r); } });
            return m;
        };
        const gA = groupBy(restA), gB = groupBy(restB);
        const usedA = new Set(), usedB = new Set();
        gA.forEach((la, k) => {
            const lb = gB.get(k);
            if (la.length === 1 && lb && lb.length === 1) {
                pairs.push([la[0], lb[0]]);
                usedA.add(la[0]); usedB.add(lb[0]);
            }
        });
        restA = restA.filter(r => !usedA.has(r));
        restB = restB.filter(r => !usedB.has(r));
        // Giữ thứ tự xuất hiện trong file Lần 2 cho phần còn lại
        const leftB = new Set(restB);
        restB = listB.filter(r => leftB.has(r));
        return { pairs, onlyA: restA, onlyB: restB };
    }

    function compare(slotA, slotB) {
        const A = slotA.records || [];
        const B = slotB.records || [];
        const { pairs, onlyA, onlyB } = matchRecords(A, B);
        const rows = [];
        pairs.forEach(([a, b]) => {
            const changes = fieldList(a, b);
            rows.push({ kind: changes.length ? 'changed' : 'same', a, b, changes });
        });
        onlyB.forEach(b => rows.push({ kind: 'added', a: null, b, changes: [] }));
        onlyA.forEach(a => rows.push({ kind: 'removed', a, b: null, changes: [] }));

        const order = { changed: 0, added: 1, removed: 2, same: 3 };
        const sheetOrder = s => { const i = SHEETS.indexOf(s); return i < 0 ? 9 : i; };
        rows.sort((x, y) => {
            const rx = x.b || x.a, ry = y.b || y.a;
            return (order[x.kind] - order[y.kind]) || (sheetOrder(rx.sheet) - sheetOrder(ry.sheet)) || ((rx.rowIndex || 0) - (ry.rowIndex || 0));
        });

        const sum = (list, f) => list.reduce((s, r) => s + (typeof f(r) === 'number' && isFinite(f(r)) ? f(r) : 0), 0);
        const totals = list => ({
            count: list.length,
            declared: sum(list, r => r.sheet === 'I.5' ? null : r.tongTienThucTe),
            recalculated: sum(list, r => r.sheet === 'I.5' ? null : r.tongTienTinhLai),
            ok: list.filter(r => statusOf(r) === 'Đạt chuẩn').length
        });
        // Bảng thống kê theo phụ lục không hiện I.5 (bảng lương, cùng người với I.1/I.2/I.3)
        const bySheet = SHEETS.filter(s => s !== 'I.5').map(s => ({
            sheet: s,
            a: totals(A.filter(r => r.sheet === s)),
            b: totals(B.filter(r => r.sheet === s))
        })).filter(x => x.a.count || x.b.count);

        const counts = { added: 0, removed: 0, changed: 0, same: 0 };
        rows.forEach(r => counts[r.kind]++);
        // Tổng cộng không tính Phụ lục I.5 (bảng lương, cùng người với I.1/I.2/I.3)
        const notI5 = r => r.sheet !== 'I.5';
        return { rows, counts, total: { a: totals(A.filter(notI5)), b: totals(B.filter(notI5)) }, bySheet };
    }

    // ------------------------------------------------------------ lưu trữ
    const Store = {
        open() {
            return new Promise(resolve => {
                try {
                    if (!window.indexedDB) return resolve(null);
                    const req = indexedDB.open(DB_NAME, 1);
                    req.onupgradeneeded = e => {
                        const db = e.target.result;
                        if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
                    };
                    req.onsuccess = () => resolve(req.result);
                    req.onerror = () => resolve(null);
                } catch (e) { resolve(null); }
            });
        },
        async run(mode, fn) {
            const db = await this.open();
            if (!db) return null;
            return new Promise(resolve => {
                try {
                    const tx = db.transaction(STORE, mode);
                    const req = fn(tx.objectStore(STORE));
                    tx.oncomplete = () => resolve(req ? req.result : true);
                    tx.onerror = () => resolve(null);
                } catch (e) { resolve(null); }
            });
        },
        put(key, value) { return this.run('readwrite', s => s.put(value, key)); },
        get(key) { return this.run('readonly', s => s.get(key)); },
        del(key) { return this.run('readwrite', s => s.delete(key)); }
    };

    // ------------------------------------------------------------- giao diện
    const CSS = `
    #compare_root { display:block; }
    .cmp-slots { display:grid; grid-template-columns:repeat(auto-fit, minmax(300px, 1fr)); gap:14px; margin-bottom:14px; }
    .cmp-slot { background:#fff; border:1px solid var(--color-border); border-radius:8px; padding:16px 18px; display:flex; flex-direction:column; gap:10px; min-width:0; }
    .cmp-slot.dragover { border-color:var(--color-primary); background:#f8fafc; }
    .cmp-slot-head { display:flex; align-items:center; gap:10px; }
    .cmp-slot-num { width:30px; height:30px; border-radius:50%; background:var(--color-primary); color:#fff; font-weight:700; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
    .cmp-slot.disabled .cmp-slot-num { background:#94a3b8; }
    .cmp-slot-title { font-weight:700; color:var(--color-ink); font-size:15px; }
    .cmp-slot-sub { font-size:12.5px; color:var(--color-muted); }
    .cmp-drop { border:2px dashed var(--color-border); border-radius:6px; padding:22px 14px; text-align:center; color:var(--color-muted); font-size:13px; cursor:pointer; }
    .cmp-drop:hover, .cmp-drop:focus-visible { border-color:var(--color-primary); color:var(--color-primary); background:#f8fafc; }
    .cmp-slot.disabled .cmp-drop { cursor:not-allowed; opacity:.6; }
    .cmp-file { font-weight:600; color:var(--color-ink); word-break:break-all; font-size:13.5px; }
    .cmp-file-meta { font-size:12.5px; color:var(--color-muted); }
    .cmp-chips { display:flex; flex-wrap:wrap; gap:6px; }
    .cmp-chip { font-size:12px; padding:2px 8px; border-radius:4px; background:#f1f5f9; border:1px solid var(--color-border-light); color:#334155; }
    .cmp-slot-actions { display:flex; gap:8px; flex-wrap:wrap; }
    .cmp-slot-actions .btn-toolbar { height:32px; }
    .cmp-bar { display:flex; justify-content:space-between; align-items:center; gap:10px; flex-wrap:wrap; margin:4px 0 14px; }
    .cmp-bar-actions { display:flex; gap:8px; flex-wrap:wrap; }
    .cmp-stats { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin-bottom:14px; }
    .cmp-stat { background:#fff; border:1px solid var(--color-border); border-radius:8px; padding:12px 14px; cursor:pointer; text-align:left; font:inherit; color:inherit; }
    .cmp-stat:hover { border-color:var(--color-primary); }
    .cmp-stat.active { outline:2px solid var(--color-primary); outline-offset:-1px; }
    .cmp-stat-label { font-size:12px; color:var(--color-muted); font-weight:600; text-transform:uppercase; letter-spacing:.2px; }
    .cmp-stat-val { font-size:22px; font-weight:700; color:var(--color-ink); font-variant-numeric:tabular-nums; margin-top:2px; }
    .cmp-stat.k-added .cmp-stat-val { color:#15803d; }
    .cmp-stat.k-removed .cmp-stat-val { color:var(--color-danger); }
    .cmp-stat.k-changed .cmp-stat-val { color:#b45309; }
    .cmp-section-title { font-weight:700; color:var(--color-ink); font-size:14px; margin:4px 0 8px; }
    .cmp-table-wrap { background:#fff; border:1px solid var(--color-border); border-radius:8px; overflow-x:auto; margin-bottom:14px; }
    .cmp-table { width:100%; border-collapse:collapse; font-size:13px; }
    .cmp-table th { background:#f8fafc; padding:9px 10px; text-align:left; font-weight:700; color:#334155; border-bottom:2px solid #cbd5e1; font-size:12px; white-space:nowrap; }
    .cmp-table td { padding:8px 10px; border-bottom:1px solid #f1f5f9; vertical-align:top; }
    .cmp-table td.num, .cmp-table th.num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
    .cmp-table tr:hover td { background:#f8fafc; }
    .cmp-up { color:#15803d; } .cmp-down { color:var(--color-danger); }
    .cmp-badge { display:inline-block; font-size:11.5px; font-weight:700; padding:2px 8px; border-radius:4px; white-space:nowrap; border:1px solid transparent; }
    .cmp-b-added { background:#f0fdf4; color:#15803d; border-color:#bbf7d0; }
    .cmp-b-removed { background:var(--color-danger-bg); color:var(--color-danger); border-color:#fecaca; }
    .cmp-b-changed { background:#fffbeb; color:#b45309; border-color:#fde68a; }
    .cmp-b-same { background:#f1f5f9; color:#475569; border-color:#e2e8f0; }
    .cmp-changes { list-style:none; margin:0; padding:0; display:flex; flex-direction:column; gap:3px; }
    .cmp-changes li { line-height:1.4; }
    .cmp-changes .f { font-weight:600; color:#334155; }
    .cmp-changes .o { color:var(--color-danger); text-decoration:line-through; }
    .cmp-changes .n { color:#15803d; font-weight:600; }
    .cmp-changes li.g-result .f { color:var(--color-primary); }
    .cmp-filters { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:10px; }
    .cmp-filters input, .cmp-filters select { height:36px; padding:0 10px; border:1px solid var(--border); border-radius:6px; font:inherit; font-size:13px; background:#fff; }
    .cmp-filters input { width:320px; max-width:100%; }
    .cmp-filters select { width:auto; min-width:220px; max-width:100%; }
    .cmp-empty { padding:36px 20px; text-align:center; color:var(--color-muted); background:#fff; border:1px solid var(--color-border); border-radius:8px; }
    .cmp-more { display:flex; justify-content:center; padding:12px; }
    /* Cửa sổ chi tiết: nền trắng, phân nhóm Lần 1 / Lần 2 bằng đường kẻ mảnh, chỉ nhấn bằng chữ */
    .cmp-detail-box { width:1040px; max-width:96vw; }
    .cmp-detail-box .modal-header { padding:14px 22px; align-items:flex-start; }
    .cmp-detail-box .modal-body { padding:16px 22px 20px; overflow-x:hidden; }
    .cmp-d-title { display:flex; flex-direction:column; gap:3px; min-width:0; }
    .cmp-d-title .t { display:flex; align-items:center; gap:8px; font-size:16px; }
    .cmp-d-title .s { font-size:12.5px; font-weight:400; color:var(--color-muted); }
    .cmp-kpis { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); border:1px solid var(--color-border); border-radius:8px; margin-bottom:6px; }
    .cmp-kpi { padding:12px 16px; min-width:0; }
    .cmp-kpi + .cmp-kpi { border-left:1px solid var(--color-border-light); }
    .cmp-kpi .k { font-size:11.5px; font-weight:700; color:var(--color-muted); text-transform:uppercase; letter-spacing:.3px; margin-bottom:4px; }
    .cmp-kpi .v { font-size:14.5px; font-weight:600; color:var(--color-ink); font-variant-numeric:tabular-nums; line-height:1.45; }
    .cmp-kpi .v .arrow { color:#94a3b8; margin:0 6px; font-weight:400; }
    .cmp-kpi .d { font-size:12.5px; margin-top:2px; font-variant-numeric:tabular-nums; }
    .cmp-d-sec { display:flex; justify-content:space-between; align-items:baseline; gap:12px; margin:20px 0 8px; }
    .cmp-d-sec h4 { font-size:13.5px; font-weight:700; color:var(--color-ink); margin:0; }
    .cmp-d-sec label { font-size:12.5px; color:var(--color-muted); display:inline-flex; align-items:center; gap:6px; cursor:pointer; }
    .cmp-d-sec label input { width:auto; margin:0; }
    .cmp-d-table { width:100%; border-collapse:collapse; font-size:13px; table-layout:fixed; }
    .cmp-d-table th, .cmp-d-table td { padding:7px 10px; border-bottom:1px solid #eef1f4; text-align:left; vertical-align:top; }
    .cmp-d-table th { font-size:11.5px; font-weight:700; color:var(--color-muted); text-transform:uppercase; letter-spacing:.3px; background:#fff; }
    .cmp-d-table td.num, .cmp-d-table th.num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
    .cmp-d-table th.grp { text-align:center; color:var(--color-ink); font-size:12.5px; padding-bottom:6px; }
    .cmp-d-table th.grp-1 { border-bottom:2px solid #94a3b8; }
    .cmp-d-table th.grp-2 { border-bottom:2px solid var(--color-primary); }
    .cmp-d-table .l2 { background:#f8fafd; }
    .cmp-d-table .sep { border-left:1px solid var(--color-border); }
    .cmp-d-table tbody tr:hover td { background:#f3f5f8; }
    .cmp-d-table td.lbl { color:#334155; }
    .cmp-d-table td.col { font-weight:600; white-space:nowrap; color:#334155; }
    .cmp-d-table td .sub { display:block; font-size:11.5px; color:var(--color-muted); font-weight:400; margin-top:1px; }
    .cmp-d-table td.chg { color:#b45309; font-weight:700; }
    .cmp-d-table td.bad { color:var(--color-danger); font-weight:700; }
    .cmp-d-table tr.is-chg td:first-child { box-shadow:inset 3px 0 0 #f59e0b; }
    .cmp-d-wrap { border:1px solid var(--color-border); border-radius:8px; overflow-x:auto; }
    .cmp-d-wrap .cmp-d-table tbody tr:last-child td { border-bottom:none; }
    .cmp-d-note { font-size:12px; color:var(--color-muted); margin-top:6px; }
    .cmp-d-errs { margin-top:18px; border-left:3px solid var(--color-danger); background:#fdf6f6; border-radius:0 6px 6px 0; padding:10px 14px; font-size:12.5px; color:#4b5563; }
    .cmp-d-errs + .cmp-d-errs { margin-top:10px; }
    .cmp-d-errs summary { cursor:pointer; font-weight:700; color:var(--color-danger); font-size:13px; }
    .cmp-d-errs ul { margin:6px 0 0 18px; line-height:1.55; }
    #cmp_d_cols { min-width:800px; }
    #cmp_d_cols tr.cmp-zero { display:none; }
    #cmp_d_cols.show-zero tr.cmp-zero { display:table-row; }
    .cmp-hint { background:#f8fafc; border:1px solid var(--color-border-light); border-radius:6px; padding:10px 14px; color:var(--color-muted); font-size:13px; margin-bottom:14px; }
    `;

    // ------------------------------------------------------- chi tiết một hồ sơ
    function infoRows(a, b) {
        const sheet = (b || a).sheet;
        const rawBirth = r => String(r.ngaySinhRaw || '').trim();
        const rows = [
            ['Họ và tên', r => r.hoTen],
            ['Phụ lục', r => r.sheet],
            ['Đơn vị', r => unitOf(r)],
            ['Cấp bậc', r => r.capBac],
            ['Chức vụ', r => r.chucVu]
        ];
        if (sheet !== 'I.5') {
            rows.push(['Ngày sinh (nội dung ô)', rawBirth], ['Ngày sinh (hệ thống hiểu)', r => r.ngaySinh],
                ['Nhập ngũ', r => r.nhapNgu], ['Thời gian sáp nhập, giải thể', r => r.sapNhap]);
        } else {
            rows.push(['Hệ số lương', r => r.heSoLuong], ['Hệ số chênh lệch bảo lưu', r => r.heSoChenhLechBaoLuu],
                ['Hệ số chức vụ', r => r.heSoChucVu], ['Nhập ngũ', r => r.nhapNgu]);
        }
        rows.push(['Thời điểm nghỉ', r => r.thoiDiemNghi], ['Dòng trong file', r => r.rowIndex]);
        return rows.map(([label, f]) => {
            const va = a ? f(a) : null, vb = b ? f(b) : null;
            return {
                label,
                a: a ? displayValue(va) : '—',
                b: b ? displayValue(vb) : '—',
                changed: !!(a && b) && label !== 'Dòng trong file' && !sameValue(va, vb)
            };
        });
    }

    function columnRows(a, b) {
        const map = new Map();
        const add = (rec, side) => {
            if (!rec) return;
            (rec.comparisons || []).forEach(c => {
                if (!c || !c.col || c.col === 'Toàn dòng') return;
                const n = parseInt(String(c.col).replace(/\D+/g, ''), 10);
                if (!map.has(c.col)) map.set(c.col, { col: c.col, n: isNaN(n) ? 999 : n, title: c.title || '' });
                map.get(c.col)[side] = c;
            });
        };
        add(a, 'a');
        add(b, 'b');
        return Array.from(map.values()).sort((x, y) => x.n - y.n);
    }

    const BQPCompare = {
        slots: { 1: null, 2: null },   // { fileName, importedAt, records, sheetStats, readReport }
        result: null,
        filter: { kind: 'diff', sheet: '', search: '' },
        shown: PAGE_SIZE,
        restored: false,
        busy: false,

        init() {
            const root = document.getElementById('compare_root');
            if (!root || root.dataset.ready) return;
            root.dataset.ready = '1';
            const style = document.createElement('style');
            style.textContent = CSS;
            document.head.appendChild(style);
            this.render();
            const btn = document.getElementById('tab_compare_btn');
            if (btn) btn.addEventListener('click', () => this.restore());
            if (document.getElementById('pane_compare')?.classList.contains('active')) this.restore();
        },

        async parse(file) {
            const UI = window.ValidationUI;
            if (!UI || typeof UI.handleFileUpload !== 'function') throw new Error('Chưa nạp được bộ thẩm định Excel.');
            const ctx = Object.create(UI);
            ['renderReadReport', 'showFormulaWarningBanner', 'hideFormulaWarningBanner', 'renderTable'].forEach(k => { ctx[k] = () => {}; });
            const res = await UI.handleFileUpload.call(ctx, file, { headless: true });
            if (!res || !Array.isArray(res.records)) throw new Error('Không đọc được dữ liệu từ file.');
            return { records: res.records, sheetStats: res.sheetStats || {}, readReport: ctx.readReport || null };
        },

        async importFile(slot, file) {
            if (!file || this.busy) return;
            if (!/\.xlsx?$/i.test(file.name || '')) { notify('warning', 'Chỉ hỗ trợ file Excel .xlsx hoặc .xls.'); return; }
            if (slot === 2 && !this.slots[1]) { notify('warning', 'Hãy nạp file Lần 1 trước.'); return; }
            this.busy = true;
            try {
                const buffer = await file.arrayBuffer();
                const parsed = await this.parse(new File([buffer], file.name));
                if (!parsed.records.length) {
                    notify('warning', `Không tìm thấy hồ sơ nào trong file "${file.name}" (cần các sheet Phụ lục I.1, I.2, I.3 hoặc I.5).`);
                    return;
                }
                const importedAt = Date.now();
                this.slots[slot] = { fileName: file.name, importedAt, ...parsed };
                await Store.put('lan' + slot, { name: file.name, buffer, importedAt });
                this.recompute();
                if (slot === 1) {
                    notify('success', this.slots[2]
                        ? `Đã lưu "${file.name}" làm Lần 1 và so sánh lại với Lần 2.`
                        : `Đã lưu "${file.name}" làm Lần 1 (${parsed.records.length.toLocaleString('vi-VN')} hồ sơ). Nạp file Lần 2 để so sánh.`);
                } else {
                    const c = this.result.counts;
                    notify('success', `Đã so sánh: ${c.changed} thay đổi, ${c.added} thêm mới, ${c.removed} bị loại.`);
                }
            } catch (err) {
                console.error('[BQPCompare] Lỗi nạp file:', err);
                notify('error', 'Có lỗi khi đọc file Excel: ' + (err && err.message ? err.message : err));
            } finally {
                this.busy = false;
                this.render();
            }
        },

        async restore() {
            if (this.restored) return;
            this.restored = true;
            const saved = [await Store.get('lan1'), await Store.get('lan2')];
            if (!saved[0] && !saved[1]) return;
            this.busy = true;
            this.render();
            try {
                for (let i = 0; i < 2; i++) {
                    const s = saved[i];
                    if (!s || !s.buffer) continue;
                    const parsed = await this.parse(new File([s.buffer], s.name));
                    this.slots[i + 1] = { fileName: s.name, importedAt: s.importedAt, ...parsed };
                }
                if (!this.slots[1]) this.slots[2] = null;
                this.recompute();
            } catch (err) {
                console.error('[BQPCompare] Lỗi khôi phục file đã lưu:', err);
                notify('error', 'Không khôi phục được file so sánh đã lưu: ' + (err && err.message ? err.message : err));
            } finally {
                this.busy = false;
                this.render();
            }
        },

        recompute() {
            this.result = (this.slots[1] && this.slots[2]) ? compare(this.slots[1], this.slots[2]) : null;
            this.shown = PAGE_SIZE;
        },

        async clearSlot(slot) {
            if (this.busy) return;
            const ok = await this.confirm(slot === 1 && this.slots[2]
                ? 'Xóa file Lần 1 sẽ xóa luôn Lần 2 và kết quả so sánh. Tiếp tục?'
                : `Xóa file Lần ${slot}?`);
            if (!ok) return;
            const targets = slot === 1 ? [1, 2] : [2];
            for (const s of targets) { this.slots[s] = null; await Store.del('lan' + s); }
            this.recompute();
            this.render();
        },

        async promote() {
            if (this.busy || !this.slots[2]) return;
            const ok = await this.confirm(`Dùng "${this.slots[2].fileName}" (Lần 2) làm Lần 1 mới? Lần 1 cũ sẽ bị thay thế và ô Lần 2 được để trống cho lần nộp tiếp theo.`);
            if (!ok) return;
            const saved = await Store.get('lan2');
            if (saved) await Store.put('lan1', saved);
            await Store.del('lan2');
            this.slots[1] = this.slots[2];
            this.slots[2] = null;
            this.recompute();
            this.render();
            notify('success', 'Đã chuyển Lần 2 thành Lần 1.');
        },

        async confirm(message) {
            if (window.ConfirmModal && typeof window.ConfirmModal.confirm === 'function') {
                return !!(await window.ConfirmModal.confirm({ title: 'So sánh hai lần', message, tone: 'warning', confirmLabel: 'Đồng ý', cancelLabel: 'Hủy' }));
            }
            return window.confirm(message);
        },

        pick(slot) {
            if (this.busy) return;
            if (slot === 2 && !this.slots[1]) { notify('warning', 'Hãy nạp file Lần 1 trước.'); return; }
            const input = document.getElementById('cmp_input_' + slot);
            if (input) input.click();
        },

        onInput(slot, ev) {
            const file = ev && ev.target && ev.target.files ? ev.target.files[0] : null;
            if (ev && ev.target) ev.target.value = '';
            this.importFile(slot, file);
        },

        setFilter(key, value) {
            this.filter[key] = value;
            this.shown = PAGE_SIZE;
            // Ô tìm kiếm/lọc phụ lục chỉ vẽ lại bảng để không mất con trỏ khi đang gõ
            if (key === 'kind') this.renderResult();
            else this.renderRows();
        },

        showMore() {
            this.shown += PAGE_SIZE;
            this.renderRows();
        },

        // ------------------------------------------------------- xem chi tiết
        openDetail(idx) {
            const row = this._viewRows && this._viewRows[idx];
            if (!row) return;
            const a = row.a, b = row.b, rec = b || a;
            const both = !!(a && b);

            // 3 chỉ số tóm tắt
            const money = n => (typeof n === 'number' && isFinite(n)) ? `${fmtMoney(n)} đ` : '—';
            const pair = (x, y) => both ? `${x}<span class="arrow">→</span>${y}` : (a ? x : y);
            const delta = (x, y) => {
                if (!both) return '';
                if (typeof x !== 'number' || typeof y !== 'number' || Math.abs(y - x) < 1) return '<div class="d" style="color:var(--color-muted);">Không đổi</div>';
                return `<div class="d ${y > x ? 'cmp-up' : 'cmp-down'}">${fmtSigned(y - x)} đ</div>`;
            };
            // Lần 2 tính lại so với chính số Lần 2 ghi
            const recalc = () => {
                if (!b) return '<div class="v">—</div><div class="d" style="color:var(--color-muted);">Lần 1 chỉ dùng làm mốc, không thẩm định</div>';
                const x = b.tongTienThucTe, y = b.tongTienTinhLai;
                const d = (typeof x === 'number' && typeof y === 'number' && Math.abs(y - x) >= 1)
                    ? `<div class="d ${y > x ? 'cmp-up' : 'cmp-down'}">Lệch so với file ghi: ${fmtSigned(y - x)} đ</div>`
                    : '<div class="d" style="color:var(--color-muted);">Khớp số file ghi</div>';
                return `<div class="v">${money(y)}</div>${d}`;
            };
            const kpis = `
                <div class="cmp-kpis">
                    <div class="cmp-kpi"><div class="k">Tổng tiền file ghi</div>
                        <div class="v">${pair(money(a && a.tongTienThucTe), money(b && b.tongTienThucTe))}</div>${delta(a && a.tongTienThucTe, b && b.tongTienThucTe)}</div>
                    <div class="cmp-kpi"><div class="k">Phần mềm tính lại (Lần 2)</div>${recalc()}</div>
                    <div class="cmp-kpi"><div class="k">Kết quả thẩm định (Lần 2)</div>
                        <div class="v">${b ? esc(statusOf(b)) : '—'}</div></div>
                </div>`;

            // Tổng tiền và kết quả thẩm định đã có ở 3 ô tóm tắt: ở đây chỉ nêu các trường gốc bị sửa
            const srcChanges = row.changes.filter(ch => ch.group !== 'result');
            const changeList = row.kind === 'changed' && srcChanges.length
                ? `<div class="cmp-d-sec"><h4>Nội dung thay đổi</h4></div>
                   <ul class="cmp-changes">${srcChanges.map(ch => `<li class="g-${ch.group}"><span class="f">${esc(ch.label)}:</span> <span class="o">${esc(ch.from)}</span> → <span class="n">${esc(ch.to)}</span></li>`).join('')}</ul>`
                : (row.kind === 'changed' ? '' : `<div class="cmp-d-note" style="margin-top:12px;">${row.kind === 'added' ? 'Hồ sơ chỉ có ở Lần 2.' : row.kind === 'removed' ? 'Hồ sơ chỉ có ở Lần 1.' : 'Không có thay đổi giữa hai lần.'}</div>`);

            // Thông tin chung
            const info = infoRows(a, b).map(r => `<tr class="${r.changed ? 'is-chg' : ''}">
                <td class="lbl">${esc(r.label)}</td>
                <td>${esc(r.a)}</td>
                <td class="l2 sep${r.changed ? ' chg' : ''}">${esc(r.b)}</td></tr>`).join('');

            // Số liệu từng cột: mặc định ẩn các cột cả hai lần đều bằng 0
            const isZero = c => !/[1-9]/.test(String(c.a ? c.a.actual || '' : '') + String(c.b ? (c.b.actual || '') + (c.b.expected || '') : ''));
            const allCols = columnRows(a, b);
            const zeroCount = allCols.filter(isZero).length;
            const cols = allCols.map(c => {
                const changed = both && c.a && c.b && c.a.actual !== c.b.actual;
                let l2File = '<td class="num l2 sep">—</td>', l2Calc = '<td class="num l2">—</td>';
                if (c.b) {
                    const lech = c.b.hasErr && c.b.diff && c.b.diff !== '---' ? `<span class="sub">lệch ${esc(c.b.diff)}</span>` : '';
                    l2File = `<td class="num l2 sep${changed ? ' chg' : ''}">${esc(c.b.actual)}</td>`;
                    l2Calc = `<td class="num l2${c.b.hasErr ? ' bad' : ''}">${esc(c.b.expected)}${lech}</td>`;
                }
                return `<tr class="${changed ? 'is-chg' : ''}${isZero(c) ? ' cmp-zero' : ''}">
                    <td class="col">${esc(c.col)}</td><td class="lbl">${esc(c.title)}</td>
                    <td class="num">${c.a ? esc(c.a.actual) : '—'}</td>${l2File}${l2Calc}</tr>`;
            }).join('');

            const errs = b && b.errorDetails && b.errorDetails.length
                ? `<details class="cmp-d-errs" open><summary>Lỗi thẩm định Lần 2 (${b.errorDetails.length})</summary>
                    <ul>${b.errorDetails.map(e => `<li>${esc(e)}</li>`).join('')}</ul></details>`
                : '';

            let ov = document.getElementById('cmp_detail_modal');
            if (!ov) {
                ov = document.createElement('div');
                ov.id = 'cmp_detail_modal';
                ov.className = 'modal-overlay';
                ov.setAttribute('role', 'dialog');
                ov.setAttribute('aria-modal', 'true');
                ov.addEventListener('click', e => { if (e.target === ov) this.closeDetail(); });
                document.body.appendChild(ov);
                document.addEventListener('keydown', e => { if (e.key === 'Escape' && ov.classList.contains('open')) this.closeDetail(); });
            }
            const meta = [rec.sheet, unitOf(rec), rec.capBac, `Dòng ${a ? a.rowIndex : '—'} (Lần 1) · ${b ? b.rowIndex : '—'} (Lần 2)`]
                .filter(Boolean).map(esc).join(' · ');
            ov.innerHTML = `
                <div class="modal-box cmp-detail-box">
                    <div class="modal-header">
                        <div class="cmp-d-title">
                            <div class="t">${esc(rec.hoTen)} <span class="cmp-badge ${KIND[row.kind].cls}">${KIND[row.kind].label}</span></div>
                            <div class="s">${meta}</div>
                        </div>
                        <button type="button" class="modal-close" onclick="BQPCompare.closeDetail()" aria-label="Đóng">×</button>
                    </div>
                    <div class="modal-body" style="overflow-y:auto;">
                        ${kpis}
                        ${changeList}

                        <div class="cmp-d-sec"><h4>Thông tin chung</h4></div>
                        <div class="cmp-d-wrap"><table class="cmp-d-table">
                            <colgroup><col style="width:30%"><col style="width:35%"><col style="width:35%"></colgroup>
                            <thead><tr><th>Trường</th><th class="grp grp-1" style="text-align:left;">Lần 1</th><th class="grp grp-2 sep" style="text-align:left;">Lần 2</th></tr></thead>
                            <tbody>${info}</tbody>
                        </table></div>

                        <div class="cmp-d-sec">
                            <h4>Số liệu từng cột</h4>
                            ${zeroCount ? `<label><input type="checkbox" onchange="document.getElementById('cmp_d_cols').classList.toggle('show-zero', this.checked)"> Hiện cả ${zeroCount} cột bằng 0</label>` : ''}
                        </div>
                        <div class="cmp-d-wrap"><table class="cmp-d-table" id="cmp_d_cols">
                            <colgroup><col style="width:8%"><col style="width:32%"><col style="width:20%"><col style="width:20%"><col style="width:20%"></colgroup>
                            <thead>
                                <tr><th rowspan="2" style="vertical-align:bottom;">Cột</th><th rowspan="2" style="vertical-align:bottom;">Chỉ tiêu</th>
                                    <th class="grp grp-1">Lần 1 (mốc)</th><th colspan="2" class="grp grp-2 sep">Lần 2</th></tr>
                                <tr><th class="num">File ghi</th><th class="num sep">File ghi</th><th class="num">Phần mềm tính lại</th></tr>
                            </thead>
                            <tbody>${cols || '<tr><td colspan="5" class="cmp-d-note">Không có dữ liệu đối chiếu.</td></tr>'}</tbody>
                        </table></div>
                        <div class="cmp-d-note">Lần 1 chỉ dùng làm mốc, không thẩm định · Chữ cam: số Lần 2 ghi khác Lần 1 · Chữ đỏ: phần mềm tính lại khác số Lần 2 ghi.</div>
                        ${errs}
                    </div>
                    <div class="modal-footer"><button type="button" class="btn-toolbar neutral" onclick="BQPCompare.closeDetail()">Đóng</button></div>
                </div>`;
            ov.classList.add('open');
        },
        closeDetail() {
            const ov = document.getElementById('cmp_detail_modal');
            if (ov) ov.classList.remove('open');
        },

        // ------------------------------------------------------------ render
        render() {
            const root = document.getElementById('compare_root');
            if (!root) return;
            root.innerHTML = `
                <div class="validate-header-title">
                    <h2>So sánh hai lần nộp file</h2>
                    <p>Nạp file vào <strong>Lần 1</strong> để lưu làm mốc. Khi nạp file vào <strong>Lần 2</strong>, hệ thống tự đọc, thẩm định và so sánh từng người với Lần 1: ai được thêm, ai bị loại, thông tin và số tiền nào thay đổi.</p>
                </div>
                <div class="cmp-slots">${this.slotHtml(1)}${this.slotHtml(2)}</div>
                <div id="cmp_result"></div>`;
            [1, 2].forEach(slot => this.bindDrop(slot));
            this.renderResult();
        },

        slotHtml(slot) {
            const d = this.slots[slot];
            const disabled = slot === 2 && !this.slots[1];
            const title = slot === 1 ? 'Lần 1' : 'Lần 2';
            const sub = slot === 1 ? 'File gốc — được lưu làm mốc so sánh' : 'File cập nhật — so sánh với Lần 1';
            let body;
            if (this.busy && !d) {
                body = `<div class="cmp-drop" aria-busy="true">Đang xử lý…</div>`;
            } else if (!d) {
                body = `<div class="cmp-drop" role="button" tabindex="${disabled ? -1 : 0}" onclick="BQPCompare.pick(${slot})" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();BQPCompare.pick(${slot});}">
                    ${disabled ? 'Nạp file Lần 1 trước' : 'Thả file Excel vào đây, hoặc click để chọn file'}
                </div>`;
            } else {
                const st = d.sheetStats || {};
                const chips = SHEETS.map(s => {
                    const n = st['i' + s.slice(2)] || 0;
                    return n ? `<span class="cmp-chip">${s}: ${n.toLocaleString('vi-VN')} người</span>` : '';
                }).join('');
                body = `
                    <div>
                        <div class="cmp-file">${esc(d.fileName)}</div>
                        <div class="cmp-file-meta">Nạp lúc ${fmtTime(d.importedAt)} · ${d.records.length.toLocaleString('vi-VN')} hồ sơ</div>
                    </div>
                    <div class="cmp-chips">${chips}</div>
                    <div class="cmp-slot-actions">
                        <button type="button" class="btn-toolbar neutral" onclick="BQPCompare.pick(${slot})"${this.busy ? ' disabled' : ''}>Thay file khác</button>
                        <button type="button" class="btn-toolbar neutral" onclick="BQPCompare.clearSlot(${slot})"${this.busy ? ' disabled' : ''}>Xóa</button>
                    </div>`;
            }
            return `
                <section class="cmp-slot${disabled ? ' disabled' : ''}" id="cmp_slot_${slot}" aria-label="${title}">
                    <input type="file" id="cmp_input_${slot}" accept=".xlsx,.xls" style="display:none" onchange="BQPCompare.onInput(${slot}, event)">
                    <div class="cmp-slot-head">
                        <div class="cmp-slot-num">${slot}</div>
                        <div><div class="cmp-slot-title">${title}</div><div class="cmp-slot-sub">${sub}</div></div>
                    </div>
                    ${body}
                </section>`;
        },

        bindDrop(slot) {
            const el = document.getElementById('cmp_slot_' + slot);
            if (!el) return;
            const allowed = () => !this.busy && !(slot === 2 && !this.slots[1]);
            el.addEventListener('dragover', e => { e.preventDefault(); if (allowed()) el.classList.add('dragover'); });
            el.addEventListener('dragleave', e => { e.preventDefault(); el.classList.remove('dragover'); });
            el.addEventListener('drop', e => {
                e.preventDefault();
                el.classList.remove('dragover');
                const f = e.dataTransfer && e.dataTransfer.files ? e.dataTransfer.files[0] : null;
                if (!f) return;
                if (!allowed()) { if (!this.busy) notify('warning', 'Hãy nạp file Lần 1 trước.'); return; }
                this.importFile(slot, f);
            });
        },

        renderResult() {
            const box = document.getElementById('cmp_result');
            if (!box) return;
            const res = this.result;
            if (!res) {
                box.innerHTML = this.slots[1]
                    ? `<div class="cmp-hint">Đã lưu Lần 1. Nạp file Lần 2 (bản đơn vị gửi lại) để xem khác biệt.</div>`
                    : '';
                return;
            }
            const c = res.counts;
            const diffCount = c.added + c.removed + c.changed;
            const stat = (kind, label, val) => `
                <button type="button" class="cmp-stat k-${kind}${this.filter.kind === kind ? ' active' : ''}" onclick="BQPCompare.setFilter('kind','${kind}')">
                    <div class="cmp-stat-label">${label}</div><div class="cmp-stat-val">${val.toLocaleString('vi-VN')}</div>
                </button>`;

            const delta = (x, y, money) => {
                const d = y - x;
                if (Math.abs(d) < (money ? 1 : 0.5)) return '<span>0</span>';
                return `<span class="${d > 0 ? 'cmp-up' : 'cmp-down'}">${fmtSigned(d)}</span>`;
            };
            const sheetRows = res.bySheet.map(s => `
                <tr>
                    <td>${esc(SHEET_NAMES[s.sheet] || s.sheet)}</td>
                    <td class="num">${s.a.count.toLocaleString('vi-VN')}</td><td class="num">${s.b.count.toLocaleString('vi-VN')}</td><td class="num">${delta(s.a.count, s.b.count)}</td>
                    <td class="num">${fmtMoney(s.a.declared)}</td><td class="num">${fmtMoney(s.b.declared)}</td><td class="num">${delta(s.a.declared, s.b.declared, true)}</td>
                    <td class="num">${s.b.ok.toLocaleString('vi-VN')}</td>
                </tr>`).join('');
            const t = res.total;

            box.innerHTML = `
                <div class="cmp-bar">
                    <div class="cmp-section-title" style="margin:0;">Kết quả so sánh: ${diffCount ? `${diffCount.toLocaleString('vi-VN')} hồ sơ khác biệt` : 'hai lần giống nhau'}</div>
                    <div class="cmp-bar-actions">
                        <button type="button" class="btn-toolbar primary" onclick="BQPCompare.exportExcel()" title="Xuất 1 file theo mẫu Phụ lục gồm những người theo bộ lọc đang chọn">📊 Xuất kết quả so sánh</button>
                        <button type="button" class="btn-toolbar neutral" onclick="BQPCompare.promote()" title="Lưu Lần 2 làm mốc mới để so sánh với lần nộp tiếp theo">Dùng Lần 2 làm Lần 1 mới</button>
                    </div>
                </div>
                <div class="cmp-stats">
                    ${stat('diff', 'Tất cả khác biệt', diffCount)}
                    ${stat('changed', 'Có thay đổi', c.changed)}
                    ${stat('added', 'Thêm mới ở Lần 2', c.added)}
                    ${stat('removed', 'Bị loại ở Lần 2', c.removed)}
                    ${stat('same', 'Không đổi', c.same)}
                </div>
                <div class="cmp-table-wrap">
                    <table class="cmp-table">
                        <thead><tr>
                            <th>Phụ lục</th><th class="num">Số người L1</th><th class="num">Số người L2</th><th class="num">Chênh lệch</th>
                            <th class="num">Tổng kê khai L1 (đ)</th><th class="num">Tổng kê khai L2 (đ)</th><th class="num">Chênh lệch (đ)</th><th class="num">Đạt chuẩn (Lần 2)</th>
                        </tr></thead>
                        <tbody>${sheetRows}
                            <tr style="font-weight:700;">
                                <td>Tổng cộng (I.1 + I.2 + I.3)</td>
                                <td class="num">${t.a.count.toLocaleString('vi-VN')}</td><td class="num">${t.b.count.toLocaleString('vi-VN')}</td><td class="num">${delta(t.a.count, t.b.count)}</td>
                                <td class="num">${fmtMoney(t.a.declared)}</td><td class="num">${fmtMoney(t.b.declared)}</td><td class="num">${delta(t.a.declared, t.b.declared, true)}</td>
                                <td class="num">${t.b.ok.toLocaleString('vi-VN')}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="cmp-filters">
                    <input type="search" id="cmp_search" placeholder="Tìm theo họ tên, đơn vị..." value="${esc(this.filter.search)}" oninput="BQPCompare.setFilter('search', this.value)">
                    <select onchange="BQPCompare.setFilter('sheet', this.value)">
                        <option value="">Tất cả phụ lục</option>
                        ${SHEETS.map(s => `<option value="${s}"${this.filter.sheet === s ? ' selected' : ''}>${esc(SHEET_NAMES[s])}</option>`).join('')}
                    </select>
                </div>
                <div id="cmp_rows"></div>`;
            this.renderRows();
        },

        filteredRows() {
            const res = this.result;
            if (!res) return [];
            const { kind, sheet, search } = this.filter;
            const q = normText(search);
            return res.rows.filter(r => {
                if (kind === 'diff' ? r.kind === 'same' : r.kind !== kind) return false;
                const a = r.a || {}, b = r.b || {};
                if (sheet && a.sheet !== sheet && b.sheet !== sheet) return false;
                if (q) {
                    const hay = normText([a.hoTen, b.hoTen, unitOf(a), unitOf(b), a.group, b.group].join(' '));
                    if (!hay.includes(q)) return false;
                }
                return true;
            });
        },

        renderRows() {
            const box = document.getElementById('cmp_rows');
            if (!box) return;
            const rows = this.filteredRows();
            if (!rows.length) {
                box.innerHTML = `<div class="cmp-empty">Không có hồ sơ nào trong mục này.</div>`;
                return;
            }
            const view = rows.slice(0, this.shown);
            this._viewRows = view;
            const money = r => (r && r.sheet !== 'I.5') ? fmtMoney(r.tongTienThucTe) : '—';
            const body = view.map((r, i) => {
                const rec = r.b || r.a;
                let detail;
                if (r.kind === 'added') detail = `<span class="cmp-slot-sub">Chỉ có ở Lần 2 (dòng ${rec.rowIndex}, sheet "${esc(rec.group || '')}")</span>`;
                else if (r.kind === 'removed') detail = `<span class="cmp-slot-sub">Chỉ có ở Lần 1 (dòng ${rec.rowIndex}, sheet "${esc(rec.group || '')}")</span>`;
                else if (r.kind === 'same') detail = `<span class="cmp-slot-sub">Không có thay đổi</span>`;
                else detail = `<ul class="cmp-changes">${r.changes.map(ch => `<li class="g-${ch.group}"><span class="f">${esc(ch.label)}:</span> <span class="o">${esc(ch.from)}</span> → <span class="n">${esc(ch.to)}</span></li>`).join('')}</ul>`;
                return `<tr>
                    <td class="num">${i + 1}</td>
                    <td><div style="font-weight:600;">${esc(rec.hoTen)}</div><div class="cmp-slot-sub">${esc(rec.capBac || '')}</div></td>
                    <td style="white-space:nowrap;">${esc(rec.sheet)}</td>
                    <td>${esc(unitOf(rec))}</td>
                    <td><span class="cmp-badge ${KIND[r.kind].cls}">${KIND[r.kind].label}</span></td>
                    <td>${detail}</td>
                    <td class="num">${money(r.a)}</td>
                    <td class="num">${money(r.b)}</td>
                    <td>${r.b ? esc(statusOf(r.b)) : '<span class="cmp-slot-sub">Không có ở Lần 2</span>'}</td>
                    <td><button type="button" class="btn-toolbar neutral" style="height:30px;" onclick="BQPCompare.openDetail(${i})">Xem chi tiết</button></td>
                </tr>`;
            }).join('');
            box.innerHTML = `
                <div class="cmp-table-wrap" style="margin-bottom:0;">
                    <table class="cmp-table">
                        <thead><tr>
                            <th class="num">#</th><th>Họ và tên</th><th>Phụ lục</th><th>Đơn vị</th><th>Tình trạng</th>
                            <th style="min-width:280px;">Nội dung thay đổi (Lần 1 → Lần 2)</th>
                            <th class="num">Tổng kê khai L1</th><th class="num">Tổng kê khai L2</th><th>Thẩm định Lần 2</th><th></th>
                        </tr></thead>
                        <tbody>${body}</tbody>
                    </table>
                </div>
                ${rows.length > view.length ? `<div class="cmp-more"><button type="button" class="btn-toolbar neutral" onclick="BQPCompare.showMore()">Hiển thị thêm (${(rows.length - view.length).toLocaleString('vi-VN')} hồ sơ còn lại)</button></div>` : ''}`;
        },

        // ------------------------------------------------------------ xuất Excel
        // Mô tả bộ lọc đang chọn: dùng cho tiêu đề sheet và tên file xuất
        filterInfo() {
            const { kind, sheet, search } = this.filter;
            const kindLabel = kind === 'diff' ? 'Tất cả khác biệt' : KIND[kind].label;
            const kindSlug = { diff: 'Khac_biet', changed: 'Co_thay_doi', added: 'Them_moi', removed: 'Bi_loai', same: 'Khong_doi' }[kind];
            const parts = [kindLabel];
            if (sheet) parts.push(SHEET_NAMES[sheet] || sheet);
            if (search && search.trim()) parts.push(`tìm "${search.trim()}"`);
            return {
                label: parts.join(' · '),
                slug: 'So_sanh_' + kindSlug + (sheet ? '_' + sheet.replace('.', '') : '') + (search && search.trim() ? '_Tim_kiem' : '')
            };
        },

        // Một file duy nhất theo mẫu "PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx":
        // các sheet của mẫu (Thống kê, Phụ lục I–IV, I.1–I.5…) chứa đúng những người theo bộ lọc.
        // Số liệu lấy theo Lần 2; người "Bị loại" chỉ còn ở Lần 1 nên lấy số liệu Lần 1.
        async exportExcel() {
            const res = this.result;
            if (!res) return;
            const rows = this.filteredRows();
            if (!rows.length) { notify('warning', 'Bộ lọc hiện tại không có hồ sơ nào để xuất.'); return; }
            const api = window.BQPValidation, tpl = window.BQPReportTemplate;
            if (!api || typeof api.exportValidatedWorkbook !== 'function' || !tpl) {
                notify('error', 'Chưa nạp được bộ xuất Phụ lục mẫu.');
                return;
            }
            const fi = this.filterInfo();
            try {
                const ctx = await tpl.createExportContext();
                const people = rows.map(r => ({ ...(r.b || r.a) }));
                const everyone = res.rows.map(r => ({ ...(r.b || r.a) }));
                const baseName = String(this.slots[2].fileName || 'Bao_cao').replace(/\.[^/.]+$/, '');
                await api.exportValidatedWorkbook(ctx.reportTemplateWorkbook, baseName + '.xlsx', people, true, fi.slug, everyone, ctx);
            } catch (err) {
                console.error('[BQPCompare] Lỗi xuất Phụ lục mẫu:', err);
                notify('error', 'Không xuất được file: ' + (err && err.message ? err.message : err));
            }
        }
    };

    BQPCompare._compare = compare;   // cho kiểm thử
    window.BQPCompare = BQPCompare;
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => BQPCompare.init());
    else BQPCompare.init();
})();
