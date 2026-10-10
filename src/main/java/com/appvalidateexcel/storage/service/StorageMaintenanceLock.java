package com.appvalidateexcel.storage.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Component
public class StorageMaintenanceLock {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); // fair lock
    private volatile boolean maintenanceMode = false;

    /**
     * Dành cho các thao tác ghi dữ liệu thông thường (hồ sơ, đơn vị, migration).
     * Dùng shared read lock để cho phép nhiều tác vụ ghi/đọc đồng thời,
     * nhưng sẽ bị chặn và từ chối ngay nếu hệ thống đang thực hiện restore (exclusive write lock).
     */
    public void runWithWriteAccess(Runnable action) {
        acquireWriteAccess();
        boolean lockHeldBySync = false;
        try {
            lockHeldBySync = registerSynchronizationIfActive();
            action.run();
        } finally {
            if (!lockHeldBySync) {
                rwLock.readLock().unlock();
            }
        }
    }

    public <T> T callWithWriteAccess(Supplier<T> action) {
        acquireWriteAccess();
        boolean lockHeldBySync = false;
        try {
            lockHeldBySync = registerSynchronizationIfActive();
            return action.get();
        } finally {
            if (!lockHeldBySync) {
                rwLock.readLock().unlock();
            }
        }
    }

    /**
     * Dành cho các thao tác đọc dữ liệu (findPaged, findById, getCounts, calculateRollup) và tạo bản sao lưu (createBackup).
     * Dùng shared read lock để cho phép nhiều tác vụ đọc/sao lưu đồng thời,
     * nhưng sẽ bị chặn hoàn toàn khi hệ thống đang thực hiện restore (exclusive write lock).
     */
    public void runWithReadAccess(Runnable action) {
        acquireReadAccess();
        try {
            action.run();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public <T> T callWithReadAccess(Supplier<T> action) {
        acquireReadAccess();
        try {
            return action.get();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void acquireReadAccess() {
        if (maintenanceMode) {
            throw new IllegalStateException("Hệ thống đang trong quá trình bảo trì / khôi phục dữ liệu (Restore). Mọi thao tác truy cập tạm thời bị khóa.");
        }
        rwLock.readLock().lock();
        if (maintenanceMode) {
            rwLock.readLock().unlock();
            throw new IllegalStateException("Hệ thống đang trong quá trình bảo trì / khôi phục dữ liệu (Restore). Mọi thao tác truy cập tạm thời bị khóa.");
        }
    }

    private void acquireWriteAccess() {
        if (maintenanceMode) {
            throw new IllegalStateException("Hệ thống đang trong quá trình bảo trì / khôi phục dữ liệu (Restore). Mọi thao tác ghi tạm thời bị khóa.");
        }
        rwLock.readLock().lock();
        if (maintenanceMode) {
            rwLock.readLock().unlock();
            throw new IllegalStateException("Hệ thống đang trong quá trình bảo trì / khôi phục dữ liệu (Restore). Mọi thao tác ghi tạm thời bị khóa.");
        }
    }

    private boolean registerSynchronizationIfActive() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            rwLock.readLock().unlock();
                        }
                    }
            );
            return true;
        }
        return false;
    }

    /**
     * Dành riêng cho thao tác Restore/Maintenance: Độc quyền toàn hệ thống (Exclusive Write Lock).
     * Chờ các tác vụ ghi đang dở dang hoàn tất, sau đó khóa tuyệt đối toàn bộ hệ thống
     * không cho bất kỳ luồng nào khác ghi vào SQLite trong suốt quá trình checkpoint -> restore -> integrity_check -> foreign_key_check.
     */
    public <T> T callWithExclusiveMaintenance(Supplier<T> action) {
        boolean acquired;
        try {
            acquired = rwLock.writeLock().tryLock(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Quá trình chờ lấy khóa bảo trì bị ngắt quãng.");
        }
        if (!acquired) {
            throw new IllegalStateException("Không thể thực hiện khôi phục dữ liệu do hệ thống đang có tác vụ ghi bận. Vui lòng thử lại sau.");
        }

        maintenanceMode = true;
        try {
            return action.get();
        } finally {
            maintenanceMode = false;
            rwLock.writeLock().unlock();
        }
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }
}
