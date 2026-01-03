package org.example.completablefuture.controller;

import org.example.completablefuture.service.BankService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final BankService bankService;
    private final Executor virtualThreadExecutor;
    private final Executor fixedThreadExecutor;

    private final Semaphore semaphore = new Semaphore(50); // Giới hạn đồng thời 50 tasks

    public TransactionController(BankService bankService,
                                 @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor,
                                 @Qualifier("fixedThreadExecutor") Executor fixedThreadExecutor) {
        this.bankService = bankService;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.fixedThreadExecutor = fixedThreadExecutor;
    }

    @GetMapping("/bulk-process-virtual")
    public ResponseEntity<String> processBulkWithVirtualThread() {
        long start = System.currentTimeMillis();
        List<String> results = processBulk(virtualThreadExecutor);
        long end = System.currentTimeMillis();
        return ResponseEntity.ok("Xử lý " + results.size() + " giao dịch trong: " + (end - start) + "ms");
    }

    @GetMapping("/bulk-process-fixed")
    public ResponseEntity<String> processBulkWithFixedThread() {
        long start = System.currentTimeMillis();
        List<String> results = processBulk(fixedThreadExecutor);
        long end = System.currentTimeMillis();
        return ResponseEntity.ok("Xử lý " + results.size() + " giao dịch trong: " + (end - start) + "ms");
    }

    private List<String> processBulk(Executor executor) {
        int totalTasks = 1000;

        // 1. Tạo danh sách 1,000 tasks
        List<CompletableFuture<String>> futures = IntStream.range(0, totalTasks)
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                // Giới hạn số lượng task đồng thời
                                semaphore.acquire();
                                return bankService.processTransaction("TX-" + i);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Bị gián đoạn");
                            } finally {
                                semaphore.release();
                            }
                        },
                        executor // Sử dụng custom executor ở đây
                ).exceptionally(ex -> "Lỗi giao dịch: " + ex.getMessage()))
                .toList();

        // 2. Chờ tất cả hoàn thành
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // 3. Thu thập kết quả
        return allOf.thenApply(v ->
                futures.stream().map(CompletableFuture::join).toList()
        ).join();
    }
}