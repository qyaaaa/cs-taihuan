package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.dto.AsyncTaskResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AsyncTaskService {
    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);
    private static final long COMPLETED_TASK_RETENTION_MILLIS = 6L * 60L * 60L * 1000L;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Map<String, AsyncTaskRecord> tasks = new ConcurrentHashMap<String, AsyncTaskRecord>();

    public AsyncTaskResponse submit(String type, String initialMessage, AsyncTaskWork work) {
        cleanupCompletedTasks();
        String taskId = UUID.randomUUID().toString();
        AsyncTaskRecord record = new AsyncTaskRecord(taskId, type, initialMessage);
        tasks.put(taskId, record);

        executorService.submit(new Callable<Void>() {
            public Void call() {
                runTask(record, work);
                return null;
            }
        });

        return record.toResponse();
    }

    public AsyncTaskResponse find(String taskId) {
        AsyncTaskRecord record = tasks.get(taskId);
        if (record == null) {
            throw new IllegalArgumentException(ErrorMessages.asyncTaskNotFound(taskId));
        }
        return record.toResponse();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runTask(AsyncTaskRecord record, AsyncTaskWork work) {
        record.running("任务已开始。");
        try {
            Object result = work.run(record);
            record.succeeded(result);
        } catch (BuffRateLimitException ex) {
            record.failed("BUFF_RATE_LIMIT", ex.getMessage(), true);
        } catch (IllegalArgumentException ex) {
            record.failed("BAD_REQUEST", ex.getMessage(), false);
        } catch (Exception ex) {
            log.error("Async task failed, taskId={}, type={}", record.taskId, record.type, ex);
            record.failed("INTERNAL_ERROR", ErrorMessages.INTERNAL_TASK_ERROR, false);
        }
    }

    private void cleanupCompletedTasks() {
        long threshold = System.currentTimeMillis() - COMPLETED_TASK_RETENTION_MILLIS;
        for (Map.Entry<String, AsyncTaskRecord> entry : tasks.entrySet()) {
            if (entry.getValue().isCompletedBefore(threshold)) {
                tasks.remove(entry.getKey());
            }
        }
    }

    public interface AsyncTaskWork {
        Object run(TaskProgress progress) throws Exception;
    }

    public interface TaskProgress {
        void update(int progress, Integer current, Integer total, String message);

        void message(String message);
    }

    private static final class AsyncTaskRecord implements TaskProgress {
        private final String taskId;
        private final String type;
        private final long createdAt;
        private String status;
        private int progress;
        private Integer current;
        private Integer total;
        private String message;
        private boolean canContinue;
        private String errorCode;
        private String errorMessage;
        private Object result;
        private Long startedAt;
        private Long finishedAt;

        private AsyncTaskRecord(String taskId, String type, String message) {
            this.taskId = taskId;
            this.type = type;
            this.createdAt = System.currentTimeMillis();
            this.status = "PENDING";
            this.progress = 0;
            this.message = message;
        }

        public synchronized void running(String message) {
            this.status = "RUNNING";
            this.startedAt = Long.valueOf(System.currentTimeMillis());
            this.message = message;
        }

        public synchronized void update(int progress, Integer current, Integer total, String message) {
            this.progress = Math.max(0, Math.min(99, progress));
            this.current = current;
            this.total = total;
            this.message = message;
        }

        public synchronized void message(String message) {
            this.message = message;
        }

        public synchronized void succeeded(Object result) {
            this.status = "SUCCEEDED";
            this.progress = 100;
            this.result = result;
            this.finishedAt = Long.valueOf(System.currentTimeMillis());
            if (result instanceof com.qyaaaa.cstaihuan.dto.SyncCatalogResponse
                && ((com.qyaaaa.cstaihuan.dto.SyncCatalogResponse) result).isPartial()) {
                this.canContinue = true;
            }
            if (result instanceof com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse) {
                this.message = ((com.qyaaaa.cstaihuan.dto.InventorySnapshotResponse) result).getMessage();
            } else if (result instanceof com.qyaaaa.cstaihuan.dto.SyncCatalogResponse) {
                this.message = ((com.qyaaaa.cstaihuan.dto.SyncCatalogResponse) result).getMessage();
            } else {
                this.message = "任务已完成。";
            }
        }

        public synchronized void failed(String errorCode, String errorMessage, boolean canContinue) {
            this.status = "FAILED";
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.message = errorMessage;
            this.canContinue = canContinue;
            this.finishedAt = Long.valueOf(System.currentTimeMillis());
        }

        public synchronized AsyncTaskResponse toResponse() {
            return new AsyncTaskResponse(
                taskId,
                type,
                status,
                progress,
                current,
                total,
                message,
                canContinue,
                errorCode,
                errorMessage,
                result,
                createdAt,
                startedAt,
                finishedAt
            );
        }

        private synchronized boolean isCompletedBefore(long threshold) {
            return finishedAt != null && finishedAt.longValue() < threshold;
        }
    }
}
