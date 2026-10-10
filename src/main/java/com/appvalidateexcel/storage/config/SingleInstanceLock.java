package com.appvalidateexcel.storage.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class SingleInstanceLock {

    private final DataDirectoryResolver dataDirectoryResolver;
    private Path lockFilePath;
    private FileChannel fileChannel;
    private FileLock fileLock;

    @org.springframework.beans.factory.annotation.Value("${bqp.lock.enabled:true}")
    private boolean lockEnabled;

    public SingleInstanceLock(DataDirectoryResolver dataDirectoryResolver) {
        this.dataDirectoryResolver = dataDirectoryResolver;
    }

    @PostConstruct
    public void acquireLock() {
        if (!lockEnabled) {
            return;
        }
        try {
            lockFilePath = dataDirectoryResolver.getDataDirectory().resolve("app.lock");
            Files.createDirectories(lockFilePath.getParent());

            fileChannel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            fileLock = fileChannel.tryLock();

            if (fileLock == null) {
                throw new IllegalStateException("Ứng dụng BQP đã đang chạy trong một tiến trình khác. Mỗi thời điểm chỉ được mở một phiên làm việc để bảo đảm toàn vẹn cơ sở dữ liệu.");
            }

            long currentPid = ProcessHandle.current().pid();
            fileChannel.truncate(0);
            fileChannel.write(StandardCharsets.UTF_8.encode(String.valueOf(currentPid)));
            fileChannel.force(true);

            System.out.println("[BQP Lock] Đã giữ khóa ứng dụng cấp HĐH thành công (PID: " + currentPid + ")");
        } catch (IllegalStateException e) {
            closeSilently();
            throw e;
        } catch (Exception e) {
            closeSilently();
            System.err.println("[BQP Lock] Không thể lấy khóa ứng dụng: " + e.getMessage());
            throw new IllegalStateException("Không thể lấy khóa ứng dụng: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void releaseLock() {
        if (!lockEnabled) {
            return;
        }
        closeSilently();
        try {
            if (lockFilePath != null) {
                Files.deleteIfExists(lockFilePath);
            }
        } catch (Exception ignored) {}
    }

    private void closeSilently() {
        try {
            if (fileLock != null && fileLock.isValid()) {
                fileLock.release();
            }
        } catch (Exception ignored) {}
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
            }
        } catch (Exception ignored) {}
    }
}
