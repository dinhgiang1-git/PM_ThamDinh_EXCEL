package com.appvalidateexcel.storage.crypto;

import com.appvalidateexcel.auth.RequestGuardFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cổng mở khóa chạy TRƯỚC khi Spring khởi động: một máy chủ HTTP nhỏ phục vụ trang nhập mật khẩu DB.
 * - DB chưa có / chưa mã hóa: mật khẩu nhập vào trở thành mật khẩu DB, hiển thị khóa khôi phục MỘT LẦN.
 * - DB đã mã hóa: nhập mật khẩu, hoặc dùng khóa khôi phục để đặt mật khẩu mới khi quên.
 * Nhập đúng thì khóa chính được giữ trong DbKey, cổng tắt và Spring Boot khởi động tiếp trên cùng cổng.
 */
public final class DbUnlockGate {

    private static final int MIN_LENGTH = 6;

    private final Path dbFile;
    private final int port;
    private final CountDownLatch unlocked = new CountDownLatch(1);
    private volatile int failedAttempts = 0;

    private DbUnlockGate(Path dbFile, int port) {
        this.dbFile = dbFile;
        this.port = port;
    }

    /** Chặn cho đến khi người dùng mở khóa thành công; kết quả nằm trong DbKey. */
    public static void awaitUnlock(Path dbFile, int port) throws Exception {
        new DbUnlockGate(dbFile, port).run();
    }

