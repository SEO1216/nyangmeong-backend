package com.nyangmeong.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.nyangmeong.community.domain.CareCancelDemand;
import com.nyangmeong.community.domain.CareCompletionRequest;
import com.nyangmeong.community.domain.CareMatch;
import com.nyangmeong.community.domain.CareMatchCondition;
import com.nyangmeong.community.domain.CareMatchStatusHistory;
import com.nyangmeong.community.dto.AdminCareMatchResponse;

/**
 * [펫돌봄 매칭 Mapper]
 * - 거래 단계(SCR-CMU-005)와 관리자 상세 조회(SCR-ADM-022)를 함께 둔다
 */
@Mapper
public interface CareMatchMapper {

    /**
     * 매칭 조회 (없으면 null)
     */
    CareMatch selectById(Long petPrtcMtchSn);

    /**
     * 매칭 잠금 읽기 (스냅샷이 아닌 최신 커밋 값)
     */
    CareMatch selectByIdForUpdate(Long petPrtcMtchSn);

    /**
     * 채팅방의 매칭 잠금 읽기 (없으면 null)
     */
    CareMatch selectByRoomForUpdate(Long otoChatRoomSn);

    /**
     * 같은 멱등 요청 ID 의 매칭 잠금 읽기
     */
    CareMatch selectByIdmptDmndIdForShare(String idmptDmndId);

    /**
     * 매칭 등록
     */
    int insertMatch(CareMatch match);

    /**
     * 매칭 상태 변경 (현재 상태가 bfrSttsCd 일 때만)
     */
    int updateStatus(@Param("petPrtcMtchSn") Long petPrtcMtchSn, @Param("bfrSttsCd") String bfrSttsCd,
            @Param("aftrSttsCd") String aftrSttsCd);

    /**
     * 요청 취소된 매칭을 다시 수락 대기로 (새 멱등 요청 ID, 수령 정보 초기화)
     */
    int updateRequestAgain(@Param("petPrtcMtchSn") Long petPrtcMtchSn, @Param("idmptDmndId") String idmptDmndId,
            @Param("canceledCd") String canceledCd, @Param("requestedCd") String requestedCd);

    /**
     * 비용 수령 확인 기록
     */
    int updateReceipt(@Param("petPrtcMtchSn") Long petPrtcMtchSn, @Param("rcvYn") String rcvYn);

    /**
     * 무응답 기준 일수 이상 지금 상태에 머문 매칭 번호 (지금 상태가 된 시각 = 그 상태로 바뀐 가장 최근 이력)
     */
    List<Long> selectNoResponseTargets(@Param("sttsCd") String sttsCd, @Param("days") long days);

    /**
     * 이 매칭이 지금도 그 상태로 기준 일수 이상 머물렀는지 (잠근 뒤 다시 확인)
     */
    boolean existsNoResponseExpired(@Param("petPrtcMtchSn") Long petPrtcMtchSn, @Param("sttsCd") String sttsCd,
            @Param("days") long days);

    /**
     * 매칭 상태 이력 등록
     */
    int insertStatusHistory(CareMatchStatusHistory history);

    /**
     * 매칭 조건 버전 등록
     */
    int insertCondition(CareMatchCondition condition);

    /**
     * 가장 최근 조건 버전 (없으면 null)
     */
    CareMatchCondition selectLatestCondition(Long petPrtcMtchSn);

    /**
     * 다음 조건 버전 번호
     */
    int selectNextCndVerNo(Long petPrtcMtchSn);

    /**
     * 완료 요청 회차 목록 (회차 순)
     */
    List<CareCompletionRequest> selectCompletionRequests(Long petPrtcMtchSn);

    /**
     * 가장 최근 완료 요청 회차 잠금 읽기 (없으면 null)
     */
    CareCompletionRequest selectLatestCompletionRequestForUpdate(Long petPrtcMtchSn);

    /**
     * 다음 완료 요청 회차
     */
    int selectNextDmndCycl(Long petPrtcMtchSn);

    /**
     * 완료 요청 회차 등록
     */
    int insertCompletionRequest(CareCompletionRequest request);

