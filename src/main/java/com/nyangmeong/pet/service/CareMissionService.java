package com.nyangmeong.pet.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyangmeong.global.auth.BrupRole;
import com.nyangmeong.global.auth.PetAuthorizer;
import com.nyangmeong.pet.domain.CareMission;
import com.nyangmeong.pet.domain.CareMsn;
import com.nyangmeong.pet.domain.DayMsn;
import com.nyangmeong.pet.domain.PetSe;
import com.nyangmeong.pet.dto.DailyMissionSummary;
import com.nyangmeong.pet.dto.MissionTargetSummary;
import com.nyangmeong.pet.mapper.CareMissionMapper;

import lombok.RequiredArgsConstructor;

/**
 * [케어미션 서비스]
 * - 출제·자정 정산은 scheduler 배치, 진행 반영은 활동이 일어난 도메인이 부름 (SCR-MYP-020)
 * - 반려동물마다 하루 8개, 합계 60P (※신규 정책) : 등급 조합 (어려움, 보통, 쉬움) = (0,4,4)·(1,2,5)·(2,0,6) 중 후보로 만들 수 있는 것을 무작위로 고름
 * - 어떤 조합도 못 만들면 그날만 등급 가중치 비율로 60P를 나눔
 * - 케어리스트 미션 후보는 health 가 계산해 scheduler 가 넘김 (pet 은 건강기록 테이블을 읽지 않음)
 * - 포인트는 출제 때의 주양육자(DAY_MSN.MBR_SN)에게 적립 (FN-MYP-020-06)
 * - 배점·달성 기준은 출제 때 DAY_MSN에 복사한 값을 씀 : 배점은 CareMission 등급, 달성 기준은 CARE_MSN (FN-ADM-071-03)
 */
