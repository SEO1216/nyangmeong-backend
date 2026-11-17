package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.nyangmeong.community.domain.News;
import com.nyangmeong.community.dto.AdminNewsCreateRequest;
import com.nyangmeong.community.dto.AdminNewsUpdateRequest;
import com.nyangmeong.community.mapper.NewsMapper;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileCategory;
import com.nyangmeong.global.file.FileOwner;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.log.AdminTaskLogService;

/**
 * [검사 - 관리자 펫뉴스 썸네일 번호 검사 (SCR-ADM-050, FN-ADM-050-02)]
 */
class AdminNewsThumbnailTest {

    private static final Long ACTOR = 1L;
    private static final String POLICY = "CMUC0020";
    private static final String ADMIN = "CMUC0023";

    private final NewsMapper newsMapper = mock(NewsMapper.class);
    private final FileStore fileStore = mock(FileStore.class);
    private final AdminNewsService service = new AdminNewsService(newsMapper, fileStore,
            mock(AdminTaskLogService.class));

    private void rejects(Long atchFileSn) {
        doThrow(new CustomException(ErrorCode.INVALID_INPUT_VALUE)).when(fileStore).requireStored(atchFileSn,
                FileCategory.ASSET_NEWS, FileOwner.service());
    }

    private News adminNews() {
        News news = new News();
        news.setNewsSn(5L);
        news.setNewsSrcCd(ADMIN);
        news.setNewsClsfCd(POLICY);
        news.setNewsTtl("제목");
        news.setNewsCn("요약");
        news.setUseYn("Y");
        return news;
    }

    @Test
    void 등록_썸네일이_뉴스_이미지가_아니면_400이고_저장하지_않는다() {
        rejects(9L);

        assertThatThrownBy(() -> service.create(ACTOR, new AdminNewsCreateRequest("제목", "요약", POLICY, 9L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(newsMapper, never()).insertAdmin(any());
    }

    @Test
    void 등록_썸네일을_검사한다_보내지_않으면_검사하지_않는다() {
        service.create(ACTOR, new AdminNewsCreateRequest("제목", "요약", POLICY, 9L));
        service.create(ACTOR, new AdminNewsCreateRequest("제목", "요약", POLICY, null));

        verify(fileStore).requireStored(9L, FileCategory.ASSET_NEWS, FileOwner.service());
        verify(fileStore, never()).requireStored(null, FileCategory.ASSET_NEWS, FileOwner.service());
    }

    @Test
    void 수정_썸네일이_뉴스_이미지가_아니면_400이고_고치지_않는다() {
        when(newsMapper.selectById(5L)).thenReturn(adminNews());
        rejects(9L);

        assertThatThrownBy(() -> service.update(ACTOR, 5L, new AdminNewsUpdateRequest(null, null, null, 9L, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(newsMapper, never()).updateAdmin(any());
    }

    @Test
    void 수정_없는_기사면_썸네일_검사보다_404가_먼저다() {
        rejects(9L);

        assertThatThrownBy(() -> service.update(ACTOR, 5L, new AdminNewsUpdateRequest(null, null, null, 9L, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NEWS_NOT_FOUND);
    }

    @Test
    void 수정_썸네일을_보내지_않으면_검사하지_않는다() {
        News news = adminNews();
        when(newsMapper.selectById(5L)).thenReturn(news);
        when(newsMapper.updateAdmin(any())).thenReturn(1);

        service.update(ACTOR, 5L, new AdminNewsUpdateRequest(null, null, null, null, false));

        verify(fileStore, never()).requireStored(any(), any(), any());
    }
}