    private void run() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.start();
        System.out.println("[BQP Unlock] Cơ sở dữ liệu đang khóa. Mở http://localhost:" + port + "/ để nhập mật khẩu.");
        unlocked.await();
        Thread.sleep(500); // để phản hồi cuối cùng được gửi đi
        server.stop(0);
        Thread.sleep(300); // giải phóng cổng cho Spring Boot
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            // Chống DNS rebinding / trang web lạ: chỉ nhận Host và Origin là localhost
            if (!RequestGuardFilter.isLocalHostHeader(ex.getRequestHeaders().getFirst("Host"))
                    || !RequestGuardFilter.isLocalOrigin(ex.getRequestHeaders().getFirst("Origin"))) {
                send(ex, 403, "text/plain", "Forbidden");
                return;
            }
            String path = ex.getRequestURI().getPath();
            if ("POST".equals(ex.getRequestMethod())) {
                // Bắt buộc application/json: trình duyệt phải gửi preflight nên trang lạ không POST chéo được
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
                    send(ex, 415, "application/json", "{\"message\":\"Unsupported Media Type\"}");
                    return;
                }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if ("/api/unlock".equals(path)) {
                    respondUnlock(ex, body);
                    return;
                }
                if ("/api/unlock/ack".equals(path)) { // người dùng đã lưu khóa khôi phục
                    send(ex, 200, "application/json", "{\"ok\":true}");
                    if (DbKey.get() != null) unlocked.countDown();
                    return;
                }
            }
            if ("GET".equals(ex.getRequestMethod()) && "/api/unlock/state".equals(path)) {
                send(ex, 200, "application/json", "{\"state\":\"" + DbCipher.state(dbFile) + "\",\"hasKeystore\":" + DbKeyStore.exists(dbFile) + "}");
                return;
            }
            send(ex, 200, "text/html;charset=UTF-8", PAGE);
        } catch (Exception e) {
            send(ex, 500, "application/json", "{\"message\":\"Lỗi hệ thống: " + escape(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    private synchronized void respondUnlock(HttpExchange ex, String body) throws Exception {
        String password = extract(body, "password");
        String recoveryKey = extract(body, "recoveryKey");
        String newPassword = extract(body, "newPassword");
        DbCipher.State state = DbCipher.state(dbFile);
        boolean hasKeystore = DbKeyStore.exists(dbFile);

        // Quên mật khẩu: dùng khóa khôi phục và đặt mật khẩu mới
        if (recoveryKey != null && !recoveryKey.isEmpty()) {
            if (state != DbCipher.State.ENCRYPTED || !hasKeystore) {
                send(ex, 400, "application/json", "{\"message\":\"Không có khóa khôi phục cho cơ sở dữ liệu này.\"}");
                return;
            }
            if (newPassword == null || newPassword.length() < MIN_LENGTH) {
                send(ex, 400, "application/json", "{\"message\":\"Mật khẩu mới phải có ít nhất " + MIN_LENGTH + " ký tự.\"}");
                return;
            }
            String master = DbKeyStore.unlockWithRecovery(dbFile, recoveryKey);
            if (master == null || !DbCipher.verify(dbFile, master)) {
                rejectWrong(ex, "Khóa khôi phục không đúng.");
                return;
            }
            DbKeyStore.changePassword(dbFile, master, newPassword);
            System.out.println("[BQP Unlock] Đã đặt lại mật khẩu bằng khóa khôi phục.");
            DbKey.set(master);
            send(ex, 200, "application/json", "{\"ok\":true}");
            unlocked.countDown();
            return;
        }

        if (password == null || password.isEmpty()) {
            send(ex, 400, "application/json", "{\"message\":\"Vui lòng nhập mật khẩu.\"}");
            return;
        }

        // DB đã mã hóa + có kho khóa: mở khóa bằng mật khẩu
        if (state == DbCipher.State.ENCRYPTED && hasKeystore) {
            String master = DbKeyStore.unlockWithPassword(dbFile, password);
            if (master == null || !DbCipher.verify(dbFile, master)) {
                rejectWrong(ex, "Mật khẩu cơ sở dữ liệu không đúng.");
                return;
            }
            DbKey.set(master);
            send(ex, 200, "application/json", "{\"ok\":true}");
            unlocked.countDown();
            return;
        }

        // DB mới, DB chưa mã hóa, hoặc DB mã hóa trực tiếp bằng mật khẩu (bản cũ): thiết lập kho khóa + khóa khôi phục
        if (password.length() < MIN_LENGTH) {
            send(ex, 400, "application/json", "{\"message\":\"Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự.\"}");
            return;
        }
        if (state == DbCipher.State.ENCRYPTED && !DbCipher.verify(dbFile, password)) {
            rejectWrong(ex, "Mật khẩu cơ sở dữ liệu không đúng.");
            return;
        }
        DbKeyStore.Created created = DbKeyStore.create(dbFile, password);
        if (state == DbCipher.State.PLAIN) {
            DbCipher.encryptInPlace(dbFile, created.masterKey());
            System.out.println("[BQP Unlock] Đã mã hóa cơ sở dữ liệu hiện có.");
        } else if (state == DbCipher.State.ENCRYPTED) {
            DbCipher.rekey(dbFile, password, created.masterKey());
            System.out.println("[BQP Unlock] Đã chuyển cơ sở dữ liệu sang khóa chính + khóa khôi phục.");
        }
        // ABSENT: DB mới được tạo đã mã hóa bằng khóa chính khi ứng dụng khởi động
        DbKey.set(created.masterKey());
        // Chờ người dùng xác nhận đã lưu khóa khôi phục (/api/unlock/ack) rồi mới khởi động tiếp
        send(ex, 200, "application/json", "{\"ok\":true,\"recoveryKey\":\"" + created.recoveryKey() + "\"}");
    }

    private void rejectWrong(HttpExchange ex, String message) throws Exception {
        failedAttempts++;
        Thread.sleep(Math.min(failedAttempts, 5) * 1000L); // làm chậm dò mật khẩu
        send(ex, 401, "application/json", "{\"message\":\"" + message + "\"}");
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\/", "/");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type.contains("charset") ? type : type + ";charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Content-Security-Policy", "frame-ancestors 'none'");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Mở khóa cơ sở dữ liệu</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a;font-family:'Segoe UI',sans-serif}
            .card{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:32px 36px;width:360px}
            h2{color:#f8fafc;font-size:18px;margin:0 0 6px;text-align:center}
            p.sub{color:#94a3b8;font-size:13px;text-align:center;margin:0 0 20px;line-height:1.5}
            label{display:block;color:#cbd5e1;font-size:13px;margin-bottom:5px;font-weight:600}
            input{width:100%;box-sizing:border-box;padding:9px 12px;margin-bottom:14px;border-radius:6px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:14px}
            button{width:100%;padding:10px;border:0;border-radius:6px;background:#2563eb;color:#fff;font-size:14px;font-weight:600;cursor:pointer;margin-bottom:8px}
            button.sec{background:#334155}
            button:disabled{opacity:.6;cursor:default}
            a{color:#60a5fa;font-size:13px;cursor:pointer;display:block;text-align:center;margin-top:6px}
            .msg{display:none;border-radius:6px;padding:9px 12px;font-size:13px;margin-bottom:14px}
            .err{background:#450a0a;color:#fca5a5}.ok{background:#052e16;color:#86efac}
            .key{font-family:Consolas,monospace;font-size:15px;letter-spacing:1px;background:#0f172a;border:1px dashed #f59e0b;color:#fcd34d;border-radius:6px;padding:12px;text-align:center;word-break:break-all;margin-bottom:12px;user-select:all}
            </style></head><body><div class="card">
            <h2>Mở khóa cơ sở dữ liệu</h2>
            <p class="sub" id="sub">Đang kiểm tra...</p>

            <form id="f">
              <label for="pw">Mật khẩu cơ sở dữ liệu</label>
              <input id="pw" type="password" autocomplete="off" autofocus required>
              <div id="msg" class="msg"></div>
              <button id="btn" type="submit">Mở khóa</button>
              <a id="toRec" style="display:none">Quên mật khẩu? Dùng khóa khôi phục</a>
            </form>

            <form id="r" style="display:none">
              <label for="rk">Khóa khôi phục</label>
              <input id="rk" autocomplete="off" placeholder="XXXX-XXXX-XXXX-..." required>
              <label for="np">Mật khẩu mới (ít nhất 6 ký tự)</label>
              <input id="np" type="password" autocomplete="off" required>
              <div id="rmsg" class="msg"></div>
              <button id="rbtn" type="submit">Đặt mật khẩu mới và mở khóa</button>
              <a id="toPw">Quay lại nhập mật khẩu</a>
            </form>

            <div id="keybox" style="display:none">
              <p class="sub" style="color:#fcd34d">KHÓA KHÔI PHỤC của bạn (chỉ hiện một lần). Hãy lưu ở nơi an toàn, riêng với máy này. Mất cả mật khẩu và khóa này thì không thể khôi phục dữ liệu.</p>
              <div class="key" id="keytxt"></div>
              <button type="button" class="sec" id="dl">Tải về file .txt</button>
              <button type="button" id="ack">Tôi đã lưu khóa, tiếp tục</button>
            </div>
            <script>
            const $=id=>document.getElementById(id);
            function show(el,t,c){el.textContent=t;el.className='msg '+c;el.style.display=t?'block':'none';}
            function post(url,body){return fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(async r=>({ok:r.ok,d:await r.json().catch(()=>({}))}));}
            function waitSpring(){
              const wait=async()=>{ try{ const t=await (await fetch('/login.html',{cache:'no-store'})).text();
                if(t.indexOf('name="bqp-'+'page"')>=0){ location.href='/login.html'; return; } }catch(_){}
                setTimeout(wait,1000); };
              setTimeout(wait,2000);
            }
            fetch('/api/unlock/state').then(r=>r.json()).then(d=>{
              const enc = d.state==='ENCRYPTED';
              if(enc) $('sub').textContent = 'Nhập mật khẩu để mở dữ liệu.';
              else $('sub').style.display = 'none';
              if(!enc) $('btn').textContent='Mã hóa và mở';
              if(enc && d.hasKeystore) $('toRec').style.display='block';
            });
            $('toRec').onclick=()=>{$('f').style.display='none';$('r').style.display='block';};
            $('toPw').onclick=()=>{$('r').style.display='none';$('f').style.display='block';};
            $('f').addEventListener('submit',async e=>{
              e.preventDefault(); show($('msg'),'',''); $('btn').disabled=true;
              try{
                const r=await post('/api/unlock',{password:$('pw').value});
                if(!r.ok){ show($('msg'),r.d.message||'Mở khóa thất bại.','err'); $('btn').disabled=false; return; }
                if(r.d.recoveryKey){
                  $('f').style.display='none'; $('sub').style.display='none';
                  $('keytxt').textContent=r.d.recoveryKey; $('keybox').style.display='block';
                  return;
                }
                show($('msg'),'Đang khởi động ứng dụng...','ok'); waitSpring();
              }catch(err){ show($('msg'),'Lỗi kết nối: '+err.message,'err'); $('btn').disabled=false; }
            });
            $('r').addEventListener('submit',async e=>{
              e.preventDefault(); show($('rmsg'),'',''); $('rbtn').disabled=true;
              try{
                const r=await post('/api/unlock',{recoveryKey:$('rk').value,newPassword:$('np').value});
                if(!r.ok){ show($('rmsg'),r.d.message||'Khôi phục thất bại.','err'); $('rbtn').disabled=false; return; }
                show($('rmsg'),'Đã đặt mật khẩu mới. Đang khởi động ứng dụng...','ok'); waitSpring();
              }catch(err){ show($('rmsg'),'Lỗi kết nối: '+err.message,'err'); $('rbtn').disabled=false; }
            });
            $('dl').onclick=()=>{
              const blob=new Blob(['KHOA KHOI PHUC CO SO DU LIEU - PHAN MEM RA SOAT, THAM DINH CHE DO NGHI HUU, PHUC VIEN\\r\\n'+$('keytxt').textContent+'\\r\\n'],{type:'text/plain'});
              const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download='bqp-khoa-khoi-phuc.txt'; a.click();
            };
            $('ack').onclick=async()=>{
              $('ack').disabled=true; await post('/api/unlock/ack',{});
              $('keybox').innerHTML='<p class="sub" style="color:#86efac">Đang khởi động ứng dụng...</p>'; waitSpring();
            };
            </script></div></body></html>
            """;
}
