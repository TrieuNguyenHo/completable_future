package org.example.completablefuture.service;

import org.example.completablefuture.model.Transaction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Service
public class BatchTransactionService {

    private final Executor bankExecutor;

    private final BankService bankService;
    // Giới hạn chỉ cho phép 50 request bắn sang ngân hàng cùng lúc
    // để tránh bị block IP hoặc lỗi Rate Limit
    private final Semaphore semaphore = new Semaphore(50);

    public BatchTransactionService(@Qualifier("virtualThreadExecutor") Executor bankExecutor, BankService bankService) {
        this.bankExecutor = bankExecutor;
        this.bankService = bankService;
    }

    public CompletableFuture<List<String>> processBulk(List<Transaction> txs) {
        List<CompletableFuture<String>> futures = txs.stream()
                .map(tx -> CompletableFuture.supplyAsync(() -> {
                    try {
                        if (tx.getTxId().contains("ERROR")) {
                            Thread.currentThread().interrupt();
                            return "Error";
                        }
                        semaphore.acquire(); // Xin "giấy phép" trước khi gọi API
                        return bankService.processTransaction(tx.getTxId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "Error";
                    } finally {
                        semaphore.release(); // Trả "giấy phép" để task khác vào
                    }
                }, bankExecutor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }
}