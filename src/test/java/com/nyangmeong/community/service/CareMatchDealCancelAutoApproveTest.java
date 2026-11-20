package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.community.domain.CareCancelDemand;
import com.nyangmeong.community.domain.CareMatch;
import com.nyangmeong.community.domain.CareMatchCondition;
import com.nyangmeong.community.domain.CareMatchStatusHistory;
import com.nyangmeong.community.domain.CareRecruitment;
import com.nyangmeong.community.domain.CareRecruitmentStatusHistory;
import com.nyangmeong.community.domain.ChatRoomDetail;
import com.nyangmeong.community.domain.ChatStatusHistory;
import com.nyangmeong.community.mapper.CareMatchMapper;
import com.nyangmeong.community.mapper.CareRecruitmentMapper;
import com.nyangmeong.community.mapper.ChatRoomMapper;
import com.nyangmeong.global.notification.EventPublisher;
import com.nyangmeong.global.notification.NotificationEvent;

/**
 * [검사 - 거래 취소 요청 무응답 자동 동의와 하루 전 알림 (배치)]
 * - 7일 무응답이면 매칭 완료·확인 대기만 직접 동의와 같은 결과로 취소, 잠근 뒤 그사이 처리된 건은 건너뜀
 * - 하루 전에는 요청을 받은 쪽에만 알림
 */
class CareMatchDealCancelAutoApproveTest {

    private static final Long GUARDIAN = 1L;
    private static final Long SITTER = 2L;
    private static final Long PST = 7L;
    private static final Long RCRT = 5L;
    private static final Long ROOM = 11L;
    private static final Long MATCH = 21L;
    private static final Long DEMAND = 41L;

