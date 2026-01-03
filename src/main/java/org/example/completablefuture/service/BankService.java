package org.example.completablefuture.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BankService {
    public String processTransaction(String txId) {
        try {
            // Mô phỏng độ trễ API
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Giao dịch " + txId + " thành công lúc " + LocalDateTime.now();
    }
}