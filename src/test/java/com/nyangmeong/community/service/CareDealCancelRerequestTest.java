package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nyangmeong.community.domain.CareCancelDemand;
import com.nyangmeong.community.domain.CareMatch;
import com.nyangmeong.community.domain.CareRecruitment;
import com.nyangmeong.community.domain.ChatParticipant;
import com.nyangmeong.community.domain.ChatRoomDetail;
import com.nyangmeong.community.dto.CareDealCancelRequest;
import com.nyangmeong.community.mapper.CareMatchMapper;
import com.nyangmeong.community.mapper.CareRecruitmentMapper;
import com.nyangmeong.community.mapper.ChatRoomMapper;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.notification.EventPublisher;

/**
 * [검사 - 거절된 거래 취소 요청의 재요청 제한]
 * - 이번 거래에서 내 요청이 거절된 적이 있으면 409 로 거절하고 저장하지 않음, 상대·거절 이력 없는 사람은 요청 가능
 */
class CareDealCancelRerequestTest {

    private static final Long GUARDIAN = 1L;
    private static final Long SITTER = 2L;
    private static final Long RCRT = 5L;
    private static final Long ROOM = 11L;
    private static final Long MATCH = 21L;
    private static final String MATCHED = "PCAC0013";
    private static final String REJECTED = "PCAC0023";

    private final CareMatchMapper careMatchMapper = mock(CareMatchMapper.class);
    private final CareRecruitmentMapper careRecruitmentMapper = mock(CareRecruitmentMapper.class);
    private final ChatRoomMapper chatRoomMapper = mock(ChatRoomMapper.class);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final ChatViewRegistry chatViewRegistry = mock(ChatViewRegistry.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final CareMatchService service = new CareMatchService(careMatchMapper, careRecruitmentMapper,
            chatRoomMapper, new ChatRoomGuard(chatRoomMapper), chatMessageService, chatViewRegistry,
            eventPublisher);

    @BeforeEach
    void stubMatched() {
        ChatRoomDetail room = new ChatRoomDetail();
        room.setOtoChatRoomSn(ROOM);
        room.setPstSn(7L);
        room.setCtptMbrSn(SITTER);
        room.setOwnerMbrSn(GUARDIAN);
        room.setPetPrtcRcrtSn(RCRT);
        room.setPetPrtcMtchSn(MATCH);
        room.setMtchDlngSttsCd(MATCHED);
        when(chatRoomMapper.selectDetail(ROOM)).thenReturn(room);
        when(chatRoomMapper.selectParticipant(eq(ROOM), anyLong())).thenAnswer(inv -> {
            ChatParticipant participant = new ChatParticipant();
            participant.setPtcpSttsCd("CHTC0003");
            return participant;
        });
        CareRecruitment recruitment = new CareRecruitment();
        recruitment.setPetPrtcRcrtSn(RCRT);
        when(careRecruitmentMapper.selectForUpdate(RCRT)).thenReturn(recruitment);
        CareMatch match = new CareMatch();
        match.setPetPrtcMtchSn(MATCH);
        match.setPetPrtcRcrtSn(RCRT);
        match.setOtoChatRoomSn(ROOM);
        match.setMtchDlngSttsCd(MATCHED);
        when(careMatchMapper.selectById(MATCH)).thenReturn(match);
        when(careMatchMapper.selectByIdForUpdate(MATCH)).thenReturn(match);
    }

    @Test
    void 이번_거래에서_내_요청이_거절된_적이_있으면_다시_요청할_수_없다() {
        when(careMatchMapper.existsRejectedCancelDemand(MATCH, SITTER, REJECTED, MATCHED)).thenReturn(true);

        assertThatThrownBy(() -> service.requestDealCancel(SITTER, MATCH, new CareDealCancelRequest("다시 요청")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CARE_DEAL_CANCEL_REJECTED));
        verify(careMatchMapper, never()).insertCancelDemand(any());
        verify(chatMessageService, never()).createSystemMessage(anyLong(), anyString());
        assertThat(ErrorCode.CARE_DEAL_CANCEL_REJECTED.status().value()).isEqualTo(409);
    }

    @Test
    void 거절_이력이_없는_상대는_요청할_수_있다() {
        when(careMatchMapper.existsRejectedCancelDemand(MATCH, SITTER, REJECTED, MATCHED)).thenReturn(true);
        when(careMatchMapper.existsRejectedCancelDemand(MATCH, GUARDIAN, REJECTED, MATCHED)).thenReturn(false);

        service.requestDealCancel(GUARDIAN, MATCH, new CareDealCancelRequest("일정이 바뀌었어요"));

        verify(careMatchMapper).insertCancelDemand(any(CareCancelDemand.class));
        verify(careMatchMapper).existsRejectedCancelDemand(MATCH, GUARDIAN, REJECTED, MATCHED);
    }
}
