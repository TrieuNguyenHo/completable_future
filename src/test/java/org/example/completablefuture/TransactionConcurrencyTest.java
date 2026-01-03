package org.example.completablefuture;

import org.example.completablefuture.model.Transaction;
import org.example.completablefuture.service.BatchTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransactionConcurrencyTest {

    @Autowired
    private BatchTransactionService batchTransactionService;

    @Test
    void testParallelExecutionTime() throws Exception {
        // Giả lập 1,000 giao dịch
        List<Transaction> transactions = IntStream.range(0, 1000)
                .mapToObj(i -> new Transaction("TX-" + i))
                .toList();

        long startTime = System.currentTimeMillis();

        // Thực thi
        CompletableFuture<List<String>> resultFuture = batchTransactionService.processBulk(transactions);
        List<String> results = resultFuture.get(25, TimeUnit.SECONDS); // Timeout sau 25s

        long duration = System.currentTimeMillis() - startTime;

        // KIỂM CHỨNG:
        // Nếu chạy tuần tự (mỗi task 1s), mất 1,000s.
        // Nếu chạy song song với 100 threads, mất ~10-11s.
        // Nếu dùng Virtual Threads, mất ~1.1s - 2s.
        System.out.println("Tổng thời gian xử lý 1,000 giao dịch: " + duration + "ms");

        assertTrue(duration < 25000, "Xử lý quá chậm, có thể không chạy song song!");
        assertEquals(1000, results.size());
    }

    @Test
    void testErrorIsolation() throws Exception {
        List<Transaction> transactions = List.of(
                new Transaction("VALID-1"),
                new Transaction("ERROR-TRIGGER"), // Giả lập task này gây lỗi
                new Transaction("VALID-2")
        );

        CompletableFuture<List<String>> resultFuture = batchTransactionService.processBulk(transactions);
        List<String> results = resultFuture.get();

        // Kiểm chứng: Task lỗi trả về message lỗi, nhưng các task khác vẫn về đích
        assertTrue(results.contains("SUCCESS:VALID-1"));
        assertTrue(results.get(1).contains("Error"));
        assertTrue(results.contains("SUCCESS:VALID-2"));
    }

}
