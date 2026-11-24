package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.nyangmeong.community.domain.CareMatchStatus;
import com.nyangmeong.global.setting.SystemSetting;
import com.nyangmeong.global.setting.SystemSettingService;

/**
 * [검사 - 무응답 배치의 거래 취소 요청 자동 동의]
 * - 자동 동의·하루 전 알림을 무응답 종료보다 먼저 하는지, 한 건 실패가 나머지를 막지 않는지, 건수에 자동 동의가 들어가는지
 */
class CareMatchNoResponseJobDealCancelTest {

    private final CareMatchService careMatchService = mock(CareMatchService.class);
    private final SystemSettingService systemSettingService = mock(SystemSettingService.class);
    private final CareMatchNoResponseJob job = new CareMatchNoResponseJob(careMatchService, systemSettingService);

    @BeforeEach
    void stubDays() {
        SystemSetting setting = new SystemSetting();
        setting.setMtchNoRspnsEndCrtrDayCnt(14L);
        when(systemSettingService.get()).thenReturn(setting);
    }

    @Test
    void 자동_동의와_하루_전_알림을_무응답_종료보다_먼저_한다() {
        when(careMatchService.findDealCancelAutoApproveTargets()).thenReturn(List.of(41L));
        when(careMatchService.findDealCancelReminderTargets()).thenReturn(List.of(42L));
        when(careMatchService.autoApproveDealCancel(41L)).thenReturn(true);
        when(careMatchService.findNoResponseTargets(CareMatchStatus.AWAITING_CONFIRM, 14L)).thenReturn(List.of(3L));
        when(careMatchService.expireNoResponse(3L, CareMatchStatus.AWAITING_CONFIRM, 14L)).thenReturn(true);

        assertThat(job.execute()).isEqualTo(2);

        InOrder order = inOrder(careMatchService);
        order.verify(careMatchService).autoApproveDealCancel(41L);
        order.verify(careMatchService).remindDealCancel(42L);
        order.verify(careMatchService).findNoResponseTargets(CareMatchStatus.REQUESTED, 14L);
        order.verify(careMatchService).expireNoResponse(3L, CareMatchStatus.AWAITING_CONFIRM, 14L);
    }

    @Test
    void 자동_동의_한_건이_실패해도_나머지와_무응답_종료를_처리한다() {
        when(careMatchService.findDealCancelAutoApproveTargets()).thenReturn(List.of(41L, 43L));
        when(careMatchService.autoApproveDealCancel(41L)).thenThrow(new IllegalStateException("잠금 시간 초과"));
        when(careMatchService.autoApproveDealCancel(43L)).thenReturn(true);
        when(careMatchService.findDealCancelReminderTargets()).thenReturn(List.of(44L, 45L));
        when(careMatchService.remindDealCancel(44L)).thenThrow(new IllegalStateException("알림 실패"));

        assertThat(job.execute()).isEqualTo(1);
        verify(careMatchService).autoApproveDealCancel(43L);
        verify(careMatchService).remindDealCancel(45L);
        verify(careMatchService).findNoResponseTargets(CareMatchStatus.AWAITING_CONFIRM, 14L);
    }

    @Test
    void 하루_전_알림은_처리_건수에_넣지_않는다() {
        when(careMatchService.findDealCancelReminderTargets()).thenReturn(List.of(42L));
        when(careMatchService.remindDealCancel(anyLong())).thenReturn(true);

        assertThat(job.execute()).isZero();
    }
}
