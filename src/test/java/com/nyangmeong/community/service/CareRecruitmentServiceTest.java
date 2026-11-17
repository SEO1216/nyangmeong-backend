package com.nyangmeong.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.github.pagehelper.PageHelper;
import com.nyangmeong.community.domain.CareRecruitment;
import com.nyangmeong.community.domain.CareRecruitmentDetail;
import com.nyangmeong.community.domain.CareRecruitmentPet;
import com.nyangmeong.community.domain.Post;
import com.nyangmeong.community.dto.CareRecruitmentCreateRequest;
import com.nyangmeong.community.dto.CareRecruitmentPetRequestItem;
import com.nyangmeong.community.dto.CareRecruitmentResponse;
import com.nyangmeong.community.dto.CareRecruitmentSearchCond;
import com.nyangmeong.community.dto.CareRecruitmentUpdateRequest;
import com.nyangmeong.community.dto.MinimumWageResponse;
import com.nyangmeong.community.mapper.CareRecruitmentMapper;
import com.nyangmeong.community.mapper.PostMapper;
import com.nyangmeong.global.auth.OwnerAuthorizer;
import com.nyangmeong.global.code.RegionService;
import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;
import com.nyangmeong.global.file.FileStore;
import com.nyangmeong.global.response.PageQuery;
import com.nyangmeong.global.security.crypto.FieldCryptoService;
import com.nyangmeong.global.setting.SystemSetting;
import com.nyangmeong.global.setting.SystemSettingService;
import com.nyangmeong.pet.service.BreedService;

/**
 * [검사 - 펫돌봄 모집 목록·상세·등록·수정·삭제]
 */
class CareRecruitmentServiceTest {

    private static final Long MBR = 1L;
    private static final Long OTHER = 2L;
    private static final Long PST = 7L;
    private static final Long RCRT = 5L;
    private static final String NORMAL = "CMUC0003";
    private static final String JOINED = "CHTC0003";
    private static final String DELETED = "CMUC0005";
    private static final String RECRUITING = "PCAC0001";
    private static final String MATCHED = "PCAC0002";
    private static final String COMPLETED = "PCAC0003";
    private static final String HOURLY = "PCAC0004";
    private static final String PER_CASE = "PCAC0005";
    private static final String DOG = "PETC0001";
    private static final String CAT = "PETC0002";
    private static final BigDecimal LAT = new BigDecimal("35.8242000000");
    private static final BigDecimal LOT = new BigDecimal("127.1480000000");
    /** 날짜 기한(오늘부터 90일, 기간 30일) 검증이 있어 오늘(KST) 기준으로 잡는다 */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime BGNG = TODAY.plusDays(5).atTime(19, 0);
    private static final LocalDateTime END = TODAY.plusDays(5).atTime(19, 30);

    private final CareRecruitmentMapper careRecruitmentMapper = mock(CareRecruitmentMapper.class);
    private final PostMapper postMapper = mock(PostMapper.class);
    private final FileStore fileStore = mock(FileStore.class);
    private final SystemSettingService systemSettingService = mock(SystemSettingService.class);
    private final FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final BreedService breedService = mock(BreedService.class);
    private final CareRecruitmentService service = new CareRecruitmentService(careRecruitmentMapper, postMapper,
            fileStore, new OwnerAuthorizer(), systemSettingService, fieldCryptoService, regionService, breedService);

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    private CareRecruitmentDetail detail(String sttsCd) {
        CareRecruitmentDetail detail = new CareRecruitmentDetail();
        detail.setPetPrtcRcrtSn(RCRT);
        detail.setPstSn(PST);
        detail.setMbrSn(MBR);
        detail.setMbrNcnm("토리맘");
        detail.setPstSttsCd(NORMAL);
        detail.setRcrtSttsCd(sttsCd);
        detail.setPstTtl("산책 도와주실 분");
        detail.setPstCn("화·목 저녁");
        detail.setRgnSn(3L);
        detail.setRlsRgnNm("전주시 완산구 서곡동");
        detail.setCstUnitCd(HOURLY);
        detail.setCstAmt(15000);
        detail.setPrtcBgngDt(BGNG);
        detail.setPrtcEndDt(END);
        detail.setSchdlCnsltnPsbltyYn("N");
        detail.setPrtcPlcDaddrEnc("enc");
        detail.setPrtcPlcLat(LAT);
        detail.setPrtcPlcLot(LOT);
        detail.setInqCnt(10);
        detail.setRcmdtnCnt(3);
        detail.setAplcntCnt(2);
        return detail;
    }