    private static final String MATCHED = "PCAC0013";
    private static final String AWAITING_CONFIRM = "PCAC0014";
    private static final String CANCELED = "PCAC0017";
    private static final String RECRUITING_CD = "PCAC0001";
    private static final String MATCHED_CD = "PCAC0002";
    private static final String DEMANDING = "PCAC0021";
    private static final String APPROVED = "PCAC0022";
    private static final List<String> AUTO_STATUSES = List.of(MATCHED, AWAITING_CONFIRM);

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
    void stubCommon() {
        when(careMatchMapper.updateStatus(anyLong(), anyString(), anyString())).thenReturn(1);
        when(careRecruitmentMapper.updateStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(careMatchMapper.updateCancelDemandStatus(eq(DEMAND), eq(DEMANDING), anyString(), isNull()))
                .thenReturn(1);
        when(careMatchMapper.selectLatestCondition(MATCH)).thenReturn(condition());
        when(careRecruitmentMapper.selectForUpdate(RCRT)).thenReturn(recruitment());
        when(careMatchMapper.selectCancelDemandById(DEMAND)).thenReturn(demand(SITTER));
        when(chatRoomMapper.updateRoomStatus(ROOM, "CHTC0001", "CHTC0002")).thenReturn(1);
    }

    private ChatRoomDetail room(String sttsCd) {
        ChatRoomDetail detail = new ChatRoomDetail();
        detail.setOtoChatRoomSn(ROOM);
        detail.setPstSn(PST);
        detail.setCtptMbrSn(SITTER);
        detail.setOwnerMbrSn(GUARDIAN);
        detail.setPetPrtcRcrtSn(RCRT);
        detail.setPetPrtcMtchSn(MATCH);
        detail.setMtchDlngSttsCd(sttsCd);
        return detail;
    }

    private CareRecruitment recruitment() {
        CareRecruitment recruitment = new CareRecruitment();
        recruitment.setPetPrtcRcrtSn(RCRT);
        recruitment.setPstSn(PST);
        recruitment.setRcrtSttsCd(MATCHED_CD);
        return recruitment;
    }

    private CareMatchCondition condition() {
        CareMatchCondition condition = new CareMatchCondition();
        condition.setMtchCndVerSn(90L);
        condition.setPetPrtcMtchSn(MATCH);
        return condition;
    }

    private CareCancelDemand demand(Long dmndMbrSn) {
        CareCancelDemand demand = new CareCancelDemand();
        demand.setMtchCnclDmndSn(DEMAND);
        demand.setPetPrtcMtchSn(MATCH);
        demand.setDmndMbrSn(dmndMbrSn);
        demand.setCnclRsn("일정이 맞지 않게 됐어요");
        demand.setDmndSttsCd(DEMANDING);
        return demand;
    }

    private void stubMatch(String sttsCd) {
        CareMatch match = new CareMatch();
        match.setPetPrtcMtchSn(MATCH);
        match.setPetPrtcRcrtSn(RCRT);
        match.setOtoChatRoomSn(ROOM);
        match.setMtchDlngSttsCd(sttsCd);
        when(careMatchMapper.selectById(MATCH)).thenReturn(match);
        when(careMatchMapper.selectByIdForUpdate(MATCH)).thenReturn(match);
        when(chatRoomMapper.selectDetail(ROOM)).thenReturn(room(sttsCd));
    }

    private void stubRequestingAndDue(boolean due) {
        when(careMatchMapper.selectRequestingCancelDemandForUpdate(MATCH, DEMANDING)).thenReturn(demand(SITTER));
        when(careMatchMapper.existsCancelDemandDue(DEMAND, DEMANDING, AUTO_STATUSES, 7L, null)).thenReturn(due);
    }

    @Test
    void 자동_동의_대상은_매칭_완료와_확인_대기에서_7일_지난_요청중_요청이다() {
        when(careMatchMapper.selectCancelDemandDueTargets(DEMANDING, AUTO_STATUSES, 7L, null))
                .thenReturn(List.of(DEMAND));

        assertThat(service.findDealCancelAutoApproveTargets()).containsExactly(DEMAND);
        assertThat(CareMatchService.DEAL_CANCEL_AUTO_APPROVE_DAYS).isEqualTo(7L);
    }

    @Test
    void 확인_대기에서_자동_동의하면_직접_동의와_같이_취소되고_양쪽에_알린다() {
        stubMatch(AWAITING_CONFIRM);
        stubRequestingAndDue(true);
        when(chatRoomMapper.selectOtherRoomSnsByStatus(PST, ROOM, "CHTC0002")).thenReturn(List.of(12L));
        when(chatRoomMapper.updateRoomStatus(12L, "CHTC0002", "CHTC0001")).thenReturn(1);

        assertThat(service.autoApproveDealCancel(DEMAND)).isTrue();

        verify(careMatchMapper).updateCancelDemandStatus(DEMAND, DEMANDING, APPROVED, null);
        verify(careMatchMapper).updateStatus(MATCH, AWAITING_CONFIRM, CANCELED);
        ArgumentCaptor<CareMatchStatusHistory> history = ArgumentCaptor.forClass(CareMatchStatusHistory.class);
        verify(careMatchMapper).insertStatusHistory(history.capture());
        assertThat(history.getValue().getMbrSn()).isNull();
        assertThat(history.getValue().getChgRsn()).isEqualTo("일정이 맞지 않게 됐어요");
        verify(careRecruitmentMapper).updateStatus(RCRT, MATCHED_CD, RECRUITING_CD, null);
        ArgumentCaptor<CareRecruitmentStatusHistory> rcrtHistory = ArgumentCaptor
                .forClass(CareRecruitmentStatusHistory.class);
        verify(careRecruitmentMapper).insertStatusHistory(rcrtHistory.capture());
        assertThat(rcrtHistory.getValue().getChgRsn()).isEqualTo("거래 취소 요청 7일 무응답 자동 동의");
        verify(chatRoomMapper).updateRoomStatus(12L, "CHTC0002", "CHTC0001");
        verify(chatMessageService).createSystemMessage(12L, "모집이 다시 열렸어요.");
        verify(chatRoomMapper).updateRoomStatus(ROOM, "CHTC0001", "CHTC0002");
        ArgumentCaptor<ChatStatusHistory> roomHistory = ArgumentCaptor.forClass(ChatStatusHistory.class);
        verify(chatRoomMapper, times(2)).insertRoomStatusHistory(roomHistory.capture());
        assertThat(roomHistory.getAllValues()).allSatisfy(h -> assertThat(h.getMbrSn()).isNull());
        String message = "7일 동안 응답이 없어 거래 취소 요청에 자동 동의했어요. 거래가 취소되었어요.";
        verify(chatMessageService).createSystemMessage(ROOM, message);
        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, times(2)).publish(event.capture());
        assertThat(event.getAllValues()).extracting(NotificationEvent::mbrSn).containsExactlyInAnyOrder(GUARDIAN,
                SITTER);
    }

