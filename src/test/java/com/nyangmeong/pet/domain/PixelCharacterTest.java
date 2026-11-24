package com.nyangmeong.pet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * [검사 - 픽셀 캐릭터 VO]
 */
class PixelCharacterTest {

    @Test
    void 기본_캐릭터_대체가_Y면_오류_확인_중이다() {
        assertThat(character("Y").isUnderReview()).isTrue();
    }

    @Test
    void 기본_캐릭터_대체가_N이나_null이면_오류_확인_중이_아니다() {
        assertThat(character("N").isUnderReview()).isFalse();
        assertThat(character(null).isUnderReview()).isFalse();
    }

    private PixelCharacter character(String bscCharSbstYn) {
        PixelCharacter character = new PixelCharacter();
        character.setBscCharSbstYn(bscCharSbstYn);
        return character;
    }
}
