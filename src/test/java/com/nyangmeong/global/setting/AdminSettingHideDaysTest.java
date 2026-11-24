package com.nyangmeong.global.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.global.log.AdminTaskLogService;
import com.nyangmeong.global.log.AdminTaskType;
import com.nyangmeong.global.security.domain.LoginMember;

/**
 * [검사 - 운영 설정 거래완료 숨김 기준 일수]
 * - 조회 응답의 community 묶음에 담기는지, 수정하면 감사 로그에 이전·이후 값이 남는지
 */
class AdminSettingHideDaysTest {

    private final SystemSettingMapper systemSettingMapper = mock(SystemSettingMapper.class);
    private final AdminTaskLogService adminTaskLogService = mock(AdminTaskLogService.class);
    private final AdminSystemSettingService service = new AdminSystemSettingService(
            new SystemSettingService(systemSettingMapper), systemSettingMapper, adminTaskLogService);
    private final LoginMember admin = new LoginMember(9L, "USRC0005");

    @Test
    void 조회_응답의_커뮤니티_묶음에_숨김_일수를_담는다() {
        SystemSetting setting = new SystemSetting();
        assertThat(AdminSettingResponse.from(setting).community().dlngCmptnHideCrtrDayCnt()).isEqualTo(7L);

        setting.setDlngCmptnHideCrtrDayCnt(3L);
        assertThat(AdminSettingResponse.from(setting).community().dlngCmptnHideCrtrDayCnt()).isEqualTo(3L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 수정하면_감사_로그에_이전_이후_값을_남긴다() {
        when(systemSettingMapper.selectCurrent()).thenReturn(new SystemSetting());

        service.update(admin, new AdminSettingUpdateRequest(null, null, null, null, null, null, null, null, 3L, null,
                null, null, null, null, null, null, null));

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(adminTaskLogService).record(eq(9L), eq(AdminTaskType.UPDATE), eq("SYS_STNG"), eq(1L),
                eq("운영 설정 수정: dlngCmptnHideCrtrDayCnt"), before.capture(), after.capture());
        assertThat((Map<String, Object>) before.getValue()).containsOnlyKeys("dlngCmptnHideCrtrDayCnt")
                .containsEntry("dlngCmptnHideCrtrDayCnt", 7L);
        assertThat((Map<String, Object>) after.getValue()).containsOnlyKeys("dlngCmptnHideCrtrDayCnt")
                .containsEntry("dlngCmptnHideCrtrDayCnt", 3L);
    }
}
