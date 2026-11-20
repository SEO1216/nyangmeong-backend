package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nyangmeong.community.domain.Comment;
import com.nyangmeong.community.domain.Post;
import com.nyangmeong.community.dto.CommentCreateRequest;
import com.nyangmeong.community.dto.ChatMessageCreateRequest;
import com.nyangmeong.community.mapper.CareMatchMapper;
import com.nyangmeong.community.mapper.ChatMessageMapper;
import com.nyangmeong.community.mapper.ChatRoomMapper;
import com.nyangmeong.community.mapper.CommentMapper;
import com.nyangmeong.community.mapper.LostReportMapper;
import com.nyangmeong.community.mapper.PostMapper;
import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.security.crypto.FieldCryptoService;
import com.nyangmeong.global.validation.ProfanityFilter;
import com.nyangmeong.global.validation.ProfanityMapper;

/**
 * [검사 - 댓글·채팅 텍스트에 금칙어가 있으면 저장하지 않는다]
 */
class ProfanityGuardTest {

    private static final Long MBR = 1L;
    private static final Long PST = 7L;
    private static final Long ROOM = 5L;
    private static final String NORMAL = "CMUC0003";

    private final ProfanityMapper profanityMapper = mock(ProfanityMapper.class);
    private final ProfanityFilter filter = new ProfanityFilter(profanityMapper);

    private void stubWords() {
        when(profanityMapper.selectWords("AIMC0041")).thenReturn(List.of("시발"));
        when(profanityMapper.selectWords("AIMC0042")).thenReturn(List.of());
    }

    @Test
    void 댓글에_금칙어가_있으면_저장하지_않고_cmntCn_칸_오류() {
        stubWords();
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostMapper postMapper = mock(PostMapper.class);
        when(postMapper.selectPost(PST, NORMAL)).thenReturn(new Post());
        CommentService service = new CommentService(commentMapper, postMapper, mock(LostReportMapper.class),
                new OwnerAuthorizer(), mock(PetStoryMissionReflector.class), filter,
                mock(com.nyangmeong.global.notification.EventPublisher.class));

        assertThatThrownBy(() -> service.create(MBR, PST, new CommentCreateRequest("시 발 뭐야", null)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFANITY_DETECTED);
                    assertThat(e.getFields()).containsExactly("cmntCn");
                });
        verify(commentMapper, never()).insertComment(any(Comment.class));
    }

    @Test
    void 채팅_텍스트에_금칙어가_있으면_전송하지_않고_msgCn_칸_오류() {
        stubWords();
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        ChatMessageService service = new ChatMessageService(mock(ChatRoomMapper.class), chatMessageMapper,
                mock(CareMatchMapper.class), mock(ChatRoomGuard.class), mock(ChatRealtimePublisher.class),
                mock(FileStore.class), mock(FieldCryptoService.class), filter);
        ChatMessageCreateRequest req = new ChatMessageCreateRequest("CHTC0005", "시발", null, null, null, "t-1");

        assertThatThrownBy(() -> service.send(MBR, ROOM, req)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROFANITY_DETECTED);
            assertThat(e.getFields()).containsExactly("msgCn");
        });
        verify(chatMessageMapper, never()).insertMessage(any());
    }
}
