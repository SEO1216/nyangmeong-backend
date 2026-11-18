package com.nyangmeong.pet.domain;

/**
 * [미션풀 코드]
 * - MYPG04 (CARE_MSN.MSN_POOL_CD) : 분류용, 출제는 등급 조합으로 하고 풀로 가르지 않음 (※신규 정책)
 */
public enum CareMissionPool {
    DAILY("MYPC0010"),
    ACTIVITY("MYPC0011");

    private final String code;

    CareMissionPool(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean is(String code) {
        return this.code.equals(code);
    }
}
