package com.nyangmeong.global.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;

/**
 * [검사 - 금칙어 검사기 정규화·예외·칸 오류]
 */
class ProfanityFilterTest {

    private final ProfanityMapper mapper = mock(ProfanityMapper.class);

    private ProfanityFilter filter() {
        when(mapper.selectWords(ProfanityFilter.USG_PROFANITY))
                .thenReturn(List.of("시발", "씨발", "병신", "개새끼", "ㅅㅂ", "ㅂㅅ", "fuck", "shit"));
        when(mapper.selectWords(ProfanityFilter.USG_EXCEPTION)).thenReturn(List.of("시발점", "병신년", "시바견"));
        return new ProfanityFilter(mapper);
    }

    @Test
    void 붙여_쓴_금칙어와_기호_숫자_띄어쓰기_우회를_잡는다() {
        ProfanityFilter filter = filter();

        assertThat(filter.contains("이런 시발 뭐야")).isTrue();
        assertThat(filter.contains("시 발")).isTrue();
        assertThat(filter.contains("시.발")).isTrue();
        assertThat(filter.contains("시1발")).isTrue();
        assertThat(filter.contains("ㅅ ㅂ")).isTrue();
        assertThat(filter.contains("ㅅㅂ")).isTrue();
        assertThat(filter.contains("FUCK you")).isTrue();
        assertThat(filter.contains("f.u.c.k")).isTrue();
    }

    @Test
    void 일상어와_예외어는_통과한다() {
        ProfanityFilter filter = filter();

        assertThat(filter.contains("다시 발견했어요")).isFalse();
        assertThat(filter.contains("다시.발견")).isFalse();
        assertThat(filter.contains("개 새끼를 낳았어요")).isFalse();
        assertThat(filter.contains("우리 시바견이에요")).isFalse();
        assertThat(filter.contains("이게 시발점이에요")).isFalse();
        assertThat(filter.contains("2016 병신년 이야기")).isFalse();
        assertThat(filter.contains("존재")).isFalse();
    }

    @Test
    void 비어_있거나_null이면_통과한다() {
        ProfanityFilter filter = filter();

        assertThat(filter.contains(null)).isFalse();
        assertThat(filter.contains("   ")).isFalse();
    }

    @Test
    void 걸린_칸만_오류로_알린다() {
        ProfanityFilter filter = filter();

        assertThatThrownBy(() -> filter.check("inqryTtl", "시발", "inqryCn", "정상 내용입니다", "extra", null))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFANITY_DETECTED);
                    assertThat(e.getFields()).containsExactly("inqryTtl");
                });
    }

    @Test
    void 여러_칸이_걸리면_칸_이름_순서로_모두_알린다() {
        ProfanityFilter filter = filter();

        assertThatThrownBy(() -> filter.check("pstTtl", "씨발", "pstCn", "병신"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getFields()).containsExactly("pstCn", "pstTtl"));
    }

    @Test
    void 깨끗한_글은_예외가_없다() {
        ProfanityFilter filter = filter();

        filter.check("cmntCn", "귀여운 강아지네요", "other", null);
    }

    @Test
    void 단어_목록을_못_읽으면_막지_않는다() {
        when(mapper.selectWords(ProfanityFilter.USG_PROFANITY)).thenThrow(new RuntimeException("DB 오류"));
        ProfanityFilter filter = new ProfanityFilter(mapper);

        assertThat(filter.contains("시발")).isFalse();
    }
}
