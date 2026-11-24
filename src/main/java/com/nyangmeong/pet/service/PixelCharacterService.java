package com.nyangmeong.pet.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyangmeong.global.auth.PetAuthorizer;
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
import com.nyangmeong.pet.domain.CharacterDefaultImage;
import com.nyangmeong.pet.domain.CharacterImagePart;
import com.nyangmeong.pet.domain.CharacterJobStatus;
import com.nyangmeong.pet.domain.CharacterJobStatusHistory;
import com.nyangmeong.pet.domain.CharacterPhoto;
import com.nyangmeong.pet.domain.PhotoRole;
import com.nyangmeong.pet.domain.PixelCharacter;
import com.nyangmeong.pet.dto.CharacterJobResponse;
import com.nyangmeong.pet.dto.CharacterRegenJobSummary;
import com.nyangmeong.pet.dto.EquippedImageSaveRequest;
import com.nyangmeong.pet.dto.EquippedItemSaveRequest;
import com.nyangmeong.pet.dto.HeldItemSummary;
import com.nyangmeong.pet.dto.PetProfileSummary;
import com.nyangmeong.pet.dto.PixelCharacterResponse;
import com.nyangmeong.pet.dto.PixelCharacterSummary;
import com.nyangmeong.pet.mapper.PixelCharacterMapper;

import lombok.RequiredArgsConstructor;

/**
 * [픽셀 캐릭터 서비스]
 * - SCR-MYP-010 : 목록·생성 작업·꾸미기(착용)·캐릭터 이미지
 * - 캐릭터 경로(/pixel-characters/{pixelCharSn})는 캐릭터의 petSn 으로 권한 검사 (docs/api/issues.md 7번)
 * - 오류 문의 접수·처리 종료는 고객센터·관리자 service 가 호출 (FN-MYP-010-23·24)
 */
@Service
@RequiredArgsConstructor
public class PixelCharacterService {

    private final PixelCharacterMapper pixelCharacterMapper;
    private final PetAuthorizer petAuthorizer;
    private final FileStore fileStore;
    private final EventPublisher eventPublisher;

    /**
     * 양육 반려동물별 캐릭터 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PixelCharacterSummary> search(Long mbrSn) {
        return pixelCharacterMapper.selectList(mbrSn);
    }

    /**
     * 캐릭터 상세 조회
     */
    @Transactional(readOnly = true)
    public PixelCharacterResponse getPixelCharacter(Long mbrSn, Long pixelCharSn) {
        PixelCharacter character = getCharacterOrThrow(pixelCharSn);
        petAuthorizer.checkCaregiver(mbrSn, character.getPetSn());
        return toResponse(character);
    }

