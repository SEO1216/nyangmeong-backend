package com.nyangmeong.global.file;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.setting.SystemSettingService;

/**
 * [검사 - 요청 파일 번호 검사 (FileStore.requireStored)]
 */
class FileStoreRequireStoredTest {

    @TempDir
    Path root;

    private final AtchFileMapper atchFileMapper = mock(AtchFileMapper.class);
    private FileStore fileStore;

    @BeforeEach
    void setUp() {
        fileStore = new FileStore(atchFileMapper, new FileRoot(root), mock(SystemSettingService.class));
    }

    private void stored(Long atchFileSn, String physStrgPath) {
        AtchFile atchFile = new AtchFile();
        atchFile.setAtchFileSn(atchFileSn);
        atchFile.setPhysStrgPath(physStrgPath);
        when(atchFileMapper.selectInUse(atchFileSn)).thenReturn(atchFile);
    }

    private void assertInvalid(Long atchFileSn) {
        assertThatThrownBy(() -> fileStore.requireStored(atchFileSn, FileCategory.ASSET_NEWS, FileOwner.service()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 없거나_사용하지_않는_번호면_400() {
        assertInvalid(999L);
    }

    @Test
    void 다른_분류_경로면_400() {
        stored(1L, "3/community/post/a.png");
        stored(2L, "assets/notice/b.png");
        stored(3L, "assets/newsletter/c.png");

        assertInvalid(1L);
        assertInvalid(2L);
        assertInvalid(3L);
    }

    @Test
    void 뉴스_썸네일_경로와_기존_공개_파일은_통과() {
        stored(4L, "assets/news/d.png");
        stored(5L, "public/news/e.png");

        assertThatCode(() -> fileStore.requireStored(4L, FileCategory.ASSET_NEWS, FileOwner.service()))
                .doesNotThrowAnyException();
        assertThatCode(() -> fileStore.requireStored(5L, FileCategory.ASSET_NEWS, FileOwner.service()))
                .doesNotThrowAnyException();
    }
}
