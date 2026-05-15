package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.LedgerAccount;
import com.example.razorpaywebhook.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByAccountType(LedgerAccountType accountType);
}