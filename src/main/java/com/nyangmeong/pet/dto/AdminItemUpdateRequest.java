package com.nyangmeong.pet.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [관리자 아이템 수정 요청]
 * - multipart/form-data, 보낸 칸만 바뀜 (FN-ADM-070-03)
 * - image 는 바꿀 때만, 굽기 대상(수정 후 bakeImgRefYn true) 아이템의 그림을 바꾸면 착용 시트를 지운다
 */
public record AdminItemUpdateRequest(
        String itemRegnCd,
        @Size(max = 50) @Pattern(regexp = "(?s).*\\S.*") String itemNm,
        @Min(0) @Max(1_000_000) Long needPnt,
        Boolean ntslYn,
        Boolean bakeImgRefYn,
        MultipartFile image) {
}
