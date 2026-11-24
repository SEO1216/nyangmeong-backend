package com.nyangmeong.pet.dto;

/**
 * [내 반려동물 한도 응답]
 * - petMaxCnt : 운영 설정의 계정당 반려동물 최대 수 (SYS_STNG.PET_MAX_CNT)
 * - linkedPetCount : 주양육 + 공동양육 합계
 * - remainingPetCount : 더 함께할 수 있는 수 (이미 넘었으면 0)
 */
public record PetLimitResponse(int petMaxCnt, int linkedPetCount, int remainingPetCount) {
}
