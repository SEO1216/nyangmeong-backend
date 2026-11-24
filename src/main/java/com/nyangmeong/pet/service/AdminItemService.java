package com.nyangmeong.pet.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileCategory;
import com.nyangmeong.global.file.FileOwner;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.log.AdminTaskLogService;
import com.nyangmeong.global.log.AdminTaskType;
import com.nyangmeong.pet.domain.Item;
import com.nyangmeong.pet.domain.ItemRegion;
import com.nyangmeong.pet.dto.AdminItemCreateRequest;
import com.nyangmeong.pet.dto.AdminItemCreateResponse;
import com.nyangmeong.pet.dto.AdminItemOrderRequest;
import com.nyangmeong.pet.dto.AdminItemSummary;
import com.nyangmeong.pet.dto.AdminItemUpdateRequest;
import com.nyangmeong.pet.mapper.ItemMapper;
import com.nyangmeong.pet.mapper.PixelCharacterMapper;

import lombok.RequiredArgsConstructor;

/**
 * [관리자 아이템 서비스]
 * - SCR-ADM-070 (FN-ADM-070-01~04)
 * - 삭제 없음 : 판매 중지(NTSL_YN='N'), 이미 산 회원의 보유는 그대로
 * - 굽기 대상은 그림 참고 굽기(BAKE_IMG_REF_YN='Y') 아이템뿐, 영어 굽기 지시는 없앰 (사람 결정 2026-11-17)
 * - 굽기 대상 아이템(수정 후 'Y')의 그림을 바꾸면 그 아이템의 착용 시트를 지워 다시 굽기 대상으로
 * - BAKE_IMG_REF_YN 만 바꾸면 이미 구운 시트는 그대로
 */
@Service
@RequiredArgsConstructor
public class AdminItemService {

    private static final String TARGET_TABLE = "ITEM";

    private final ItemMapper itemMapper;
    private final PixelCharacterMapper pixelCharacterMapper;
    private final FileStore fileStore;
    private final AdminTaskLogService adminTaskLogService;

    /** 감사 로그 전후 값 */
    private record ItemState(String itemRegnCd, String itemNm, Long needPnt, String ntslYn, Long sortSeq,
            String bakeImgRefYn, Long atchFileSn) {
        static ItemState from(Item item) {
            return new ItemState(item.getItemRegnCd(), item.getItemNm(), item.getNeedPnt(), item.getNtslYn(),
                    item.getSortSeq(), item.getBakeImgRefYn(), item.getAtchFileSn());
        }
    }

    /** 순서 저장 감사 로그 값 */
    private record ItemOrderState(String itemRegnCd, List<Long> itemSns) {
    }

    /**
     * 관리자 아이템 목록 조회 (판매 중지 포함, 부위·정렬 순서·번호순)
     */
    @Transactional(readOnly = true)
    public List<AdminItemSummary> search(String itemRegnCd) {
        if (hasText(itemRegnCd)) {
            checkRegion(itemRegnCd);
        }
        return itemMapper.selectAdminList(hasText(itemRegnCd) ? itemRegnCd : null);
    }