    @Test
    void 매칭_완료에서도_자동_동의한다() {
        stubMatch(MATCHED);
        stubRequestingAndDue(true);

        assertThat(service.autoApproveDealCancel(DEMAND)).isTrue();
        verify(careMatchMapper).updateStatus(MATCH, MATCHED, CANCELED);
    }

    @Test
    void 잠근_뒤_다시_보니_수령_대기이거나_기한이_안_됐으면_건너뛴다() {
        stubMatch("PCAC0015");
        stubRequestingAndDue(false);

        assertThat(service.autoApproveDealCancel(DEMAND)).isFalse();
        verify(careMatchMapper, never()).updateCancelDemandStatus(anyLong(), anyString(), anyString(), any());
        verify(careMatchMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(eventPublisher, never()).publish(any(NotificationEvent.class));
    }

    @Test
    void 그사이_요청이_처리됐으면_건너뛴다() {
        stubMatch(MATCHED);
        when(careMatchMapper.selectRequestingCancelDemandForUpdate(MATCH, DEMANDING)).thenReturn(null);

        assertThat(service.autoApproveDealCancel(DEMAND)).isFalse();

        CareCancelDemand newer = demand(GUARDIAN);
        newer.setMtchCnclDmndSn(99L);
        when(careMatchMapper.selectRequestingCancelDemandForUpdate(MATCH, DEMANDING)).thenReturn(newer);
        assertThat(service.autoApproveDealCancel(DEMAND)).isFalse();

        verify(careMatchMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(chatMessageService, never()).createSystemMessage(anyLong(), anyString());
    }

    @Test
    void 없는_요청은_건너뛴다() {
        assertThat(service.autoApproveDealCancel(999L)).isFalse();
        assertThat(service.remindDealCancel(999L)).isFalse();
    }

    @Test
    void 하루_전_알림_대상은_6일_이상_7일_미만이다() {
        when(careMatchMapper.selectCancelDemandDueTargets(DEMANDING, AUTO_STATUSES, 6L, 7L))
                .thenReturn(List.of(DEMAND));

        assertThat(service.findDealCancelReminderTargets()).containsExactly(DEMAND);
    }

    @Test
    void 하루_전_알림은_요청을_받은_쪽에만_보내고_시스템_메시지는_남기지_않는다() {
        stubMatch(MATCHED);
        when(careMatchMapper.existsCancelDemandDue(DEMAND, DEMANDING, AUTO_STATUSES, 6L, 7L)).thenReturn(true);

        assertThat(service.remindDealCancel(DEMAND)).isTrue();

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().mbrSn()).isEqualTo(GUARDIAN);
        verify(chatMessageService, never()).createSystemMessage(anyLong(), anyString());
        verify(careMatchMapper, never()).updateCancelDemandStatus(anyLong(), anyString(), anyString(), any());

        when(careMatchMapper.selectCancelDemandById(DEMAND)).thenReturn(demand(GUARDIAN));
        service.remindDealCancel(DEMAND);
        verify(eventPublisher, times(2)).publish(event.capture());
        assertThat(event.getValue().mbrSn()).isEqualTo(SITTER);
    }

    @Test
    void 그사이_처리된_요청은_하루_전_알림을_보내지_않는다() {
        stubMatch(MATCHED);
        when(careMatchMapper.existsCancelDemandDue(DEMAND, DEMANDING, AUTO_STATUSES, 6L, 7L)).thenReturn(false);

        assertThat(service.remindDealCancel(DEMAND)).isFalse();
        verify(eventPublisher, never()).publish(any(NotificationEvent.class));
    }
}
