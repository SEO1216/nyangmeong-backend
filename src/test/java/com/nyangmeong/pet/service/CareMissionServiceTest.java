package com.nyangmeong.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.global.auth.BrupRole;
import com.nyangmeong.global.auth.PetAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.pet.domain.CareMission;
import com.nyangmeong.pet.domain.CareMissionPool;
import com.nyangmeong.pet.domain.CareMsn;
import com.nyangmeong.pet.domain.DayMsn;
import com.nyangmeong.pet.domain.PetSe;
import com.nyangmeong.pet.dto.MissionTargetSummary;
import com.nyangmeong.pet.mapper.CareMissionMapper;

/**
 * [검사 - 케어미션 출제·반영·정산]
 */
class CareMissionServiceTest {

    private static final Long PRIMARY = 1L;
    private static final Long CO_PARENT = 2L;
    private static final Long PET = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    /** 출제 결과를 여러 번 보는 횟수 : 랜덤 출제라 한 번으로는 조건을 확인할 수 없음 */
    private static final int REPEAT = 200;

    private final CareMissionMapper mapper = mock(CareMissionMapper.class);
    private final PointService pointService = mock(PointService.class);
    private final PetAuthorizer authorizer = mock(PetAuthorizer.class);
    private final CareMissionService service = new CareMissionService(mapper, pointService, authorizer);