    /**
     * 보호자 완료 확인 기록
     */
    int updateCompletionConfirm(@Param("prtcCmptnDmndCyclSn") Long prtcCmptnDmndCyclSn,
            @Param("prtcrIdntyRsltCd") String prtcrIdntyRsltCd);

    /**
     * 거래 취소 요청 등록
     */
    int insertCancelDemand(CareCancelDemand demand);

    /**
     * 요청중인 거래 취소 요청 잠금 읽기 (없으면 null, 매칭 잠금 뒤라 최신 커밋 값을 읽음)
     */
    CareCancelDemand selectRequestingCancelDemandForUpdate(@Param("petPrtcMtchSn") Long petPrtcMtchSn,
            @Param("requestingCd") String requestingCd);

    /**
     * 가장 최근 거래 취소 요청 (없으면 null)
     */
    CareCancelDemand selectLatestCancelDemand(Long petPrtcMtchSn);

    /**
     * 거래 취소 요청 처리 (현재 상태가 bfrSttsCd 일 때만)
     */
    int updateCancelDemandStatus(@Param("mtchCnclDmndSn") Long mtchCnclDmndSn, @Param("bfrSttsCd") String bfrSttsCd,
            @Param("aftrSttsCd") String aftrSttsCd, @Param("prcsMbrSn") Long prcsMbrSn);

    /**
     * 이번 거래(가장 최근 매칭 완료 전환 이후)에서 이 회원의 거래 취소 요청이 거절된 적이 있는지
     * - 같은 매칭 행이 요청 취소 뒤 다시 요청돼 재사용되므로 지난 거래의 거절은 세지 않는다
     */
    boolean existsRejectedCancelDemand(@Param("petPrtcMtchSn") Long petPrtcMtchSn, @Param("dmndMbrSn") Long dmndMbrSn,
            @Param("rejectedCd") String rejectedCd, @Param("matchedCd") String matchedCd);

    /**
     * 거래 취소 요청 단건 (없으면 null)
     */
    CareCancelDemand selectCancelDemandById(Long mtchCnclDmndSn);

    /**
     * 기한이 된 거래 취소 요청 번호 : 요청중, 매칭이 sttsCds 중 하나, 등록 후 fromDays 일 이상 (toDays 가 있으면 toDays 일 미만)
     */
    List<Long> selectCancelDemandDueTargets(@Param("requestingCd") String requestingCd,
            @Param("sttsCds") List<String> sttsCds, @Param("fromDays") long fromDays, @Param("toDays") Long toDays);

    /**
     * 이 거래 취소 요청이 지금도 기한 조건에 맞는지 (잠근 뒤 다시 확인)
     */
    boolean existsCancelDemandDue(@Param("mtchCnclDmndSn") Long mtchCnclDmndSn,
            @Param("requestingCd") String requestingCd, @Param("sttsCds") List<String> sttsCds,
            @Param("fromDays") long fromDays, @Param("toDays") Long toDays);

    /**
     * 모집글의 매칭 목록 (최신순)
     */
    List<AdminCareMatchResponse.Match> selectAdminMatches(@Param("petPrtcRcrtSn") Long petPrtcRcrtSn);

    /**
     * 모집글 매칭들의 합의 조건 버전 (매칭별 버전 내림차순)
     */
    List<AdminCareMatchResponse.ConditionVersion> selectAdminConditionVersions(
            @Param("petPrtcRcrtSn") Long petPrtcRcrtSn);

    /**
     * 모집글 매칭들의 상태 이력 (오래된 순)
     */
    List<AdminCareMatchResponse.StatusHistory> selectAdminStatusHistories(
            @Param("petPrtcRcrtSn") Long petPrtcRcrtSn);

    /**
     * 모집글 매칭들의 돌봄 완료 요청 회차 (회차 순)
     */
    List<AdminCareMatchResponse.CompletionRequest> selectAdminCompletionRequests(
            @Param("petPrtcRcrtSn") Long petPrtcRcrtSn);

    /**
     * 모집글 매칭들의 돌봄 완료 사진 (정렬 순서)
     */
    List<AdminCareMatchResponse.Photo> selectAdminCompletionPhotos(@Param("petPrtcRcrtSn") Long petPrtcRcrtSn);
}
