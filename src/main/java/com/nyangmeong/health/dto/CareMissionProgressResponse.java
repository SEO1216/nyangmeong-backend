package com.nyangmeong.health.dto;

/**
 * [케어리스트 오늘 케어미션 진행 응답] (※신규 정책)
 * - GET /api/pets/{petSn}/care-items/mission-progress : 공동양육자도 볼 수 있게 포인트는 담지 않음
 *
 * @param prmyYn   요청 회원이 주양육자인지
 * @param cmptnCnt 오늘 완료한 미션 수
 * @param totCnt   오늘 출제된 미션 수 (출제 전이면 0)
 */
public record CareMissionProgressResponse(boolean prmyYn, int cmptnCnt, int totCnt) {
}
