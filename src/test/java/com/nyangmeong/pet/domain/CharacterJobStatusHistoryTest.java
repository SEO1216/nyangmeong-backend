package com.nyangmeong.pet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * [검사 - 캐릭터 생성 작업 상태 이력 VO]
 */
class CharacterJobStatusHistoryTest {

    @Test
    void 작업_번호_회원_이전_상태_이후_상태를_그대로_담는다() {
        CharacterJobStatusHistory history = CharacterJobStatusHistory.of(7L, 3L, CharacterJobStatus.REQUESTED,
                CharacterJobStatus.CREATING);

        assertThat(history.getCharCrtJobSn()).isEqualTo(7L);
        assertThat(history.getMbrSn()).isEqualTo(3L);
        assertThat(history.getBfrSttsCd()).isEqualTo(CharacterJobStatus.REQUESTED.getCode());
        assertThat(history.getAftrSttsCd()).isEqualTo(CharacterJobStatus.CREATING.getCode());
        assertThat(history.getChgRsn()).isNull();
    }

    @Test
    void 처음_등록이면_이전_상태와_회원이_비어_있다() {
        CharacterJobStatusHistory history = CharacterJobStatusHistory.of(7L, null, null, CharacterJobStatus.REQUESTED);

        assertThat(history.getMbrSn()).isNull();
        assertThat(history.getBfrSttsCd()).isNull();
        assertThat(history.getAftrSttsCd()).isEqualTo(CharacterJobStatus.REQUESTED.getCode());
    }
}
