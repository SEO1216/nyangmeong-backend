# SQL 경계 확인: 캐릭터 작업 시간 초과·자동 블라인드 판별·탈퇴 보관 기간 (2026-11-20)

`docs/convention/test.md`는 DB에 붙는 자동 테스트를 만들지 않고 Mapper를 로컬 MySQL에서 직접 실행해 확인하도록 정한다. 이 문서는 그 확인 기록이다. 아래 SQL은 다시 돌릴 수 있다.

## 실행 방법

1. 로컬 MySQL에 임시 스키마를 만든다. 로컬 계정 권한이 `nyangmeong_`으로 시작하는 스키마에만 있으면 그 이름을 쓴다. 공용 DB에서 돌리지 않는다.
   ```sql
   CREATE DATABASE nyangmeong_sqlcheck_tmp DEFAULT CHARACTER SET utf8mb4;
   ```
2. 그 스키마에서 `db/20261120_create_tables_v0.15.sql`, `db/20261120_insert_base_data_v0.15.sql`을 차례로 실행한다.
3. 아래 "확인 SQL"을 실행한다(예: `mysql -N nyangmeong_sqlcheck_tmp < 파일`). 정리 DELETE가 맨 앞에 있어 같은 스키마에서 다시 돌려도 된다.
4. 끝나면 `DROP DATABASE nyangmeong_sqlcheck_tmp;`

외래키는 데이터를 넣는 동안만 끈다. 확인하는 조건과 무관한 부모 행(반려동물, 공통코드, 게시판 코드)을 만들지 않기 위해서다. 코드 값 중 `'X'`는 조건에 쓰이지 않는 자리다.

## 넣는 값

조회 SQL은 Mapper XML의 `<select>` 본문을 그대로 두고 `#{}` 자리만 서비스가 넘기는 값으로 바꿨다.

| SQL | 파라미터 | 값을 정하는 곳 |
| --- | --- | --- |
| `PixelCharacterMapper.xml` `selectTimedOutJobs` | crtJobSttsCd=CHRC0002(생성 중), minutes=10 | `CharacterCreationService` `TIMEOUT_MINUTES`, `CharacterJobStatus.CREATING` |
| `PixelCharacterMapper.xml` `selectUnstartedJobs` | crtJobSttsCd=CHRC0001(요청), minutes=10 | `CharacterJobStatus.REQUESTED` |
| `ReportMapper.xml` `existsAutoTarget`(`autoTargetCondition` 포함) | blindCd=CMUC0004, autoBlindRsn=`신고 누적`, autoDeleteRsn=`신고 누적 자동 삭제` | `PostStatus.BLIND`, `ReportService.AUTO_BLIND_RSN`·`AUTO_DELETE_RSN` |
| `MemberMapper.xml` `selectWithdrawnList` | withdrawnSttsCd=USRC0003, keepDayCnt=30 | `MemberStatus.WITHDRAWN`, `SYS_STNG.WHDWL_MBR_KPNG_DAY_CNT`(기준 데이터 기본 30) |

`existsAutoTarget`은 신고 한 건마다 `autoTargetCondition`을 판별하므로 신고마다 실행했다. 관리자 자동 블라인드 목록(`selectAutoBlindList`)과 신고 목록의 자동 필터(`selectAdminList`)도 같은 조건을 쓴다.

## 경계 행과 기대

시각은 스크립트 시작 때의 `NOW()`를 `@T`로 잡아 그 기준으로 넣는다.

### (1) 캐릭터 생성 시간 초과 (SCR-MYP-010)

| 작업 | 상태 | 기준 시각 | DEL_YN | 시간 초과 조회 | 미시작 조회 | 이유 |
| --- | --- | --- | --- | --- | --- | --- |
| 201 | CHRC0002 | 시작 T-11분, 등록 T-11분 | N | 나온다 | 안 나온다 | 10분 넘김. 미시작 조회는 상태가 달라 빠짐 |
| 202 | CHRC0002 | 시작 T-9분 | N | 안 나온다 | 안 나온다 | 10분 안 |
| 203 | CHRC0003 | 시작 T-11분 | N | 안 나온다 | 안 나온다 | 다른 상태 |
| 204 | CHRC0002 | 시작 T-11분 | Y | 안 나온다 | 안 나온다 | 삭제됨 |
| 205 | CHRC0002 | 시작 NULL | N | 안 나온다 | 안 나온다 | 시작 시각 없음(`NULL < x`는 참이 아님) |
| 211 | CHRC0001 | 등록 T-11분 | N | 안 나온다 | 나온다 | 10분 동안 시작 안 함 |
| 212 | CHRC0001 | 등록 T-9분 | N | 안 나온다 | 안 나온다 | 10분 안 |
| 214 | CHRC0001 | 등록 T-11분 | Y | 안 나온다 | 안 나온다 | 삭제됨 |