@Service
@RequiredArgsConstructor
public class CareMissionService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 등급 조합 (어려움, 보통, 쉬움) : 합계 모두 60P */
    static final int[][] COMBOS = { { 0, 4, 4 }, { 1, 2, 5 }, { 2, 0, 6 } };

    /** 케어리스트 기본 항목이 아직 없는 반려동물의 케어리스트 후보 : 기본 항목을 만들 때 보이는 매일 항목과 같음 */
    static final Set<CareMission> DEFAULT_CARE_LIST = EnumSet.of(CareMission.TOILET_CLEANING,
            CareMission.TOOTH_BRUSHING, CareMission.BRUSHING, CareMission.PLAY);

    /** 뽑은 미션과 배점 */
    record Picked(CareMsn master, int point) {
    }

    private final CareMissionMapper careMissionMapper;
    private final PointService pointService;
    private final PetAuthorizer petAuthorizer;

    /**
     * 그날의 케어미션 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DailyMissionSummary> search(Long mbrSn, Long petSn, LocalDate msnCrtrYmd) {
        petAuthorizer.checkPrimary(mbrSn, petSn);
        return careMissionMapper.selectDailyMissions(petSn, msnCrtrYmd == null ? LocalDate.now(ZONE) : msnCrtrYmd);
    }

    /**
     * 반려동물의 그날 케어미션 목록 조회 (읽기 전용)
     * - 케어리스트의 미션 표시용 : 공동양육자도 보므로 권한은 호출하는 쪽에서 검사 (※신규 정책)
     */
    @Transactional(readOnly = true)
    public List<DailyMissionSummary> searchByPet(Long petSn, LocalDate msnCrtrYmd) {
        return careMissionMapper.selectDailyMissions(petSn, msnCrtrYmd);
    }

    /**
     * 그날 케어미션 출제
     */
    @Transactional
    public void issue(LocalDate msnCrtrYmd, Set<Long> obsrvPetSns, Map<Long, Set<CareMission>> careListMissions) {
        Map<CareMission, CareMsn> masters = usableMasters();
        Set<Long> issuedPetSns = new HashSet<>(careMissionMapper.selectIssuedPetSns(msnCrtrYmd));
        for (MissionTargetSummary target : careMissionMapper.selectIssueTargets(BrupRole.PRIMARY.getCode())) {
            if (!issuedPetSns.add(target.getPetSn())) {
                continue;
            }
            insertMissions(masters, target, msnCrtrYmd, obsrvPetSns.contains(target.getPetSn()),
                    careListMissions.getOrDefault(target.getPetSn(), DEFAULT_CARE_LIST));
        }
    }

    /**
     * 반려동물 1마리의 그날 케어미션 출제
     * - 이미 출제된 날, 주양육 관계가 없는 반려동물은 건너뜀 (배치와 중복 출제 없음)
     * - 대상 선정·pick 은 issue 와 같음, 방금 등록해 케어리스트 기본 항목이 없으므로 기본 후보를 씀
     */
    @Transactional
    public void issueFor(Long petSn, LocalDate msnCrtrYmd, boolean observing) {
        if (careMissionMapper.countDayMissions(petSn, msnCrtrYmd) > 0) {
            return;
        }
        MissionTargetSummary target = careMissionMapper.selectIssueTarget(petSn, BrupRole.PRIMARY.getCode());
        if (target == null) {
            return;
        }
        insertMissions(usableMasters(), target, msnCrtrYmd, observing, DEFAULT_CARE_LIST);
    }

    /**
     * 반려동물 1마리의 미션 고르기와 일별미션 등록
     */
    private void insertMissions(Map<CareMission, CareMsn> masters, MissionTargetSummary target,
            LocalDate msnCrtrYmd, boolean observing, Set<CareMission> careListMissions) {
        for (Picked picked : pick(masters, target, observing, careListMissions)) {
            CareMsn master = picked.master();
            DayMsn dayMsn = new DayMsn();
            dayMsn.setCareMsnSn(master.getCareMsnSn());
            dayMsn.setPetSn(target.getPetSn());
            dayMsn.setMbrSn(target.getMbrSn());
            dayMsn.setMsnCrtrYmd(msnCrtrYmd);
            dayMsn.setScorPnt(picked.point());
            dayMsn.setAchvCrtrNmtm(master.getAchvCrtrNmtm());
            careMissionMapper.insertDayMission(dayMsn);
        }
    }

    /**
     * 반려동물 활동의 케어미션 반영
     */
    @Transactional
    public void reflect(Long petSn, CareMission mission, Long actorMbrSn) {
        reflect(petSn, mission, actorMbrSn, LocalDate.now(ZONE));
    }

    void reflect(Long petSn, CareMission mission, Long actorMbrSn, LocalDate today) {
        CareMsn master = careMissionMapper.selectMissionByName(mission.getMsnNm());
        if (master == null) {
            return;
        }
        progress(careMissionMapper.selectDayMissionForUpdate(petSn, today, master.getCareMsnSn()), master,
                actorMbrSn);
    }

    /**
     * 회원 활동의 대표 반려동물 케어미션 반영
     * - 펫이야기 글·댓글 : 대표 반려동물에게만 출제되므로 회원 기준으로 찾음
     */
    @Transactional
    public void reflectRepresentative(Long mbrSn, CareMission mission) {
        reflectRepresentative(mbrSn, mission, LocalDate.now(ZONE));
    }

    void reflectRepresentative(Long mbrSn, CareMission mission, LocalDate today) {
        CareMsn master = careMissionMapper.selectMissionByName(mission.getMsnNm());
        if (master == null) {
            return;
        }
        progress(careMissionMapper.selectMemberDayMissionForUpdate(mbrSn, today, master.getCareMsnSn()), master,
                mbrSn);
    }

    /**
     * 그날 케어미션 정산
     */
    @Transactional
    public void settle(LocalDate msnCrtrYmd) {
        Map<Long, CareMsn> masters = careMissionMapper.selectMissions().stream()
                .collect(Collectors.toMap(CareMsn::getCareMsnSn, Function.identity()));
        for (DayMsn dayMsn : careMissionMapper.selectUnsettledDayMissions(msnCrtrYmd)) {
            if ("Y".equals(dayMsn.getCmptnYn())) {
                careMissionMapper.updateDayMissionSettled(dayMsn.getDayMsnSn(), dayMsn.getGivePnt());
                continue;
            }
            CareMsn master = masters.get(dayMsn.getCareMsnSn());
            int givePnt = settledPoint(dayMsn.getScorPnt(), dayMsn.getPrgrsNmtm(), dayMsn.getAchvCrtrNmtm());
            careMissionMapper.updateDayMissionSettled(dayMsn.getDayMsnSn(), givePnt);
            if (givePnt > 0) {
                pointService.earnMission(dayMsn.getMbrSn(), dayMsn.getDayMsnSn(), givePnt,
                        memo(dayMsn.getPetSn(), master));
            }
        }
    }

    /**
     * 미완료 미션의 정산 포인트
     * - 진행률만큼 지급 (※신규 정책, FN-MYP-020-05) : 15P 미션 2/3 → 10P, 10P 미션 1/2 → 5P
     */
    static int settledPoint(int scorPnt, int prgrsNmtm, int achvCrtrNmtm) {
        return scorPnt * Math.min(prgrsNmtm, achvCrtrNmtm) / achvCrtrNmtm;
    }

    /**
     * 진행 횟수 반영과 완료 시 즉시 지급
     */
    private void progress(DayMsn dayMsn, CareMsn master, Long actorMbrSn) {
        if (dayMsn == null || "Y".equals(dayMsn.getCmptnYn())) {
            return;
        }
        int prgrsNmtm = dayMsn.getPrgrsNmtm() + 1;
        if (prgrsNmtm < dayMsn.getAchvCrtrNmtm()) {
            careMissionMapper.updateDayMissionProgress(dayMsn.getDayMsnSn(), prgrsNmtm);
            return;
        }
        careMissionMapper.updateDayMissionComplete(dayMsn.getDayMsnSn(), prgrsNmtm, actorMbrSn, dayMsn.getScorPnt());
        pointService.earnMission(dayMsn.getMbrSn(), dayMsn.getDayMsnSn(), dayMsn.getScorPnt(),
                memo(dayMsn.getPetSn(), master));
    }

    /**
     * 반려동물 1마리의 그날 미션 고르기
     * - 등급 조합 (어려움, 보통, 쉬움) 중 후보로 만들 수 있는 것을 무작위로 골라 8개, 합계 60P
     * - 어떤 조합도 못 만들면 후보 전체(최대 8개)를 등급 가중치 비율로 60P 배분
     */
    private List<Picked> pick(Map<CareMission, CareMsn> masters, MissionTargetSummary target, boolean observing,
            Set<CareMission> careListMissions) {
        boolean dog = PetSe.DOG.is(target.getPetSeCd());
        Map<CareMission.Grade, List<CareMsn>> byGrade = new EnumMap<>(CareMission.Grade.class);
        for (CareMission.Grade grade : CareMission.Grade.values()) {
            byGrade.put(grade, new ArrayList<>());
        }
        List<CareMsn> all = new ArrayList<>();
        masters.forEach((mission, master) -> {
            if (isCandidate(mission, dog, observing, target.isRprsPetYn(), careListMissions)) {
                byGrade.get(mission.getGrade()).add(master);
                all.add(master);
            }
        });
        byGrade.values().forEach(Collections::shuffle);

        List<int[]> possible = new ArrayList<>();
        for (int[] combo : COMBOS) {
            if (byGrade.get(CareMission.Grade.HARD).size() >= combo[0]
                    && byGrade.get(CareMission.Grade.NORMAL).size() >= combo[1]
                    && byGrade.get(CareMission.Grade.EASY).size() >= combo[2]) {
                possible.add(combo);
            }
        }
        if (possible.isEmpty()) {
            return weighted(masters, all);
        }
        int[] combo = possible.get(ThreadLocalRandom.current().nextInt(possible.size()));
        List<Picked> picked = new ArrayList<>();
        addPicked(picked, byGrade.get(CareMission.Grade.HARD), combo[0], CareMission.Grade.HARD);
        addPicked(picked, byGrade.get(CareMission.Grade.NORMAL), combo[1], CareMission.Grade.NORMAL);
        addPicked(picked, byGrade.get(CareMission.Grade.EASY), combo[2], CareMission.Grade.EASY);
        Collections.shuffle(picked);
        return picked;
    }

    private static void addPicked(List<Picked> picked, List<CareMsn> candidates, int count,
            CareMission.Grade grade) {
        candidates.subList(0, count).forEach(master -> picked.add(new Picked(master, grade.getPoint())));
    }

    /**
     * 조합을 못 만드는 날 : 후보 전체(최대 8개)에 60P를 등급 가중치 비율로 나눔
     * - 내림으로 나눈 뒤 남는 포인트는 높은 등급부터(같은 등급은 출제 순서대로) 1P씩 더함
     */
    private List<Picked> weighted(Map<CareMission, CareMsn> masters, List<CareMsn> all) {
        Collections.shuffle(all);
        List<CareMsn> chosen = new ArrayList<>(all.subList(0, Math.min(CareMission.ISSUE_COUNT, all.size())));
        if (chosen.isEmpty()) {
            return List.of();
        }
        Map<CareMsn, CareMission> missionOf = new HashMap<>();
        masters.forEach((mission, master) -> missionOf.put(master, mission));
        int[] points = new int[chosen.size()];
        int weightSum = chosen.stream().mapToInt(m -> missionOf.get(m).getGrade().getWeight()).sum();
        int given = 0;
        for (int i = 0; i < points.length; i++) {
            points[i] = CareMission.DAILY_TOTAL_PNT * missionOf.get(chosen.get(i)).getGrade().getWeight() / weightSum;
            given += points[i];
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            order.add(i);
        }
        order.sort((a, b) -> missionOf.get(chosen.get(b)).getGrade().getWeight()
                - missionOf.get(chosen.get(a)).getGrade().getWeight());
        for (int i = 0; given < CareMission.DAILY_TOTAL_PNT; i++, given++) {
            points[order.get(i % order.size())]++;
        }
        List<Picked> picked = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            picked.add(new Picked(chosen.get(i), points[i]));
        }
        return picked;
    }

    /**
     * 출제 후보 여부
     * - 케어리스트 미션은 보이는 항목·예정일이 된 항목으로 health 가 가린 후보만, 나머지는 출제 조건만
     */
    private static boolean isCandidate(CareMission mission, boolean dog, boolean observing, boolean representative,
            Set<CareMission> careListMissions) {
        if (mission.isCareList() && !careListMissions.contains(mission)) {
            return false;
        }
        return mission.canIssue(dog, observing, representative);
    }

    /**
     * 사용 중 미션 마스터
     * - CareMission 에 없는 행(시드의 옛 미션 등)은 출제하지 않음
     */
    private Map<CareMission, CareMsn> usableMasters() {
        Map<CareMission, CareMsn> masters = new EnumMap<>(CareMission.class);
        for (CareMsn master : careMissionMapper.selectMissions()) {
            if (!"Y".equals(master.getUseYn())) {
                continue;
            }
            Optional<CareMission> mission = CareMission.fromMsnNm(master.getMsnNm());
            mission.ifPresent(m -> masters.put(m, master));
        }
        return masters;
    }

    /**
     * 원장 메모 : {반려동물명} · {미션명}
     */
    private String memo(Long petSn, CareMsn master) {
        return careMissionMapper.selectPetNm(petSn) + " · " + master.getMsnNm();
    }
}
