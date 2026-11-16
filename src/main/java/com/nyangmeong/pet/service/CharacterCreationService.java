package com.nyangmeong.pet.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.external.CharacterSpec;
import com.nyangmeong.global.file.FileCategory;
import com.nyangmeong.global.file.FileExtension;
import com.nyangmeong.global.file.FileOwner;
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

import lombok.RequiredArgsConstructor;

/**
 * [캐릭터 생성 작업 상태 처리]
 * - 생성은 Python, Java 는 보고(reportSuccess·reportFailure)로 상태와 결과만 저장
 * - 무료 재생성은 관리자 쿠키로 Python 이 시작(regenStart)·원본 사진(getRegenPhoto)·보고 (FN-MYP-010-23)
 * - 완료 시 결과 저장 + 오류 확인 중 해제 + 캐릭터 알림 (FN-MYP-010-09·23)
 */
@Service
@RequiredArgsConstructor
public class CharacterCreationService {

    static final String SYSTEM = "SYSTEM";
    static final String MVMN_PATH = "/mypage/characters";
    private static final int FAIL_RSN_MAX = 1000;
    /** Python 제한 180초 × 2회 + 여유 */
    private static final int TIMEOUT_MINUTES = 10;

    private final PixelCharacterMapper pixelCharacterMapper;
    private final FileStore fileStore;
    private final EventPublisher eventPublisher;
    private final OwnerAuthorizer ownerAuthorizer;

