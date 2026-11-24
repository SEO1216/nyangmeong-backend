package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyangmeong.community.domain.Comment;
import com.nyangmeong.community.domain.Post;
import com.nyangmeong.community.dto.CommentCreateRequest;
import com.nyangmeong.community.dto.CommentResponse;
import com.nyangmeong.community.dto.PostAuthor;
import com.nyangmeong.community.mapper.CommentMapper;
import com.nyangmeong.community.mapper.LostReportMapper;
import com.nyangmeong.community.mapper.PostMapper;
import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.validation.ProfanityFilter;

/**
 * [검사 - 답글 대상 「@닉네임」 저장]
 */
class CommentReplyTargetTest {

    private static final Long MBR = 1L;
    private static final Long OTHER = 2L;
    private static final Long PST = 7L;
    private static final Long NEW_CMNT = 99L;
    private static final String NORMAL = "CMUC0003";

    private final CommentMapper commentMapper = mock(CommentMapper.class);
    private final PostMapper postMapper = mock(PostMapper.class);
    private final CommentService service = new CommentService(commentMapper, postMapper,
            mock(LostReportMapper.class), new OwnerAuthorizer(), mock(PetStoryMissionReflector.class),
            mock(ProfanityFilter.class), mock(com.nyangmeong.global.notification.EventPublisher.class));

    private Comment comment(Long cmntSn, Long prntCmntSn, Long mbrSn) {
        Comment comment = new Comment();
        comment.setCmntSn(cmntSn);
        comment.setPrntCmntSn(prntCmntSn);
        comment.setPstSn(PST);
        comment.setMbrSn(mbrSn);
        comment.setCmntCn("댓글");
        comment.setRegDt(LocalDateTime.of(2026, 11, 18, 9, 0));
        return comment;
    }

    private PostAuthor author(String ncnm) {
        PostAuthor author = new PostAuthor();
        author.setMbrNcnm(ncnm);
        return author;
    }

    /** 등록한 댓글을 다시 조회하면 저장한 값 그대로 돌려주도록 준비 */
    private void stubInsert() {
        when(postMapper.selectPost(PST, NORMAL)).thenReturn(new Post());
        when(postMapper.selectAuthor(MBR)).thenReturn(author("나"));
        when(postMapper.selectAuthor(OTHER)).thenReturn(author("상대"));
        doAnswer(invocation -> {
            Comment arg = invocation.getArgument(0);
            arg.setCmntSn(NEW_CMNT);
            Comment saved = comment(NEW_CMNT, arg.getPrntCmntSn(), MBR);
            saved.setRplyTrgtCmntSn(arg.getRplyTrgtCmntSn());
            when(commentMapper.selectComment(NEW_CMNT)).thenReturn(saved);
            return 1;
        }).when(commentMapper).insertComment(any(Comment.class));
    }

    private Comment saved() {
        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insertComment(saved.capture());
        return saved.getValue();
    }

    @Test
    void 대댓글에_답글을_달면_부모는_최상위_댓글_대상은_고른_대댓글() {
        stubInsert();
        when(commentMapper.selectComment(20L)).thenReturn(comment(20L, 10L, OTHER));

        CommentResponse res = service.create(MBR, PST, new CommentCreateRequest("답글의 답글", 20L));

        assertThat(saved().getPrntCmntSn()).isEqualTo(10L);
        assertThat(saved().getRplyTrgtCmntSn()).isEqualTo(20L);
        assertThat(res.prntCmntSn()).isEqualTo(10L);
        assertThat(res.rplyTrgtCmntSn()).isEqualTo(20L);
        assertThat(res.rplyTrgtMbrNcnm()).isEqualTo("상대");
        assertThat(res.mbrNcnm()).isEqualTo("나");
    }

    @Test
    void 최상위_댓글에_바로_단_답글은_대상이_없다() {
        stubInsert();
        when(commentMapper.selectComment(10L)).thenReturn(comment(10L, null, OTHER));

        CommentResponse res = service.create(MBR, PST, new CommentCreateRequest("답글", 10L));

        assertThat(saved().getPrntCmntSn()).isEqualTo(10L);
        assertThat(saved().getRplyTrgtCmntSn()).isNull();
        assertThat(res.rplyTrgtCmntSn()).isNull();
        assertThat(res.rplyTrgtMbrNcnm()).isNull();
    }

    @Test
    void 일반_댓글은_대상이_없다() {
        stubInsert();

        CommentResponse res = service.create(MBR, PST, new CommentCreateRequest("댓글", null));

        assertThat(saved().getRplyTrgtCmntSn()).isNull();
        assertThat(res.rplyTrgtMbrNcnm()).isNull();
    }
}
