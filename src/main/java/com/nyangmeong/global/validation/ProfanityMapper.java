package com.nyangmeong.global.validation;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * [금칙어 Mapper]
 * - KWRD_DICT 중 금칙어(AIMC0041)·금칙어 예외(AIMC0042) 용도의 행 (global 이 쓰는 자기 Mapper)
 */
@Mapper
public interface ProfanityMapper {

    /**
     * 사용 중인 사전 단어 (용도 코드별)
     */
    List<String> selectWords(@Param("dictUsgCd") String dictUsgCd);
}
