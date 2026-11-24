package com.nyangmeong.place.service;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.external.PlaceSearchClient;
import com.nyangmeong.global.external.PlaceSearchClient.PlaceSearchPage;
import com.nyangmeong.global.external.PlaceSearchClient.PlaceSearchQuery;
import com.nyangmeong.global.external.PlaceSearchItem;
import com.nyangmeong.global.response.PageQuery;
import com.nyangmeong.global.response.PageResponse;
import com.nyangmeong.place.domain.Place;
import com.nyangmeong.place.domain.PlaceCategory;
import com.nyangmeong.place.domain.PlaceSort;
import com.nyangmeong.place.domain.PlaceSource;
import com.nyangmeong.place.dto.PlaceSearchCond;
import com.nyangmeong.place.dto.PlaceSummary;
import com.nyangmeong.place.mapper.PlaceMapper;

import lombok.RequiredArgsConstructor;

/**
 * [장소 검색 서비스]
 * - SCR-HOS-001·PLC-001~004 (FN-HOS-001-03~07·16·19·20·30)
 * - 카카오 검색 결과를 PLC 에 저장한 뒤 장소 번호·내 즐겨찾기 번호를 붙여 반환
 * - 이름순은 카카오가 지원하지 않아 전체(최대 45건)를 받아 정렬한 뒤 15건씩 자름
 * - whole=true 는 범위의 결과를 한 응답으로 줌. 카카오 결과가 45건 이상이면 범위를 4등분해 칸마다 모아 합침
 * - 여러 쪽·여러 칸은 1쪽을 받은 뒤 가상 스레드로 동시에 부름
 */
