package com.nyangmeong.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.log.AdminTaskLogService;
import com.nyangmeong.pet.domain.Item;
import com.nyangmeong.pet.dto.AdminItemCreateRequest;
import com.nyangmeong.pet.dto.AdminItemOrderRequest;
import com.nyangmeong.pet.dto.AdminItemUpdateRequest;
import com.nyangmeong.pet.mapper.ItemMapper;
import com.nyangmeong.pet.mapper.PixelCharacterMapper;

/**
 * [검사 - 관리자 아이템 등록·수정·순서]
 */
class AdminItemServiceTest {

    private final ItemMapper itemMapper = mock(ItemMapper.class);
    private final PixelCharacterMapper pixelCharacterMapper = mock(PixelCharacterMapper.class);
    private final FileStore fileStore = mock(FileStore.class);
    private final AdminTaskLogService adminTaskLogService = mock(AdminTaskLogService.class);
    private final AdminItemService service = new AdminItemService(itemMapper, pixelCharacterMapper, fileStore,
            adminTaskLogService);

    private final MockMultipartFile image = new MockMultipartFile("image", "hat.png", "image/png", new byte[] {1});

    private Item stored(String regnCd, String bakeImgRefYn) {
        Item item = new Item();
        item.setItemSn(7L);
        item.setItemRegnCd(regnCd);
        item.setItemNm("밀짚모자");
        item.setNeedPnt(500L);
        item.setSortSeq(3L);
        item.setNtslYn("Y");
        item.setBakeImgRefYn(bakeImgRefYn);
        item.setAtchFileSn(11L);
        return item;
    }

    private AdminItemUpdateRequest update(String regnCd, Boolean ntslYn) {
        return new AdminItemUpdateRequest(regnCd, null, null, ntslYn, null, null);
    }

    private AdminItemUpdateRequest updateBake(Boolean bakeImgRefYn, MultipartFile newImage) {
        return new AdminItemUpdateRequest(null, null, null, null, bakeImgRefYn, newImage);
    }

