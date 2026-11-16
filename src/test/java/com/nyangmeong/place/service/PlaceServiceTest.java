package com.nyangmeong.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.external.PlaceSearchClient;
import com.nyangmeong.global.external.PlaceSearchClient.PlaceSearchPage;
import com.nyangmeong.global.external.PlaceSearchClient.PlaceSearchQuery;
import com.nyangmeong.global.external.PlaceSearchItem;
import com.nyangmeong.global.response.PageQuery;
import com.nyangmeong.global.response.PageResponse;
import com.nyangmeong.place.domain.Place;
import com.nyangmeong.place.dto.PlaceSearchCond;
import com.nyangmeong.place.dto.PlaceSummary;
import com.nyangmeong.place.mapper.PlaceMapper;

/**
 * [검사 - 장소 검색]
 */
class PlaceServiceTest {

    private static final Long MBR = 1L;
    private static final String HOSPITAL = "PLCC0001";
    private static final String PET_HOSPITAL = "가정,생활 > 반려동물 > 동물병원";

    private final PlaceSearchClient placeSearchClient = mock(PlaceSearchClient.class);
    private final PlaceMapper placeMapper = mock(PlaceMapper.class);
    private final PlaceService service = new PlaceService(placeSearchClient, placeMapper);

    private PlaceSearchCond cond(String plcClsfCd, String keyword) {
        PlaceSearchCond cond = new PlaceSearchCond();
        cond.setPlcClsfCd(plcClsfCd);
        cond.setKeyword(keyword);
        cond.setSwLat(new BigDecimal("35.70"));
        cond.setSwLot(new BigDecimal("127.00"));
        cond.setNeLat(new BigDecimal("35.95"));
        cond.setNeLot(new BigDecimal("127.25"));
        cond.setCntrLat(new BigDecimal("35.8242"));
        cond.setCntrLot(new BigDecimal("127.1480"));
        return cond;
    }

    private PlaceSearchItem item(String id, String name, String categoryName) {
        return new PlaceSearchItem(id, name, "전북 전주시", "063-000-0000", "http://place.map.kakao.com/" + id,
                new BigDecimal("35.8242"), new BigDecimal("127.1480"), categoryName);
    }

    private PlaceSummary saved(String kakaoPlcId, Long plcSn, String name, Long plcBkmkSn) {
        PlaceSummary summary = new PlaceSummary();
        summary.setPlcSn(plcSn);
        summary.setKakaoPlcId(kakaoPlcId);
        summary.setPlcClsfCd(HOSPITAL);
        summary.setPlcConm(name);
        summary.setPlcLat(new BigDecimal("35.8342"));
        summary.setPlcLot(new BigDecimal("127.1480"));
        summary.setPlcBkmkSn(plcBkmkSn);
        return summary;
    }

    private PlaceSearchQuery lastQuery() {
        ArgumentCaptor<PlaceSearchQuery> captor = ArgumentCaptor.forClass(PlaceSearchQuery.class);
        verify(placeSearchClient).search(captor.capture());
        return captor.getValue();
    }

