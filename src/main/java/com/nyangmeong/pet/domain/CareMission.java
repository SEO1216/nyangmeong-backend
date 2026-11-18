package com.nyangmeong.pet.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * [케어미션]
 * - CARE_MSN 행을 MSN_NM(UK)으로 찾음 : 이름을 바꾸면 db/ 기준 데이터 파일(insert_base_data)과 같이 바꿈
 * - 케어리스트 미션은 건강기록 기본 항목(FN-HLT-003-12)만, 사용자 추가·처방약·반복 예방 항목은 미션 아님
 * - 등급·배점은 DB 컬럼이 아니라 여기에 둠 (※신규 정책) : 쉬움 5P·보통 10P·어려움 15P, CARE_MSN.CMPN_PNT는 배점에 쓰지 않음
 */
public enum CareMission {
    TOILET_CLEANING("화장실 청소하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.EASY, true),
    TOOTH_BRUSHING("양치하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.HARD, true),
    BRUSHING("빗질하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.NORMAL, true),
    PAW_CARE("발 상태 확인하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.EASY, true),
    PLAY("놀이하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.NORMAL, true),
    WEIGHT_RECORD("체중 기록하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.EASY, false),
    MEAL_RECORD("식사량 기록하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.NORMAL, false),
    WATER_RECORD("음수량 기록하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.EASY, false),
    STOOL_RECORD("배변 기록하기", CareMissionPool.DAILY, Condition.ALWAYS, Grade.EASY, false),
    EXPENSE_CREATE("가계부 지출 등록하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.EASY, false),
    SCHEDULE_CREATE("케어 캘린더 일정 등록하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.EASY, false),
    EAR_CLEANING("귀 청소하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.NORMAL, true),
    NAIL_CARE("발톱 관리하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.HARD, true),
    BATH("목욕하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.HARD, true),
    EYE_CARE("눈곱 닦기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.EASY, true),
    PAW_HAIR_CARE("발바닥 털 다듬기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.HARD, true),
    MOUTH_CHECK("잇몸·치석 확인하기", CareMissionPool.ACTIVITY, Condition.ALWAYS, Grade.EASY, true),
    WALK_RECORD("산책 기록하기", CareMissionPool.ACTIVITY, Condition.DOG, Grade.HARD, false),
    OBSERVATION_RECORD("관찰 기록 추가하기", CareMissionPool.ACTIVITY, Condition.OBSERVING, Grade.NORMAL, false),
    PET_STORY_POST("펫이야기 글 등록하기", CareMissionPool.ACTIVITY, Condition.REPRESENTATIVE, Grade.HARD, false),
    PET_STORY_COMMENT("펫이야기 댓글 작성하기", CareMissionPool.ACTIVITY, Condition.REPRESENTATIVE, Grade.EASY, false);

    /** 반려동물당 하루 총 배점 */
    public static final int DAILY_TOTAL_PNT = 60;

    /** 반려동물당 하루 출제 수 */
    public static final int ISSUE_COUNT = 8;

    /**
     * [출제 조건]
     */
    public enum Condition {
        ALWAYS, DOG, OBSERVING, REPRESENTATIVE
    }

    /**
     * [등급] : 고정 배점과, 조합을 못 만드는 날 비율 배분에 쓰는 가중치
     */
    public enum Grade {
        EASY(5, 1), NORMAL(10, 2), HARD(15, 3);

        private final int point;
        private final int weight;

        Grade(int point, int weight) {
            this.point = point;
            this.weight = weight;
        }

        public int getPoint() {
            return point;
        }

        public int getWeight() {
            return weight;
        }
    }

    private final String msnNm;
    private final CareMissionPool pool;
    private final Condition condition;
    private final Grade grade;
    private final boolean careList;

    CareMission(String msnNm, CareMissionPool pool, Condition condition, Grade grade, boolean careList) {
        this.msnNm = msnNm;
        this.pool = pool;
        this.condition = condition;
        this.grade = grade;
        this.careList = careList;
    }

    public String getMsnNm() {
        return msnNm;
    }

    public CareMissionPool getPool() {
        return pool;
    }

    public Grade getGrade() {
        return grade;
    }

    /**
     * 케어리스트 기본 항목 미션 여부 : 보이는 항목·예정일로 출제 후보를 가림
     */
    public boolean isCareList() {
        return careList;
    }

    /**
     * 미션명으로 케어미션 조회
     */
    public static Optional<CareMission> fromMsnNm(String msnNm) {
        return Arrays.stream(values()).filter(m -> m.msnNm.equals(msnNm)).findFirst();
    }

    /**
     * 출제 조건 충족 여부
     */
    public boolean canIssue(boolean dog, boolean observing, boolean representative) {
        return switch (condition) {
            case ALWAYS -> true;
            case DOG -> dog;
            case OBSERVING -> observing;
            case REPRESENTATIVE -> representative;
        };
    }
}
