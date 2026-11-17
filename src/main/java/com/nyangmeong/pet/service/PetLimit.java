package com.nyangmeong.pet.service;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;

/**
 * [계정당 반려동물 한도]
 * - 주양육 + 공동양육을 합친 최대 수는 운영 설정(PET_MAX_CNT)이 정한다 (등록·초대 수락·한도 조회가 같이 쓴다)
 */
final class PetLimit {

    private PetLimit() {
    }

    /**
     * 더 함께할 수 있는 수 (이미 넘었으면 0)
     */
    static int remaining(int max, int currentCount) {
        return Math.max(0, max - currentCount);
    }

    /**
     * 한도 초과 문구 : 운영 설정의 최대 수를 넣는다
     */
    static String message(int max) {
        return ErrorCode.PET_LIMIT_EXCEEDED.message().formatted(max);
    }

    /**
     * 한도 초과 예외
     */
    static CustomException exceeded(int max) {
        return CustomException.withMessage(ErrorCode.PET_LIMIT_EXCEEDED, message(max));
    }
}
