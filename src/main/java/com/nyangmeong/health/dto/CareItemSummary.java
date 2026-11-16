package com.nyangmeong.health.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * [케어 항목 목록 행]
 * - 토탈 케어리스트 (FN-HLT-003-12)
 */
@Data
public class CareItemSummary {

    /** 케어항목일련번호 */
    private Long careArtclSn;
    /** 케어항목명 */
    private String careArtclNm;
    /** 케어구역공통코드(HLTG13) */
    private String careZoneCd;
    /** 케어주기공통코드(HLTG15) */
    private String careCycleCd;
    /** 케어주기명 */
    private String careCycleCdNm;
    /** 주기직접입력일수 */
    private Integer cycleDrctInptDayCnt;
    /** 대표아이콘ID */
    private String rprsIconId;
    /** 차기예정일자 */
    private LocalDate nxtmPrnmntYmd;
    /** 최종완료일자 */
    private LocalDate lastCmptnYmd;
    /** 기본항목여부 */
    private boolean bscArtclYn;
    /** 처방일련번호 */
    private Long prscSn;
    /** 반복예방관리일련번호 */
    private Long reptPrvntMngSn;
    /** 출처 검진기록일련번호 */
    private Long ckupRcdSn;
    /** 복용종료일자 */
    private LocalDate mdcnEndYmd;
    /** 1일복용횟수 */
    private Integer dayMdcnNmtm;
    /** 오늘 완료 건수 */
    private Integer todayCmptnCnt;
    /** 케어리스트 표시여부 (※신규 정책) : 고른 적 없으면 추천 항목·직접 추가·연동 항목, 연동 항목은 항상 true */
    private Boolean indctYn;
    /** 오늘 케어미션 여부 */
    private boolean todayMsnYn;
    /** 오늘 케어미션 배점 포인트 : 주양육자에게만, 공동양육자는 null (※신규 정책) */
    private Integer msnPnt;
    /** 오늘 케어미션 완료 여부 */
    private boolean msnCmptnYn;
}
