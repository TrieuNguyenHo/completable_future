package org.example.completablefuture.service;

import org.springframework.stereotype.Service;

@Service
public class BankService {
    public String processTransaction(String txId) {
        try {
            // Mô phỏng độ trễ API
            Thread.sleep(1000);
            return "SUCCESS:" + txId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "FAILED:" + txId;
        }
    }
}