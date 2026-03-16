package com.banking.service;

import com.banking.domain.Account;
import com.banking.domain.Transaction;
import com.banking.domain.TransactionType;
import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.request.TransactionRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.DuplicateAccountException;
import com.banking.exception.InsufficientBalanceException;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(1L)
                .holderName("Arthur Oliveira")
                .document("123.456.789-00")
                .accountNumber("0001-1")
                .balance(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createAccount_success() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName("Arthur Oliveira");
        request.setDocument("123.456.789-00");
        request.setAccountNumber("0001-1");
        request.setInitialBalance(BigDecimal.ZERO);

        when(accountRepository.existsByDocument(any())).thenReturn(false);
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any())).thenReturn(account);

        AccountResponse response = accountService.createAccount(request);

        assertThat(response.getHolderName()).isEqualTo("Jane Doe");
        assertThat(response.getAccountNumber()).isEqualTo("0001-1");
        verify(accountRepository, times(1)).save(any());
    }

    @Test
    void createAccount_duplicateDocument_throws() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName("Arthur Oliveira");
        request.setDocument("123.456.789-00");
        request.setAccountNumber("0001-1");
        request.setInitialBalance(BigDecimal.ZERO);

        when(accountRepository.existsByDocument("123.456.789-00")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("document");
    }

    @Test
    void createAccount_duplicateAccountNumber_throws() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName("Arthur Oliveira");
        request.setDocument("123.456.789-00");
        request.setAccountNumber("0001-1");
        request.setInitialBalance(BigDecimal.ZERO);

        when(accountRepository.existsByDocument(any())).thenReturn(false);
        when(accountRepository.existsByAccountNumber("0001-1")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("account number");
    }

    @Test
    void findById_notFound_throws() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findById(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAll_returnsAllAccounts() {
        when(accountRepository.findAll()).thenReturn(List.of(account));

        List<AccountResponse> result = accountService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void deposit_updatesBalanceAndCreatesTransaction() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("100.00"));

        Transaction saved = Transaction.builder()
                .id(1L)
                .type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .timestamp(LocalDateTime.now())
                .account(account)
                .build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionResponse response = accountService.deposit(1L, request);

        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getAmount()).isEqualByComparingTo("100.00");
        assertThat(account.getBalance()).isEqualByComparingTo("600.00");
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void deposit_accountNotFound_throws() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("100.00"));

        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(99L, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void withdraw_success() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("200.00"));

        Transaction saved = Transaction.builder()
                .id(2L)
                .type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("200.00"))
                .timestamp(LocalDateTime.now())
                .account(account)
                .build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionResponse response = accountService.withdraw(1L, request);

        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(account.getBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void withdraw_insufficientBalance_throws() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("1000.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(1L, request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void getTransactions_accountNotFound_throws() {
        when(accountRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.getTransactions(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getTransactions_returnsOrderedList() {
        Transaction t1 = Transaction.builder()
                .id(1L).type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .timestamp(LocalDateTime.now().minusHours(1))
                .account(account).build();

        Transaction t2 = Transaction.builder()
                .id(2L).type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("50.00"))
                .timestamp(LocalDateTime.now())
                .account(account).build();

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transactionRepository.findByAccountIdOrderByTimestampDesc(1L))
                .thenReturn(List.of(t2, t1));

        List<TransactionResponse> result = accountService.getTransactions(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }
}
