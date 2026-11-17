package com.nyangmeong.pet.dto;

import com.nyangmeong.global.error.ErrorCode;

/**
 * [공동양육 초대 수락 처리 결과]
 * - 반려동물 한도 초과는 예외가 아니라 결과로 돌려줌 : 응답 data 에 남은 자리 수를 싣기 위해
 * - 결과로 돌려주는 실패는 바꾼 것이 없어야 하므로 서비스가 쓰기 전에 검사한다
 * - message : 운영 설정의 최대 수가 들어간 문구 (실패일 때만)
 */
public record CaregiverInvitationAcceptResult(ErrorCode error, String message, PetLimitExceededResponse limit) {

    public static CaregiverInvitationAcceptResult success() {
        return new CaregiverInvitationAcceptResult(null, null, null);
    }

    public static CaregiverInvitationAcceptResult limitExceeded(String message, int remainingPetCount) {
        return new CaregiverInvitationAcceptResult(ErrorCode.PET_LIMIT_EXCEEDED, message,
                new PetLimitExceededResponse(remainingPetCount));
    }

    public boolean succeeded() {
        return error == null;
    }
}
