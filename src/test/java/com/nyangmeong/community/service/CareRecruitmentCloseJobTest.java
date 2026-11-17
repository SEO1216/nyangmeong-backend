package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nyangmeong.global.batch.BatchType;

/**
 * [검사 - 펫돌봄 지난 모집 자동 마감 배치]
 * - 대상마다 마감을 부르고 처리 건수를 돌려주는지, 한 건 실패가 나머지를 막지 않는지
 */
class CareRecruitmentCloseJobTest {

    private final CareRecruitmentService careRecruitmentService = mock(CareRecruitmentService.class);
    private final CareRecruitmentCloseJob job = new CareRecruitmentCloseJob(careRecruitmentService);

    @Test
    void 배치_구분과_주기() {
        assertThat(job.type()).isEqualTo(BatchType.CARE_RECRUITMENT_CLOSE);
        assertThat(job.type().getCode()).isEqualTo("SYSC0033");
        assertThat(job.cron()).isEqualTo("0 0 0 * * *");
    }

    @Test
    void 대상마다_마감하고_마감한_건수를_돌려준다() {
        when(careRecruitmentService.findCloseTargets()).thenReturn(List.of(1L, 2L, 3L));
        when(careRecruitmentService.closeExpired(1L)).thenReturn(true);
        when(careRecruitmentService.closeExpired(2L)).thenReturn(false);
        when(careRecruitmentService.closeExpired(3L)).thenReturn(true);

        assertThat(job.execute()).isEqualTo(2);
        verify(careRecruitmentService).closeExpired(1L);
        verify(careRecruitmentService).closeExpired(2L);
        verify(careRecruitmentService).closeExpired(3L);
    }

    @Test
    void 한_건이_실패해도_나머지를_처리한다() {
        when(careRecruitmentService.findCloseTargets()).thenReturn(List.of(1L, 2L));
        when(careRecruitmentService.closeExpired(1L)).thenThrow(new IllegalStateException("잠금 시간 초과"));
        when(careRecruitmentService.closeExpired(2L)).thenReturn(true);

        assertThat(job.execute()).isEqualTo(1);
        verify(careRecruitmentService).closeExpired(2L);
    }

    @Test
    void 대상이_없으면_0건() {
        when(careRecruitmentService.findCloseTargets()).thenReturn(List.of());

        assertThat(job.execute()).isZero();
    }
}
