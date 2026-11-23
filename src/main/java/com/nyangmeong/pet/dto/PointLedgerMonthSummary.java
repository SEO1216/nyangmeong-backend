package com.nyangmeong.pet.dto;

/**
 * [포인트 내역이 있는 연월]
 * - GET /point-ledgers/months, 최신순 (FN-MYP-020-02)
 * - yearMonth = yyyy-MM
 */
public record PointLedgerMonthSummary(String yearMonth) {
}
