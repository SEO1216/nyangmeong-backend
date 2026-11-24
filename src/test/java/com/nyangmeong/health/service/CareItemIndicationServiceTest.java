package com.nyangmeong.health.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.global.auth.PetAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.health.domain.BasicCareItem;
import com.nyangmeong.health.domain.CareItem;
import com.nyangmeong.health.domain.CareZone;
import com.nyangmeong.health.dto.CareItemIndicationSaveRequest;
import com.nyangmeong.health.dto.CareItemSearchCond;
import com.nyangmeong.health.dto.CareItemSummary;
import com.nyangmeong.health.dto.CareMissionProgressResponse;
import com.nyangmeong.health.mapper.CareItemMapper;
import com.nyangmeong.pet.dto.DailyMissionSummary;
import com.nyangmeong.pet.service.CareMissionService;

/**
 * [검사 - 케어리스트 표시 항목·오늘 케어미션] (※신규 정책)
 * - 고른 적 없으면 추천 5개·직접 추가·연동 항목, 5개 이상, 연동 항목은 항상 보임
 * - 오늘 미션 여부·완료 여부, 포인트는 주양육자에게만
 */
class CareItemIndicationServiceTest {

    private static final Long MBR = 1L;
    private static final Long PET = 10L;
    private static final Long CUSTOM = 200L;
    private static final Long MEDICATION = 300L;
    private static final Long PREVENTION = 400L;

