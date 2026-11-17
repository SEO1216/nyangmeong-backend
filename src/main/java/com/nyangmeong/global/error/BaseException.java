package com.nyangmeong.global.error;

import java.util.List;

import lombok.Getter;

/**
 * [예외 계층의 최상위]
 * - RuntimeException 기반 -> @Transactional 자동 롤백 대상
 * - ErrorCode 에러 핸들링 일관성
 * - fields : 틀린 요청 칸 이름(camelCase). 있으면 응답 data에 [{field, message}]로 나간다
 */
@Getter
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    private final List<String> fields;

    public BaseException(ErrorCode errorCode) {
        this(errorCode, new String[0]);
    }

    public BaseException(ErrorCode errorCode, String... fields) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.fields = List.of(fields);
    }

    /**
     * 문구를 따로 정하는 예외 (문구에 운영 설정값 같은 숫자가 들어갈 때)
     * - 인자 순서를 바꿔 fields 가변 인자 생성자와 섞이지 않게 한다
     */
    protected BaseException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.fields = List.of();
    }
}
