package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.SettlementReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, UUID> {
}