    private final CareItemMapper mapper = mock(CareItemMapper.class);
    private final PetAuthorizer petAuthorizer = mock(PetAuthorizer.class);
    private final CareMissionService careMissionService = mock(CareMissionService.class);
    private final CareItemService service = new CareItemService(mapper, petAuthorizer, careMissionService);

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        when(mapper.countBasicItems(PET)).thenReturn(11L);
        when(mapper.selectPetForUpdate(PET)).thenReturn(PET);
    }

    /** 기본 11개 행 : SQL처럼 고른 적 없으면 indctYn null, 번호는 1~11 */
    private List<CareItemSummary> basicRows(Boolean indctYn) {
        List<CareItemSummary> rows = new ArrayList<>();
        long sn = 1;
        for (BasicCareItem basic : BasicCareItem.values()) {
            rows.add(row(sn++, basic.getCareArtclNm(), true, indctYn));
        }
        return rows;
    }

    private CareItemSummary row(Long careArtclSn, String careArtclNm, boolean isBasic, Boolean indctYn) {
        CareItemSummary row = new CareItemSummary();
        row.setCareArtclSn(careArtclSn);
        row.setCareArtclNm(careArtclNm);
        row.setBscArtclYn(isBasic);
        row.setIndctYn(indctYn);
        return row;
    }

    private CareItemSummary medicationRow() {
        CareItemSummary row = row(MEDICATION, "넥스가드", false, true);
        row.setPrscSn(50L);
        return row;
    }

    private CareItemSummary preventionRow() {
        CareItemSummary row = row(PREVENTION, "심장사상충 예방", false, true);
        row.setReptPrvntMngSn(60L);
        return row;
    }

    private void storedRows(List<CareItemSummary> rows) {
        when(mapper.selectList(eq(PET), any(), any(), any())).thenAnswer(invocation -> copy(rows));
    }

    private List<CareItemSummary> copy(List<CareItemSummary> rows) {
        List<CareItemSummary> copied = new ArrayList<>();
        for (CareItemSummary row : rows) {
            CareItemSummary c = row(row.getCareArtclSn(), row.getCareArtclNm(), row.isBscArtclYn(), row.getIndctYn());
            c.setPrscSn(row.getPrscSn());
            c.setReptPrvntMngSn(row.getReptPrvntMngSn());
            copied.add(c);
        }
        return copied;
    }

    private CareItem customItem() {
        CareItem item = new CareItem();
        item.setCareArtclSn(CUSTOM);
        item.setPetSn(PET);
        item.setCareZoneCd(CareZone.DAILY.getCode());
        item.setCareArtclNm("산책 준비");
        item.setBscArtclYn("N");
        item.setRegDt(LocalDateTime.of(2026, 10, 1, 9, 0));
        item.setDelYn("N");
        return item;
    }

    private DailyMissionSummary mission(String msnNm, int scorPnt, boolean isCompleted) {
        DailyMissionSummary mission = new DailyMissionSummary();
        mission.setMsnNm(msnNm);
        mission.setScorPnt(scorPnt);
        mission.setCmptnYn(isCompleted);
        return mission;
    }

    private CustomException thrown(Runnable call) {
        return assertThrows(CustomException.class, call::run);
    }

    @SuppressWarnings("unchecked")
    private Collection<Long> savedIndications() {
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).updateIndications(eq(PET), captor.capture(), eq("1"));
        return captor.getValue();
    }

    @Test
    void 고른_적_없으면_추천_5개와_직접_추가와_연동_항목만_보인다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(row(CUSTOM, "산책 준비", false, true));
        rows.add(medicationRow());
        storedRows(rows);

        List<CareItemSummary> result = service.search(MBR, PET, new CareItemSearchCond());

        assertThat(result).filteredOn(CareItemSummary::getIndctYn).extracting(CareItemSummary::getCareArtclNm)
                .containsExactlyInAnyOrder("화장실 청소", "양치", "빗질", "놀이", "귀 청소", "산책 준비", "넥스가드");
        assertThat(result).filteredOn(row -> !row.getIndctYn()).hasSize(6);
    }

    @Test
    void 저장된_표시여부는_그대로_쓴다() {
        List<CareItemSummary> rows = basicRows(false);
        rows.get(6).setIndctYn(true);
        storedRows(rows);

        List<CareItemSummary> result = service.search(MBR, PET, new CareItemSearchCond());

        assertThat(result).filteredOn(CareItemSummary::getIndctYn).extracting(CareItemSummary::getCareArtclNm)
                .containsExactly(BasicCareItem.values()[6].getCareArtclNm());
    }

    @Test
    void 저장은_보낸_항목과_연동_항목을_보임으로_한다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(medicationRow());
        rows.add(preventionRow());
        storedRows(rows);

        service.saveIndications(MBR, PET, new CareItemIndicationSaveRequest(List.of(6L, 7L, 8L)));

        assertThat(savedIndications()).containsExactlyInAnyOrder(6L, 7L, 8L, MEDICATION, PREVENTION);
    }

    @Test
    void 연동_항목을_뺀_저장도_오류_없이_연동_항목은_보인다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(medicationRow());
        storedRows(rows);

        service.saveIndications(MBR, PET, new CareItemIndicationSaveRequest(List.of(1L, 2L, 3L, 4L, 5L)));

        assertThat(savedIndications()).contains(MEDICATION).hasSize(6);
    }

    @Test
    void 보일_항목이_5개_미만이면_indctYn_400() {
        storedRows(basicRows(null));

        CustomException e = thrown(
                () -> service.saveIndications(MBR, PET, new CareItemIndicationSaveRequest(List.of(1L, 2L, 3L, 4L))));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(e.getFields()).containsExactly("indctYn");
        verify(mapper, never()).updateIndications(anyLong(), any(), anyString());
    }

    @Test
    void 연동_항목도_5개_계산에_들어간다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(medicationRow());
        storedRows(rows);

        service.saveIndications(MBR, PET, new CareItemIndicationSaveRequest(List.of(1L, 2L, 3L, 4L)));

        assertThat(savedIndications()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, MEDICATION);
    }

    @Test
    void 남의_항목이나_중복_번호는_400() {
        storedRows(basicRows(null));

        for (List<Long> sns : List.of(List.of(1L, 2L, 3L, 4L, 999L), List.of(1L, 2L, 3L, 4L, 5L, 5L))) {
            CustomException e = thrown(() -> service.saveIndications(MBR, PET, new CareItemIndicationSaveRequest(sns)));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThat(e.getFields()).containsExactly("careArtclSns");
        }
        verify(mapper, never()).updateIndications(anyLong(), any(), anyString());
    }

    @Test
    void 저장_전에_반려동물을_잠근다() {
        when(mapper.selectPetForUpdate(PET)).thenReturn(null);

        assertThat(thrown(() -> service.saveIndications(MBR, PET,
                new CareItemIndicationSaveRequest(List.of(1L, 2L, 3L, 4L, 5L)))).getErrorCode())
                .isEqualTo(ErrorCode.PET_NOT_FOUND);
        verify(mapper, never()).updateIndications(anyLong(), any(), anyString());
    }

    @Test
    void 보이는_항목을_지워_5개_미만이_되면_indctYn_400() {
        List<CareItemSummary> rows = basicRows(false);
        for (int i = 0; i < 4; i++) {
            rows.get(i).setIndctYn(true);
        }
        rows.add(row(CUSTOM, "산책 준비", false, true));
        storedRows(rows);
        when(mapper.selectItemForUpdate(CUSTOM)).thenReturn(customItem());

        CustomException e = thrown(() -> service.delete(MBR, PET, CUSTOM));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(e.getFields()).containsExactly("indctYn");
        verify(mapper, never()).deleteItem(anyLong(), anyString());
    }

    @Test
    void 숨긴_항목은_5개_미만이어도_지운다() {
        List<CareItemSummary> rows = basicRows(false);
        for (int i = 0; i < 5; i++) {
            rows.get(i).setIndctYn(true);
        }
        rows.add(row(CUSTOM, "산책 준비", false, false));
        storedRows(rows);
        when(mapper.selectItemForUpdate(CUSTOM)).thenReturn(customItem());

        service.delete(MBR, PET, CUSTOM);

        verify(mapper).deleteItem(CUSTOM, "1");
    }

    @Test
    void 보이는_항목을_지워도_5개가_남으면_지운다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(row(CUSTOM, "산책 준비", false, true));
        storedRows(rows);
        when(mapper.selectItemForUpdate(CUSTOM)).thenReturn(customItem());

        service.delete(MBR, PET, CUSTOM);

        verify(mapper).selectPetForUpdate(PET);
        verify(mapper).deleteItem(CUSTOM, "1");
    }

    @Test
    void 오늘_출제된_기본_항목만_미션_값이_있고_주양육자는_포인트를_받는다() {
        List<CareItemSummary> rows = basicRows(null);
        rows.add(row(CUSTOM, "양치", false, true));
        storedRows(rows);
        when(petAuthorizer.isPrimary(MBR, PET)).thenReturn(true);
        when(careMissionService.searchByPet(PET, today))
                .thenReturn(List.of(mission("양치하기", 20, true), mission("체중 기록하기", 10, false)));

        List<CareItemSummary> result = service.search(MBR, PET, new CareItemSearchCond());

        assertThat(result).filteredOn(CareItemSummary::isTodayMsnYn)
                .extracting(CareItemSummary::getCareArtclSn, CareItemSummary::getMsnPnt, CareItemSummary::isMsnCmptnYn)
                .containsExactly(tuple(2L, 20, true));
        assertThat(result).filteredOn(row -> !row.isTodayMsnYn())
                .allMatch(row -> row.getMsnPnt() == null && !row.isMsnCmptnYn());
    }

    @Test
    void 공동양육자는_미션_여부와_완료_여부만_보고_포인트는_null() {
        storedRows(basicRows(null));
        when(petAuthorizer.isPrimary(MBR, PET)).thenReturn(false);
        when(careMissionService.searchByPet(PET, today)).thenReturn(List.of(mission("빗질하기", 20, false)));

        List<CareItemSummary> result = service.search(MBR, PET, new CareItemSearchCond());

        assertThat(result).filteredOn(CareItemSummary::isTodayMsnYn)
                .extracting(CareItemSummary::getCareArtclNm, CareItemSummary::getMsnPnt, CareItemSummary::isMsnCmptnYn)
                .containsExactly(tuple("빗질", null, false));
    }

    @Test
    void 미션_진행은_그날_전체_미션_수와_완료_수_주양육자_여부를_준다() {
        when(petAuthorizer.isPrimary(MBR, PET)).thenReturn(false);
        when(careMissionService.searchByPet(PET, today)).thenReturn(List.of(mission("양치하기", 20, true),
                mission("체중 기록하기", 10, true), mission("목욕하기", 30, false)));

        CareMissionProgressResponse progress = service.searchMissionProgress(MBR, PET);

        assertThat(progress).isEqualTo(new CareMissionProgressResponse(false, 2, 3));
        verify(petAuthorizer).checkCaregiver(MBR, PET);
    }

    @Test
    void 미션_진행은_양육자가_아니면_권한_오류() {
        doThrow(new CustomException(ErrorCode.PET_ACCESS_DENIED)).when(petAuthorizer).checkCaregiver(MBR, PET);

        assertThat(thrown(() -> service.searchMissionProgress(MBR, PET)).getErrorCode())
                .isEqualTo(ErrorCode.PET_ACCESS_DENIED);
        verify(careMissionService, never()).searchByPet(any(), any());
    }

    @Test
    void 출제_전이면_0_0() {
        when(petAuthorizer.isPrimary(MBR, PET)).thenReturn(true);

        assertThat(service.searchMissionProgress(MBR, PET)).isEqualTo(new CareMissionProgressResponse(true, 0, 0));
    }
}
