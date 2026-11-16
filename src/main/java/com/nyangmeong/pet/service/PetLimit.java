package com.nyangmeong.pet.service;

/**
 * [계정당 반려동물 한도]
 * - 주양육 + 공동양육을 합쳐 10마리 (등록·초대 수락이 같이 쓴다)
 */
final class PetLimit {

    static final int MAX_PET_COUNT = 10;

    private PetLimit() {
    }

    /**
     * 더 함께할 수 있는 수 (이미 넘었으면 0)
     */
    static int remaining(int currentCount) {
        return Math.max(0, MAX_PET_COUNT - currentCount);
    }
}