    @Test
    void 등록하면_부위_마지막_다음_순서로_넣는다() {
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(11L);
        when(itemMapper.selectMaxSortSeq("MYPC0001")).thenReturn(5L);

        service.create(1L, new AdminItemCreateRequest("MYPC0001", " 새 모자 ", 300L, true, null, image));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).insert(captor.capture());
        Item item = captor.getValue();
        assertThat(item.getSortSeq()).isEqualTo(6L);
        assertThat(item.getItemNm()).isEqualTo("새 모자");
        assertThat(item.getNtslYn()).isEqualTo("Y");
        assertThat(item.getAtchFileSn()).isEqualTo(11L);
    }

    @Test
    void 같은_이름이면_409() {
        when(itemMapper.insert(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(
                () -> service.create(1L, new AdminItemCreateRequest("MYPC0001", "밀짚모자", 300L, true, null, image)))
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_NAME_DUPLICATED);
    }

    @Test
    void 신발을_굽기_대상으로_등록하면_400() {
        assertThatThrownBy(
                () -> service.create(1L, new AdminItemCreateRequest("MYPC0006", "장화", 300L, true, true, image)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(itemMapper, never()).insert(any());
    }

    @Test
    void 신발은_사용하지_않는_부위라_등록하면_400() {
        assertThatThrownBy(
                () -> service.create(1L, new AdminItemCreateRequest("MYPC0006", "장화", 300L, true, null, image)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(itemMapper, never()).insert(any());
    }

    @Test
    void 신발로_부위를_바꾸면_400() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", null));

        assertThatThrownBy(() -> service.update(1L, 7L, update("MYPC0006", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(itemMapper, never()).update(any());
    }

    @Test
    void 신발_부위로_목록을_조회하면_400() {
        assertThatThrownBy(() -> service.search("MYPC0006"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(itemMapper, never()).selectAdminList(any());
    }

    @Test
    void 없는_부위면_400() {
        assertThatThrownBy(
                () -> service.create(1L, new AdminItemCreateRequest("MYPC9999", "모자", 300L, true, null, image)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 보유_회원이_있으면_부위를_바꿀_수_없다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", null));
        when(itemMapper.countHolders(7L)).thenReturn(2L);

        assertThatThrownBy(() -> service.update(1L, 7L, update("MYPC0002", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_REGION_LOCKED);
        verify(itemMapper, never()).update(any());
    }

    @Test
    void 부위를_바꾸면_새_부위_마지막_다음_순서() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", null));
        when(itemMapper.selectMaxSortSeq("MYPC0002")).thenReturn(9L);

        service.update(1L, 7L, update("MYPC0002", null));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(captor.capture());
        assertThat(captor.getValue().getItemRegnCd()).isEqualTo("MYPC0002");
        assertThat(captor.getValue().getSortSeq()).isEqualTo(10L);
    }

    @Test
    void 판매_중지는_보유를_건드리지_않는다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "Y"));

        service.update(1L, 7L, update(null, false));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(captor.capture());
        assertThat(captor.getValue().getNtslYn()).isEqualTo("N");
        verify(pixelCharacterMapper, never()).updateItemSheetDeleteByItemSn(anyLong());
    }

    @Test
    void 굽기_대상_아이템의_그림을_바꾸면_착용_시트를_지운다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "Y"));
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(12L);

        service.update(1L, 7L, updateBake(null, image));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(captor.capture());
        assertThat(captor.getValue().getAtchFileSn()).isEqualTo(12L);
        verify(pixelCharacterMapper).updateItemSheetDeleteByItemSn(7L);
    }

    @Test
    void 그림과_함께_굽기를_켜면_착용_시트를_지운다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "N"));
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(12L);

        service.update(1L, 7L, updateBake(true, image));

        verify(pixelCharacterMapper).updateItemSheetDeleteByItemSn(7L);
    }

    @Test
    void 굽기_대상이_아닌_아이템의_그림을_바꾸면_시트를_지우지_않는다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "N"));
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(12L);

        service.update(1L, 7L, updateBake(null, image));

        verify(itemMapper).update(any());
        verify(pixelCharacterMapper, never()).updateItemSheetDeleteByItemSn(anyLong());
    }

    @Test
    void 그림과_함께_굽기를_끄면_시트를_지우지_않는다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "Y"));
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(12L);

        service.update(1L, 7L, updateBake(false, image));

        verify(pixelCharacterMapper, never()).updateItemSheetDeleteByItemSn(anyLong());
    }

    @Test
    void 굽기_여부만_바꾸면_시트를_지우지_않는다() {
        when(itemMapper.selectItem(7L)).thenReturn(stored("MYPC0001", "N"));

        service.update(1L, 7L, updateBake(true, null));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).update(captor.capture());
        assertThat(captor.getValue().getBakeImgRefYn()).isEqualTo("Y");
        verify(pixelCharacterMapper, never()).updateItemSheetDeleteByItemSn(anyLong());
    }

    @Test
    void 없는_아이템_수정은_404() {
        assertThatThrownBy(() -> service.update(1L, 7L, update(null, false)))
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void 순서는_부위_아이템_전부를_받아_1부터_매긴다() {
        when(itemMapper.selectItemSnsByRegion("MYPC0001")).thenReturn(List.of(1L, 2L, 3L));

        service.saveOrder(1L, new AdminItemOrderRequest("MYPC0001", List.of(3L, 1L, 2L)));

        verify(itemMapper).updateSortSeq(3L, 1L, "1");
        verify(itemMapper).updateSortSeq(1L, 2L, "1");
        verify(itemMapper).updateSortSeq(2L, 3L, "1");
    }

    @Test
    void 순서에_빠짐_중복_남의_부위가_있으면_400() {
        when(itemMapper.selectItemSnsByRegion("MYPC0001")).thenReturn(List.of(1L, 2L, 3L));

        for (List<Long> itemSns : List.of(List.of(1L, 2L), List.of(1L, 1L, 2L), List.of(1L, 2L, 9L))) {
            assertThatThrownBy(() -> service.saveOrder(1L, new AdminItemOrderRequest("MYPC0001", itemSns)))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> {
                        assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                        assertThat(((CustomException) e).getFields()).containsExactly("itemSns");
                    });
        }
        verify(itemMapper, never()).updateSortSeq(anyLong(), anyLong(), anyString());
    }

    @Test
    void 신발_부위_순서_저장은_itemRegnCd로_400() {
        assertThatThrownBy(() -> service.saveOrder(1L, new AdminItemOrderRequest("MYPC0006", List.of(1L))))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(((CustomException) e).getFields()).containsExactly("itemRegnCd");
                });
        verify(itemMapper, never()).updateSortSeq(anyLong(), anyLong(), anyString());
    }

    @Test
    void 굽기_대상으로_등록하면_Y로_넣는다() {
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(11L);

        service.create(1L, new AdminItemCreateRequest("MYPC0001", "거북이 가방", 300L, true, true, image));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getBakeImgRefYn()).isEqualTo("Y");
    }

    @Test
    void 그림_참고_굽기를_안_보내면_N으로_등록한다() {
        when(fileStore.store(any(MultipartFile.class), any(), any(), anyString())).thenReturn(11L);

        service.create(1L, new AdminItemCreateRequest("MYPC0001", "털모자", 300L, true, null, image));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getBakeImgRefYn()).isEqualTo("N");
    }
}