    private void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(CustomException.class).hasFieldOrPropertyWithValue("errorCode",
                code);
    }

    @Test
    void 검색어가_없으면_카테고리_단어로_검색한다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        assertThat(lastQuery().query()).isEqualTo("동물병원");
    }

    @Test
    void 검색어에_카테고리_단어를_붙인다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        service.search(MBR, cond(HOSPITAL, " 전주 "), new PageQuery(null, null, null));
        assertThat(lastQuery().query()).isEqualTo("전주 동물병원");
    }

    @Test
    void 검색어에_카테고리_단어가_있으면_그대로_검색한다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        service.search(MBR, cond(HOSPITAL, "전주 동물병원"), new PageQuery(null, null, null));

        assertThat(lastQuery().query()).isEqualTo("전주 동물병원");
    }

    @Test
    void 반려동물_카테고리가_아닌_업체는_저장하지_않고_거른다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(
                List.of(item("1", "온누리동물병원", PET_HOSPITAL), item("2", "사람병원", "의료,건강 > 병원")), 2, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "온누리동물병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        ArgumentCaptor<List<Place>> placeCaptor = ArgumentCaptor.forClass(List.class);
        verify(placeMapper).upsertKakaoPlaces(placeCaptor.capture());
        assertThat(placeCaptor.getValue()).extracting(Place::getKakaoPlcId).containsExactly("1");
        assertThat(placeCaptor.getValue().get(0).getPlcClsfCd()).isEqualTo(HOSPITAL);
        assertThat(placeCaptor.getValue().get(0).getPlcSrcCd()).isEqualTo("PLCC0008");
        assertThat(res.content()).extracting(PlaceSummary::getPlcSn).containsExactly(11L);
    }

    @Test
    void 카카오가_빈_문자열로_준_전화번호_주소_URL은_null로_저장한다() {
        PlaceSearchItem blank = new PlaceSearchItem("1", "전북동물병원", " ", "", "",
                new BigDecimal("35.8242"), new BigDecimal("127.1480"), PET_HOSPITAL);
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(blank), 1, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "전북동물병원", null)));

        service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        ArgumentCaptor<List<Place>> placeCaptor = ArgumentCaptor.forClass(List.class);
        verify(placeMapper).upsertKakaoPlaces(placeCaptor.capture());
        Place place = placeCaptor.getValue().get(0);
        assertThat(place.getRprsTelno()).isNull();
        assertThat(place.getPlcAddr()).isNull();
        assertThat(place.getKakaoUrlPath()).isNull();
    }

    @Test
    void 저장한_뒤_장소_번호와_즐겨찾기_번호를_붙이고_카카오_순서를_지킨다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(
                List.of(item("1", "가병원", PET_HOSPITAL), item("2", "나병원", PET_HOSPITAL)), 30, false));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("2", 12L, "나병원", 5L), saved("1", 11L, "가병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        assertThat(res.content()).extracting(PlaceSummary::getPlcSn).containsExactly(11L, 12L);
        assertThat(res.content().get(1).getPlcBkmkSn()).isEqualTo(5L);
        assertThat(res.content().get(0).getPlcBkmkSn()).isNull();
        assertThat(res.totalElements()).isEqualTo(30);
        assertThat(res.totalPages()).isEqualTo(2);
        assertThat(res.size()).isEqualTo(15);
        assertThat(res.hasNext()).isTrue();
    }

    @Test
    void 관리자가_숨긴_업체는_결과에서_빠진다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(
                List.of(item("1", "가병원", PET_HOSPITAL), item("2", "숨긴병원", PET_HOSPITAL)), 2, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "가병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        assertThat(res.content()).extracting(PlaceSummary::getKakaoPlcId).containsExactly("1");
    }

    @Test
    void 거리는_지도_중앙에서_미터로_계산한다() {
        when(placeSearchClient.search(any())).thenReturn(
                new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 1, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "가병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        /** 위도 0.01도 차이 = 약 1,112m */
        assertThat(res.content().get(0).getDistance()).isBetween(1100, 1125);
    }

    @Test
    void 거리순은_카카오_거리순으로_요청한다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, "DISTANCE"));

        assertThat(lastQuery().distanceFirst()).isTrue();
    }

    @Test
    void 이름순은_세_쪽을_모두_받아_가나다순으로_정렬한_뒤_쪽을_자른다() {
        when(placeSearchClient.search(any())).thenReturn(
                new PlaceSearchPage(List.of(item("1", "다병원", PET_HOSPITAL), item("2", "가병원", PET_HOSPITAL)), 4, false),
                new PlaceSearchPage(List.of(item("3", "나병원", PET_HOSPITAL), item("4", "라병원", PET_HOSPITAL)), 4, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList())).thenReturn(List.of(
                saved("1", 11L, "다병원", null), saved("2", 12L, "가병원", null),
                saved("3", 13L, "나병원", null), saved("4", 14L, "라병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(1, null, "NAME"));

        assertThat(res.content()).extracting(PlaceSummary::getPlcConm).containsExactly("가병원", "나병원", "다병원", "라병원");
        assertThat(res.totalElements()).isEqualTo(4);
        assertThat(res.hasNext()).isFalse();
    }

    @Test
    void 이름순_두_번째_쪽은_15건_뒤부터_준다() {
        List<PlaceSearchItem> items = new java.util.ArrayList<>();
        List<PlaceSummary> savedRows = new java.util.ArrayList<>();
        for (int i = 1; i <= 17; i++) {
            String name = String.format("병원%02d", i);
            items.add(item(String.valueOf(i), name, PET_HOSPITAL));
            savedRows.add(saved(String.valueOf(i), (long) i, name, null));
        }
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(items, 17, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList())).thenReturn(savedRows);

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(2, null, "NAME"));

        assertThat(res.content()).extracting(PlaceSummary::getPlcConm).containsExactly("병원16", "병원17");
        assertThat(res.page()).isEqualTo(2);
        assertThat(res.hasNext()).isFalse();
    }

    @Test
    void 세_쪽을_넘기면_카카오를_부르지_않고_빈_결과다() {
        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(4, null, null));

        assertThat(res.content()).isEmpty();
        assertThat(res.hasNext()).isFalse();
        verify(placeSearchClient, never()).search(any());
    }

    @Test
    void 결과가_없으면_저장하지_않고_빈_목록이다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, "없는곳"), new PageQuery(null, null, null));

        assertThat(res.content()).isEmpty();
        verify(placeMapper, never()).upsertKakaoPlaces(anyList());
    }

    @Test
    void 없는_카테고리나_정렬은_400이다() {
        assertError(() -> service.search(MBR, cond("XXXX", null), new PageQuery(null, null, null)),
                ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, "RANDOM")),
                ErrorCode.INVALID_INPUT_VALUE);
        verify(placeSearchClient, never()).search(any());
    }

    @Test
    void 카카오가_실패하면_502를_그대로_올린다() {
        when(placeSearchClient.search(any())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR));

        assertError(() -> service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null)),
                ErrorCode.EXTERNAL_API_ERROR);
        verify(placeMapper, never()).upsertKakaoPlaces(anyList());
    }

    /** 좌표 6칸이 모두 없는 조건 (AI 대화창) */
    private PlaceSearchCond condWithoutCoordinates(String keyword) {
        PlaceSearchCond cond = new PlaceSearchCond();
        cond.setPlcClsfCd(HOSPITAL);
        cond.setKeyword(keyword);
        return cond;
    }

    private void assertInvalidFields(Runnable call, String... fields) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThat(e.getFields()).containsExactly(fields);
        });
    }

    @Test
    void 좌표가_없으면_범위_없이_검색하고_거리는_null이다() {
        when(placeSearchClient.search(any())).thenReturn(
                new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 1, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "가병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, condWithoutCoordinates("전주"),
                new PageQuery(null, null, null));

        PlaceSearchQuery query = lastQuery();
        assertThat(query.hasArea()).isFalse();
        assertThat(query.distanceFirst()).isFalse();
        assertThat(query.query()).isEqualTo("전주 동물병원");
        assertThat(res.content()).extracting(PlaceSummary::getPlcSn).containsExactly(11L);
        assertThat(res.content().get(0).getDistance()).isNull();
    }

    @Test
    void 좌표가_없어도_이름순은_검색한다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0, true));

        service.search(MBR, condWithoutCoordinates(null), new PageQuery(1, null, "NAME"));

        assertThat(lastQuery().hasArea()).isFalse();
    }

    @Test
    void 좌표가_없는데_거리순이면_400이고_칸은_sort다() {
        assertInvalidFields(() -> service.search(MBR, condWithoutCoordinates(null),
                new PageQuery(null, null, "DISTANCE")), "sort");
        verify(placeSearchClient, never()).search(any());
    }

    @Test
    void 좌표가_일부만_오면_400이고_빠진_칸을_담는다() {
        PlaceSearchCond cond = condWithoutCoordinates(null);
        cond.setSwLat(new BigDecimal("35.70"));
        cond.setCntrLot(new BigDecimal("127.1480"));

        assertInvalidFields(() -> service.search(MBR, cond, new PageQuery(null, null, null)),
                "swLot", "neLat", "neLot", "cntrLat");
        verify(placeSearchClient, never()).search(any());
    }

    @Test
    void 좌표가_모두_있으면_범위와_거리를_그대로_쓴다() {
        when(placeSearchClient.search(any())).thenReturn(
                new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 1, true));
        when(placeMapper.selectByKakaoPlcIds(any(), anyList()))
                .thenReturn(List.of(saved("1", 11L, "가병원", null)));

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(null, null, null));

        assertThat(lastQuery().hasArea()).isTrue();
        assertThat(res.content().get(0).getDistance()).isNotNull();
    }

    /** whole=true 조건 */
    private PlaceSearchCond wholeCond() {
        PlaceSearchCond cond = cond(HOSPITAL, null);
        cond.setWhole(true);
        return cond;
    }

    /** 저장 조회를 넘긴 카카오 번호대로 돌려준다 : 이름·위도는 번호별 값 */
    private void savedByIds(Map<String, String> names, Map<String, String> lats) {
        when(placeMapper.selectByKakaoPlcIds(any(), anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(1);
            return ids.stream().map(id -> {
                PlaceSummary summary = saved(id, Long.valueOf(id.hashCode()), names.get(id), null);
                if (lats.containsKey(id)) {
                    summary.setPlcLat(new BigDecimal(lats.get(id)));
                }
                return summary;
            }).toList();
        });
    }

    @Test
    void whole이면_세_쪽을_한_응답으로_주고_다음_쪽이_없다() {
        when(placeSearchClient.search(any())).thenAnswer(invocation -> {
            PlaceSearchQuery query = invocation.getArgument(0);
            return switch (query.page()) {
                case 1 -> new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 40, false);
                case 2 -> new PlaceSearchPage(List.of(item("2", "나병원", PET_HOSPITAL)), 40, false);
                default -> new PlaceSearchPage(List.of(item("3", "다병원", PET_HOSPITAL)), 40, true);
            };
        });
        savedByIds(Map.of("1", "가병원", "2", "나병원", "3", "다병원"), Map.of());

        PageResponse<PlaceSummary> res = service.search(MBR, wholeCond(), new PageQuery(2, null, null));

        assertThat(res.content()).extracting(PlaceSummary::getKakaoPlcId).containsExactly("1", "2", "3");
        assertThat(res.page()).isEqualTo(1);
        assertThat(res.size()).isEqualTo(45);
        assertThat(res.totalElements()).isEqualTo(3);
        assertThat(res.totalPages()).isEqualTo(1);
        assertThat(res.hasNext()).isFalse();
        verify(placeSearchClient, times(3)).search(any());
    }

    @Test
    void whole_거리순이면_거리_오름차순이다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(
                item("1", "먼병원", PET_HOSPITAL), item("2", "가까운병원", PET_HOSPITAL),
                item("3", "중간병원", PET_HOSPITAL)), 3, true));
        savedByIds(Map.of("1", "먼병원", "2", "가까운병원", "3", "중간병원"),
                Map.of("1", "35.9242", "2", "35.8252", "3", "35.8542"));

        PageResponse<PlaceSummary> res = service.search(MBR, wholeCond(), new PageQuery(null, null, "DISTANCE"));

        assertThat(res.content()).extracting(PlaceSummary::getPlcConm).containsExactly("가까운병원", "중간병원", "먼병원");
        assertThat(res.content()).extracting(PlaceSummary::getDistance).isSorted();
    }

    @Test
    void whole_이름순이면_가나다순이다() {
        when(placeSearchClient.search(any())).thenReturn(new PlaceSearchPage(List.of(
                item("1", "다병원", PET_HOSPITAL), item("2", "가병원", PET_HOSPITAL),
                item("3", "나병원", PET_HOSPITAL)), 3, true));
        savedByIds(Map.of("1", "다병원", "2", "가병원", "3", "나병원"), Map.of());

        PageResponse<PlaceSummary> res = service.search(MBR, wholeCond(), new PageQuery(null, null, "NAME"));

        assertThat(res.content()).extracting(PlaceSummary::getPlcConm).containsExactly("가병원", "나병원", "다병원");
    }

    @Test
    void whole_결과가_15건_이하면_카카오를_한_번만_부른다() {
        when(placeSearchClient.search(any()))
                .thenReturn(new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 10, true));
        savedByIds(Map.of("1", "가병원"), Map.of());

        PageResponse<PlaceSummary> res = service.search(MBR, wholeCond(), new PageQuery(null, null, "NAME"));

        assertThat(res.content()).hasSize(1);
        verify(placeSearchClient, times(1)).search(any());
    }

    @Test
    void whole_결과가_45건_이상이면_범위를_4등분해_모으고_겹치는_업체는_하나로_합친다() {
        List<PlaceSearchQuery> calls = new CopyOnWriteArrayList<>();
        when(placeSearchClient.search(any())).thenAnswer(invocation -> {
            PlaceSearchQuery query = invocation.getArgument(0);
            calls.add(query);
            boolean whole = query.swLat().compareTo(new BigDecimal("35.70")) == 0
                    && query.neLat().compareTo(new BigDecimal("35.95")) == 0
                    && query.swLot().compareTo(new BigDecimal("127.00")) == 0
                    && query.neLot().compareTo(new BigDecimal("127.25")) == 0;
            if (whole) {
                return new PlaceSearchPage(List.of(item("A", "전체병원", PET_HOSPITAL)), 45, false);
            }
            String id = query.swLat().toPlainString() + "/" + query.swLot().toPlainString();
            return new PlaceSearchPage(List.of(item(id, "칸병원" + id, PET_HOSPITAL),
                    item("A", "전체병원", PET_HOSPITAL)), 2, true);
        });
        when(placeMapper.selectByKakaoPlcIds(any(), anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(1);
            return ids.stream().map(id -> saved(id, Long.valueOf(id.hashCode()), "병원" + id, null)).toList();
        });

        PageResponse<PlaceSummary> res = service.search(MBR, wholeCond(), new PageQuery(null, null, null));

        assertThat(calls).hasSize(5);
        assertThat(res.content()).extracting(PlaceSummary::getKakaoPlcId)
                .containsExactly("A", "35.70/127.00", "35.70/127.125", "35.825/127.00", "35.825/127.125");
        assertThat(res.totalElements()).isEqualTo(5);
        assertThat(res.hasNext()).isFalse();
        List<String> rects = calls.stream().skip(1)
                .map(q -> q.swLat().toPlainString() + "," + q.swLot().toPlainString() + "," + q.neLat().toPlainString()
                        + "," + q.neLot().toPlainString())
                .sorted().toList();
        assertThat(rects).containsExactly("35.70,127.00,35.825,127.125", "35.70,127.125,35.825,127.25",
                "35.825,127.00,35.95,127.125", "35.825,127.125,35.95,127.25");
        assertThat(calls).allSatisfy(q -> {
            assertThat(q.cntrLat()).isEqualByComparingTo("35.8242");
            assertThat(q.cntrLot()).isEqualByComparingTo("127.1480");
        });
    }

    @Test
    void whole_동시에_부른_쪽이_실패하면_502를_그대로_올린다() {
        when(placeSearchClient.search(any())).thenAnswer(invocation -> {
            PlaceSearchQuery query = invocation.getArgument(0);
            if (query.page() == 1) {
                return new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 30, false);
            }
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        });

        assertError(() -> service.search(MBR, wholeCond(), new PageQuery(null, null, null)),
                ErrorCode.EXTERNAL_API_ERROR);
        verify(placeMapper, never()).upsertKakaoPlaces(anyList());
    }

    @Test
    void whole을_생략하면_지금처럼_15건_한_쪽과_다음_쪽_여부를_준다() {
        when(placeSearchClient.search(any()))
                .thenReturn(new PlaceSearchPage(List.of(item("1", "가병원", PET_HOSPITAL)), 30, false));
        savedByIds(Map.of("1", "가병원"), Map.of());

        PageResponse<PlaceSummary> res = service.search(MBR, cond(HOSPITAL, null), new PageQuery(1, null, null));

        assertThat(res.page()).isEqualTo(1);
        assertThat(res.size()).isEqualTo(15);
        assertThat(res.hasNext()).isTrue();
        verify(placeSearchClient, times(1)).search(any());
    }
}
