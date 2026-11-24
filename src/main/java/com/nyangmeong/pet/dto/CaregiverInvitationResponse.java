package com.nyangmeong.pet.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * [받은 초대 상세 응답]
 * - 초대한 사람·반려동물은 대기중이고 이메일이 맞을 때만 : 다른 계정에 남의 반려동물 정보 노출 방지
 * - 없으면 JSON 에서 제외
 * - remainingPetCount : 로그인 회원이 더 함께할 수 있는 반려동물 수(대기중·이메일 일치일 때만)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaregiverInvitationResponse(Long jntBrupInvtnSn, String sndrMbrNcnm, String invtnSttsCd,
        LocalDateTime invtnExpryDt, String invtnTrgtEmlMask, boolean emailMatched,
        List<CaregiverInvitationPetSummary> pets, Integer remainingPetCount) {

    /** 남은 자리 수가 없는 응답(초대 발송 등) */
    public CaregiverInvitationResponse(Long jntBrupInvtnSn, String sndrMbrNcnm, String invtnSttsCd,
            LocalDateTime invtnExpryDt, String invtnTrgtEmlMask, boolean emailMatched,
            List<CaregiverInvitationPetSummary> pets) {
        this(jntBrupInvtnSn, sndrMbrNcnm, invtnSttsCd, invtnExpryDt, invtnTrgtEmlMask, emailMatched, pets, null);
    }
}
