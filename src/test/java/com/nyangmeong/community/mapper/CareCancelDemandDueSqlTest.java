package com.nyangmeong.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * [검사 - 거래 취소 요청 무응답 기한 SQL]
 * - 자동 동의·하루 전 알림 대상이 요청중·매칭 상태·등록 경과일로 걸리는지, 무응답 종료가 요청중인 취소 요청이 있는 매칭을 빼는지
 */
class CareCancelDemandDueSqlTest {

    private static Configuration configuration;

    @BeforeAll
    static void loadMappers() throws Exception {
        configuration = new Configuration();
        String displayPath = "mapper/global/MemberDisplay.xml";
        try (InputStream in = CareCancelDemandDueSqlTest.class.getResourceAsStream("/" + displayPath)) {
            new XMLMapperBuilder(in, configuration, displayPath, configuration.getSqlFragments()).parse();
        }
        String path = "mapper/community/CareMatchMapper.xml";
        try (InputStream in = CareCancelDemandDueSqlTest.class.getResourceAsStream("/" + path)) {
            new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
        }
    }

    private BoundSql bound(String statement, Map<String, Object> params) {
        return configuration.getMappedStatement("com.nyangmeong.community.mapper.CareMatchMapper." + statement)
                .getBoundSql(params);
    }

    private String sql(String statement, Map<String, Object> params) {
        return bound(statement, params).getSql().replaceAll("\\s+", " ");
    }

    private Map<String, Object> dueParams(Long toDays) {
        Map<String, Object> params = new HashMap<>();
        params.put("mtchCnclDmndSn", 41L);
        params.put("requestingCd", "PCAC0021");
        params.put("sttsCds", List.of("PCAC0013", "PCAC0014"));
        params.put("fromDays", 7L);
        params.put("toDays", toDays);
        return params;
    }

    @Test
    void 자동_동의_대상은_요청중이고_매칭_상태가_맞고_등록_후_기준_일수가_지난_요청이다() {
        for (String statement : new String[] { "selectCancelDemandDueTargets", "existsCancelDemandDue" }) {
            String sql = sql(statement, dueParams(null));

            assertThat(sql).contains("FROM MTCH_CNCL_DMND D JOIN PET_PRTC_MTCH M")
                    .contains("D.DMND_STTS_CD = ?")
                    .contains("M.MTCH_DLNG_STTS_CD IN ( ? , ? )")
                    .contains("D.REG_DT <= NOW() - INTERVAL ? DAY")
                    .doesNotContain("D.REG_DT > NOW()")
                    .doesNotContain("FOR UPDATE");
        }
        assertThat(sql("existsCancelDemandDue", dueParams(null))).contains("D.MTCH_CNCL_DMND_SN = ?");
    }

    @Test
    void 하루_전_알림_대상은_상한_일수_미만으로도_묶인다() {
        assertThat(sql("selectCancelDemandDueTargets", dueParams(7L))).contains("D.REG_DT <= NOW() - INTERVAL ? DAY")
                .contains("D.REG_DT > NOW() - INTERVAL ? DAY");
    }

    @Test
    void 무응답_종료는_요청중인_거래_취소_요청이_있는_매칭을_뺀다() {
        for (String statement : new String[] { "selectNoResponseTargets", "existsNoResponseExpired" }) {
            BoundSql bound = bound(statement, new HashMap<>());

            assertThat(bound.getSql().replaceAll("\\s+", " ")).contains(
                    "NOT EXISTS ( SELECT 1 FROM MTCH_CNCL_DMND CD WHERE CD.PET_PRTC_MTCH_SN = T.PET_PRTC_MTCH_SN AND CD.DMND_STTS_CD = ? )");
            assertThat(bound.getAdditionalParameter("requestingDemandCd")).isEqualTo("PCAC0021");
        }
    }

    @Test
    void 거래_취소_요청_단건_조회는_잠금을_걸지_않는다() {
        assertThat(sql("selectCancelDemandById", new HashMap<>())).contains("D.MTCH_CNCL_DMND_SN = ?")
                .doesNotContain("FOR UPDATE");
    }
}