정확히 10분이면 `<`라서 빠진다(다음 배치에서 잡힌다).

### (2) 자동 블라인드·자동 삭제 판별 (SCR-ADM-030)

| 신고 | 대상 | 상태·이력 | 기대 | 이유 |
| --- | --- | --- | --- | --- |
| 401 | 글 301 | 블라인드, 마지막 이력 처리자 없음·`신고 누적` | 1 | 자동 블라인드 |
| 402 | 글 302 | 블라인드, 자동 이력 뒤 관리자 이력 | 0 | 마지막 이력에 처리자 있음 |
| 403 | 글 303 | 정상, 마지막 이력 자동 | 0 | 블라인드 상태 아님 |
| 404 | 글 304 | 블라인드, 사유 `신고 누적으로 블라인드` | 0 | 사유가 다름(옛 시드 사유) |
| 405 | 글 305 | 블라인드, 관리자 이력 뒤 자동 이력 | 1 | 마지막 이력이 자동 |
| 411 | 댓글 501 | 삭제, 수정 T-1분, 자동 삭제 이력 T | 1 | 이력 뒤 변경 없음 |
| 412 | 댓글 502 | 삭제, 수정 T, 자동 삭제 이력 T | 1 | 같은 초는 `<=`라서 포함 |
| 413 | 댓글 503 | 삭제, 수정 T+1분, 자동 삭제 이력 T | 0 | 복구 뒤 다시 지움 |
| 414 | 댓글 504 | 복구(DEL_YN N), 자동 삭제 이력 T | 0 | 삭제 상태 아님 |
| 415 | 댓글 505 | 삭제, 이력 처리자 있음·`관리자 삭제` | 0 | 자동 삭제 사유 아님 |
| 416 | 댓글 506 | 삭제, 이 신고에는 이력 없음 | 1 | 같은 댓글의 다른 신고(417)에 자동 삭제 이력 |
| 417 | 댓글 506 | 삭제, 자동 삭제 이력 T | 1 | 자동 삭제 |

### (3) 탈퇴 회원 보관 기간 (SCR-ADM-013)

| 회원 | 상태 | 탈퇴 시각 | 기대 | 이유 |
| --- | --- | --- | --- | --- |
| 101 | USRC0003 | T-30일+1분 | 나온다 | 보관 기간 안 |
| 102 | USRC0003 | T-30일-1분 | 안 나온다 | 보관 기간 지남 |
| 103 | USRC0003 | NULL | 안 나온다 | 탈퇴 시각 없음 |
| 104 | USRC0001 | T-1일 | 안 나온다 | 탈퇴 상태 아님 |

정확히 30일이면 `>`라서 빠진다. 파기 배치(`selectExpiredWithdrawnMbrSns`)가 같은 시점을 어떻게 보는지는 이번 범위 밖이다.

## 확인 SQL

```sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET @T = NOW();
-- 다시 돌릴 때를 위한 정리
DELETE FROM DCLR_STTS_HSTRY WHERE DCLR_SN BETWEEN 400 AND 499;
DELETE FROM DCLR WHERE DCLR_SN BETWEEN 400 AND 499;
DELETE FROM CMNT WHERE CMNT_SN BETWEEN 500 AND 599;
DELETE FROM PST_STTS_HSTRY WHERE PST_SN BETWEEN 300 AND 399;
DELETE FROM PST WHERE PST_SN BETWEEN 300 AND 399;
DELETE FROM CHAR_CRT_JOB WHERE CHAR_CRT_JOB_SN BETWEEN 200 AND 299;
DELETE FROM MBR WHERE MBR_SN IN (1,101,102,103,104);
-- (3) 회원
INSERT INTO MBR (MBR_SN, MBR_STTS_CD, MBR_AUTHRT_CD, JOIN_PATH_CD, ACNT_EML_ENC, ACNT_EML_HMAC, MBR_NCNM, WHDWL_DT) VALUES
 (1,  'USRC0001','X','X','e1','h1','작성자', NULL),
 (101,'USRC0003','X','X','e101','h101','보관안_30일-1분', @T - INTERVAL 30 DAY + INTERVAL 1 MINUTE),
 (102,'USRC0003','X','X','e102','h102','보관밖_30일+1분', @T - INTERVAL 30 DAY - INTERVAL 1 MINUTE),
 (103,'USRC0003','X','X','e103','h103','탈퇴일NULL', NULL),
 (104,'USRC0001','X','X','e104','h104','정상회원_최근탈퇴일', @T - INTERVAL 1 DAY);
-- (1) 캐릭터 작업
INSERT INTO CHAR_CRT_JOB (CHAR_CRT_JOB_SN, PET_SN, MBR_SN, CRT_JOB_STTS_CD, PRCS_BGNG_DT, IDMPT_DMND_ID, DEL_YN, REG_DT) VALUES
 (201,1,1,'CHRC0002', @T - INTERVAL 11 MINUTE, 'k201','N', @T - INTERVAL 11 MINUTE),
 (202,1,1,'CHRC0002', @T - INTERVAL 9 MINUTE,  'k202','N', @T - INTERVAL 20 MINUTE),
 (203,1,1,'CHRC0003', @T - INTERVAL 11 MINUTE, 'k203','N', @T - INTERVAL 20 MINUTE),
 (204,1,1,'CHRC0002', @T - INTERVAL 11 MINUTE, 'k204','Y', @T - INTERVAL 20 MINUTE),
 (205,1,1,'CHRC0002', NULL,                    'k205','N', @T - INTERVAL 20 MINUTE),
 (211,1,1,'CHRC0001', NULL, 'k211','N', @T - INTERVAL 11 MINUTE),
 (212,1,1,'CHRC0001', NULL, 'k212','N', @T - INTERVAL 9 MINUTE),
 (214,1,1,'CHRC0001', NULL, 'k214','Y', @T - INTERVAL 11 MINUTE);
-- (2) 글
INSERT INTO PST (PST_SN, MBR_SN, BBS_CD, PST_STTS_CD, PST_TTL, PST_CN) VALUES
 (301,1,'X','CMUC0004','자동블라인드','c'),(302,1,'X','CMUC0004','자동뒤관리자','c'),
 (303,1,'X','CMUC0003','정상상태','c'),(304,1,'X','CMUC0004','다른사유','c'),
 (305,1,'X','CMUC0004','관리자뒤자동','c'),(390,1,'X','CMUC0003','댓글부모','c');
INSERT INTO PST_STTS_HSTRY (PST_STTS_HSTRY_SN, PST_SN, MBR_SN, BFR_STTS_CD, AFTR_STTS_CD, CHG_RSN) VALUES
 (1,301,NULL,'CMUC0003','CMUC0004','신고 누적'),
 (2,302,NULL,'CMUC0003','CMUC0004','신고 누적'),(3,302,1,'CMUC0004','CMUC0004','관리자 처리'),
 (4,303,NULL,'CMUC0003','CMUC0004','신고 누적'),
 (5,304,NULL,'CMUC0003','CMUC0004','신고 누적으로 블라인드'),
 (6,305,1,'CMUC0003','CMUC0004','관리자 처리'),(7,305,NULL,'CMUC0004','CMUC0004','신고 누적');
-- (2) 댓글
INSERT INTO CMNT (CMNT_SN, PST_SN, MBR_SN, CMNT_CN, DEL_YN, MDFCN_DT) VALUES
 (501,390,1,'이력보다 먼저 수정','Y', @T - INTERVAL 1 MINUTE),
 (502,390,1,'이력과 같은 초','Y', @T),
 (503,390,1,'복구 뒤 재삭제','Y', @T + INTERVAL 1 MINUTE),
 (504,390,1,'복구됨','N', @T + INTERVAL 1 MINUTE),
 (505,390,1,'다른 사유','Y', @T - INTERVAL 1 MINUTE),
 (506,390,1,'신고 두 건','Y', @T - INTERVAL 1 MINUTE);
INSERT INTO DCLR (DCLR_SN, PST_SN, CMNT_SN, MBR_SN, DCLR_RSN_CD, DCLR_PRCS_STTS_CD) VALUES
 (401,301,NULL,1,'X','X'),(402,302,NULL,1,'X','X'),(403,303,NULL,1,'X','X'),(404,304,NULL,1,'X','X'),(405,305,NULL,1,'X','X'),
 (411,NULL,501,1,'X','X'),(412,NULL,502,1,'X','X'),(413,NULL,503,1,'X','X'),(414,NULL,504,1,'X','X'),(415,NULL,505,1,'X','X'),
 (416,NULL,506,1,'X','X'),(417,NULL,506,101,'X','X');
INSERT INTO DCLR_STTS_HSTRY (DCLR_SN, MBR_SN, AFTR_STTS_CD, CHG_RSN, REG_DT) VALUES
 (411,NULL,'X','신고 누적 자동 삭제',@T),(412,NULL,'X','신고 누적 자동 삭제',@T),(413,NULL,'X','신고 누적 자동 삭제',@T),
 (414,NULL,'X','신고 누적 자동 삭제',@T),(415,1,'X','관리자 삭제',@T),(417,NULL,'X','신고 누적 자동 삭제',@T);
SET FOREIGN_KEY_CHECKS = 1;

SELECT '(1) selectTimedOutJobs: 기대 201' AS CASE_NM;
        SELECT CHAR_CRT_JOB_SN
          FROM CHAR_CRT_JOB
         WHERE CRT_JOB_STTS_CD = 'CHRC0002'
           AND PRCS_BGNG_DT < NOW() - INTERVAL 10 MINUTE
           AND DEL_YN = 'N';
SELECT '(1) selectUnstartedJobs: 기대 211' AS CASE_NM;
        SELECT CHAR_CRT_JOB_SN
          FROM CHAR_CRT_JOB
         WHERE CRT_JOB_STTS_CD = 'CHRC0001'
           AND REG_DT < NOW() - INTERVAL 10 MINUTE
           AND DEL_YN = 'N';
SELECT '(2) existsAutoTarget: 기대 1 = 401,405,411,412,416,417 / 0 = 402,403,404,413,414,415' AS CASE_NM;
SELECT 401 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 401
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 402 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 402
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 403 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 403
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 404 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 404
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 405 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 405
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 411 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 411
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 412 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 412
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 413 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 413
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 414 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 414
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 415 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 415
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 416 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 416
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT 417 AS DCLR_SN, EXISTS (SELECT 1
                         FROM DCLR D
          LEFT JOIN PST P
            ON P.PST_SN = D.PST_SN
          LEFT JOIN CMNT C
            ON C.CMNT_SN = D.CMNT_SN
                        WHERE D.DCLR_SN = 417
                          AND ((D.PST_SN IS NOT NULL
          AND P.PST_STTS_CD = 'CMUC0004'
          AND (SELECT H.MBR_SN IS NULL AND H.CHG_RSN = '신고 누적'
                 FROM PST_STTS_HSTRY H
                WHERE H.PST_SN = D.PST_SN
                ORDER BY H.PST_STTS_HSTRY_SN DESC
                LIMIT 1) = 1)
         OR (D.CMNT_SN IS NOT NULL
             AND C.DEL_YN = 'Y'
             AND EXISTS (SELECT 1
                           FROM DCLR_STTS_HSTRY H
                           JOIN DCLR X
                             ON X.DCLR_SN = H.DCLR_SN
                          WHERE X.CMNT_SN = D.CMNT_SN
                            AND H.CHG_RSN = '신고 누적 자동 삭제'
                            AND C.MDFCN_DT <= H.REG_DT)))
                       ) AS AUTO_YN;
SELECT '(3) selectWithdrawnList keepDayCnt=30: 기대 101' AS CASE_NM;
        SELECT MBR_SN
             , MBR_NCNM
             , ACNT_EML_ENC
             , WHDWL_DT
          FROM MBR
         WHERE MBR_STTS_CD = 'USRC0003'
           AND WHDWL_DT IS NOT NULL
           AND WHDWL_DT > DATE_SUB(NOW(), INTERVAL 30 DAY)
         ORDER BY WHDWL_DT DESC
                , MBR_SN DESC;
```

## 결과 (2026-11-20, MySQL 8.0.46, 로컬 임시 스키마)

모두 기대와 같다. 틀린 동작은 없었다.

```
(1) selectTimedOutJobs: 기대 201
201
(1) selectUnstartedJobs: 기대 211
211
(2) existsAutoTarget: 기대 1 = 401,405,411,412,416,417 / 0 = 402,403,404,413,414,415
401	1
402	0
403	0
404	0
405	1
411	1
412	1
413	0
414	0
415	0
416	1
417	1
(3) selectWithdrawnList keepDayCnt=30: 기대 101
101	보관안_30일-1분	e101	2026-10-21 17:11:24
```

(3)의 탈퇴 시각은 실행 시각 기준이라 다시 돌리면 바뀐다.
