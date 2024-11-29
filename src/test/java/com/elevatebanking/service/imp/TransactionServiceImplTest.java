package com.elevatebanking.service.imp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private IAccountService accountService;
    
    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    
    private TransactionServiceImpl transactionService;
    
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
            transactionRepository, 
            accountService,
            kafkaTemplate
        );
        
        // Setup test accounts
        fromAccount = new Account();
        fromAccount.setId("acc1");
        fromAccount.setBalance(new BigDecimal("1000.00"));
        
        toAccount = new Account();
        toAccount.setId("acc2");
        toAccount.setBalance(new BigDecimal("500.00"));
        
        // Basic mocks setup
        when(accountService.getAccountById("acc1"))
            .thenReturn(Optional.of(fromAccount));
        when(accountService.getAccountById("acc2"))
            .thenReturn(Optional.of(toAccount));
            
        // Mock Kafka để tránh lỗi
        doNothing().when(kafkaTemplate).send(anyString(), anyString(), any(TransactionEvent.class));
    }

    @Test
    void testProcessTransfer_InsufficientBalance() {
        // Arrange
        BigDecimal amount = new BigDecimal("2000.00");
        String fromAccountId = "acc1";
        String toAccountId = "acc2";
        
        // Mock validateAccount để throw exception ngay lập tức
        doThrow(new IllegalArgumentException("Insufficient balance"))
            .when(accountService)
            .validateAccount(eq(fromAccountId), eq(amount));
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transactionService.processTransfer(fromAccountId, toAccountId, amount, "Test transfer")
        );
        
        assertEquals("Insufficient balance", exception.getMessage());
        
        // Verify
        verify(accountService).validateAccount(fromAccountId, amount);
        verify(transactionRepository, never()).save(any(Transaction.class)); // Không nên lưu transaction khi validate fail
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(TransactionEvent.class)); // Không nên gửi event
    }
}
