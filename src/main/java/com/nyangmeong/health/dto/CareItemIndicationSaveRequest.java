package com.nyangmeong.health.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * [케어리스트 표시 항목 저장 요청] (※신규 정책)
 * - PUT /api/pets/{petSn}/care-items/indications
 * - 케어리스트에 보일 항목 번호 전체 : 빠진 항목은 숨김, 처방·반복 예방 연동 항목은 빠져도 보임
 * - 5개 미만·남의 항목·중복은 서비스가 거절
 */
public record CareItemIndicationSaveRequest(
        @NotNull(message = "보일 항목 목록을 입력해 주세요.") List<@NotNull(message = "항목 번호를 입력해 주세요.") Long> careArtclSns) {
}
