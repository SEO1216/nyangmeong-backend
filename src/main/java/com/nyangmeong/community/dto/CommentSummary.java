package com.nyangmeong.community.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [댓글 목록 항목]
 * - 삭제된 댓글은 delYn=true, 내용·작성자 없음 (답글은 유지)
 * - 대댓글에 단 답글은 rplyTrgtCmntSn·rplyTrgtMbrNcnm으로 「@닉네임」 표시
 */
@Data
public class CommentSummary {
    private Long cmntSn;
    private Long prntCmntSn;
    private Long rplyTrgtCmntSn;
    private String rplyTrgtMbrNcnm;
    private boolean delYn;
    private String cmntCn;
    private Long mbrSn;
    private String mbrNcnm;
    private Long mbrAtchFileSn;
    private boolean sysAutoCrtYn;
    private Boolean owner;
    private LocalDateTime regDt;
}