@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int PLC_CONM_MAX_LENGTH = 200;
    private static final int PLC_ADDR_MAX_LENGTH = 200;
    private static final int RPRS_TELNO_MAX_LENGTH = 20;
    private static final int KAKAO_URL_PATH_MAX_LENGTH = 500;
    private static final double EARTH_RADIUS_METER = 6_371_000;
    private static final int KAKAO_MAX_COUNT = PlaceSearchClient.PAGE_SIZE * PlaceSearchClient.MAX_PAGE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final PlaceSearchClient placeSearchClient;
    private final PlaceMapper placeMapper;

    /**
     * 장소 검색 : 외부 호출 동안 DB 연결을 잡지 않으려고 트랜잭션 없이 실행, 저장은 쿼리 한 번이라 그 자체로 원자적
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PageResponse<PlaceSummary> search(Long mbrSn, PlaceSearchCond cond, PageQuery pq) {
        PlaceCategory category = PlaceCategory.from(cond.getPlcClsfCd());
        PlaceSort sort = PlaceSort.from(pq.sort());
        validateCoordinates(cond, sort);
        int page = pq.pageOrDefault();
        String query = category.toQuery(cond.getKeyword());

        if (cond.isWholeArea()) {
            return searchWhole(mbrSn, category, cond, query, sort);
        }
        if (page > PlaceSearchClient.MAX_PAGE) {
            return emptyPage(page);
        }
        if (PlaceSort.NAME.is(sort.getCode())) {
            return searchByName(mbrSn, category, cond, query, page);
        }

        PlaceSearchPage result = placeSearchClient.search(toQuery(query, sort, cond, page));
        List<PlaceSummary> content = resolve(mbrSn, category, cond, result.items());
        boolean hasNext = !result.end() && page < PlaceSearchClient.MAX_PAGE;
        return toPage(content, page, result.totalCount(), hasNext);
    }

    /**
     * 이름순 검색 : 최대 3쪽을 모두 받아 상호명 가나다순으로 정렬한 뒤 요청한 쪽만 반환
     */
    private PageResponse<PlaceSummary> searchByName(Long mbrSn, PlaceCategory category, PlaceSearchCond cond,
            String query, int page) {
        List<PlaceSearchItem> items = collectPages(query, PlaceSort.NAME, cond.getSwLat(), cond.getSwLot(),
                cond.getNeLat(), cond.getNeLot(), cond).items();
        List<PlaceSummary> all = new ArrayList<>(resolve(mbrSn, category, cond, items));
        all.sort(byName());

        int from = (page - 1) * PlaceSearchClient.PAGE_SIZE;
        int to = Math.min(from + PlaceSearchClient.PAGE_SIZE, all.size());
        List<PlaceSummary> content = from >= all.size() ? List.of() : all.subList(from, to);
        return toPage(content, page, all.size(), to < all.size());
    }

    /**
     * 범위 한 번에 조회 : 쪽 없이 전체를 정렬해 한 응답으로 반환
     * - 1쪽의 카카오 건수가 45건 이상이면 범위를 4등분해 칸마다 다시 모아 1쪽 뒤에 이어 붙임(겹치는 업체는 하나로)
     * - RECOMMEND 는 카카오 순서, DISTANCE 는 거리 오름차순, NAME 은 가나다순
     */
    private PageResponse<PlaceSummary> searchWhole(Long mbrSn, PlaceCategory category, PlaceSearchCond cond,
            String query, PlaceSort sort) {
        List<PlaceSearchItem> items;
        if (cond.hasCoordinates()) {
            PlaceSearchPage first = placeSearchClient.search(toQuery(query, sort, cond, 1));
            items = first.totalCount() >= KAKAO_MAX_COUNT
                    ? collectQuarters(query, sort, cond, first)
                    : collectRest(query, sort, cond.getSwLat(), cond.getSwLot(), cond.getNeLat(), cond.getNeLot(),
                            cond, first).items();
        } else {
            items = collectPages(query, sort, null, null, null, null, cond).items();
        }

        List<PlaceSummary> content = new ArrayList<>(resolve(mbrSn, category, cond, items));
        if (PlaceSort.NAME.is(sort.getCode())) {
            content.sort(byName());
        } else if (PlaceSort.DISTANCE.is(sort.getCode())) {
            content.sort(Comparator.comparing(PlaceSummary::getDistance,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return new PageResponse<>(content, 1, KAKAO_MAX_COUNT, content.size(), content.isEmpty() ? 0 : 1, false);
    }

    /**
     * 범위를 4등분해 칸마다 최대 3쪽을 동시에 모음 : 전체 범위 1쪽 결과를 앞에 둠
     */
    private List<PlaceSearchItem> collectQuarters(String query, PlaceSort sort, PlaceSearchCond cond,
            PlaceSearchPage first) {
        BigDecimal midLat = cond.getSwLat().add(cond.getNeLat()).divide(TWO);
        BigDecimal midLot = cond.getSwLot().add(cond.getNeLot()).divide(TWO);
        List<Callable<List<PlaceSearchItem>>> quarters = List.of(
                () -> collectPages(query, sort, cond.getSwLat(), cond.getSwLot(), midLat, midLot, cond).items(),
                () -> collectPages(query, sort, cond.getSwLat(), midLot, midLat, cond.getNeLot(), cond).items(),
                () -> collectPages(query, sort, midLat, cond.getSwLot(), cond.getNeLat(), midLot, cond).items(),
                () -> collectPages(query, sort, midLat, midLot, cond.getNeLat(), cond.getNeLot(), cond).items());

        List<PlaceSearchItem> items = new ArrayList<>(first.items());
        for (List<PlaceSearchItem> quarter : runAll(quarters)) {
            items.addAll(quarter);
        }
        return items;
    }

    /**
     * 한 범위의 최대 3쪽 모으기 : 1쪽을 받은 뒤 나머지 쪽을 동시에
     */
    private PlaceSearchPage collectPages(String query, PlaceSort sort, BigDecimal swLat, BigDecimal swLot,
            BigDecimal neLat, BigDecimal neLot, PlaceSearchCond cond) {
        PlaceSearchPage first = placeSearchClient.search(toQuery(query, sort, swLat, swLot, neLat, neLot, cond, 1));
        return collectRest(query, sort, swLat, swLot, neLat, neLot, cond, first);
    }

    /**
     * 1쪽 뒤의 쪽을 동시에 받아 쪽 순서대로 이어 붙임
     * - 남은 쪽 수는 1쪽의 pageable_count 로 정함(끝이 아닌데 건수가 15 이하로 오면 2쪽까지)
     */
    private PlaceSearchPage collectRest(String query, PlaceSort sort, BigDecimal swLat, BigDecimal swLot,
            BigDecimal neLat, BigDecimal neLot, PlaceSearchCond cond, PlaceSearchPage first) {
        if (first.end()) {
            return first;
        }
        int byCount = (first.totalCount() + PlaceSearchClient.PAGE_SIZE - 1) / PlaceSearchClient.PAGE_SIZE;
        int lastPage = Math.min(PlaceSearchClient.MAX_PAGE, Math.max(2, byCount));
        List<Callable<List<PlaceSearchItem>>> rest = new ArrayList<>();
        for (int current = 2; current <= lastPage; current++) {
            PlaceSearchQuery pageQuery = toQuery(query, sort, swLat, swLot, neLat, neLot, cond, current);
            rest.add(() -> placeSearchClient.search(pageQuery).items());
        }

        List<PlaceSearchItem> items = new ArrayList<>(first.items());
        for (List<PlaceSearchItem> pageItems : runAll(rest)) {
            items.addAll(pageItems);
        }
        return new PlaceSearchPage(items, first.totalCount(), true);
    }

    /**
     * 가상 스레드로 동시에 실행하고 넣은 순서대로 결과 반환 : 하나라도 실패하면 그 예외를 그대로 올림
     */
    private <T> List<T> runAll(List<Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(task));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private Comparator<PlaceSummary> byName() {
        return Comparator.comparing(PlaceSummary::getPlcConm, Collator.getInstance(Locale.KOREAN));
    }

    /**
     * 카카오 검색 결과를 저장하고 장소 번호·즐겨찾기 번호·거리를 붙인 목록 : 카카오 순서 유지, 숨긴 업체 제외
     */
    private List<PlaceSummary> resolve(Long mbrSn, PlaceCategory category, PlaceSearchCond cond,
            List<PlaceSearchItem> items) {
        Map<String, PlaceSearchItem> kakaoItems = new LinkedHashMap<>();
        for (PlaceSearchItem item : items) {
            if (category.matches(item.categoryName())) {
                kakaoItems.putIfAbsent(item.kakaoPlcId(), item);
            }
        }
        if (kakaoItems.isEmpty()) {
            return List.of();
        }

        placeMapper.upsertKakaoPlaces(kakaoItems.values().stream().map(item -> toPlace(category, item)).toList());
        Map<String, PlaceSummary> saved = placeMapper.selectByKakaoPlcIds(mbrSn, new ArrayList<>(kakaoItems.keySet()))
                .stream()
                .collect(Collectors.toMap(PlaceSummary::getKakaoPlcId, Function.identity()));

        List<PlaceSummary> content = new ArrayList<>();
        for (String kakaoPlcId : kakaoItems.keySet()) {
            PlaceSummary summary = saved.get(kakaoPlcId);
            if (summary != null) {
                if (cond.hasCoordinates()) {
                    summary.setDistance(distanceMeter(cond.getCntrLat(), cond.getCntrLot(), summary.getPlcLat(),
                            summary.getPlcLot()));
                }
                content.add(summary);
            }
        }
        return content;
    }

    /**
     * 좌표 6칸은 모두 있거나 모두 없어야 함, 좌표 없이 거리순 불가
     */
    private void validateCoordinates(PlaceSearchCond cond, PlaceSort sort) {
        if (cond.hasCoordinates()) {
            return;
        }
        if (!cond.hasNoCoordinates()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, missingCoordinateFields(cond));
        }
        if (PlaceSort.DISTANCE.is(sort.getCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "sort");
        }
    }

    /**
     * 일부만 온 좌표에서 빠진 칸 이름
     */
    private String[] missingCoordinateFields(PlaceSearchCond cond) {
        Map<String, BigDecimal> coordinates = new LinkedHashMap<>();
        coordinates.put("swLat", cond.getSwLat());
        coordinates.put("swLot", cond.getSwLot());
        coordinates.put("neLat", cond.getNeLat());
        coordinates.put("neLot", cond.getNeLot());
        coordinates.put("cntrLat", cond.getCntrLat());
        coordinates.put("cntrLot", cond.getCntrLot());
        return coordinates.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    private PlaceSearchQuery toQuery(String query, PlaceSort sort, PlaceSearchCond cond, int page) {
        return toQuery(query, sort, cond.getSwLat(), cond.getSwLot(), cond.getNeLat(), cond.getNeLot(), cond, page);
    }

    /**
     * 범위를 바꿔 쓰는 검색 조건 : 거리 기준은 언제나 검색 시점의 지도 중앙
     */
    private PlaceSearchQuery toQuery(String query, PlaceSort sort, BigDecimal swLat, BigDecimal swLot,
            BigDecimal neLat, BigDecimal neLot, PlaceSearchCond cond, int page) {
        return new PlaceSearchQuery(query, PlaceSort.DISTANCE.is(sort.getCode()), swLat, swLot, neLat, neLot,
                cond.getCntrLat(), cond.getCntrLot(), page);
    }

    private Place toPlace(PlaceCategory category, PlaceSearchItem item) {
        Place place = new Place();
        place.setPlcClsfCd(category.getCode());
        place.setPlcSrcCd(PlaceSource.KAKAO.getCode());
        place.setPlcConm(cut(item.plcConm(), PLC_CONM_MAX_LENGTH));
        place.setPlcAddr(cut(blankToNull(item.plcAddr()), PLC_ADDR_MAX_LENGTH));
        place.setRprsTelno(dropIfLonger(blankToNull(item.rprsTelno()), RPRS_TELNO_MAX_LENGTH));
        place.setKakaoPlcId(item.kakaoPlcId());
        place.setKakaoUrlPath(dropIfLonger(blankToNull(item.kakaoUrlPath()), KAKAO_URL_PATH_MAX_LENGTH));
        place.setPlcLat(item.plcLat());
        place.setPlcLot(item.plcLot());
        return place;
    }

    /**
     * 두 좌표 사이 거리(미터), 하버사인 공식
     */
    static int distanceMeter(BigDecimal fromLat, BigDecimal fromLot, BigDecimal toLat, BigDecimal toLot) {
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double dLat = lat2 - lat1;
        double dLot = Math.toRadians(toLot.doubleValue() - fromLot.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLot / 2) * Math.sin(dLot / 2);
        return (int) Math.round(2 * EARTH_RADIUS_METER * Math.asin(Math.sqrt(a)));
    }

    private PageResponse<PlaceSummary> toPage(List<PlaceSummary> content, int page, int totalElements,
            boolean hasNext) {
        int totalPages = (totalElements + PlaceSearchClient.PAGE_SIZE - 1) / PlaceSearchClient.PAGE_SIZE;
        return new PageResponse<>(content, page, PlaceSearchClient.PAGE_SIZE, totalElements, totalPages, hasNext);
    }

    private PageResponse<PlaceSummary> emptyPage(int page) {
        return new PageResponse<>(List.of(), page, PlaceSearchClient.PAGE_SIZE, 0, 0, false);
    }

    /**
     * 빈 값 정리 : 카카오는 없는 전화번호를 빈 문자열로 줌
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String cut(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String dropIfLonger(String value, int maxLength) {
        return value == null || value.length() > maxLength ? null : value;
    }
}
