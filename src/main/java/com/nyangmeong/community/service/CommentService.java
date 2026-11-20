package com.nyangmeong.community.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyangmeong.community.domain.BoardType;
import com.nyangmeong.community.domain.Comment;
import com.nyangmeong.community.domain.LostReport;
import com.nyangmeong.community.domain.LostReportStatus;
import com.nyangmeong.community.domain.Post;
import com.nyangmeong.community.domain.PostStatus;
import com.nyangmeong.community.dto.CommentCreateRequest;
import com.nyangmeong.community.dto.CommentResponse;
import com.nyangmeong.community.dto.CommentSummary;
import com.nyangmeong.community.dto.PostAuthor;
import com.nyangmeong.community.mapper.CommentMapper;
import com.nyangmeong.community.mapper.LostReportMapper;
import com.nyangmeong.community.mapper.PostMapper;
import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.notification.EventPublisher;
import com.nyangmeong.global.notification.NotificationEvent;
import com.nyangmeong.global.notification.NotificationType;
import com.nyangmeong.global.validation.ProfanityFilter;
import com.nyangmeong.pet.domain.CareMission;

import lombok.RequiredArgsConstructor;

/**
 * [댓글 서비스]
 * - SCR-CMU-001 댓글·대댓글 목록·등록·삭제, 실종센터 상세(SCR-CMU-002)도 같이 사용
 * - 대댓글에 단 답글은 최상위 댓글에 붙여 1depth 유지
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final String COMMENT_TTL = "내 글에 댓글이 달렸어요";
    private static final String REPLY_TTL = "내 댓글에 답글이 달렸어요";
    private static final String POST_PATH = "/community/posts/%d";
    private static final String LOST_PATH = "/community/missing/%d";

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final LostReportMapper lostReportMapper;
    private final OwnerAuthorizer ownerAuthorizer;
    private final PetStoryMissionReflector missionReflector;
    private final ProfanityFilter profanityFilter;
    private final EventPublisher eventPublisher;

    /**
     * 댓글·대댓글 목록 조회
     */
    @Transactional(readOnly = true)
    public List<CommentSummary> search(Long mbrSn, Long pstSn) {
        Post post = checkPost(pstSn);
        /** 찾았어요 처리된 실종 글의 댓글은 작성자만 본다 */
        LostReport lostReport = lostReportMapper.selectByPstSn(pstSn);
        if (isFound(lostReport) && !mbrSn.equals(post.getMbrSn())) {
            return List.of();
        }
        return commentMapper.selectList(pstSn, mbrSn);
    }

    /**
     * 댓글·대댓글 등록
     */
    @Transactional
    public CommentResponse create(Long mbrSn, Long pstSn, CommentCreateRequest req) {
        profanityFilter.check("cmntCn", req.cmntCn());
        Post post = checkPost(pstSn);
        if (isFound(lostReportMapper.selectByPstSn(pstSn))) {
            throw new CustomException(ErrorCode.LOST_REPORT_CLOSED);
        }
        Long prntCmntSn = null;
        Long rplyTrgtCmntSn = null;
        String rplyTrgtMbrNcnm = null;
        Long replyReceiverMbrSn = null;
        if (req.prntCmntSn() != null) {
            Comment parent = commentMapper.selectComment(req.prntCmntSn());
            if (parent == null || !pstSn.equals(parent.getPstSn())) {
                throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
            }
            /** 대댓글에 단 답글은 원 댓글 스레드에 이어서 노출 */
            prntCmntSn = parent.getPrntCmntSn() != null ? parent.getPrntCmntSn() : parent.getCmntSn();
            /** 답한 댓글(대댓글에 단 답글이면 그 대댓글)의 작성자에게 알림 */
            replyReceiverMbrSn = parent.getMbrSn();
            /** 대댓글에 단 답글만 실제로 답한 대댓글을 남긴다 (「@닉네임」 표시) */
            if (parent.getPrntCmntSn() != null) {
                rplyTrgtCmntSn = parent.getCmntSn();
                rplyTrgtMbrNcnm = targetNickname(parent.getMbrSn());
            }
        }

        Comment comment = new Comment();
        comment.setPrntCmntSn(prntCmntSn);
        comment.setRplyTrgtCmntSn(rplyTrgtCmntSn);
        comment.setPstSn(pstSn);
        comment.setMbrSn(mbrSn);
        comment.setCmntCn(req.cmntCn());
        commentMapper.insertComment(comment);
        if (BoardType.exists(post.getBbsCd())) {
            missionReflector.reflectAfterCommit(mbrSn, CareMission.PET_STORY_COMMENT);
        }
        PostAuthor author = postMapper.selectAuthor(mbrSn);
        publishNotifications(mbrSn, post, replyReceiverMbrSn, author, req.cmntCn());
        return CommentResponse.from(commentMapper.selectComment(comment.getCmntSn()), author, rplyTrgtMbrNcnm);
    }

    /**
     * 댓글·답글 알림 발행 (글 작성자, 답글이면 답한 댓글 작성자. 본인에게는 보내지 않고 같은 사람은 1건)
     */
    private void publishNotifications(Long writerMbrSn, Post post, Long replyReceiverMbrSn, PostAuthor author,
            String cmntCn) {
        String content = (author != null && author.getMbrNcnm() != null ? author.getMbrNcnm() : "회원") + ": "
                + cmntCn;
        if (replyReceiverMbrSn != null && !replyReceiverMbrSn.equals(writerMbrSn)) {
            eventPublisher.publish(new NotificationEvent(replyReceiverMbrSn, NotificationType.COMMUNITY,
                    REPLY_TTL, content, movePath(post)));
        }
        Long postMbrSn = post.getMbrSn();
        if (postMbrSn != null && !postMbrSn.equals(writerMbrSn)
                && !postMbrSn.equals(replyReceiverMbrSn)) {
            eventPublisher.publish(new NotificationEvent(postMbrSn, NotificationType.COMMUNITY, COMMENT_TTL,
                    content, movePath(post)));
        }
    }

    /**
     * 알림을 누르면 이동할 글 상세 (실종 글은 실종센터 상세)
     */
    private String movePath(Post post) {
        if (BoardType.LOST.is(post.getBbsCd())) {
            LostReport lostReport = lostReportMapper.selectByPstSn(post.getPstSn());
            if (lostReport != null) {
                return String.format(LOST_PATH, lostReport.getLostTpofSn());
            }
        }
        return String.format(POST_PATH, post.getPstSn());
    }

    /**
     * 댓글 삭제 (답글은 유지)
     */
    @Transactional
    public void delete(Long mbrSn, Long pstSn, Long cmntSn) {
        Comment comment = commentMapper.selectComment(cmntSn);
        if (comment == null || !pstSn.equals(comment.getPstSn())) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        ownerAuthorizer.checkOwner(mbrSn, comment.getMbrSn());
        commentMapper.updateDeleted(cmntSn);
    }

    /**
     * 정상 상태 게시글 확인
     */
    private Post checkPost(Long pstSn) {
        Post post = postMapper.selectPost(pstSn, PostStatus.NORMAL.getCode());
        if (post == null) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /**
     * 답글 대상 작성자 표시 닉네임 (파기 회원은 「탈퇴한 회원」)
     */
    private String targetNickname(Long mbrSn) {
        PostAuthor author = postMapper.selectAuthor(mbrSn);
        return author != null ? author.getMbrNcnm() : null;
    }

    private boolean isFound(LostReport lostReport) {
        return lostReport != null && LostReportStatus.FOUND.is(lostReport.getLostTpofSttsCd());
    }
}
