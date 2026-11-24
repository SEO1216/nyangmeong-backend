package com.nyangmeong.global.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.nyangmeong.global.error.CustomException;
import com.nyangmeong.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * [금칙어 검사]
 * - 금칙어(AIMC0041)·예외(AIMC0042)는 KWRD_DICT 에서 읽어 메모리에 두고 5분마다 다시 읽는다
 * - 정규화 : 한글·자모·영문만 남기고 소문자로. 숫자·기호·공백으로 끼워 넣은 우회(「시.발」「시1발」「시 발」)를 막는다
 * - 한 글자 토막이 이어질 때만 붙인다(「시 발」「ㅅ ㅂ」은 잡고 「다시 발견」「개 새끼를 낳았어요」는 통과)
 * - 예외어(「시바견」 등)는 정규화 전에 글에서 지운다
 * - 단어를 못 읽으면 막지 않고 마지막으로 읽은 목록(없으면 빈 목록)으로 검사한다
 */
@Slf4j
@Component
public class ProfanityFilter {

    static final String USG_PROFANITY = "AIMC0041";
    static final String USG_EXCEPTION = "AIMC0042";
    private static final long REFRESH_MILLIS = 5 * 60 * 1000L;
    private static final String SEPARATOR = " ";

    private record Words(List<String> banned, List<String> exceptions, long loadedAt) {
    }

    private final ProfanityMapper profanityMapper;
    private volatile Words words;

    public ProfanityFilter(ProfanityMapper profanityMapper) {
        this.profanityMapper = profanityMapper;
    }

    /**
     * 칸별 글에 금칙어가 있으면 400 PROFANITY_DETECTED (걸린 칸 이름만 응답 data 로)
     *
     * @param fields 칸 이름(camelCase) -> 입력한 글. 비어 있거나 null 인 글은 건너뜀
     */
    public void check(Map<String, String> fields) {
        TreeSet<String> flagged = new TreeSet<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (contains(entry.getValue())) {
                flagged.add(entry.getKey());
            }
        }
        if (!flagged.isEmpty()) {
            throw new CustomException(ErrorCode.PROFANITY_DETECTED, flagged.toArray(String[]::new));
        }
    }

    /**
     * 칸 이름과 글을 번갈아 넘겨 검사 (예: check("pstTtl", ttl, "pstCn", cn)). 글이 null 이어도 된다
     */
    public void check(String... fieldAndTextPairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fieldAndTextPairs.length; i += 2) {
            fields.put(fieldAndTextPairs[i], fieldAndTextPairs[i + 1]);
        }
        check(fields);
    }

    /**
     * 글에 금칙어가 들어 있는지
     */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Words current = currentWords();
        if (current.banned().isEmpty()) {
            return false;
        }
        String normalized = normalize(removeExceptions(text, current.exceptions()));
        for (String word : current.banned()) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String removeExceptions(String text, List<String> exceptions) {
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String exception : exceptions) {
            lowered = lowered.replace(exception.toLowerCase(Locale.ROOT), SEPARATOR);
        }
        return lowered;
    }

    /**
     * 한글·자모·영문 토막으로 나눈 뒤, 한 글자 토막이 이어진 구간만 붙여 한 덩어리로 만든다
     */
    static String normalize(String lowered) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (isLetter(c)) {
                token.append(c);
            } else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }

        StringBuilder out = new StringBuilder();
        StringBuilder singles = new StringBuilder();
        for (String t : tokens) {
            if (t.length() == 1) {
                singles.append(t);
                continue;
            }
            flush(out, singles);
            out.append(t).append(SEPARATOR);
        }
        flush(out, singles);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder singles) {
        if (!singles.isEmpty()) {
            out.append(singles).append(SEPARATOR);
            singles.setLength(0);
        }
    }

    /** 한글 음절·한글 자모·영문 소문자 */
    private static boolean isLetter(char c) {
        return (c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅣ') || (c >= 'a' && c <= 'z');
    }

    private Words currentWords() {
        Words cached = words;
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt() < REFRESH_MILLIS) {
            return cached;
        }
        try {
            Words loaded = new Words(prepare(profanityMapper.selectWords(USG_PROFANITY)),
                    profanityMapper.selectWords(USG_EXCEPTION), now);
            words = loaded;
            return loaded;
        } catch (RuntimeException e) {
            log.warn("금칙어 목록을 읽지 못해 이전 목록으로 검사 : {}", e.getMessage());
            Words fallback = cached != null ? new Words(cached.banned(), cached.exceptions(), now)
                    : new Words(List.of(), List.of(), now);
            words = fallback;
            return fallback;
        }
    }

    /** 금칙어도 글과 같은 방식으로 정규화(붙여 쓴 단어 하나로) */
    private List<String> prepare(List<String> raw) {
        List<String> prepared = new ArrayList<>();
        for (String word : raw) {
            String normalized = normalize(word.toLowerCase(Locale.ROOT)).replace(SEPARATOR, "");
            if (!normalized.isEmpty()) {
                prepared.add(normalized);
            }
        }
        return prepared;
    }
}