    /**
     * 아이템 등록 (정렬은 그 부위 마지막 다음)
     */
    @Transactional
    public AdminItemCreateResponse create(Long actorSn, AdminItemCreateRequest req) {
        checkRegion(req.itemRegnCd());

        String actorId = String.valueOf(actorSn);
        Item item = new Item();
        item.setAtchFileSn(fileStore.store(req.image(), FileCategory.ASSET_ITEM, FileOwner.service(), actorId));
        item.setItemRegnCd(req.itemRegnCd());
        item.setItemNm(req.itemNm().strip());
        item.setNeedPnt(req.needPnt());
        item.setSortSeq(itemMapper.selectMaxSortSeq(req.itemRegnCd()) + 1);
        item.setNtslYn(toYn(req.ntslYn()));
        item.setBakeImgRefYn(toYn(req.bakeImgRefYn()));
        item.setRgtrId(actorId);
        item.setMdfrId(actorId);
        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new CustomException(ErrorCode.ITEM_NAME_DUPLICATED);
        }
        adminTaskLogService.record(actorSn, AdminTaskType.CREATE, TARGET_TABLE, item.getItemSn(), "아이템 등록", null,
                ItemState.from(item));
        return new AdminItemCreateResponse(item.getItemSn());
    }

    /**
     * 아이템 수정·판매 중지 (보낸 칸만)
     * - 보유 회원이 있으면 부위 변경 불가 (착용 부위가 어긋남)
     */
    @Transactional
    public void update(Long actorSn, Long itemSn, AdminItemUpdateRequest req) {
        Item before = getOrThrow(itemSn);
        String actorId = String.valueOf(actorSn);

        boolean regionChanged = req.itemRegnCd() != null && !req.itemRegnCd().equals(before.getItemRegnCd());
        if (regionChanged) {
            checkRegion(req.itemRegnCd());
            if (itemMapper.countHolders(itemSn) > 0) {
                throw new CustomException(ErrorCode.ITEM_REGION_LOCKED);
            }
        }
        boolean imageChanged = req.image() != null && !req.image().isEmpty();
        String bakeImgRefYn = req.bakeImgRefYn() == null ? before.getBakeImgRefYn() : toYn(req.bakeImgRefYn());
        boolean resetSheets = imageChanged && "Y".equals(bakeImgRefYn);

        Item item = new Item();
        item.setItemSn(itemSn);
        item.setItemRegnCd(regionChanged ? req.itemRegnCd() : null);
        item.setSortSeq(regionChanged ? itemMapper.selectMaxSortSeq(req.itemRegnCd()) + 1 : null);
        item.setItemNm(req.itemNm() == null ? null : req.itemNm().strip());
        item.setNeedPnt(req.needPnt());
        item.setNtslYn(req.ntslYn() == null ? null : toYn(req.ntslYn()));
        item.setBakeImgRefYn(req.bakeImgRefYn() == null ? null : toYn(req.bakeImgRefYn()));
        if (imageChanged) {
            item.setAtchFileSn(fileStore.store(req.image(), FileCategory.ASSET_ITEM, FileOwner.service(), actorId));
        }
        item.setMdfrId(actorId);
        try {
            itemMapper.update(item);
        } catch (DuplicateKeyException e) {
            throw new CustomException(ErrorCode.ITEM_NAME_DUPLICATED);
        }
        if (resetSheets) {
            pixelCharacterMapper.updateItemSheetDeleteByItemSn(itemSn);
        }
        adminTaskLogService.record(actorSn, AdminTaskType.UPDATE, TARGET_TABLE, itemSn, "아이템 수정",
                ItemState.from(before), ItemState.from(getOrThrow(itemSn)));
    }

    /**
     * 부위 안 상점 순서 저장 (1부터 다시 매김)
     * - 그 부위 아이템 전부를 빠짐·중복 없이 보내야 함
     */
    @Transactional
    public void saveOrder(Long actorSn, AdminItemOrderRequest req) {
        checkRegion(req.itemRegnCd());
        List<Long> current = itemMapper.selectItemSnsByRegion(req.itemRegnCd());
        List<Long> itemSns = req.itemSns();
        if (itemSns.size() != current.size() || new HashSet<>(itemSns).size() != itemSns.size()
                || !new HashSet<>(current).equals(new HashSet<>(itemSns))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "itemSns");
        }
        String actorId = String.valueOf(actorSn);
        for (int i = 0; i < itemSns.size(); i++) {
            itemMapper.updateSortSeq(itemSns.get(i), i + 1L, actorId);
        }
        adminTaskLogService.record(actorSn, AdminTaskType.UPDATE, TARGET_TABLE, null, "아이템 순서 변경",
                new ItemOrderState(req.itemRegnCd(), current), new ItemOrderState(req.itemRegnCd(), itemSns));
    }

    private Item getOrThrow(Long itemSn) {
        Item item = itemMapper.selectItem(itemSn);
        if (item == null) {
            throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 신발은 서비스에서 뺀 부위 (코드 USE_YN='N', 기준 데이터 v0.14 (2))
     */
    private void checkRegion(String itemRegnCd) {
        if (ItemRegion.from(itemRegnCd).isEmpty() || ItemRegion.SHOES.is(itemRegnCd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "itemRegnCd");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String toYn(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Y" : "N";
    }
}