    private CareRecruitmentPetRequestItem dog(Long vrtySn, String drctNm, String sizeCd) {
        return new CareRecruitmentPetRequestItem(DOG, vrtySn, drctNm, sizeCd);
    }

    private CareRecruitmentCreateRequest create(List<CareRecruitmentPetRequestItem> pets, String unitCd, Integer amt,
            Boolean schdl, LocalDateTime bgng, LocalDateTime end, List<MultipartFile> images, Boolean agree) {
        return new CareRecruitmentCreateRequest(pets, List.of("PCAC0008", "PCAC0009"), bgng, end, schdl, unitCd, amt,
                "전주시 완산구 서곡동", "서곡동 12-3", LAT, LOT, "산책 도와주실 분", "화·목 저녁", images, agree);
    }

    private CareRecruitmentCreateRequest valid() {
        return create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG, END, null, true);
    }

    private CareRecruitmentUpdateRequest update(String unitCd, Integer amt, Boolean schdl, LocalDateTime bgng,
            LocalDateTime end) {
        return new CareRecruitmentUpdateRequest(null, null, bgng, end, schdl, unitCd, amt, null, null, null, null,
                null, null, null, null);
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile("images", name, "image/png", new byte[] {1});
    }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void stubSystemSetting() {
        SystemSetting setting = new SystemSetting();
        setting.setLowstHrwgAmt(10320L);
        when(systemSettingService.get()).thenReturn(setting);
    }

    /** 등록 성공에 필요한 준비 */
    private void stubCreate() {
        stubSystemSetting();
        when(regionService.findJeonbukRgnSn(any(), any())).thenReturn(3L);
        when(fieldCryptoService.encrypt(anyString())).thenReturn("enc");
        doAnswer(inv -> {
            inv.<Post>getArgument(0).setPstSn(PST);
            return 1;
        }).when(postMapper).insertPost(any(Post.class));
        doAnswer(inv -> {
            inv.<CareRecruitment>getArgument(0).setPetPrtcRcrtSn(RCRT);
            return 1;
        }).when(careRecruitmentMapper).insertRecruitment(any(CareRecruitment.class));
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
    }

    /** 목록 */

    @Test
    void 목록은_정상_글을_기본_10건으로_조회한다() {
        stubSystemSetting();
        service.search(MBR, new CareRecruitmentSearchCond(), new PageQuery(null, null, null));

        verify(careRecruitmentMapper).selectList(MBR, NORMAL, null, false, null, null, null, null, DOG, false, null,
                null, COMPLETED, 7L, JOINED);
    }

    @Test
    void 숨김_일수는_운영_설정_값을_넘긴다() {
        SystemSetting setting = new SystemSetting();
        setting.setDlngCmptnHideCrtrDayCnt(3L);
        when(systemSettingService.get()).thenReturn(setting);
        CareRecruitmentSearchCond cond = new CareRecruitmentSearchCond();
        cond.setMineYn(true);

        service.search(MBR, cond, new PageQuery(null, null, null));

        verify(careRecruitmentMapper).selectList(MBR, NORMAL, null, true, null, null, null, null, DOG, false, null,
                null, COMPLETED, 3L, JOINED);
    }

    @Test
    void 조건을_모두_넘기고_날짜_상한은_다음날_0시다() {
        stubSystemSetting();
        CareRecruitmentSearchCond cond = new CareRecruitmentSearchCond();
        cond.setRcrtSttsCd(RECRUITING);
        cond.setMineYn(true);
        cond.setRgnSn(10L);
        cond.setPrtcBgngDe(LocalDate.of(2026, 11, 10));
        cond.setPrtcEndDe(LocalDate.of(2026, 11, 12));
        cond.setPetSeCd(DOG);
        cond.setPetSizeCd("MEDIUM");

        service.search(MBR, cond, new PageQuery(null, null, null));

        verify(breedService).checkPetSe(DOG);
        verify(careRecruitmentMapper).selectList(MBR, NORMAL, RECRUITING, true, 10L,
                LocalDateTime.of(2026, 11, 10, 0, 0), LocalDateTime.of(2026, 11, 13, 0, 0), DOG, DOG, true,
                new BigDecimal("10"), new BigDecimal("25"), COMPLETED, 7L, JOINED);
    }

    @Test
    void 대형은_상한_없이_하한만_넘긴다() {
        stubSystemSetting();
        CareRecruitmentSearchCond cond = new CareRecruitmentSearchCond();
        cond.setPetSizeCd("LARGE");

        service.search(MBR, cond, new PageQuery(null, null, null));

        verify(careRecruitmentMapper).selectList(MBR, NORMAL, null, false, null, null, null, null, DOG, true,
                new BigDecimal("25"), null, COMPLETED, 7L, JOINED);
    }

    @Test
    void 고양이를_고르면_크기_조건은_무시한다() {
        stubSystemSetting();
        CareRecruitmentSearchCond cond = new CareRecruitmentSearchCond();
        cond.setPetSeCd(CAT);
        cond.setPetSizeCd("SMALL");

        service.search(MBR, cond, new PageQuery(null, null, null));

        verify(careRecruitmentMapper).selectList(MBR, NORMAL, null, false, null, null, null, CAT, DOG, false, null,
                null, COMPLETED, 7L, JOINED);
    }

    @Test
    void 알_수_없는_상태_크기나_거꾸로_된_날짜는_400() {
        CareRecruitmentSearchCond status = new CareRecruitmentSearchCond();
        status.setRcrtSttsCd("PCAC0099");
        assertError(() -> service.search(MBR, status, new PageQuery(null, null, null)),
                ErrorCode.INVALID_INPUT_VALUE);

        CareRecruitmentSearchCond size = new CareRecruitmentSearchCond();
        size.setPetSizeCd("HUGE");
        assertError(() -> service.search(MBR, size, new PageQuery(null, null, null)), ErrorCode.INVALID_INPUT_VALUE);

        CareRecruitmentSearchCond reversed = new CareRecruitmentSearchCond();
        reversed.setPrtcBgngDe(LocalDate.of(2026, 11, 12));
        reversed.setPrtcEndDe(LocalDate.of(2026, 11, 10));
        assertError(() -> service.search(MBR, reversed, new PageQuery(null, null, null)),
                ErrorCode.INVALID_INPUT_VALUE);
    }

    /** 상세 */

    @Test
    void 상세는_조회수를_올리고_작성자에게만_채팅_인원수를_준다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        when(postMapper.insertViewIgnore(PST, OTHER)).thenReturn(1);

        CareRecruitmentResponse res = service.getRecruitment(OTHER, RCRT);

        verify(postMapper).updateInqCnt(PST);
        assertThat(res.inqCnt()).isEqualTo(11);
        assertThat(res.owner()).isFalse();
        assertThat(res.aplcntCnt()).isNull();
    }

    @Test
    void 작성자가_보면_조회_이력도_조회수도_건드리지_않는다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));

        CareRecruitmentResponse res = service.getRecruitment(MBR, RCRT);

        verify(postMapper, never()).insertViewIgnore(anyLong(), anyLong());
        verify(postMapper, never()).updateInqCnt(anyLong());
        assertThat(res.inqCnt()).isEqualTo(10);
    }

    @Test
    void 같은_날_다시_보면_조회수는_그대로이고_작성자는_채팅_인원수를_본다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));

        CareRecruitmentResponse res = service.getRecruitment(MBR, RCRT);

        verify(postMapper, never()).updateInqCnt(anyLong());
        assertThat(res.inqCnt()).isEqualTo(10);
        assertThat(res.owner()).isTrue();
        assertThat(res.aplcntCnt()).isEqualTo(2);
    }

    @Test
    void 상세는_로그인_회원이_채팅_중인지를_나가지_않은_참여_기준으로_준다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        when(careRecruitmentMapper.existsChatting(PST, OTHER, "CHTC0003")).thenReturn(true);

        assertThat(service.getRecruitment(OTHER, RCRT).chatting()).isTrue();
        assertThat(service.getRecruitment(MBR, RCRT).chatting()).isFalse();
    }

    @Test
    void 매칭_완료와_거래_완료면_확정된_돌보미_닉네임을_준다() {
        for (String sttsCd : new String[] { MATCHED, "PCAC0003" }) {
            CareRecruitmentDetail matched = detail(sttsCd);
            matched.setCfmtnPetPrtcMtchSn(30L);
            when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(matched);
            when(careRecruitmentMapper.selectMatchedSitterNcnm(30L)).thenReturn("돌보미");

            assertThat(service.getRecruitment(OTHER, RCRT).mtchSitterNcnm()).isEqualTo("돌보미");
        }
    }

    @Test
    void 모집중이거나_확정된_매칭이_없으면_돌보미_닉네임은_null이고_조회하지_않는다() {
        CareRecruitmentDetail recruiting = detail(RECRUITING);
        recruiting.setCfmtnPetPrtcMtchSn(30L);
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(recruiting);
        assertThat(service.getRecruitment(OTHER, RCRT).mtchSitterNcnm()).isNull();

        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(MATCHED));
        assertThat(service.getRecruitment(OTHER, RCRT).mtchSitterNcnm()).isNull();

        verify(careRecruitmentMapper, never()).selectMatchedSitterNcnm(anyLong());
    }

    @Test
    void 상세는_확정된_매칭이_있었는지를_준다() {
        CareRecruitmentDetail dealt = detail("PCAC0003");
        dealt.setCfmtnPetPrtcMtchSn(30L);
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(dealt);
        assertThat(service.getRecruitment(OTHER, RCRT).matched()).isTrue();

        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail("PCAC0003"));
        assertThat(service.getRecruitment(OTHER, RCRT).matched()).isFalse();
    }

    @Test
    void 자동_마감_대상은_모집중_정상글_수락_대기_코드와_30일로_찾는다() {
        when(careRecruitmentMapper.selectCloseTargets(RECRUITING, NORMAL, "PCAC0012", 30)).thenReturn(List.of(5L));

        assertThat(service.findCloseTargets()).containsExactly(5L);
    }

    @Test
    void 자동_마감은_거래완료로_바꾸고_처리자_없이_사유와_이력을_남긴다() {
        when(careRecruitmentMapper.selectForUpdate(RCRT)).thenReturn(new CareRecruitment());
        when(careRecruitmentMapper.existsCloseTarget(RCRT, RECRUITING, NORMAL, "PCAC0012", 30)).thenReturn(true);
        when(careRecruitmentMapper.updateStatus(RCRT, RECRUITING, "PCAC0003", null)).thenReturn(1);

        assertThat(service.closeExpired(RCRT)).isTrue();

        ArgumentCaptor<com.nyangmeong.community.domain.CareRecruitmentStatusHistory> captor = ArgumentCaptor
                .forClass(com.nyangmeong.community.domain.CareRecruitmentStatusHistory.class);
        verify(careRecruitmentMapper).insertStatusHistory(captor.capture());
        assertThat(captor.getValue().getPetPrtcRcrtSn()).isEqualTo(RCRT);
        assertThat(captor.getValue().getMbrSn()).isNull();
        assertThat(captor.getValue().getBfrSttsCd()).isEqualTo(RECRUITING);
        assertThat(captor.getValue().getAftrSttsCd()).isEqualTo("PCAC0003");
        assertThat(captor.getValue().getChgRsn()).isEqualTo("모집 기간 종료 자동 마감");
    }

    @Test
    void 잠근_뒤_대상에서_빠졌으면_바꾸지_않는다() {
        when(careRecruitmentMapper.selectForUpdate(RCRT)).thenReturn(new CareRecruitment());
        when(careRecruitmentMapper.existsCloseTarget(RCRT, RECRUITING, NORMAL, "PCAC0012", 30)).thenReturn(false);

        assertThat(service.closeExpired(RCRT)).isFalse();
        verify(careRecruitmentMapper, never()).updateStatus(anyLong(), anyString(), anyString(), any());
        verify(careRecruitmentMapper, never()).insertStatusHistory(any());
    }

    @Test
    void 없는_모집은_마감하지_않는다() {
        assertThat(service.closeExpired(9L)).isFalse();
        verify(careRecruitmentMapper, never()).updateStatus(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void 최저시급_조회는_설정의_현재_값을_매번_읽어_준다() {
        SystemSetting first = new SystemSetting();
        first.setLowstHrwgAplcnYr("2026");
        first.setLowstHrwgAmt(10320L);
        SystemSetting second = new SystemSetting();
        second.setLowstHrwgAplcnYr("2027");
        second.setLowstHrwgAmt(10700L);
        when(systemSettingService.get()).thenReturn(first, second);

        assertThat(service.getMinimumWage()).isEqualTo(new MinimumWageResponse("2026", 10320L));
        assertThat(service.getMinimumWage()).isEqualTo(new MinimumWageResponse("2027", 10700L));
    }

    @Test
    void 없거나_삭제된_모집은_404() {
        assertError(() -> service.getRecruitment(MBR, 9L), ErrorCode.CARE_RECRUITMENT_NOT_FOUND);
    }

    @Test
    void 응답에는_상세_주소와_정확_좌표가_없다() {
        assertThat(CareRecruitmentResponse.class.getRecordComponents()).extracting("name")
                .doesNotContain("prtcPlcDaddr", "prtcPlcDaddrEnc", "prtcPlcLat", "prtcPlcLot");
    }

    /** 등록 */

    @Test
    void 등록은_펫돌봄_게시판_글과_모집중_모집을_만든다() {
        stubCreate();

        service.create(MBR, valid());

        ArgumentCaptor<Post> post = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insertPost(post.capture());
        assertThat(post.getValue().getBbsCd()).isEqualTo("CMUC0028");
        assertThat(post.getValue().getMbrSn()).isEqualTo(MBR);

        ArgumentCaptor<CareRecruitment> recruitment = ArgumentCaptor.forClass(CareRecruitment.class);
        verify(careRecruitmentMapper).insertRecruitment(recruitment.capture());
        assertThat(recruitment.getValue().getRcrtSttsCd()).isEqualTo(RECRUITING);
        assertThat(recruitment.getValue().getRgnSn()).isEqualTo(3L);
        assertThat(recruitment.getValue().getPrtcPlcDaddrEnc()).isEqualTo("enc");
        assertThat(recruitment.getValue().getUtztnRulAgreYn()).isEqualTo("Y");
        verify(careRecruitmentMapper).insertStatusHistory(any());
        verify(careRecruitmentMapper, times(2)).insertShape(any());
    }

    @Test
    void 강아지는_크기별_대표_체중으로_저장한다() {
        stubCreate();

        service.create(MBR, create(List.of(dog(1L, null, "LARGE")), HOURLY, 11000, false, BGNG, END, null, true));

        ArgumentCaptor<CareRecruitmentPet> pet = ArgumentCaptor.forClass(CareRecruitmentPet.class);
        verify(careRecruitmentMapper).insertPet(pet.capture());
        assertThat(pet.getValue().getPetBdwg()).isEqualByComparingTo("30");
        assertThat(pet.getValue().getPetPrtcRcrtSn()).isEqualTo(RCRT);
    }

    @Test
    void 강아지와_고양이를_함께_등록하면_고양이_체중은_0이다() {
        stubCreate();
        List<CareRecruitmentPetRequestItem> pets = List.of(dog(1L, null, "SMALL"),
                new CareRecruitmentPetRequestItem(CAT, null, "코숏", null));

        service.create(MBR, create(pets, HOURLY, 11000, false, BGNG, END, null, true));

        ArgumentCaptor<CareRecruitmentPet> pet = ArgumentCaptor.forClass(CareRecruitmentPet.class);
        verify(careRecruitmentMapper, times(2)).insertPet(pet.capture());
        assertThat(pet.getAllValues().get(1).getPetBdwg()).isEqualByComparingTo("0");
        assertThat(pet.getAllValues().get(1).getVrtyDrctInptNm()).isEqualTo("코숏");
    }

    @Test
    void 시급이_최저시급_미만이면_거부한다() {
        stubSystemSetting();

        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10319, false, BGNG,
                END, null, true)), ErrorCode.CARE_MIN_WAGE_NOT_MET);
        verify(postMapper, never()).insertPost(any());
    }

    @Test
    void 시급_최저시급과_건당_금액은_통과한다() {
        stubCreate();

        service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG, END, null, true));
        service.create(MBR, create(List.of(dog(1L, null, "SMALL")), PER_CASE, 5000, false, BGNG, END, null, true));

        verify(careRecruitmentMapper, times(2)).insertRecruitment(any());
    }

    @Test
    void 일정_협의가_아니면_일정이_필수다() {
        stubCreate();

        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, null,
                null, null, true)), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 일정_협의면_일정_없이_등록한다() {
        stubCreate();

        service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, true, null, null, null, true));

        ArgumentCaptor<CareRecruitment> recruitment = ArgumentCaptor.forClass(CareRecruitment.class);
        verify(careRecruitmentMapper).insertRecruitment(recruitment.capture());
        assertThat(recruitment.getValue().getSchdlCnsltnPsbltyYn()).isEqualTo("Y");
        assertThat(recruitment.getValue().getPrtcBgngDt()).isNull();
    }

    @Test
    void 종료가_시작보다_뒤가_아니거나_30분_단위가_아니면_400() {
        stubCreate();

        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG,
                BGNG, null, true)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false,
                BGNG.plusMinutes(10), END, null, true)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG,
                null, null, true)), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 전북_밖_좌표는_거부한다() {
        stubSystemSetting();
        when(regionService.findJeonbukRgnSn(any(), any())).thenReturn(null);

        assertError(() -> service.create(MBR, valid()), ErrorCode.CARE_REGION_NOT_ALLOWED);
        verify(postMapper, never()).insertPost(any());
    }

    @Test
    void 이용수칙에_동의하지_않으면_400() {
        stubCreate();

        assertError(() -> service.create(MBR,
                create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG, END, null, false)),
                ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 품종은_마스터와_직접입력_중_하나만_받는다() {
        stubCreate();

        assertError(() -> service.create(MBR, create(List.of(dog(null, null, "SMALL")), HOURLY, 10320, false, BGNG,
                END, null, true)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.create(MBR, create(List.of(dog(1L, "믹스", "SMALL")), HOURLY, 10320, false, BGNG,
                END, null, true)), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 강아지는_크기가_필수고_같은_구분은_두_번_못_고른다() {
        stubCreate();

        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, null)), HOURLY, 10320, false, BGNG, END,
                null, true)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL"), dog(2L, null, "LARGE")),
                HOURLY, 10320, false, BGNG, END, null, true)), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 돌봄_형태_코드가_틀리면_400() {
        stubCreate();
        CareRecruitmentCreateRequest req = new CareRecruitmentCreateRequest(List.of(dog(1L, null, "SMALL")),
                List.of("PCAC0099"), BGNG, END, false, HOURLY, 10320, "서곡동", "서곡동 12-3", LAT, LOT, "제목", "내용",
                null, true);

        assertError(() -> service.create(MBR, req), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 사진은_설정한_최대_장수를_넘을_수_없다() {
        stubCreate();
        List<MultipartFile> images = java.util.stream.IntStream.range(0, 11).mapToObj(i -> file("a" + i + ".png"))
                .toList();

        assertError(() -> service.create(MBR, create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false, BGNG,
                END, images, true)), ErrorCode.POST_IMAGE_LIMIT_EXCEEDED);
    }

    /** 수정 */

    @Test
    void 작성자가_아니면_수정할_수_없다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));

        assertError(() -> service.update(OTHER, RCRT, update(null, null, null, null, null)), ErrorCode.NOT_OWNER);
    }

    @Test
    void 모집중이_아니면_수정할_수_없다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(MATCHED));

        assertError(() -> service.update(MBR, RCRT, update(null, null, null, null, null)),
                ErrorCode.CARE_RECRUITMENT_NOT_EDITABLE);
    }

    @Test
    void 비용만_바꿔도_합친_값으로_최저시급을_검사한다() {
        stubSystemSetting();
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));

        assertError(() -> service.update(MBR, RCRT, update(null, 9000, null, null, null)),
                ErrorCode.CARE_MIN_WAGE_NOT_MET);
        verify(careRecruitmentMapper, never()).updateRecruitment(any(), anyString());
    }

    @Test
    void 보낸_필드만_바꾸고_나머지는_그대로_둔다() {
        stubSystemSetting();
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        when(careRecruitmentMapper.updateRecruitment(any(), eq(RECRUITING))).thenReturn(1);
        when(postMapper.updatePost(eq(PST), any(), any(), any(), anyString(), eq(NORMAL))).thenReturn(1);

        service.update(MBR, RCRT, update(PER_CASE, 7000, null, null, null));

        ArgumentCaptor<CareRecruitment> recruitment = ArgumentCaptor.forClass(CareRecruitment.class);
        verify(careRecruitmentMapper).updateRecruitment(recruitment.capture(), eq(RECRUITING));
        assertThat(recruitment.getValue().getCstUnitCd()).isEqualTo(PER_CASE);
        assertThat(recruitment.getValue().getCstAmt()).isEqualTo(7000);
        assertThat(recruitment.getValue().getRgnSn()).isEqualTo(3L);
        assertThat(recruitment.getValue().getPrtcBgngDt()).isEqualTo(BGNG);
        assertThat(recruitment.getValue().getPrtcPlcDaddrEnc()).isEqualTo("enc");
        verify(careRecruitmentMapper, never()).deletePets(anyLong());
        verify(careRecruitmentMapper, never()).deleteShapes(anyLong());
    }

    private CareRecruitment updatedRecruitment() {
        ArgumentCaptor<CareRecruitment> recruitment = ArgumentCaptor.forClass(CareRecruitment.class);
        verify(careRecruitmentMapper).updateRecruitment(recruitment.capture(), eq(RECRUITING));
        return recruitment.getValue();
    }

    private void stubUpdate() {
        stubSystemSetting();
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        when(careRecruitmentMapper.updateRecruitment(any(), eq(RECRUITING))).thenReturn(1);
        when(postMapper.updatePost(eq(PST), any(), any(), any(), anyString(), eq(NORMAL))).thenReturn(1);
    }

    @Test
    void 일정_협의_가능만_보내고_일정이_없으면_기존_일정을_비운다() {
        stubUpdate();

        service.update(MBR, RCRT, update(null, null, true, null, null));

        CareRecruitment saved = updatedRecruitment();
        assertThat(saved.getPrtcBgngDt()).isNull();
        assertThat(saved.getPrtcEndDt()).isNull();
        assertThat(saved.getSchdlCnsltnPsbltyYn()).isEqualTo("Y");
    }

    @Test
    void 일정_협의_가능이면서_일정을_보내면_희망_일정으로_저장한다() {
        stubUpdate();
        LocalDateTime newBgng = TODAY.plusDays(7).atTime(10, 0);
        LocalDateTime newEnd = TODAY.plusDays(7).atTime(12, 0);

        service.update(MBR, RCRT, update(null, null, true, newBgng, newEnd));

        CareRecruitment saved = updatedRecruitment();
        assertThat(saved.getPrtcBgngDt()).isEqualTo(newBgng);
        assertThat(saved.getPrtcEndDt()).isEqualTo(newEnd);
        assertThat(saved.getSchdlCnsltnPsbltyYn()).isEqualTo("Y");
    }

    @Test
    void 일정_협의_가능이면서_일정을_하나만_보내면_저장된_값과_합쳐_검사한다() {
        stubUpdate();

        assertError(() -> service.update(MBR, RCRT, update(null, null, true, END.plusHours(1), null)),
                ErrorCode.INVALID_INPUT_VALUE);
        verify(careRecruitmentMapper, never()).updateRecruitment(any(), anyString());
    }

    @Test
    void 일정_협의가_아닌데_일정이_없는_글이면_400이다() {
        stubSystemSetting();
        CareRecruitmentDetail noSchedule = detail(RECRUITING);
        noSchedule.setPrtcBgngDt(null);
        noSchedule.setPrtcEndDt(null);
        noSchedule.setSchdlCnsltnPsbltyYn("Y");
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(noSchedule);

        assertError(() -> service.update(MBR, RCRT, update(null, null, false, null, null)),
                ErrorCode.INVALID_INPUT_VALUE);
        verify(careRecruitmentMapper, never()).updateRecruitment(any(), anyString());
    }

    @Test
    void 협의_여부를_보내지_않은_수정은_일정을_그대로_둔다() {
        stubUpdate();

        service.update(MBR, RCRT, update(PER_CASE, 7000, null, null, null));

        CareRecruitment saved = updatedRecruitment();
        assertThat(saved.getPrtcBgngDt()).isEqualTo(BGNG);
        assertThat(saved.getPrtcEndDt()).isEqualTo(END);
        assertThat(saved.getSchdlCnsltnPsbltyYn()).isEqualTo("N");
    }

    @Test
    void 반려동물을_보내면_목록을_교체한다() {
        stubSystemSetting();
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        when(careRecruitmentMapper.updateRecruitment(any(), eq(RECRUITING))).thenReturn(1);
        when(postMapper.updatePost(eq(PST), any(), any(), any(), anyString(), eq(NORMAL))).thenReturn(1);
        CareRecruitmentUpdateRequest req = new CareRecruitmentUpdateRequest(List.of(dog(1L, null, "MEDIUM")), null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        service.update(MBR, RCRT, req);

        verify(careRecruitmentMapper).deletePets(RCRT);
        verify(careRecruitmentMapper).insertPet(any());
    }

    @Test
    void 좌표를_하나만_보내면_400() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));
        CareRecruitmentUpdateRequest onlyLat = new CareRecruitmentUpdateRequest(null, null, null, null, null, null,
                null, null, null, LAT, null, null, null, null, null);

        assertError(() -> service.update(MBR, RCRT, onlyLat), ErrorCode.INVALID_INPUT_VALUE);
    }

    /** 일정 날짜 기한(시작일 오늘부터 90일, 종료일 시작일부터 30일) */

    private LocalDateTime at(LocalDate date) {
        return date.atTime(19, 0);
    }

    private void assertPeriodRejected(LocalDateTime bgng, LocalDateTime end, LocalDateTime savedBgng,
            LocalDateTime savedEnd) {
        assertError(() -> CareRecruitmentService.checkSchedule(false, bgng, end, savedBgng, savedEnd, TODAY),
                ErrorCode.INVALID_INPUT_VALUE);
    }

    private void assertPeriodAllowed(LocalDateTime bgng, LocalDateTime end, LocalDateTime savedBgng,
            LocalDateTime savedEnd) {
        CareRecruitmentService.checkSchedule(false, bgng, end, savedBgng, savedEnd, TODAY);
    }

    @Test
    void 시작일이_어제면_거부하고_오늘이면_통과한다() {
        assertPeriodRejected(at(TODAY.minusDays(1)), at(TODAY.minusDays(1)).plusHours(1), null, null);
        assertPeriodAllowed(at(TODAY), at(TODAY).plusHours(1), null, null);
    }

    @Test
    void 시작일이_오늘_90일_뒤면_통과하고_91일_뒤면_거부한다() {
        assertPeriodAllowed(at(TODAY.plusDays(90)), at(TODAY.plusDays(90)).plusHours(1), null, null);
        assertPeriodRejected(at(TODAY.plusDays(91)), at(TODAY.plusDays(91)).plusHours(1), null, null);
    }

    @Test
    void 기간이_30일이면_통과하고_31일이면_거부한다() {
        LocalDate start = TODAY.plusDays(10);
        assertPeriodAllowed(at(start), at(start.plusDays(30)), null, null);
        assertPeriodRejected(at(start), at(start.plusDays(31)), null, null);
    }

    @Test
    void 바뀌지_않은_날짜는_지난_날짜여도_통과한다() {
        LocalDateTime pastBgng = at(TODAY.minusDays(3));
        LocalDateTime pastEnd = at(TODAY.minusDays(2));

        assertPeriodAllowed(pastBgng, pastEnd, pastBgng, pastEnd);
        assertPeriodAllowed(pastBgng.withHour(10), pastEnd, pastBgng, pastEnd);
    }

    @Test
    void 수정에서_시작일을_어제로_바꾸면_거부한다() {
        assertPeriodRejected(at(TODAY.minusDays(1)), at(TODAY.minusDays(1)).plusHours(1), BGNG, END);
    }

    @Test
    void 등록은_지난_일정이면_400이고_저장하지_않는다() {
        stubSystemSetting();
        CareRecruitmentCreateRequest past = create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false,
                at(TODAY.minusDays(1)), at(TODAY.minusDays(1)).plusHours(1), null, true);

        assertError(() -> service.create(MBR, past), ErrorCode.INVALID_INPUT_VALUE);
        verify(postMapper, never()).insertPost(any(Post.class));
    }

    @Test
    void 등록은_시작일이_91일_뒤면_400이다() {
        stubSystemSetting();
        CareRecruitmentCreateRequest far = create(List.of(dog(1L, null, "SMALL")), HOURLY, 10320, false,
                at(TODAY.plusDays(91)), at(TODAY.plusDays(91)).plusHours(1), null, true);

        assertError(() -> service.create(MBR, far), ErrorCode.INVALID_INPUT_VALUE);
        verify(postMapper, never()).insertPost(any(Post.class));
    }

    @Test
    void 지난_일정이_저장된_글의_제목만_수정하면_통과한다() {
        stubUpdate();
        CareRecruitmentDetail pastDetail = detail(RECRUITING);
        pastDetail.setPrtcBgngDt(at(TODAY.minusDays(3)));
        pastDetail.setPrtcEndDt(at(TODAY.minusDays(2)));
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(pastDetail);
        CareRecruitmentUpdateRequest titleOnly = new CareRecruitmentUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, "제목만 수정", null, null, null);

        service.update(MBR, RCRT, titleOnly);

        assertThat(updatedRecruitment().getPrtcBgngDt()).isEqualTo(at(TODAY.minusDays(3)));
    }

    @Test
    void 수정에서_시작일을_어제로_바꾸면_400이고_저장하지_않는다() {
        stubUpdate();

        assertError(() -> service.update(MBR, RCRT,
                update(null, null, null, at(TODAY.minusDays(1)), at(TODAY.minusDays(1)).plusHours(1))),
                ErrorCode.INVALID_INPUT_VALUE);
        verify(careRecruitmentMapper, never()).updateRecruitment(any(), anyString());
    }

    /** 삭제 */

    @Test
    void 작성자는_상태와_무관하게_삭제하고_채팅_기록은_건드리지_않는다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(MATCHED));
        when(postMapper.updateStatus(PST, NORMAL, DELETED, "1")).thenReturn(1);

        service.delete(MBR, RCRT);

        verify(postMapper).insertStatusHistory(any());
        verify(careRecruitmentMapper, never()).deletePets(anyLong());
    }

    @Test
    void 작성자가_아니면_삭제할_수_없다() {
        when(careRecruitmentMapper.selectDetail(RCRT, NORMAL, JOINED)).thenReturn(detail(RECRUITING));

        assertError(() -> service.delete(OTHER, RCRT), ErrorCode.NOT_OWNER);
        verify(postMapper, never()).updateStatus(anyLong(), anyString(), anyString(), anyString());
    }
}
