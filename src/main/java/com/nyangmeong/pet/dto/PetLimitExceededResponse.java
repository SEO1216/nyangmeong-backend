package com.nyangmeong.pet.dto;

/**
 * [반려동물 한도 초과 오류 응답 data]
 * - 초대 수락이 한도를 넘을 때 : 프론트가 남은 자리만큼만 고르게 안내
 */
public record PetLimitExceededResponse(int remainingPetCount) {
}
