package com.nyangmeong.pet.dto;

/**
 * [캐릭터 무료 재생성 작업 요약]
 * - 관리자 1:1 문의 상세의 재생성 상태와 실패 뒤 다시 처리 판단 (FN-ADM-042-07)
 */
public record CharacterRegenJobSummary(Long charCrtJobSn, String crtJobSttsCd, boolean inProgress, boolean failed) {
}
