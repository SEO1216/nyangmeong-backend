package com.nyangmeong.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.notification.EventPublisher;
import com.nyangmeong.global.validation.ProfanityFilter;
import com.nyangmeong.global.validation.ProfanityMapper;
import com.nyangmeong.pet.service.PixelCharacterService;
import com.nyangmeong.support.domain.Inquiry;
import com.nyangmeong.support.dto.InquiryCreateRequest;
import com.nyangmeong.support.mapper.InquiryMapper;

/**
 * [검사 - 1:1 문의 제목·내용에 금칙어가 있으면 저장하지 않는다]
 */
class InquiryProfanityTest {

    private final ProfanityMapper profanityMapper = mock(ProfanityMapper.class);
    private final InquiryMapper inquiryMapper = mock(InquiryMapper.class);
    private final InquiryService service = new InquiryService(inquiryMapper, mock(FileStore.class),
            mock(PixelCharacterService.class), mock(EventPublisher.class), new ProfanityFilter(profanityMapper));

    @Test
    void 제목만_금칙어면_inqryTtl_칸만_오류로_알리고_저장하지_않는다() {
        when(profanityMapper.selectWords("AIMC0041")).thenReturn(List.of("시발"));
        when(profanityMapper.selectWords("AIMC0042")).thenReturn(List.of());
        InquiryCreateRequest req = new InquiryCreateRequest("INQC0001", "시.발", "정상 문의 내용입니다", null, null, null);

        assertThatThrownBy(() -> service.create(1L, req)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFANITY_DETECTED);
            assertThat(e.getFields()).containsExactly("inqryTtl");
        });
        verify(inquiryMapper, never()).insertInquiry(any(Inquiry.class));
    }
}
