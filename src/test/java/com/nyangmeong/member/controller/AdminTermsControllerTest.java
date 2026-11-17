package com.nyangmeong.member.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyangmeong.global.error.GlobalExceptionHandler;
import com.nyangmeong.global.log.AdminTaskLogService;
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
import com.nyangmeong.member.mapper.TermsMapper;
import com.nyangmeong.member.service.AdminTermsService;

/**
 * [검사 - 관리자 약관 버전 이력 API]
 * - 실제 AdminTermsService로 종류 키 누락·공백이 400 INVALID_INPUT_VALUE인지 본다
 */
@WebMvcTest(controllers = AdminTermsController.class)
@Import({AdminTermsService.class, SecurityConfig.class, JwtTokenProvider.class, JwtProperties.class,
        TokenCookies.class, ErrorResponseWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SensitiveDataMasker.class, GlobalExceptionHandler.class})
class AdminTermsControllerTest {

    private static final String ADMIN = "USRC0005";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TermsMapper termsMapper;

    @MockitoBean
    AdminTaskLogService adminTaskLogService;

    @MockitoBean
    AuthMemberMapper authMemberMapper;

    @Test
    @WithMockLoginMember(id = 9L, mbrAuthrtCd = ADMIN)
    void 이력은_종류_키가_없으면_400_INVALID_INPUT_VALUE() throws Exception {
        mockMvc.perform(get("/api/admin/terms/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        verify(termsMapper, never()).selectAdminHistory(anyString());
    }

    @Test
    @WithMockLoginMember(id = 9L, mbrAuthrtCd = ADMIN)
    void 이력은_종류_키가_공백이면_400_INVALID_INPUT_VALUE() throws Exception {
        mockMvc.perform(get("/api/admin/terms/history").param("trmsFixIdntfKey", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        verify(termsMapper, never()).selectAdminHistory(anyString());
    }

    @Test
    @WithMockLoginMember(id = 9L, mbrAuthrtCd = ADMIN)
    void 이력은_종류_키로_조회한다() throws Exception {
        when(termsMapper.selectAdminHistory("SERVICE")).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/terms/history").param("trmsFixIdntfKey", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(termsMapper).selectAdminHistory("SERVICE");
    }

    @Test
    @WithMockLoginMember
    void 일반_회원은_이력을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/terms/history").param("trmsFixIdntfKey", "SERVICE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }
}
