package com.nyangmeong.community.service;

import org.springframework.stereotype.Component;

import com.nyangmeong.global.batch.BatchJob;
import com.nyangmeong.global.batch.BatchType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [펫돌봄 지난 모집 자동 마감 배치 (SCR-CMU-004, 프론트 요청서 2번)]
 * - 매일 00:00 KST : 매칭 없는 모집중 글 중 일정이 지났거나 일정 없이 작성 후 30일이 지난 글을 거래완료로
 * - 글마다 따로 트랜잭션 : 한 건이 실패해도 나머지는 처리
 * - community 안에서 끝나는 배치라 scheduler 가 아닌 이 도메인에 둠 (docs/convention/architecture.md)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareRecruitmentCloseJob implements BatchJob {

    private final CareRecruitmentService careRecruitmentService;

    @Override
    public BatchType type() {
        return BatchType.CARE_RECRUITMENT_CLOSE;
    }

    @Override
    public String cron() {
        return "0 0 0 * * *";
    }

    @Override
    public Integer execute() {
        int processed = 0;
        for (Long petPrtcRcrtSn : careRecruitmentService.findCloseTargets()) {
            try {
                if (careRecruitmentService.closeExpired(petPrtcRcrtSn)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.warn("지난 모집 자동 마감 실패 : petPrtcRcrtSn={}", petPrtcRcrtSn, e);
            }
        }
        log.info("지난 모집 자동 마감 : {}건", processed);
        return processed;
    }
}
