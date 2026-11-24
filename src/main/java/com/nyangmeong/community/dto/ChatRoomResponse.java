package com.nyangmeong.community.dto;

import com.nyangmeong.community.domain.ChatRoomDetail;

/**
 * [채팅방 응답]
 * - ctptMbr* : 보는 사람 기준 상대 회원(보호자에게는 지원자, 지원자에게는 보호자)
 * - myRole : GUARDIAN(게시글 작성자) 또는 SITTER(지원자)
 * - created : 이번 요청으로 방을 만들었는지
 * - cnclDmnd : 이 매칭의 가장 최근 거래 취소 요청(조회에서만 채움, 없으면 null)
 */
public record ChatRoomResponse(
        Long otoChatRoomSn,
        String chatRoomSttsCd,
        Long pstSn,
        String pstTtl,
        Long petPrtcRcrtSn,
        String rcrtSttsCd,
        Long ctptMbrSn,
        String ctptMbrNcnm,
        Long ctptProfileAtchFileSn,
        String myRole,
        Long petPrtcMtchSn,
        String mtchDlngSttsCd,
        boolean created,
        CareCancelDemandItem cnclDmnd) {

    /** 취소 요청 정보 없이 만드는 응답 (생성·기존 방 반환) */
    public ChatRoomResponse(Long otoChatRoomSn, String chatRoomSttsCd, Long pstSn, String pstTtl, Long petPrtcRcrtSn,
            String rcrtSttsCd, Long ctptMbrSn, String ctptMbrNcnm, Long ctptProfileAtchFileSn, String myRole,
            Long petPrtcMtchSn, String mtchDlngSttsCd, boolean created) {
        this(otoChatRoomSn, chatRoomSttsCd, pstSn, pstTtl, petPrtcRcrtSn, rcrtSttsCd, ctptMbrSn, ctptMbrNcnm,
                ctptProfileAtchFileSn, myRole, petPrtcMtchSn, mtchDlngSttsCd, created, null);
    }

    public static final String ROLE_GUARDIAN = "GUARDIAN";
    public static final String ROLE_SITTER = "SITTER";

    public static ChatRoomResponse of(ChatRoomDetail room, Long mbrSn, boolean created) {
        boolean guardian = mbrSn.equals(room.getOwnerMbrSn());
        return new ChatRoomResponse(room.getOtoChatRoomSn(), room.getChatRoomSttsCd(), room.getPstSn(),
                room.getPstTtl(), room.getPetPrtcRcrtSn(), room.getRcrtSttsCd(),
                guardian ? room.getCtptMbrSn() : room.getOwnerMbrSn(),
                guardian ? room.getApplicantNcnm() : room.getOwnerNcnm(),
                guardian ? room.getApplicantAtchFileSn() : room.getOwnerAtchFileSn(),
                guardian ? ROLE_GUARDIAN : ROLE_SITTER, room.getPetPrtcMtchSn(), room.getMtchDlngSttsCd(), created);
    }

    public ChatRoomResponse withCnclDmnd(CareCancelDemandItem item) {
        return new ChatRoomResponse(otoChatRoomSn, chatRoomSttsCd, pstSn, pstTtl, petPrtcRcrtSn, rcrtSttsCd,
                ctptMbrSn, ctptMbrNcnm, ctptProfileAtchFileSn, myRole, petPrtcMtchSn, mtchDlngSttsCd, created, item);
    }
}
