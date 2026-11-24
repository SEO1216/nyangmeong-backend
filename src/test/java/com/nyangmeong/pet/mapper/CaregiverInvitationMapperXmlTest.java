package com.nyangmeong.pet.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * [검사 - 공동양육 초대 대상 반려동물 매퍼 XML]
 * - DB에 붙지 않고 SQL 문장만 확인 (docs/convention/test.md)
 * - 수락 카드·보낸 초대 목록의 반려동물에 품종명이 따라오는지 (SCR-MYP-005, FN-MYP-005-05)
 */
class CaregiverInvitationMapperXmlTest {

    private static Element find(String tag, String id) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Path xml = Path.of("src/main/resources/mapper/pet/CaregiverInvitationMapper.xml");
        Element root = factory.newDocumentBuilder().parse(xml.toFile()).getDocumentElement();
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            if (id.equals(node.getAttribute("id"))) {
                return node;
            }
        }
        throw new AssertionError(tag + " 없음: CaregiverInvitationMapper." + id);
    }

    private static String sql(String id) throws Exception {
        return find("select", id).getTextContent().replaceAll("\\s+", " ");
    }

    @Test
    void 초대_대상_반려동물_조회는_품종명과_직접_입력_품종을_함께_고른다() throws Exception {
        assertThat(sql("selectInvitationPets"))
                .contains(", V.VRTY_NM")
                .contains(", P.VRTY_DRCT_INPT_NM")
                .contains("LEFT JOIN VRTY V ON V.VRTY_SN = P.VRTY_SN");
    }

    @Test
    void 보낸_초대_목록도_품종명과_직접_입력_품종을_함께_고른다() throws Exception {
        assertThat(sql("selectSentList"))
                .contains(", V.VRTY_NM")
                .contains(", P.VRTY_DRCT_INPT_NM")
                .contains("LEFT JOIN VRTY V ON V.VRTY_SN = P.VRTY_SN");
    }

    @Test
    void 삭제한_초대는_조회_세_곳에서_빠진다() throws Exception {
        assertThat(sql("selectInvitation")).contains("AND DEL_YN = 'N'");
        assertThat(sql("selectSentList")).contains("AND I.DEL_YN = 'N'");
        assertThat(sql("selectPendingExpryDt")).contains("AND I.DEL_YN = 'N'");
    }

    @Test
    void 취소는_발송자의_만료_전_대기_초대만_바꾼다() throws Exception {
        String cancel = find("update", "updateCancel").getTextContent().replaceAll("\s+", " ");
        assertThat(cancel)
                .contains("SNDR_MBR_SN = #{sndrMbrSn}")
                .contains("INVTN_STTS_CD = #{pendingCd}")
                .contains("INVTN_EXPRY_DT > #{now}")
                .contains("DEL_YN = 'N'");
    }

    @Test
    void 삭제는_발송자의_취소한_초대를_DEL_YN으로만_숨긴다() throws Exception {
        String delete = find("update", "updateDelete").getTextContent().replaceAll("\s+", " ");
        assertThat(delete)
                .contains("SET DEL_YN = 'Y'")
                .contains("SNDR_MBR_SN = #{sndrMbrSn}")
                .contains("INVTN_STTS_CD = #{canceledCd}")
                .contains("DEL_YN = 'N'");
    }

    @Test
    void 초대_결과_매핑이_삭제_여부를_담는다() throws Exception {
        NodeList results = find("resultMap", "invitationResultMap").getElementsByTagName("result");
        boolean mapped = false;
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            mapped |= "DEL_YN".equals(result.getAttribute("column")) && "delYn".equals(result.getAttribute("property"));
        }
        assertThat(mapped).isTrue();
    }

    @Test
    void 반려동물_결과_매핑이_두_품종_컬럼을_따로_담는다() throws Exception {
        Element map = find("resultMap", "invitationPetResultMap");
        NodeList results = map.getElementsByTagName("result");
        String vrtyNm = null;
        String vrtyDrctInptNm = null;
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            if ("VRTY_NM".equals(result.getAttribute("column"))) {
                vrtyNm = result.getAttribute("property");
            }
            if ("VRTY_DRCT_INPT_NM".equals(result.getAttribute("column"))) {
                vrtyDrctInptNm = result.getAttribute("property");
            }
        }
        assertThat(vrtyNm).isEqualTo("vrtyNm");
        assertThat(vrtyDrctInptNm).isEqualTo("vrtyDrctInptNm");
    }
}
