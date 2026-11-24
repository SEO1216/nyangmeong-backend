package com.nyangmeong.community.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [펫돌봄 거래 취소 요청 VO]
 * - MTCH_CNCL_DMND 1:1
 */
@Data
public class CareCancelDemand {
    private Long mtchCnclDmndSn;
    private Long petPrtcMtchSn;
    private Long dmndMbrSn;
    private String cnclRsn;
    private String dmndSttsCd;
    private Long prcsMbrSn;
    private LocalDateTime prcsDt;
    private LocalDateTime regDt;
    private LocalDateTime mdfcnDt;
}
