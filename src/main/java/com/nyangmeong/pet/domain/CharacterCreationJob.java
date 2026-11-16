package com.nyangmeong.pet.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [캐릭터 생성 작업 VO]
 * - CHAR_CRT_JOB 1:1, 최초 생성과 무료 재생성마다 1행
 * - 무료 재생성 작업은 멱등 ID 가 REGEN- 으로 시작 (FN-MYP-010-23)
 */
@Data
public class CharacterCreationJob {

    /** 무료 재생성 작업 멱등 ID 접두어 */
    public static final String REGEN_ID_PREFIX = "REGEN-";

    private Long charCrtJobSn;
    private Long petSn;
    private Long mbrSn;
    private Long otoInqrySn;
    private String crtJobSttsCd;
    private LocalDateTime prcsBgngDt;
    private LocalDateTime prcsEndDt;
    private String failRsn;
    private String idmptDmndId;
    private LocalDateTime regDt;
    private LocalDateTime mdfcnDt;

    /**
     * 무료 재생성 작업 여부
     */
    public boolean isRegeneration() {
        return idmptDmndId != null && idmptDmndId.startsWith(REGEN_ID_PREFIX);
    }
}
