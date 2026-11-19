package com.nyangmeong.community.dto;

import java.time.LocalDateTime;

import com.nyangmeong.community.domain.CareCancelDemand;

/**
 * [거래 취소 요청 항목]
 * - mineYn : 로그인한 회원이 요청한 쪽이면 true (요청한 쪽·받은 쪽 카드 구분)
 */
public record CareCancelDemandItem(
        Long mtchCnclDmndSn,
        Long dmndMbrSn,
        boolean mineYn,
        String cnclRsn,
        String dmndSttsCd,
        LocalDateTime regDt,
        LocalDateTime prcsDt) {

    public static CareCancelDemandItem of(CareCancelDemand demand, Long mbrSn) {
        return new CareCancelDemandItem(demand.getMtchCnclDmndSn(), demand.getDmndMbrSn(),
                demand.getDmndMbrSn().equals(mbrSn), demand.getCnclRsn(), demand.getDmndSttsCd(), demand.getRegDt(),
                demand.getPrcsDt());
    }
}