    /**
     * 생성 성공 보고
     * - 이미 완료 -> 같은 번호, 시간 초과 등으로 실패한 뒤의 보고는 거절
     * - 관리자는 무료 재생성 작업만 보고한다. 결과는 작업의 캐릭터 주인 것으로 저장
     * - photo 가 있으면 원본 사진으로 남긴다 (무료 재생성 작업은 이미 원본이 있어 무시)
     */
    @Transactional
    public CharacterSuccessResponse reportSuccess(Long mbrSn, boolean admin, Long charCrtJobSn,
            CharacterSuccessRequest req) {
        CharacterCreationJob job = getReportableJob(mbrSn, admin, charCrtJobSn);
        PixelCharacter character = pixelCharacterMapper.selectByPetSn(job.getPetSn());
        PetProfileSummary pet = pixelCharacterMapper.selectPetProfile(job.getPetSn());
        if (character == null || pet == null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        if (CharacterJobStatus.COMPLETED.is(job.getCrtJobSttsCd())) {
            return new CharacterSuccessResponse(character.getPixelCharSn());
        }
        if (!CharacterJobStatus.CREATING.is(job.getCrtJobSttsCd())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        }
        byte[] image = CharacterSheetService.readBytes(req.image());
        if (!CharacterSpec.matches(image)) {
            throw new CustomException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        byte[] sheet = CharacterSheetService.readSheet(req.sheet());
        saveOriginalPhoto(job, character, req.photo());

        saveResult(charCrtJobSn, character, pet, image);
        Long sheetFileSn = fileStore.store(sheet, FileExtension.PNG, "sheet-" + character.getPixelCharSn() + ".png",
                FileCategory.PET_SHEET, FileOwner.pet(character.getMbrSn(), character.getPetSn()), SYSTEM);
        pixelCharacterMapper.updateBodySheet(character.getPixelCharSn(), sheetFileSn, req.scale(), req.originX(),
                req.originY(), SYSTEM);
        pixelCharacterMapper.updateItemSheetDelete(character.getPixelCharSn());
        pixelCharacterMapper.updateWearImage(character.getPixelCharSn(), null, SYSTEM);
        return new CharacterSuccessResponse(character.getPixelCharSn());
    }

    /**
     * 생성 실패 보고
     */
    @Transactional
    public void reportFailure(Long mbrSn, boolean admin, Long charCrtJobSn, String failRsn) {
        getReportableJob(mbrSn, admin, charCrtJobSn);
        fail(charCrtJobSn, failRsn);
    }

    /**
     * 무료 재생성 시작 (관리자 쿠키로 Python 이 호출)
     * - 요청 상태인 재생성 작업만 생성 중으로 바꾸고 생성 입력을 준다
     */
    @Transactional
    public CharacterJobResponse regenStart(boolean admin, Long charCrtJobSn) {
        checkAdmin(admin);
        CharacterCreationJob job = pixelCharacterMapper.selectJob(charCrtJobSn);
        if (job == null || !job.isRegeneration()) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        PetProfileSummary pet = pixelCharacterMapper.selectPetProfile(job.getPetSn());
        if (pet == null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        if (!CharacterJobStatus.REQUESTED.is(job.getCrtJobSttsCd())
                || pixelCharacterMapper.updateJobStarted(charCrtJobSn, CharacterJobStatus.REQUESTED.getCode(),
                        CharacterJobStatus.CREATING.getCode()) == 0) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        }
        insertHistory(charCrtJobSn, CharacterJobStatus.REQUESTED, CharacterJobStatus.CREATING, null);
        return CharacterJobResponse.of(charCrtJobSn, pet);
    }

    /**
     * 무료 재생성 원본 사진 (생성 중인 재생성 작업만)
     */
    @Transactional(readOnly = true)
    public StoredFile getRegenPhoto(boolean admin, Long charCrtJobSn) {
        checkAdmin(admin);
        CharacterCreationJob job = pixelCharacterMapper.selectJob(charCrtJobSn);
        if (job == null || !job.isRegeneration() || !CharacterJobStatus.CREATING.is(job.getCrtJobSttsCd())) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        CharacterPhoto original = pixelCharacterMapper.selectJobPhoto(charCrtJobSn, PhotoRole.ORIGINAL.getCode());
        if (original == null) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileStore.load(original.getAtchFileSn());
    }

    /**
     * 시간 초과 작업 조회
     * - 시작 뒤 제한 시간을 넘긴 생성 중 작업 + 등록 뒤 제한 시간 동안 시작하지 않은 요청 작업(무료 재생성)
     */
    @Transactional(readOnly = true)
    public List<Long> searchTimedOutJobs() {
        List<Long> jobs = new ArrayList<>(
                pixelCharacterMapper.selectTimedOutJobs(CharacterJobStatus.CREATING.getCode(), TIMEOUT_MINUTES));
        jobs.addAll(pixelCharacterMapper.selectUnstartedJobs(CharacterJobStatus.REQUESTED.getCode(), TIMEOUT_MINUTES));
        return jobs;
    }

    /**
     * 원본 사진 저장 (회원 첫 생성 보고에만)
     */
    private void saveOriginalPhoto(CharacterCreationJob job, PixelCharacter character, MultipartFile photo) {
        if (photo == null || photo.isEmpty() || job.isRegeneration()
                || pixelCharacterMapper.selectJobPhoto(job.getCharCrtJobSn(), PhotoRole.ORIGINAL.getCode()) != null) {
            return;
        }
        Long atchFileSn = fileStore.store(photo, FileCategory.PET_ORIGINAL,
                FileOwner.pet(character.getMbrSn(), character.getPetSn()), SYSTEM);
        CharacterPhoto original = new CharacterPhoto();
        original.setPixelCharSn(character.getPixelCharSn());
        original.setCharCrtJobSn(job.getCharCrtJobSn());
        original.setAtchFileSn(atchFileSn);
        original.setPhotoRoleCd(PhotoRole.ORIGINAL.getCode());
        original.setSortSeq(0);
        original.setNowIndctYn("N");
        pixelCharacterMapper.insertPhoto(original);
    }

    /**
     * 결과 저장·완료 처리·알림
     */
    private void saveResult(Long charCrtJobSn, PixelCharacter character, PetProfileSummary pet, byte[] image) {
        Long atchFileSn = fileStore.store(image, FileExtension.PNG,
                "character-" + character.getPixelCharSn() + ".png", FileCategory.PET_CHARACTER,
                FileOwner.pet(character.getMbrSn(), character.getPetSn()), SYSTEM);
        pixelCharacterMapper.updatePhotoHidden(character.getPixelCharSn());
        CharacterPhoto result = new CharacterPhoto();
        result.setPixelCharSn(character.getPixelCharSn());
        result.setCharCrtJobSn(charCrtJobSn);
        result.setAtchFileSn(atchFileSn);
        result.setPhotoRoleCd(PhotoRole.RESULT.getCode());
        result.setSortSeq(0);
        result.setNowIndctYn("Y");
        pixelCharacterMapper.insertPhoto(result);

        pixelCharacterMapper.updateJobFinished(charCrtJobSn, List.of(CharacterJobStatus.CREATING.getCode()),
                CharacterJobStatus.COMPLETED.getCode(), null);
        insertHistory(charCrtJobSn, CharacterJobStatus.CREATING, CharacterJobStatus.COMPLETED, null);
        pixelCharacterMapper.updateBscCharSbstYn(character.getPixelCharSn(), "N", SYSTEM);

        eventPublisher.publish(new NotificationEvent(character.getMbrSn(), NotificationType.CHARACTER,
                pet.getPetNm() + "의 캐릭터가 완성됐어요", null, MVMN_PATH));
    }

    /**
     * 생성 실패
     */
    @Transactional
    public void fail(Long charCrtJobSn, String reason) {
        CharacterCreationJob job = pixelCharacterMapper.selectJob(charCrtJobSn);
        if (job == null || !CharacterJobStatus.isInProgress(job.getCrtJobSttsCd())) {
            return;
        }
        String failRsn = truncate(reason);
        int updated = pixelCharacterMapper.updateJobFinished(charCrtJobSn,
                List.of(CharacterJobStatus.REQUESTED.getCode(), CharacterJobStatus.CREATING.getCode()),
                CharacterJobStatus.FAILED.getCode(), failRsn);
        if (updated > 0) {
            CharacterJobStatus before = CharacterJobStatus.REQUESTED.is(job.getCrtJobSttsCd())
                    ? CharacterJobStatus.REQUESTED
                    : CharacterJobStatus.CREATING;
            insertHistory(charCrtJobSn, before, CharacterJobStatus.FAILED, failRsn);
        }
    }

    /**
     * 보고할 수 있는 작업 조회 : 작업을 만든 회원 본인, 또는 관리자이면서 무료 재생성 작업
     */
    private CharacterCreationJob getReportableJob(Long mbrSn, boolean admin, Long charCrtJobSn) {
        CharacterCreationJob job = pixelCharacterMapper.selectJob(charCrtJobSn);
        if (job == null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        if (!(admin && job.isRegeneration())) {
            ownerAuthorizer.checkOwner(mbrSn, job.getMbrSn());
        }
        return job;
    }

    /**
     * 관리자 검사
     */
    private void checkAdmin(boolean admin) {
        if (!admin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 상태 이력 등록
     */
    private void insertHistory(Long charCrtJobSn, CharacterJobStatus before, CharacterJobStatus after, String reason) {
        CharacterJobStatusHistory history = new CharacterJobStatusHistory();
        history.setCharCrtJobSn(charCrtJobSn);
        history.setBfrSttsCd(before.getCode());
        history.setAftrSttsCd(after.getCode());
        history.setChgRsn(reason);
        pixelCharacterMapper.insertJobStatusHistory(history);
    }

    /**
     * 실패 사유 길이 제한
     */
    private String truncate(String reason) {
        String value = reason == null || reason.isBlank() ? "알 수 없는 오류" : reason;
        return value.length() > FAIL_RSN_MAX ? value.substring(0, FAIL_RSN_MAX) : value;
    }
}
