package com.nyangmeong.health.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.error.GlobalExceptionHandler;
import com.nyangmeong.global.log.SensitiveDataMasker;
import com.nyangmeong.global.security.AuthMemberMapper;
import com.nyangmeong.global.security.SecurityConfig;
import com.nyangmeong.global.security.WithMockLoginMember;
import com.nyangmeong.global.security.handler.ErrorResponseWriter;
import com.nyangmeong.global.security.handler.RestAccessDeniedHandler;
import com.nyangmeong.global.security.handler.RestAuthenticationEntryPoint;
import com.nyangmeong.global.security.jwt.JwtProperties;
import com.nyangmeong.global.security.jwt.JwtTokenProvider;
import com.nyangmeong.global.security.jwt.TokenCookies;
import com.nyangmeong.health.dto.CareItemSummary;
import com.nyangmeong.health.dto.CareMissionProgressResponse;
import com.nyangmeong.health.service.CareItemService;

/**
 * [검사 - 케어리스트 표시 항목·미션 진행 API] (※신규 정책)
 */
@WebMvcTest(controllers = CareItemController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtProperties.class, TokenCookies.class,
        ErrorResponseWriter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        SensitiveDataMasker.class, GlobalExceptionHandler.class})
class CareItemIndicationControllerTest {

    private static final String XHR = "X-Requested-With";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CareItemService careItemService;

    @MockitoBean
    AuthMemberMapper authMemberMapper;

    @Test
    @WithMockLoginMember
    void 목록_행은_표시여부와_오늘_미션_값을_준다() throws Exception {
        CareItemSummary row = new CareItemSummary();
        row.setCareArtclSn(2L);
        row.setCareArtclNm("양치");
        row.setIndctYn(true);
        row.setTodayMsnYn(true);
        row.setMsnPnt(null);
        row.setMsnCmptnYn(true);
        when(careItemService.search(eq(1L), eq(10L), any())).thenReturn(List.of(row));

        mockMvc.perform(get("/api/pets/10/care-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].indctYn").value(true))
                .andExpect(jsonPath("$.data[0].todayMsnYn").value(true))
                .andExpect(jsonPath("$.data[0].msnCmptnYn").value(true))
                .andExpect(jsonPath("$.data[0].msnPnt").doesNotExist());
    }

    @Test
    @WithMockLoginMember
    void 표시_저장은_보낸_번호로_서비스를_부르고_목록을_준다() throws Exception {
        CareItemSummary row = new CareItemSummary();
        row.setCareArtclSn(1L);
        row.setIndctYn(true);
        when(careItemService.saveIndications(eq(1L), eq(10L), any())).thenReturn(List.of(row));

        mockMvc.perform(put("/api/pets/10/care-items/indications").header(XHR, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"careArtclSns\":[1,2,3,4,5]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].indctYn").value(true));
        verify(careItemService).saveIndications(eq(1L), eq(10L),
                argThat(req -> req.careArtclSns().equals(List.of(1L, 2L, 3L, 4L, 5L))));
    }

    @Test
    @WithMockLoginMember
    void 표시_저장에_목록이_없으면_400() throws Exception {
        mockMvc.perform(put("/api/pets/10/care-items/indications").header(XHR, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        verify(careItemService, never()).saveIndications(anyLong(), anyLong(), any());
    }

    @Test
    @WithMockLoginMember
    void 표시_항목이_5개_미만이면_400_INVALID_INPUT_VALUE_필드는_indctYn() throws Exception {
        when(careItemService.saveIndications(eq(1L), eq(10L), any()))
                .thenThrow(new CustomException(ErrorCode.INVALID_INPUT_VALUE, "indctYn"));

        mockMvc.perform(put("/api/pets/10/care-items/indications").header(XHR, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"careArtclSns\":[1,2,3,4]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.data[0].field").value("indctYn"));
    }

    @Test
    @WithMockLoginMember
    void 보이는_항목_삭제로_5개_미만이면_400_필드는_indctYn() throws Exception {
        doThrow(new CustomException(ErrorCode.INVALID_INPUT_VALUE, "indctYn"))
                .when(careItemService).delete(1L, 10L, 200L);

        mockMvc.perform(delete("/api/pets/10/care-items/200").header(XHR, "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.data[0].field").value("indctYn"));
    }

    @Test
    @WithMockLoginMember
    void 미션_진행은_주양육자_여부와_완료_수_전체_수만_준다() throws Exception {
        when(careItemService.searchMissionProgress(1L, 10L)).thenReturn(new CareMissionProgressResponse(false, 3, 8));

        mockMvc.perform(get("/api/pets/10/care-items/mission-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prmyYn").value(false))
                .andExpect(jsonPath("$.data.cmptnCnt").value(3))
                .andExpect(jsonPath("$.data.totCnt").value(8));
    }
}