    private final List<CareMsn> masters = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long sn = 1;
        for (CareMission mission : CareMission.values()) {
            masters.add(master(sn++, mission.getMsnNm(), mission.getPool(), 99, 1, "Y"));
        }
        masters.add(master(sn, "케어리스트 완료하기", CareMissionPool.DAILY, 20, 1, "N"));
        when(mapper.selectMissions()).thenReturn(masters);
        when(mapper.selectIssuedPetSns(any())).thenReturn(List.of());
        when(mapper.selectPetNm(PET)).thenReturn("뭉치");
    }

    @Test
    void 반려동물마다_8개를_내고_등급_조합_중_하나이며_합계는_60P() {
        target(PetSe.DOG, true);

        for (int i = 0; i < REPEAT; i++) {
            clearInvocations(mapper);
            service.issue(TODAY, Set.of(PET), Map.of());

            List<DayMsn> issued = inserted();
            assertThat(issued).hasSize(8);
            assertThat(issued).allSatisfy(d -> {
                assertThat(d.getPetSn()).isEqualTo(PET);
                assertThat(d.getMbrSn()).isEqualTo(PRIMARY);
                assertThat(d.getMsnCrtrYmd()).isEqualTo(TODAY);
                assertThat(d.getScorPnt()).isEqualTo(missionOf(d).getGrade().getPoint());
            });
            assertThat(issued.stream().mapToInt(DayMsn::getScorPnt).sum()).isEqualTo(CareMission.DAILY_TOTAL_PNT);
            assertThat(gradeCounts(issued)).isIn(List.of(0, 4, 4), List.of(1, 2, 5), List.of(2, 0, 6));
            assertThat(issued).extracting(DayMsn::getCareMsnSn).doesNotHaveDuplicates();
        }
    }

    @Test
    void 만들_수_있는_조합만_고른다() {
        target(PetSe.CAT, false);

        for (int i = 0; i < REPEAT; i++) {
            clearInvocations(mapper);
            service.issue(TODAY, Set.of(), Map.of());

            /** 어려움 후보가 양치하기 1개뿐이고 보통 후보가 3개뿐이라 (1,2,5)만 가능 */
            assertThat(gradeCounts(inserted())).isEqualTo(List.of(1, 2, 5));
        }
    }

    @Test
    void 배점은_등급으로_정하고_케어미션_마스터의_배점은_쓰지_않는다() {
        masters.forEach(m -> m.setCmpnPnt(1000));
        target(PetSe.DOG, true);

        service.issue(TODAY, Set.of(PET), Map.of());

        assertThat(inserted()).allSatisfy(d -> assertThat(d.getScorPnt()).isIn(5, 10, 15));
    }

    @Test
    void 고양이에게는_산책이_없고_대표가_아니면_펫이야기_관찰이_없으면_관찰_기록이_없다() {
        target(PetSe.CAT, false);

        Set<CareMission> seen = issueRepeatedly(Set.of());

        assertThat(seen).doesNotContain(CareMission.WALK_RECORD, CareMission.PET_STORY_POST,
                CareMission.PET_STORY_COMMENT, CareMission.OBSERVATION_RECORD);
    }

    @Test
    void 강아지_대표_관찰_중이면_산책_펫이야기_관찰_기록도_후보가_된다() {
        target(PetSe.DOG, true);

        Set<CareMission> seen = issueRepeatedly(Set.of(PET));

        assertThat(seen).contains(CareMission.WALK_RECORD, CareMission.PET_STORY_POST,
                CareMission.PET_STORY_COMMENT, CareMission.OBSERVATION_RECORD);
    }

    @Test
    void 사용_중지됐거나_케어미션에_없는_행은_내지_않는다() {
        target(PetSe.DOG, true);

        assertThat(issuedSnsRepeatedly(Set.of(PET))).doesNotContain(masters.get(masters.size() - 1).getCareMsnSn());
    }

    @Test
    void 사용_중이어도_케어미션에_없는_행은_사용자_추가_항목처럼_내지_않는다() {
        CareMsn userAdded = master(900L, "사용자 추가 항목", CareMissionPool.DAILY, 10, 1, "Y");
        masters.add(userAdded);
        target(PetSe.CAT, true);

        assertThat(issuedSnsRepeatedly(Set.of(PET))).doesNotContain(userAdded.getCareMsnSn());
    }

    @Test
    void 조합을_못_만들면_그날만_등급_가중치_비율로_60P를_나누고_남는_포인트는_높은_등급부터_더한다() {
        /** 고양이·대표 아님·케어리스트 후보 없음 : 쉬움 5개(체중·음수량·배변·가계부·일정) + 보통 1개(식사량), 어려움 0개 */
        target(PetSe.CAT, false);

        service.issue(TODAY, Set.of(), Map.of(PET, Set.of()));

        List<DayMsn> issued = inserted();
        assertThat(issued).hasSize(6);
        assertThat(issued.stream().mapToInt(DayMsn::getScorPnt).sum()).isEqualTo(60);
        /** 가중치 합 7 : 쉬움 8P씩 40, 보통 17P, 남는 3P는 보통 1P 뒤 쉬움 2개에 */
        DayMsn normal = issued.stream().filter(d -> missionOf(d) == CareMission.MEAL_RECORD).findFirst().orElseThrow();
        assertThat(normal.getScorPnt()).isEqualTo(18);
        assertThat(issued.stream().filter(d -> d != normal).map(DayMsn::getScorPnt)).containsOnly(8, 9)
                .filteredOn(p -> p == 9).hasSize(2);
    }

    @Test
    void 케어리스트_미션은_health가_넘긴_후보만_낸다() {
        target(PetSe.CAT, false);
        Set<CareMission> candidates = Set.of(CareMission.TOOTH_BRUSHING, CareMission.EAR_CLEANING,
                CareMission.PAW_CARE, CareMission.EYE_CARE);

        Set<CareMission> seen = issueRepeatedly(Set.of(), Map.of(PET, candidates));

        Set<CareMission> careList = EnumSet.noneOf(CareMission.class);
        for (CareMission mission : CareMission.values()) {
            if (mission.isCareList()) {
                careList.add(mission);
            }
        }
        assertThat(seen.stream().filter(CareMission::isCareList)).isSubsetOf(candidates);
        assertThat(seen).contains(CareMission.TOOTH_BRUSHING, CareMission.EAR_CLEANING);
        assertThat(careList).hasSize(11);
    }

    @Test
    void 케어리스트_기본_항목이_없는_반려동물은_추천_매일_항목_4개만_케어리스트_후보로_본다() {
        target(PetSe.DOG, true);

        Set<CareMission> seen = issueRepeatedly(Set.of(PET), Map.of());

        assertThat(seen.stream().filter(CareMission::isCareList))
                .isSubsetOf(CareMission.TOILET_CLEANING, CareMission.TOOTH_BRUSHING, CareMission.BRUSHING,
                        CareMission.PLAY);
        assertThat(seen).doesNotContain(CareMission.EAR_CLEANING, CareMission.PAW_CARE, CareMission.NAIL_CARE);
    }

    @Test
    void 그날_미션이_이미_있는_반려동물은_건너뛴다() {
        target(PetSe.DOG, false);
        when(mapper.selectIssuedPetSns(TODAY)).thenReturn(List.of(PET));

        service.issue(TODAY, Set.of(), Map.of());

        verify(mapper, never()).insertDayMission(any());
    }

    @Test
    void 등록_직후_한_마리만_8개_합계_60P를_내고_케어리스트는_추천_매일_항목만_후보로_본다() {
        issueTarget(PetSe.DOG, true);

        service.issueFor(PET, TODAY, false);

        List<DayMsn> issued = inserted();
        assertThat(issued).hasSize(8);
        assertThat(issued).allSatisfy(d -> {
            assertThat(d.getPetSn()).isEqualTo(PET);
            assertThat(d.getMbrSn()).isEqualTo(PRIMARY);
            assertThat(d.getMsnCrtrYmd()).isEqualTo(TODAY);
        });
        assertThat(issued.stream().mapToInt(DayMsn::getScorPnt).sum()).isEqualTo(60);
        assertThat(issued.stream().map(this::missionOf).filter(CareMission::isCareList))
                .isSubsetOf(CareMission.TOILET_CLEANING, CareMission.TOOTH_BRUSHING, CareMission.BRUSHING,
                        CareMission.PLAY);
        assertThat(issued).extracting(DayMsn::getCareMsnSn).doesNotHaveDuplicates();
        verify(mapper, never()).selectIssueTargets(any());
    }

    @Test
    void 그날_미션이_이미_있으면_등록_직후_출제는_건너뛴다() {
        issueTarget(PetSe.DOG, true);
        when(mapper.countDayMissions(PET, TODAY)).thenReturn(8);

        service.issueFor(PET, TODAY, false);

        verify(mapper, never()).insertDayMission(any());
    }

    @Test
    void 주양육_관계가_없으면_등록_직후_출제는_아무것도_하지_않는다() {
        when(mapper.selectIssueTarget(PET, BrupRole.PRIMARY.getCode())).thenReturn(null);

        service.issueFor(PET, TODAY, false);

        verify(mapper, never()).insertDayMission(any());
    }

    @Test
    void 등록_직후_출제한_뒤_배치가_돌아도_미션이_늘지_않는다() {
        issueTarget(PetSe.DOG, true);
        target(PetSe.DOG, true);
        service.issueFor(PET, TODAY, false);
        int afterRegister = inserted().size();
        /** 등록 직후 출제로 DAY_MSN 이 생겼으니 배치의 출제 완료 조회에 잡힌다 */
        when(mapper.selectIssuedPetSns(TODAY)).thenReturn(List.of(PET));

        service.issue(TODAY, Set.of(), Map.of());

        assertThat(afterRegister).isEqualTo(8);
        assertThat(inserted()).hasSize(8);
    }

    @Test
    void 마지막_1회로_완료되면_즉시_주양육자에게_배점만큼_적립한다() {
        reflectable(CareMission.TOOTH_BRUSHING, 1, day(77L, 0, 5, "N"));

        service.reflect(PET, CareMission.TOOTH_BRUSHING, PRIMARY, TODAY);

        verify(mapper).updateDayMissionComplete(77L, 1, PRIMARY, 5);
        verify(pointService).earnMission(PRIMARY, 77L, 5, "뭉치 · 양치하기");
    }

    @Test
    void 기준_횟수에_못_미치면_진행_횟수만_올린다() {
        reflectable(CareMission.MEAL_RECORD, 3, day(77L, 1, 15, "N"));

        service.reflect(PET, CareMission.MEAL_RECORD, PRIMARY, TODAY);

        verify(mapper).updateDayMissionProgress(77L, 2);
        verify(mapper, never()).updateDayMissionComplete(anyLong(), anyInt(), any(), anyInt());
        verify(pointService, never()).earnMission(any(), any(), anyInt(), any());
    }

    @Test
    void 공동양육자가_완료하면_완료회원은_공동양육자_적립은_주양육자() {
        reflectable(CareMission.WEIGHT_RECORD, 1, day(77L, 0, 10, "N"));

        service.reflect(PET, CareMission.WEIGHT_RECORD, CO_PARENT, TODAY);

        verify(mapper).updateDayMissionComplete(77L, 1, CO_PARENT, 10);
        verify(pointService).earnMission(eq(PRIMARY), eq(77L), eq(10), any());
    }

    @Test
    void 이미_완료했거나_오늘_미션에_없으면_아무것도_하지_않는다() {
        reflectable(CareMission.TOOTH_BRUSHING, 1, day(77L, 1, 5, "Y"));
        service.reflect(PET, CareMission.TOOTH_BRUSHING, PRIMARY, TODAY);

        reflectable(CareMission.BRUSHING, 1, null);
        service.reflect(PET, CareMission.BRUSHING, PRIMARY, TODAY);

        verify(mapper, never()).updateDayMissionComplete(anyLong(), anyInt(), any(), anyInt());
        verify(mapper, never()).updateDayMissionProgress(anyLong(), anyInt());
        verify(pointService, never()).earnMission(any(), any(), anyInt(), any());
    }

    @Test
    void 펫이야기는_회원_앞으로_출제된_미션에_반영한다() {
        CareMsn master = masterOf(CareMission.PET_STORY_COMMENT, 1);
        when(mapper.selectMissionByName(CareMission.PET_STORY_COMMENT.getMsnNm())).thenReturn(master);
        when(mapper.selectMemberDayMissionForUpdate(PRIMARY, TODAY, master.getCareMsnSn()))
                .thenReturn(day(88L, 0, 5, "N"));

        service.reflectRepresentative(PRIMARY, CareMission.PET_STORY_COMMENT, TODAY);

        verify(mapper).updateDayMissionComplete(88L, 1, PRIMARY, 5);
        verify(pointService).earnMission(PRIMARY, 88L, 5, "뭉치 · 펫이야기 댓글 작성하기");
    }

    @Test
    void 정산은_완료분은_다시_적립하지_않고_미완료는_진행률만큼_절반_이하도_지급() {
        CareMsn threeTimes = master(901L, "세 번 미션", CareMissionPool.DAILY, 15, 3, "Y");
        CareMsn twoTimes = master(902L, "두 번 미션", CareMissionPool.DAILY, 10, 2, "Y");
        masters.add(threeTimes);
        masters.add(twoTimes);
        DayMsn done = day(1L, 1, 5, "Y");
        done.setGivePnt(5);
        done.setCareMsnSn(masters.get(0).getCareMsnSn());
        DayMsn twoOfThree = day(2L, 2, 15, "N");
        twoOfThree.setCareMsnSn(901L);
        twoOfThree.setAchvCrtrNmtm(3);
        DayMsn oneOfTwo = day(3L, 1, 10, "N");
        oneOfTwo.setCareMsnSn(902L);
        oneOfTwo.setAchvCrtrNmtm(2);
        LocalDate yesterday = TODAY.minusDays(1);
        when(mapper.selectUnsettledDayMissions(yesterday)).thenReturn(List.of(done, twoOfThree, oneOfTwo), List.of());

        service.settle(yesterday);
        service.settle(yesterday);

        verify(mapper).updateDayMissionSettled(1L, 5);
        verify(mapper).updateDayMissionSettled(2L, 10);
        verify(mapper).updateDayMissionSettled(3L, 5);
        verify(pointService, times(1)).earnMission(PRIMARY, 2L, 10, "뭉치 · 세 번 미션");
        verify(pointService, times(1)).earnMission(PRIMARY, 3L, 5, "뭉치 · 두 번 미션");
        verify(pointService, times(2)).earnMission(any(), any(), anyInt(), any());
    }

    @Test
    void 출제는_등급_배점과_달성_기준을_그날_미션에_복사한다() {
        masters.forEach(m -> m.setAchvCrtrNmtm(2));
        target(PetSe.CAT, false);

        service.issue(TODAY, Set.of(), Map.of());

        assertThat(inserted()).hasSize(8).allSatisfy(d -> {
            assertThat(d.getScorPnt()).isEqualTo(missionOf(d).getGrade().getPoint());
            assertThat(d.getAchvCrtrNmtm()).isEqualTo(2);
        });
    }

    @Test
    void 출제_뒤_마스터를_바꿔도_진행과_완료_지급은_출제_때_값() {
        CareMsn master = masterOf(CareMission.MEAL_RECORD, 1);
        master.setCmpnPnt(100);
        master.setUseYn("N");
        DayMsn issued = day(77L, 1, 15, "N");
        issued.setAchvCrtrNmtm(3);
        when(mapper.selectMissionByName(CareMission.MEAL_RECORD.getMsnNm())).thenReturn(master);
        when(mapper.selectDayMissionForUpdate(PET, TODAY, master.getCareMsnSn())).thenReturn(issued);

        service.reflect(PET, CareMission.MEAL_RECORD, PRIMARY, TODAY);

        verify(mapper).updateDayMissionProgress(77L, 2);
        verify(mapper, never()).updateDayMissionComplete(anyLong(), anyInt(), any(), anyInt());

        DayMsn last = day(78L, 2, 15, "N");
        last.setAchvCrtrNmtm(3);
        when(mapper.selectDayMissionForUpdate(PET, TODAY, master.getCareMsnSn())).thenReturn(last);

        service.reflect(PET, CareMission.MEAL_RECORD, PRIMARY, TODAY);

        verify(mapper).updateDayMissionComplete(78L, 3, PRIMARY, 15);
        verify(pointService).earnMission(PRIMARY, 78L, 15, "뭉치 · 식사량 기록하기");
    }

    @Test
    void 출제_뒤_마스터_달성_기준을_바꿔도_정산은_출제_때_기준() {
        CareMsn master = master(903L, "세 번 미션", CareMissionPool.DAILY, 15, 1, "N");
        masters.add(master);
        DayMsn twoOfThree = day(5L, 2, 15, "N");
        twoOfThree.setCareMsnSn(903L);
        twoOfThree.setAchvCrtrNmtm(3);
        LocalDate yesterday = TODAY.minusDays(1);
        when(mapper.selectUnsettledDayMissions(yesterday)).thenReturn(List.of(twoOfThree));

        service.settle(yesterday);

        verify(mapper).updateDayMissionSettled(5L, 10);
        verify(pointService).earnMission(PRIMARY, 5L, 10, "뭉치 · 세 번 미션");
    }

    @Test
    void 정산_포인트는_진행률만큼이다() {
        assertThat(CareMissionService.settledPoint(15, 2, 3)).isEqualTo(10);
        assertThat(CareMissionService.settledPoint(10, 1, 2)).isEqualTo(5);
        assertThat(CareMissionService.settledPoint(10, 0, 1)).isZero();
        assertThat(CareMissionService.settledPoint(15, 3, 4)).isEqualTo(11);
    }

    @Test
    void 등급_배점과_하루_합계_상수() {
        assertThat(CareMission.Grade.EASY.getPoint()).isEqualTo(5);
        assertThat(CareMission.Grade.NORMAL.getPoint()).isEqualTo(10);
        assertThat(CareMission.Grade.HARD.getPoint()).isEqualTo(15);
        assertThat(CareMission.DAILY_TOTAL_PNT).isEqualTo(60);
        assertThat(Arrays.stream(CareMission.values()).filter(m -> m.getGrade() == CareMission.Grade.EASY).count())
                .isEqualTo(10);
        assertThat(Arrays.stream(CareMission.values()).filter(m -> m.getGrade() == CareMission.Grade.NORMAL).count())
                .isEqualTo(5);
        assertThat(Arrays.stream(CareMission.values()).filter(m -> m.getGrade() == CareMission.Grade.HARD).count())
                .isEqualTo(6);
    }

    @Test
    void 미션_조회는_주양육자만_날짜가_없으면_오늘() {
        doThrow(new CustomException(ErrorCode.PET_PRIMARY_ONLY)).when(authorizer).checkPrimary(CO_PARENT, PET);

        assertThatThrownBy(() -> service.search(CO_PARENT, PET, TODAY))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PET_PRIMARY_ONLY);

        service.search(PRIMARY, PET, null);
        verify(mapper).selectDailyMissions(PET, LocalDate.now(CareMissionService.ZONE));
    }

    private Set<CareMission> issueRepeatedly(Set<Long> obsrvPetSns) {
        return issueRepeatedly(obsrvPetSns, Map.of());
    }

    private Set<CareMission> issueRepeatedly(Set<Long> obsrvPetSns, Map<Long, Set<CareMission>> careList) {
        return issuedSnsRepeatedly(obsrvPetSns, careList).stream().map(this::missionOf).collect(Collectors.toSet());
    }

    private List<Long> issuedSnsRepeatedly(Set<Long> obsrvPetSns) {
        return issuedSnsRepeatedly(obsrvPetSns, Map.of());
    }

    private List<Long> issuedSnsRepeatedly(Set<Long> obsrvPetSns, Map<Long, Set<CareMission>> careList) {
        List<Long> careMsnSns = new ArrayList<>();
        for (int i = 0; i < REPEAT; i++) {
            clearInvocations(mapper);
            service.issue(TODAY, obsrvPetSns, careList);
            List<DayMsn> issued = inserted();
            assertThat(issued).hasSize(8);
            issued.forEach(d -> careMsnSns.add(d.getCareMsnSn()));
        }
        return careMsnSns;
    }

    private void target(PetSe petSe, boolean representative) {
        MissionTargetSummary target = new MissionTargetSummary();
        target.setPetSn(PET);
        target.setMbrSn(PRIMARY);
        target.setPetSeCd(petSe.getCode());
        target.setRprsPetYn(representative);
        when(mapper.selectIssueTargets(BrupRole.PRIMARY.getCode())).thenReturn(List.of(target));
    }

    private void issueTarget(PetSe petSe, boolean representative) {
        MissionTargetSummary target = new MissionTargetSummary();
        target.setPetSn(PET);
        target.setMbrSn(PRIMARY);
        target.setPetSeCd(petSe.getCode());
        target.setRprsPetYn(representative);
        when(mapper.selectIssueTarget(PET, BrupRole.PRIMARY.getCode())).thenReturn(target);
    }

    private void reflectable(CareMission mission, int achvCrtrNmtm, DayMsn dayMsn) {
        CareMsn master = masterOf(mission, achvCrtrNmtm);
        when(mapper.selectMissionByName(mission.getMsnNm())).thenReturn(master);
        if (dayMsn != null) {
            dayMsn.setAchvCrtrNmtm(achvCrtrNmtm);
        }
        when(mapper.selectDayMissionForUpdate(PET, TODAY, master.getCareMsnSn())).thenReturn(dayMsn);
    }

    private CareMsn masterOf(CareMission mission, int achvCrtrNmtm) {
        CareMsn master = masters.stream().filter(m -> m.getMsnNm().equals(mission.getMsnNm())).findFirst()
                .orElseThrow();
        master.setAchvCrtrNmtm(achvCrtrNmtm);
        return master;
    }

    private List<DayMsn> inserted() {
        ArgumentCaptor<DayMsn> captor = ArgumentCaptor.forClass(DayMsn.class);
        verify(mapper, atLeast(0)).insertDayMission(captor.capture());
        return captor.getAllValues();
    }

    /** 등급별 개수 (어려움, 보통, 쉬움) */
    private List<Integer> gradeCounts(List<DayMsn> issued) {
        return List.of(gradeCount(issued, CareMission.Grade.HARD), gradeCount(issued, CareMission.Grade.NORMAL),
                gradeCount(issued, CareMission.Grade.EASY));
    }

    private int gradeCount(List<DayMsn> issued, CareMission.Grade grade) {
        return (int) issued.stream().filter(d -> missionOf(d).getGrade() == grade).count();
    }

    private CareMission missionOf(DayMsn d) {
        return missionOf(d.getCareMsnSn());
    }

    private CareMission missionOf(Long careMsnSn) {
        Map<Long, CareMsn> bySn = masters.stream().collect(Collectors.toMap(CareMsn::getCareMsnSn, Function.identity()));
        return CareMission.fromMsnNm(bySn.get(careMsnSn).getMsnNm()).orElseThrow();
    }

    private static CareMsn master(long sn, String msnNm, CareMissionPool pool, int cmpnPnt, int achvCrtrNmtm,
            String useYn) {
        CareMsn master = new CareMsn();
        master.setCareMsnSn(sn);
        master.setMsnNm(msnNm);
        master.setMsnPoolCd(pool.getCode());
        master.setCmpnPnt(cmpnPnt);
        master.setAchvCrtrNmtm(achvCrtrNmtm);
        master.setUseYn(useYn);
        return master;
    }

    private static DayMsn day(Long dayMsnSn, int prgrsNmtm, int scorPnt, String cmptnYn) {
        DayMsn day = new DayMsn();
        day.setDayMsnSn(dayMsnSn);
        day.setPetSn(PET);
        day.setMbrSn(PRIMARY);
        day.setPrgrsNmtm(prgrsNmtm);
        day.setScorPnt(scorPnt);
        day.setAchvCrtrNmtm(1);
        day.setCmptnYn(cmptnYn);
        day.setClclnCfmtnYn("N");
        return day;
    }
}
