package com.nyangmeong.pet.dto;

import lombok.Data;

/**
 * [굽기 대상 아이템]
 * - FN-MYP-011-01, FN-ADM-070-02
 * - regnCd 부위(MYPG01)
 * - 굽기 대상은 bakeImgRefYn true 아이템뿐이라 항상 true, 아이템 그림(atchFileSn)을 참고로 굽는다
 */
@Data
public class BakeItemSummary {
    private Long itemSn;
    private String regnCd;
    private Boolean bakeImgRefYn;
    private Long atchFileSn;
}
