package com.nyangmeong.community.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.nyangmeong.community.domain.CareMatchStatus;
import com.nyangmeong.global.batch.BatchJob;
import com.nyangmeong.global.batch.BatchType;
import com.nyangmeong.global.setting.SystemSettingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [펫돌봄 매칭 무응답 종료 배치 (FN-CMU-005-06·09)]
 * - 매일 03:00 KST : 수락 대기 -> 요청 취소, 확인 대기 -> 무응답 종료 (기준 일수 SYS_STNG.MTCH_NO_RSPNS_END_CRTR_DAY_CNT)
 * - 수령 대기는 종료하지 않음 (FN-CMU-005-10 미수령 시 상태 유지, 사람 결정 2026-11-06)
 * - 거래 취소 요청을 받은 쪽이 7일 무응답이면 무응답 종료보다 먼저 자동 동의 (매칭 완료만, 사람 결정 2026-11-20)
 *   자동 동의 하루 전에는 받은 쪽에 알림. 확인 대기는 요청중인 취소 요청이 있어도 무응답 종료 대상
 * - 매칭마다 따로 트랜잭션 : 한 건이 실패해도 나머지는 처리
 * - community 안에서 끝나는 배치라 scheduler 가 아닌 이 도메인에 둠 (docs/convention/architecture.md)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareMatchNoResponseJob implements BatchJob {

    private static final List<CareMatchStatus> TARGETS = List.of(CareMatchStatus.REQUESTED,
            CareMatchStatus.AWAITING_CONFIRM);

    private final CareMatchService careMatchService;
    private final SystemSettingService systemSettingService;

    @Override
    public BatchType type() {
        return BatchType.CARE_MATCH_NO_RESPONSE;
    }

    @Override
    public String cron() {
        return "0 0 3 * * *";
    }

    @Override
    public Integer execute() {
        int approved = 0;
        for (Long mtchCnclDmndSn : careMatchService.findDealCancelAutoApproveTargets()) {
            try {
                if (careMatchService.autoApproveDealCancel(mtchCnclDmndSn)) {
                    approved++;
                }
            } catch (RuntimeException e) {
                log.warn("거래 취소 요청 자동 동의 실패 : mtchCnclDmndSn={}", mtchCnclDmndSn, e);
            }
        }
        int reminded = 0;
        for (Long mtchCnclDmndSn : careMatchService.findDealCancelReminderTargets()) {
            try {
                if (careMatchService.remindDealCancel(mtchCnclDmndSn)) {
                    reminded++;
                }
            } catch (RuntimeException e) {
                log.warn("거래 취소 요청 자동 동의 사전 알림 실패 : mtchCnclDmndSn={}", mtchCnclDmndSn, e);
            }
        }
        log.info("거래 취소 요청 자동 동의 : {}건, 하루 전 알림 : {}건", approved, reminded);

        long days = systemSettingService.get().getMtchNoRspnsEndCrtrDayCnt();
        int processed = 0;
        for (CareMatchStatus from : TARGETS) {
            for (Long petPrtcMtchSn : careMatchService.findNoResponseTargets(from, days)) {
                try {
                    if (careMatchService.expireNoResponse(petPrtcMtchSn, from, days)) {
                        processed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("매칭 무응답 처리 실패 : petPrtcMtchSn={}, from={}", petPrtcMtchSn, from, e);
                }
            }
        }
        log.info("매칭 무응답 종료 : {}건 (기준 {}일)", processed, days);
        return approved + processed;
    }
}
