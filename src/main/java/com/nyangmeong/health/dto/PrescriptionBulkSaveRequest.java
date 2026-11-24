package com.nyangmeong.health.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [처방약 일괄 저장 요청]
 * - 한 검진기록의 처방약 여러 개를 추가·수정 : 하나라도 실패하면 전부 저장하지 않음 (FN-HLT-005-03)
 */
public record PrescriptionBulkSaveRequest(
        @NotNull Long ckupRcdSn,
        @NotEmpty List<@NotNull @Valid PrescriptionItemRequest> values) {

    /**
     * [처방약 1개]
     * - prscSn이 있으면 전체 값을 덮어써 수정, 없으면 추가 : 값·검증은 PrescriptionCreateRequest와 같음
     * - careRfltYn 미전송은 주기 체크 OFF
     */
    public record PrescriptionItemRequest(
            Long prscSn,
            @NotBlank @Size(max = 40) String mdcnNm,
            @NotNull LocalDate mdcnBgngYmd,
            @NotNull LocalDate mdcnEndYmd,
            @NotNull @Min(1) @Max(9999) Integer dayMdcnNmtm,
            @NotBlank String prscSttsCd,
            Boolean careRfltYn) {
    }
}
