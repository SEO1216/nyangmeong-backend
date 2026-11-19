package com.nyangmeong.community.dto;

import java.time.LocalDateTime;

import com.nyangmeong.community.domain.Comment;

/**
 * [댓글 등록 응답]
 */
public record CommentResponse(
        Long cmntSn,
        Long pstSn,
        Long prntCmntSn,
        Long rplyTrgtCmntSn,
        String rplyTrgtMbrNcnm,
        String cmntCn,
        Long mbrSn,
        String mbrNcnm,
        Long mbrAtchFileSn,
        LocalDateTime regDt) {

    /**
     * 답글 대상 없는 응답 (대상 필드 추가 전 생성자 유지)
     */
    public CommentResponse(Long cmntSn, Long pstSn, Long prntCmntSn, String cmntCn, Long mbrSn, String mbrNcnm,
            Long mbrAtchFileSn, LocalDateTime regDt) {
        this(cmntSn, pstSn, prntCmntSn, null, null, cmntCn, mbrSn, mbrNcnm, mbrAtchFileSn, regDt);
    }

    public static CommentResponse from(Comment comment, PostAuthor author, String rplyTrgtMbrNcnm) {
        return new CommentResponse(comment.getCmntSn(), comment.getPstSn(), comment.getPrntCmntSn(),
                comment.getRplyTrgtCmntSn(), rplyTrgtMbrNcnm, comment.getCmntCn(), comment.getMbrSn(), author.getMbrNcnm(), author.getMbrAtchFileSn(),
                comment.getRegDt());
    }
}
