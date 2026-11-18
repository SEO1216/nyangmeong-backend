package com.nyangmeong.community.domain;

/**
 * [펫돌봄 거래 취소 요청 상태 코드]
 * - PCAG07 (MTCH_CNCL_DMND.DMND_STTS_CD)
 */
public enum CareCancelDemandStatus {
    REQUESTING("PCAC0021"),
    APPROVED("PCAC0022"),
    REJECTED("PCAC0023"),
    WITHDRAWN("PCAC0024");

    private final String code;

    CareCancelDemandStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean is(String code) {
        return this.code.equals(code);
    }
}
