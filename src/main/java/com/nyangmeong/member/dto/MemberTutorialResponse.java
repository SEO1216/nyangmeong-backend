package com.nyangmeong.member.dto;

/**
 * [서비스 튜토리얼 봤음 여부 응답]
 * - false 면 프론트가 메인에서 튜토리얼 모달을 띄운다
 */
public record MemberTutorialResponse(boolean tutrlViewYn) {

    public static MemberTutorialResponse from(String tutrlViewYn) {
        return new MemberTutorialResponse("Y".equals(tutrlViewYn));
    }
}
