package com.nyangmeong.pet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.external.CharacterSpec;
import com.nyangmeong.global.file.FileCategory;
import com.nyangmeong.global.file.FileExtension;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.file.StoredFile;
import com.nyangmeong.global.notification.EventPublisher;
import com.nyangmeong.global.notification.NotificationEvent;
import com.nyangmeong.global.notification.NotificationType;
import com.nyangmeong.pet.domain.CharacterCreationJob;
import com.nyangmeong.pet.domain.CharacterJobStatus;
import com.nyangmeong.pet.domain.CharacterJobStatusHistory;
import com.nyangmeong.pet.domain.CharacterPhoto;
import com.nyangmeong.pet.domain.PhotoRole;
import com.nyangmeong.pet.domain.PixelCharacter;
import com.nyangmeong.pet.dto.CharacterJobResponse;
import com.nyangmeong.pet.dto.CharacterSuccessRequest;
import com.nyangmeong.pet.dto.CharacterSuccessResponse;
import com.nyangmeong.pet.dto.PetProfileSummary;
import com.nyangmeong.pet.mapper.PixelCharacterMapper;

/**
 * [검사 - 캐릭터 생성 작업 시작·완료·실패]
 */
class CharacterCreationServiceTest {

    private final PixelCharacterMapper mapper = mock(PixelCharacterMapper.class);
    private final FileStore fileStore = mock(FileStore.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final OwnerAuthorizer ownerAuthorizer = mock(OwnerAuthorizer.class);
    private final CharacterCreationService service = new CharacterCreationService(mapper, fileStore, eventPublisher,
            ownerAuthorizer);

    private static byte[] png(int size) {
        return CharacterSpec.toPng(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }

    private CharacterSuccessRequest success(byte[] image, byte[] sheet) {
        return new CharacterSuccessRequest(new MockMultipartFile("image", image),
                new MockMultipartFile("sheet", sheet), 1.5, 2.0, 3.0, null);
    }

    private PixelCharacter pixelCharacter() {
        PixelCharacter character = new PixelCharacter();
        character.setPixelCharSn(3L);
        character.setPetSn(10L);
        character.setMbrSn(1L);
        return character;
    }

    private void givenReport(CharacterJobStatus status) {
        when(mapper.selectJob(5L)).thenReturn(job(status));
        when(mapper.selectByPetSn(10L)).thenReturn(pixelCharacter());
        when(mapper.selectPetProfile(10L)).thenReturn(pet());
    }

    @Test
    void 성공_보고는_결과와_맨몸_시트를_저장하고_이전_착용_시트를_지우고_알림을_보낸다() {
        givenReport(CharacterJobStatus.CREATING);
        when(fileStore.store(any(byte[].class), eq(FileExtension.PNG), anyString(), eq(FileCategory.PET_CHARACTER), any(), anyString())).thenReturn(30L);
        when(fileStore.store(any(byte[].class), eq(FileExtension.PNG), anyString(), eq(FileCategory.PET_SHEET), any(), anyString())).thenReturn(31L);

        CharacterSuccessResponse res = service.reportSuccess(1L, false, 5L, success(png(512), png(1024)));

        assertThat(res.pixelCharSn()).isEqualTo(3L);
        verify(ownerAuthorizer).checkOwner(1L, 1L);
        verify(mapper).updateBodySheet(3L, 31L, 1.5, 2.0, 3.0, "SYSTEM");
        verify(mapper).updateItemSheetDelete(3L);
        verify(mapper).updateJobFinished(5L, List.of(CharacterJobStatus.CREATING.getCode()),
                CharacterJobStatus.COMPLETED.getCode(), null);
        verify(eventPublisher).publish(any(NotificationEvent.class));
    }

    @Test
    void 이미_완료된_작업의_성공_보고는_저장_없이_같은_번호를_준다() {
        givenReport(CharacterJobStatus.COMPLETED);

        CharacterSuccessResponse res = service.reportSuccess(1L, false, 5L, success(png(512), png(1024)));

        assertThat(res.pixelCharSn()).isEqualTo(3L);
        verify(fileStore, never()).store(any(byte[].class), any(), anyString(), eq(FileCategory.PET_CHARACTER), any(), anyString());
        verify(eventPublisher, never()).publish(any(NotificationEvent.class));
    }

    @Test
    void 실패로_끝난_작업의_성공_보고는_ALREADY_PROCESSED() {
        givenReport(CharacterJobStatus.FAILED);

        assertThatThrownBy(() -> service.reportSuccess(1L, false, 5L, success(png(512), png(1024))))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED);
    }

    @Test
    void 규격_밖_이미지나_시트는_FILE_TYPE_NOT_ALLOWED() {
        givenReport(CharacterJobStatus.CREATING);

        assertThatThrownBy(() -> service.reportSuccess(1L, false, 5L, success(png(100), png(1024))))
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertThatThrownBy(() -> service.reportSuccess(1L, false, 5L, success(png(512), png(512))))
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        verify(fileStore, never()).store(any(byte[].class), any(), anyString(), eq(FileCategory.PET_CHARACTER), any(), anyString());
    }

    @Test
    void 남의_작업은_보고할_수_없다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.CREATING));
        doThrow(new CustomException(ErrorCode.NOT_OWNER)).when(ownerAuthorizer).checkOwner(2L, 1L);

        assertThatThrownBy(() -> service.reportSuccess(2L, false, 5L, success(png(512), png(1024))))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_OWNER);
        assertThatThrownBy(() -> service.reportFailure(2L, false, 5L, "CHARACTER_API_ERROR"))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_OWNER);
        verify(mapper, never()).updateJobFinished(anyLong(), anyList(), anyString(), any());
    }

    @Test
    void 없는_작업은_PIXEL_CHARACTER_NOT_FOUND() {
        assertThatThrownBy(() -> service.reportFailure(1L, false, 5L, "CHARACTER_API_ERROR"))
                .extracting("errorCode").isEqualTo(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
    }

    @Test
    void 실패_보고는_진행_중_작업만_실패로_바꾸고_이력을_남긴다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.CREATING));
        when(mapper.updateJobFinished(eq(5L), anyList(), eq(CharacterJobStatus.FAILED.getCode()),
                eq("CHARACTER_API_ERROR"))).thenReturn(1);

        service.reportFailure(1L, false, 5L, "CHARACTER_API_ERROR");

        ArgumentCaptor<CharacterJobStatusHistory> history = ArgumentCaptor.forClass(CharacterJobStatusHistory.class);
        verify(mapper).insertJobStatusHistory(history.capture());
        assertThat(history.getValue().getAftrSttsCd()).isEqualTo(CharacterJobStatus.FAILED.getCode());
    }

    @Test
    void 끝난_작업의_실패_보고는_그대로_둔다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.COMPLETED));

        service.reportFailure(1L, false, 5L, "CHARACTER_API_ERROR");

        verify(mapper, never()).updateJobFinished(anyLong(), anyList(), anyString(), any());
    }

    @Test
    void 시간_초과_대상은_생성_중_작업을_10분_기준으로_찾는다() {
        when(mapper.selectTimedOutJobs(CharacterJobStatus.CREATING.getCode(), 10)).thenReturn(List.of(5L));

        assertThat(service.searchTimedOutJobs()).containsExactly(5L);
    }

    @Test
    void 시간_초과_대상에는_10분_동안_시작하지_않은_요청_작업도_들어간다() {
        when(mapper.selectTimedOutJobs(CharacterJobStatus.CREATING.getCode(), 10)).thenReturn(List.of(5L));
        when(mapper.selectUnstartedJobs(CharacterJobStatus.REQUESTED.getCode(), 10)).thenReturn(List.of(6L));

        assertThat(service.searchTimedOutJobs()).containsExactly(5L, 6L);
    }

    @Test
    void 성공_보고에_원본_사진이_있으면_원본으로_남긴다() {
        givenReport(CharacterJobStatus.CREATING);
        MockMultipartFile photo = new MockMultipartFile("photo", "kongi.jpg", "image/jpeg", new byte[] {9});
        when(fileStore.store(eq(photo), eq(FileCategory.PET_ORIGINAL), any(), eq("SYSTEM"))).thenReturn(40L);

        service.reportSuccess(1L, false, 5L, success(png(512), png(1024), photo));

        ArgumentCaptor<CharacterPhoto> photos = ArgumentCaptor.forClass(CharacterPhoto.class);
        verify(mapper, times(2)).insertPhoto(photos.capture());
        CharacterPhoto original = photos.getAllValues().get(0);
        assertThat(original.getAtchFileSn()).isEqualTo(40L);
        assertThat(original.getCharCrtJobSn()).isEqualTo(5L);
        assertThat(original.getPhotoRoleCd()).isEqualTo(PhotoRole.ORIGINAL.getCode());
        assertThat(original.getNowIndctYn()).isEqualTo("N");
    }

    @Test
    void 원본_사진이_없으면_원본을_남기지_않는다() {
        givenReport(CharacterJobStatus.CREATING);

        service.reportSuccess(1L, false, 5L, success(png(512), png(1024)));

        verify(fileStore, never()).store(any(MultipartFile.class), any(), any(), anyString());
    }

    @Test
    void 관리자는_무료_재생성_작업을_주인_확인_없이_보고하고_원본은_다시_남기지_않는다() {
        givenReport(CharacterJobStatus.CREATING);
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.CREATING));
        MockMultipartFile photo = new MockMultipartFile("photo", "kongi.jpg", "image/jpeg", new byte[] {9});

        CharacterSuccessResponse res = service.reportSuccess(99L, true, 5L, success(png(512), png(1024), photo));

        assertThat(res.pixelCharSn()).isEqualTo(3L);
        verify(ownerAuthorizer, never()).checkOwner(anyLong(), anyLong());
        verify(fileStore, never()).store(any(MultipartFile.class), any(), any(), anyString());
        verify(mapper).updateJobFinished(5L, List.of(CharacterJobStatus.CREATING.getCode()),
                CharacterJobStatus.COMPLETED.getCode(), null);
        verify(mapper).updateBscCharSbstYn(3L, "N", "SYSTEM");
    }

    @Test
    void 관리자도_첫_생성_작업은_주인_확인을_거친다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.CREATING));
        doThrow(new CustomException(ErrorCode.NOT_OWNER)).when(ownerAuthorizer).checkOwner(99L, 1L);

        assertThatThrownBy(() -> service.reportFailure(99L, true, 5L, "CHARACTER_API_ERROR"))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 관리자는_무료_재생성_실패를_보고한다() {
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.CREATING));
        when(mapper.updateJobFinished(eq(5L), anyList(), eq(CharacterJobStatus.FAILED.getCode()), anyString()))
                .thenReturn(1);

        service.reportFailure(99L, true, 5L, "CHARACTER_API_ERROR");

        verify(ownerAuthorizer, never()).checkOwner(anyLong(), anyLong());
        verify(mapper).updateJobFinished(eq(5L), anyList(), eq(CharacterJobStatus.FAILED.getCode()),
                eq("CHARACTER_API_ERROR"));
    }

    @Test
    void 재생성_시작은_요청_작업을_생성_중으로_바꾸고_생성_입력을_준다() {
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.REQUESTED));
        when(mapper.selectPetProfile(10L)).thenReturn(pet());
        when(mapper.updateJobStarted(5L, CharacterJobStatus.REQUESTED.getCode(), CharacterJobStatus.CREATING.getCode()))
                .thenReturn(1);

        CharacterJobResponse res = service.regenStart(true, 5L);

        assertThat(res.charCrtJobSn()).isEqualTo(5L);
        assertThat(res.petSeCd()).isEqualTo("PETC0002");
        assertThat(res.vrtyNm()).isEqualTo("코리안숏헤어");
        assertThat(res.colrNm()).isEqualTo("고등어");
        assertThat(res.chrcCn()).isEqualTo("오른쪽 귀 한쪽만 접힘");
        ArgumentCaptor<CharacterJobStatusHistory> history = ArgumentCaptor.forClass(CharacterJobStatusHistory.class);
        verify(mapper).insertJobStatusHistory(history.capture());
        assertThat(history.getValue().getBfrSttsCd()).isEqualTo(CharacterJobStatus.REQUESTED.getCode());
        assertThat(history.getValue().getAftrSttsCd()).isEqualTo(CharacterJobStatus.CREATING.getCode());
    }

    @Test
    void 재생성_시작은_관리자만() {
        assertThatThrownBy(() -> service.regenStart(false, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(mapper, never()).updateJobStarted(anyLong(), anyString(), anyString());
    }

    @Test
    void 이미_시작한_재생성은_ALREADY_PROCESSED() {
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.CREATING));
        when(mapper.selectPetProfile(10L)).thenReturn(pet());

        assertThatThrownBy(() -> service.regenStart(true, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verify(mapper, never()).updateJobStarted(anyLong(), anyString(), anyString());
    }

    @Test
    void 동시에_시작해_상태가_먼저_바뀌면_ALREADY_PROCESSED() {
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.REQUESTED));
        when(mapper.selectPetProfile(10L)).thenReturn(pet());

        assertThatThrownBy(() -> service.regenStart(true, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verify(mapper, never()).insertJobStatusHistory(any());
    }

    @Test
    void 재생성이_아니거나_없는_작업의_시작은_PIXEL_CHARACTER_NOT_FOUND() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.REQUESTED));

        assertThatThrownBy(() -> service.regenStart(true, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        assertThatThrownBy(() -> service.regenStart(true, 6L))
                .extracting("errorCode").isEqualTo(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
    }

    @Test
    void 생성_중인_재생성_작업의_원본_사진을_준다() {
        CharacterPhoto original = new CharacterPhoto();
        original.setAtchFileSn(20L);
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.CREATING));
        when(mapper.selectJobPhoto(5L, PhotoRole.ORIGINAL.getCode())).thenReturn(original);
        when(fileStore.load(20L)).thenReturn(new StoredFile(new byte[] {7}, "image/jpeg", "kongi.jpg"));

        StoredFile file = service.getRegenPhoto(true, 5L);

        assertThat(file.content()).containsExactly(7);
        assertThat(file.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void 원본_사진은_관리자만() {
        assertThatThrownBy(() -> service.getRegenPhoto(false, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 시작_전이거나_재생성이_아니거나_원본이_없으면_FILE_NOT_FOUND() {
        when(mapper.selectJob(5L)).thenReturn(regenJob(CharacterJobStatus.REQUESTED));
        when(mapper.selectJob(6L)).thenReturn(job(CharacterJobStatus.CREATING));
        CharacterCreationJob noPhoto = regenJob(CharacterJobStatus.CREATING);
        noPhoto.setCharCrtJobSn(7L);
        when(mapper.selectJob(7L)).thenReturn(noPhoto);

        for (Long sn : List.of(5L, 6L, 7L, 8L)) {
            assertThatThrownBy(() -> service.getRegenPhoto(true, sn))
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_NOT_FOUND);
        }
        verify(fileStore, never()).load(anyLong());
    }

    private CharacterSuccessRequest success(byte[] image, byte[] sheet, MultipartFile photo) {
        return new CharacterSuccessRequest(new MockMultipartFile("image", image),
                new MockMultipartFile("sheet", sheet), 1.5, 2.0, 3.0, photo);
    }

    private CharacterCreationJob regenJob(CharacterJobStatus status) {
        CharacterCreationJob job = job(status);
        job.setIdmptDmndId(CharacterCreationJob.REGEN_ID_PREFIX + "x");
        return job;
    }

    private CharacterCreationJob job(CharacterJobStatus status) {
        CharacterCreationJob job = new CharacterCreationJob();
        job.setCharCrtJobSn(5L);
        job.setPetSn(10L);
        job.setMbrSn(1L);
        job.setCrtJobSttsCd(status.getCode());
        return job;
    }

    private PetProfileSummary pet() {
        PetProfileSummary pet = new PetProfileSummary();
        pet.setPetSn(10L);
        pet.setPetNm("콩이");
        pet.setPetSeCd("PETC0002");
        pet.setVrtyNm("코리안숏헤어");
        pet.setColrNm("고등어");
        pet.setChrcCn("오른쪽 귀 한쪽만 접힘");
        return pet;
    }

    private PixelCharacter character() {
        PixelCharacter character = new PixelCharacter();
        character.setPixelCharSn(3L);
        character.setPetSn(10L);
        character.setMbrSn(1L);
        character.setBscCharSbstYn("N");
        return character;
    }

    @Test
    void 실패하면_실패_사유를_남기고_알림은_없다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.CREATING));
        when(mapper.updateJobFinished(eq(5L), anyList(), eq(CharacterJobStatus.FAILED.getCode()), anyString()))
                .thenReturn(1);

        service.fail(5L, "외부 API 장애");

        verify(mapper).updateJobFinished(5L,
                List.of(CharacterJobStatus.REQUESTED.getCode(), CharacterJobStatus.CREATING.getCode()),
                CharacterJobStatus.FAILED.getCode(), "외부 API 장애");
        ArgumentCaptor<CharacterJobStatusHistory> history = ArgumentCaptor.forClass(CharacterJobStatusHistory.class);
        verify(mapper).insertJobStatusHistory(history.capture());
        assertThat(history.getValue().getAftrSttsCd()).isEqualTo(CharacterJobStatus.FAILED.getCode());
        assertThat(history.getValue().getChgRsn()).isEqualTo("외부 API 장애");
        verify(eventPublisher, never()).publish(any(NotificationEvent.class));
    }

    @Test
    void 끝난_작업은_실패로_바꾸지_않는다() {
        when(mapper.selectJob(5L)).thenReturn(job(CharacterJobStatus.COMPLETED));

        service.fail(5L, "늦은 오류");

        verify(mapper, never()).updateJobFinished(anyLong(), anyList(), anyString(), anyString());
    }
}
