package com.nyangmeong.global.error;

/**
 * [비즈니스 예외]
 * - 사용 예: throw new CustomException(ErrorCode.PET_NOT_FOUND);
 * - 틀린 칸이 있는 입력 검사: throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "careCycleCd");
 */
public class CustomException extends BaseException {

    public CustomException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CustomException(ErrorCode errorCode, String... fields) {
        super(errorCode, fields);
    }

    private CustomException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }

    /**
     * 문구를 따로 정한 예외 : 응답 message 가 이 문구로 나간다
     * - 사용 예: throw CustomException.withMessage(ErrorCode.PET_LIMIT_EXCEEDED, ErrorCode.PET_LIMIT_EXCEEDED.message().formatted(max));
     */
    public static CustomException withMessage(ErrorCode errorCode, String message) {
        return new CustomException(message, errorCode);
    }
}