    /**
     * 캐릭터 생성 작업 생성
     * - 사진·생성은 Python, 결과는 reportSuccess 보고로 저장
     * - 멱등 ID는 직전 작업 기준 : 동시 요청은 유일 키 충돌 -> PIXEL_CHARACTER_CREATING
     */
    @Transactional
    public CharacterJobResponse createJob(Long mbrSn, Long petSn) {
        petAuthorizer.checkPrimary(mbrSn, petSn);
        PetProfileSummary pet = pixelCharacterMapper.selectPetProfile(petSn);
        if (pet == null) {
            throw new CustomException(ErrorCode.PET_NOT_FOUND);
        }
        PixelCharacter existing = pixelCharacterMapper.selectByPetSn(petSn);
        CharacterCreationJob latest = pixelCharacterMapper.selectLatestJob(petSn);
        checkCreatable(existing, latest);

        CharacterCreationJob job;
        try {
            if (existing == null) {
                PixelCharacter character = new PixelCharacter();
                character.setMbrSn(mbrSn);
                character.setPetSn(petSn);
                character.setMdfrId(String.valueOf(mbrSn));
                pixelCharacterMapper.insertPixelCharacter(character);
            }
            job = insertRequestedJob(petSn, mbrSn, idmptDmndId(petSn, latest), mbrSn);
        } catch (DuplicateKeyException e) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_CREATING);
        }
        startJob(job.getCharCrtJobSn(), mbrSn);
        return CharacterJobResponse.of(job.getCharCrtJobSn(), pet);
    }

    /**
     * 착용 상태 저장
     */
    @Transactional
    public PixelCharacterResponse saveEquippedItems(Long mbrSn, Long pixelCharSn, EquippedItemSaveRequest req) {
        PixelCharacter character = getCharacterOrThrow(pixelCharSn);
        petAuthorizer.checkPrimary(mbrSn, character.getPetSn());
        if (character.isUnderReview()) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_UNDER_REVIEW);
        }

        Map<Long, HeldItemSummary> held = pixelCharacterMapper.selectHeldItems(pixelCharSn).stream()
                .collect(Collectors.toMap(HeldItemSummary::getHldItemSn, Function.identity()));
        List<Long> hldItemSns = req.hldItemSns().stream().distinct().toList();
        Set<String> regions = new HashSet<>();
        for (Long hldItemSn : hldItemSns) {
            HeldItemSummary item = held.get(hldItemSn);
            if (item == null) {
                throw new CustomException(ErrorCode.ITEM_NOT_HELD);
            }
            if (!regions.add(item.getItemRegnCd())) {
                throw new CustomException(ErrorCode.ITEM_SLOT_DUPLICATED);
            }
        }

        /** 부위별 착용 유일 키(UK_HLD_ITEM_2) 충돌 방지 : 해제 먼저 */
        pixelCharacterMapper.updateUnequipAll(pixelCharSn);
        if (!hldItemSns.isEmpty()) {
            pixelCharacterMapper.updateEquip(pixelCharSn, hldItemSns);
        }
        /** 대표 이미지는 새 착용으로 Python이 다시 합성해 보낸다 */
        pixelCharacterMapper.updateWearImage(pixelCharSn, null, String.valueOf(mbrSn));
        return toResponse(character);
    }

    /**
     * 착용 모습 대표 이미지 저장
     * - Python이 합성한 512 PNG, 합성에 쓴 아이템이 지금 착용과 같을 때만 저장 (늦게 끝난 옛 합성 차단)
     * - 관리자는 무료 재생성 뒤 합성으로 남의 캐릭터도 저장 (FN-MYP-010-23)
     */
    @Transactional
    public void saveEquippedImage(Long mbrSn, boolean admin, Long pixelCharSn, EquippedImageSaveRequest req) {
        PixelCharacter character = getCharacterOrThrow(pixelCharSn);
        if (!admin) {
            petAuthorizer.checkPrimary(mbrSn, character.getPetSn());
        }
        byte[] image = CharacterSheetService.readBytes(req.image());
        if (!CharacterSpec.matches(image)) {
            throw new CustomException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        Set<Long> equipped = pixelCharacterMapper.selectHeldItems(pixelCharSn).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEquipYn()))
                .map(HeldItemSummary::getItemSn)
                .collect(Collectors.toSet());
        Set<Long> requested = req.itemSns() == null ? Set.of() : new HashSet<>(req.itemSns());
        if (!equipped.equals(requested)) {
            throw new CustomException(ErrorCode.EQUIPPED_ITEMS_CHANGED);
        }
        String actorId = String.valueOf(mbrSn);
        Long atchFileSn = fileStore.store(image, FileExtension.PNG, "wear-" + pixelCharSn + ".png",
                FileCategory.PET_WEAR, FileOwner.pet(character.getMbrSn(), character.getPetSn()), actorId);
        pixelCharacterMapper.updateWearImage(pixelCharSn, atchFileSn, actorId);
    }

    /**
     * 캐릭터 이미지 조회
     */
    @Transactional(readOnly = true)
    public StoredFile getCharacterImage(Long mbrSn, Long petSn, CharacterImagePart part) {
        petAuthorizer.checkCaregiver(mbrSn, petSn);
        StoredFile image = currentImage(petSn);
        if (part == CharacterImagePart.FACE) {
            return new StoredFile(CharacterSpec.cropFace(image.content()), FileExtension.PNG.getContentType(),
                    image.orgnlFileNm());
        }
        return image;
    }

    /**
     * 캐릭터 오류 문의 접수
     */
    @Transactional
    public Long receiveErrorInquiry(Long mbrSn, Long pixelCharSn, Long otoInqrySn) {
        PixelCharacter character = getCharacterOrThrow(pixelCharSn);
        petAuthorizer.checkPrimary(mbrSn, character.getPetSn());
        CharacterCreationJob job = pixelCharacterMapper.selectLatestJob(character.getPetSn());
        if (job == null || CharacterJobStatus.isInProgress(job.getCrtJobSttsCd())) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_CREATING);
        }
        CharacterPhoto current = pixelCharacterMapper.selectCurrentPhoto(pixelCharSn);
        if (current == null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        if (job.getOtoInqrySn() != null || pixelCharacterMapper.updateJobInquiry(job.getCharCrtJobSn(), otoInqrySn) == 0) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_INQUIRY_ALREADY_RECEIVED);
        }
        pixelCharacterMapper.updateBscCharSbstYn(pixelCharSn, "Y", String.valueOf(mbrSn));
        return current.getAtchFileSn();
    }

    /**
     * 캐릭터 사진의 첨부 파일 번호 조회 : 없거나 그 캐릭터의 사진이 아니면 null
     */
    @Transactional(readOnly = true)
    public Long getPhotoAtchFileSn(Long pixelCharSn, Long charPhotoSn) {
        if (pixelCharSn == null || charPhotoSn == null) {
            return null;
        }
        CharacterPhoto photo = pixelCharacterMapper.selectPhoto(charPhotoSn);
        if (photo == null || !pixelCharSn.equals(photo.getPixelCharSn())) {
            return null;
        }
        return photo.getAtchFileSn();
    }

    /**
     * 캐릭터 오류 확인 종료
     * - 복원 : 바로 해제·알림, null
     * - 무료 재생성 : 요청 상태 재생성 작업을 만들고 그 번호. 관리자 화면이 Python 재생성을 부른다
     * - 재생성이 실패로 끝나도 오류 확인 중은 유지되어 다시 처리할 수 있다
     */
    @Transactional
    public Long finishErrorCheck(Long pixelCharSn, boolean regenerate) {
        PixelCharacter character = getCharacterOrThrow(pixelCharSn);
        if (!character.isUnderReview()) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        }
        CharacterCreationJob latest = pixelCharacterMapper.selectLatestJob(character.getPetSn());
        if (latest != null && CharacterJobStatus.isInProgress(latest.getCrtJobSttsCd())) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_CREATING);
        }
        if (!regenerate) {
            pixelCharacterMapper.updateBscCharSbstYn(pixelCharSn, "N", CharacterCreationService.SYSTEM);
            PetProfileSummary pet = pixelCharacterMapper.selectPetProfile(character.getPetSn());
            String petNm = pet != null ? pet.getPetNm() : "반려동물";
            eventPublisher.publish(new NotificationEvent(character.getMbrSn(), NotificationType.CHARACTER,
                    petNm + "의 캐릭터 오류 확인이 끝났어요", null, CharacterCreationService.MVMN_PATH));
            return null;
        }

        /** 오류 확인 중 표시는 재생성 완료까지 유지 (완료 시 CharacterCreationService 가 해제) */
        CharacterPhoto original = latest != null
                ? pixelCharacterMapper.selectJobPhoto(latest.getCharCrtJobSn(), PhotoRole.ORIGINAL.getCode())
                : null;
        if (original == null) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        /** 멱등 ID 는 직전 작업 기준 : 같은 결과로 동시에 다시 처리하면 유일 키 충돌 -> PIXEL_CHARACTER_CREATING */
        CharacterCreationJob job;
        try {
            job = insertRequestedJob(character.getPetSn(), character.getMbrSn(),
                    CharacterCreationJob.REGEN_ID_PREFIX + latest.getCharCrtJobSn(), null);
        } catch (DuplicateKeyException e) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_CREATING);
        }
        insertOriginalPhoto(pixelCharSn, job.getCharCrtJobSn(), original.getAtchFileSn());
        return job.getCharCrtJobSn();
    }

    /**
     * 캐릭터의 최근 무료 재생성 작업 (최근 작업이 재생성이 아니면 null)
     */
    @Transactional(readOnly = true)
    public CharacterRegenJobSummary getRegenJob(Long pixelCharSn) {
        PixelCharacter character = pixelCharacterMapper.selectPixelCharacter(pixelCharSn);
        if (character == null) {
            return null;
        }
        CharacterCreationJob latest = pixelCharacterMapper.selectLatestJob(character.getPetSn());
        if (latest == null || !latest.isRegeneration()) {
            return null;
        }
        return new CharacterRegenJobSummary(latest.getCharCrtJobSn(), latest.getCrtJobSttsCd(),
                CharacterJobStatus.isInProgress(latest.getCrtJobSttsCd()),
                CharacterJobStatus.FAILED.is(latest.getCrtJobSttsCd()));
    }

    /**
     * 생성 가능 여부 검사
     * - 실패로 끝나 결과가 없는 캐릭터는 다시 요청 가능
     */
    private void checkCreatable(PixelCharacter existing, CharacterCreationJob latest) {
        if (existing == null) {
            return;
        }
        if (latest != null && CharacterJobStatus.isInProgress(latest.getCrtJobSttsCd())) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_CREATING);
        }
        if (pixelCharacterMapper.selectCurrentPhoto(existing.getPixelCharSn()) != null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_ALREADY_EXISTS);
        }
    }

    /**
     * 작업 멱등 ID
     */
    private String idmptDmndId(Long petSn, CharacterCreationJob latest) {
        return "PET-" + petSn + "-" + (latest != null ? latest.getCharCrtJobSn() : 0);
    }

    /**
     * 생성 중 처리
     */
    private void startJob(Long charCrtJobSn, Long mbrSn) {
        pixelCharacterMapper.updateJobStarted(charCrtJobSn, CharacterJobStatus.REQUESTED.getCode(),
                CharacterJobStatus.CREATING.getCode());
        pixelCharacterMapper.insertJobStatusHistory(CharacterJobStatusHistory.of(charCrtJobSn, mbrSn,
                CharacterJobStatus.REQUESTED, CharacterJobStatus.CREATING));
    }

    /**
     * 요청 상태 생성 작업 등록
     */
    private CharacterCreationJob insertRequestedJob(Long petSn, Long ownerMbrSn, String idmptDmndId, Long actorMbrSn) {
        CharacterCreationJob job = new CharacterCreationJob();
        job.setPetSn(petSn);
        job.setMbrSn(ownerMbrSn);
        job.setCrtJobSttsCd(CharacterJobStatus.REQUESTED.getCode());
        job.setIdmptDmndId(idmptDmndId);
        pixelCharacterMapper.insertJob(job);

        pixelCharacterMapper.insertJobStatusHistory(CharacterJobStatusHistory.of(job.getCharCrtJobSn(), actorMbrSn,
                null, CharacterJobStatus.REQUESTED));
        return job;
    }

    /**
     * 원본 사진 등록
     */
    private void insertOriginalPhoto(Long pixelCharSn, Long charCrtJobSn, Long atchFileSn) {
        CharacterPhoto photo = new CharacterPhoto();
        photo.setPixelCharSn(pixelCharSn);
        photo.setCharCrtJobSn(charCrtJobSn);
        photo.setAtchFileSn(atchFileSn);
        photo.setPhotoRoleCd(PhotoRole.ORIGINAL.getCode());
        photo.setSortSeq(0);
        photo.setNowIndctYn("N");
        pixelCharacterMapper.insertPhoto(photo);
    }

    /**
     * 현재 표시 이미지
     * - 결과가 없거나 오류 확인 중이면 종별 기본 캐릭터
     */
    private StoredFile currentImage(Long petSn) {
        PixelCharacter character = pixelCharacterMapper.selectByPetSn(petSn);
        if (character != null && !character.isUnderReview()) {
            if (character.getWearImgFileSn() != null) {
                return fileStore.load(character.getWearImgFileSn());
            }
            CharacterPhoto current = pixelCharacterMapper.selectCurrentPhoto(character.getPixelCharSn());
            if (current != null) {
                return fileStore.load(current.getAtchFileSn());
            }
        }
        PetProfileSummary pet = pixelCharacterMapper.selectPetProfile(petSn);
        return defaultImage(CharacterDefaultImage.from(pet != null ? pet.getPetSeCd() : null));
    }

    /**
     * 종별 기본 캐릭터 이미지
     */
    private StoredFile defaultImage(CharacterDefaultImage image) {
        try (InputStream in = new ClassPathResource(image.getPath()).getInputStream()) {
            return new StoredFile(in.readAllBytes(), FileExtension.PNG.getContentType(), image.getPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 캐릭터 조회
     */
    private PixelCharacter getCharacterOrThrow(Long pixelCharSn) {
        PixelCharacter character = pixelCharacterMapper.selectPixelCharacter(pixelCharSn);
        if (character == null) {
            throw new CustomException(ErrorCode.PIXEL_CHARACTER_NOT_FOUND);
        }
        return character;
    }

    /**
     * 상세 응답 조립
     */
    private PixelCharacterResponse toResponse(PixelCharacter character) {
        CharacterCreationJob job = pixelCharacterMapper.selectLatestJob(character.getPetSn());
        return PixelCharacterResponse.from(character, job, pixelCharacterMapper.selectHeldItems(character.getPixelCharSn()));
    }
}
