package com.nyangmeong.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [거래 취소 요청]
 * - 매칭 완료 이후 거래를 취소해 달라는 요청. 사유는 칩 선택 문구 또는 직접 입력 50자
 */
public record CareDealCancelRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "(?s).*\\S.*") String cnclRsn) {
}
