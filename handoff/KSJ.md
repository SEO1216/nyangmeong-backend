# KSJ 핸드오프

## 지금 하는 것

SCR-CMU-004 우리동네 펫돌봄 · 거래완료 글 공개 목록 숨김 — 진행 중, 검증 3회차 실패로 보관 (승인 2026-11-16 11:22, 시작 2026-11-16 17:30)

근거: 프론트 저장소 `docs/backend-request-care-chat.md` 3번 앞 두 항목(사람 요청 2026-11-15). 명세 기능 ID 추가 없음. 걸치는 FN-CMU-004-01 목록. 3번의 채팅 목록·나가기 항목은 "지금 코드 그대로"라고 적혀 있어 이번 범위가 아니다.

정한 것:
- 숨김 대상: `mineYn`이 false인 목록에서 거래완료(PCAC0003)가 된 시각이 기준 일수 이전인 글. 거래완료 시각은 그 글의 상태 이력(`PET_PRTC_RCRT_STTS_HSTRY`) 중 `AFTR_STTS_CD=PCAC0003`인 가장 최근 `REG_DT`, 이력이 없으면(시드) `PET_PRTC_RCRT.MDFCN_DT`. 경계: 완료 시각 `<= NOW() - INTERVAL 일수 DAY`이면 숨김(무응답 배치와 같은 방식)
- `mineYn=true`면 기간과 관계없이 나온다. `mineYn`은 지금처럼 작성자 기준이다. 요청서의 "돌봄 제공자"까지 넓히는 것은 `mineYn`의 뜻을 바꾸는 일이라 이번에 하지 않는다
- 상태 필터와 관계없이 적용한다(`rcrtSttsCd=PCAC0003`으로 골라도 오래된 거래완료는 공개 목록에 안 나옴). 상세 조회·관리자 목록(`selectAdminList`)은 그대로
- 기준 일수: `SYS_STNG`에 새 컬럼 `DLNG_CMPTN_HIDE_CRTR_DAY_CNT` decimal(2,0) NOT NULL DEFAULT 7, COMMENT '거래완료숨김기준일수'(`NOTI_HIDE_CRTR_DAY_CNT`·`CMPTN_DT`와 같은 이름 방식). 관리자 운영 설정(SCR-ADM-110, LJS)에서 바꿀 수 있게 조회·수정 응답의 `community` 묶음에 넣는다(요청서 "운영 중에 바꿀 수 있다")

- FN-CMU-004-01 목록: `community/service/CareRecruitmentService.java` `search`에서 `systemSettingService.get().getDlngCmptnHideCrtrDayCnt()`를 읽어 넘김. `community/mapper/CareRecruitmentMapper.java` `selectList`에 `completedCd`·`hideDays` 인자, `resources/mapper/community/CareRecruitmentMapper.xml` `selectList`에 `<if test="!mineYn">` 숨김 조건. API: `GET /care-recruitments`(KSJ, `Page<CareRecruitmentSummary>`) 경로·필드 그대로. 완료 조건: `명령:` `./gradlew test --tests com.nyangmeong.community.service.CareRecruitmentServiceTest -q`(설정 일수와 PCAC0003 코드를 Mapper에 넘김, `mineYn=true`도 같은 값을 넘기고 XML이 거름). `화면:` Swagger에서 거래완료 8일 지난 글이 전체 목록·`rcrtSttsCd=PCAC0003` 목록에 없고, `mineYn=true`(작성자 로그인)에는 있고, 상세 `GET /care-recruitments/{sn}`는 200
- 운영 설정: `global/setting/SystemSetting.java`(필드, 기본 7)·`resources/mapper/global/SystemSettingMapper.xml`(resultMap·조회·저장 4곳)·`AdminSettingResponse.java`(`Community`에 `dlngCmptnHideCrtrDayCnt`)·`AdminSettingUpdateRequest.java`(`@Min(1) @Max(99)`)·`AdminSystemSettingService.java`(`change` 한 줄, 감사 로그 같이). API: `GET·PATCH /admin/settings`(LJS, `AdminSettingResponse`·`AdminSettingUpdateRequest`)에 선택 필드 하나 추가, 기존 필드 그대로. 완료 조건: `명령:` `./gradlew test --tests 'com.nyangmeong.global.setting.*' -q`(새 필드 조회·수정·감사 로그 이전·이후 값). `화면:` Swagger `PATCH /api/admin/settings` `{"dlngCmptnHideCrtrDayCnt": 3}` → 응답 `community.dlngCmptnHideCrtrDayCnt=3`, 100이면 400. 프론트 연결: 관리자 운영 설정 화면 커뮤니티 묶음에 입력칸(프론트 저장소에서 /start, LJS)
- DB: `db/20261109_create_tables_v0.14.sql` 변경 절 `v0.14 (17)`에 `ALTER TABLE SYS_STNG ADD COLUMN ... AFTER MTCH_NO_RSPNS_END_CRTR_DAY_CNT`, 본문 `SYS_STNG`에 같은 컬럼. 시드 `SYS_STNG` INSERT는 컬럼 목록을 적어 두어 기본값 7로 들어가므로 고치지 않는다. 공용 DB 실행은 사람
- 문서: `docs/api/global.md` `AdminSettingResponse`의 `community` 묶음·`AdminSettingUpdateRequest`에 행 추가. `docs/api/community.md` 목록 설명에 「`mineYn`이 아니면 거래완료 후 `SYS_STNG.DLNG_CMPTN_HIDE_CRTR_DAY_CNT`(기본 7)일이 지난 글은 빠진다」 한 줄
- 사람 확인: 공용 DB에 ALTER 실행 뒤, 거래완료 글 하나의 이력 `REG_DT`(이력 없으면 `MDFCN_DT`)를 8일 전으로 바꿔(또는 설정을 1일로 바꿔) 위 `화면:`을 본다. 바꾼 값은 되돌린다
- 검증: `./gradlew build -q`

새 의존성: 없음

## 지난 작업

SCR-CMU-004 지난 글 자동 마감(2026-11-16), SCR-CMU-004 일정 기한 검증(2026-11-16, PR #244), SCR-CMU-005 알림 이동 경로(PR #240, 2026-11-13), SCR-CMU-004 목록 기본 정렬(2026-11-13), SCR-CMU-002 운영 지역 변환 설정(PR #226, 2026-11-11), SCR-CMU-001 내 글만 보기(PR #211, 2026-11-10), SCR-CMU-001 케어미션 반영(PR #209, 2026-11-10), SCR-CMU-003 검색어 설정 인코딩(PR #204, 2026-11-09), SCR-CMU-002 만료 알림 경로(PR #203, 2026-11-09, 공용 DB SYSC0032 입력·확인용 알림 정리 끝, 외부 뉴스 비우고 재수집 끝), SCR-CMU-003 검색어 반려 3종(PR #201, 2026-11-09), SCR-CMU-004 작성자 조회수 제외(커밋 0328347, 2026-11-09), SCR-CMU-002 작성자 조회수 제외(커밋 f0cafb8, 2026-11-09), SCR-CMU-002 품종 선택 항목·만료 7일·1일 전 알림(PR #190, 2026-11-06), SCR-CMU-005 14일 무응답 자동 종료(PR #186, 2026-11-06), SCR-CMU-001·002·004·005 탈퇴 회원 표시(PR #164·#166·#170·#172)·SCR-ADM-022 완료 사진 열람(2026-11-05), SCR-CMU-004 펫돌봄 대기 4건(2026-11-04), SCR-CMU-002 전북 밖 좌표 거절(PR #154)·시드 게시판 코드(PR #147), SCR-CMU-005 펫돌봄 채팅(PR #139) — 모두 끝남

## 다음에 할 것
- 진행 중: SCR-CMU-004 거래완료 글 공개 목록 숨김. 검증 루프 3회차 실패로 보관 중. 기획은 고치지 않는다. 루프를 버리려면 `! sh scripts/verify-loop.sh clear`. 회차별 실패는 모두 「화면 확인 못 함」(검증 에이전트가 공용 DB 조회 권한이 없어 Swagger 확인 불가)이고 코드·테스트·문서는 기획과 맞다(3회차 보고). 1회차에는 운영 설정 테스트 단언 부족도 있었고 `AdminSettingHideDaysTest`로 고쳤다
- 사람 확인 결과(2026-11-16, 8080 새 코드): 공용 DB에 v0.14 (17) ALTER 실행됨(`SHOW COLUMNS` decimal(2,0), 기본 7). 설정 7일·184번 회원 `GET /api/care-recruitments?rcrtSttsCd=PCAC0003&size=50` → 109번(완료 11-10)만 있고 3번(완료 10-02) 없음. 설정 3일이면 둘 다 없음. 작성자 1번 `?mineYn=true` → 3·109 모두 있음. `GET /api/care-recruitments/3` → 200. 관리자 `PATCH /api/admin/settings` `{"dlngCmptnHideCrtrDayCnt": 3}` → 반영, `100` → 400 `INVALID_INPUT_VALUE`. 설정은 7로 되돌림
- 사람 확인 중 찾아 고친 버그: 설정 조회를 `PageHelper.startPage` 뒤에서 해 설정 쿼리에 `LIMIT`이 붙어 목록이 500 → `search`에서 설정을 먼저 읽게 고침(3회차 검증 대상 코드에 포함)
- 다음 세션: 위 사람 확인 결과로 `/verify`를 다시 돌리고, 통과하면 `/end`. 프론트 연결: 관리자 운영 설정 화면 커뮤니티 묶음에 `dlngCmptnHideCrtrDayCnt` 입력칸(LJS, 프론트 저장소에서 /start)
- 연결 남음: SCR-CMU-004 지난 글 자동 마감 (프론트 연결 펫돌봄 목록·상세 배지 `PCAC0003`+`matched=false`면 「모집 종료」, 프론트 저장소에서 /start)
- 사람이 할 일: PR 병합 뒤 공용 DB에 `SYSC0033`(기준 데이터 「v0.14 (16)」) INSERT가 들어갔는지 확인하고, 팀에 "매일 00:00 배치가 지난 모집중 펫돌봄 글을 거래완료로 마감한다(첫 실행 때 시드의 지난 글이 정리됨)"고 알린다
- 사람이 할 일: 기획 담당에게 커뮤니티 기능명세서 FN-CMU-004-01 「기본 최신순」을 「모집 상태(모집중 → 매칭완료 → 거래완료) → 같은 상태는 최신순」으로 고쳐 달라고 알린다. 관리자 펫돌봄 목록은 최신순 그대로
- 사람이 할 일: 배포 담당에게 운영 서버 비공개 설정·환경변수에 `app.kakao-local.rest-api-key`가 있는지 확인(없으면 배포 뒤 실종·펫돌봄 글 작성이 502). 운영에 `app.region.client`를 따로 정해 두었는지도 확인
- 사람이 할 일: 장소 검색 `app.kakao-local.client`가 운영에서도 설정이 없어 `fake`라고 YAY·LJS에게 알린다
- 사람이 할 일: 디스코드의 `application.properties`에서 `app.news.query` 줄을 지우거나 주석으로 바꾸고 팀에 알린다(한글이 깨져 뉴스 수집이 0건이 됨)
- 사람이 할 일: 공용 DB에 `SYSC0031`(「v0.13 (3)」, PR #186) INSERT가 들어갔는지 확인(`SET NAMES utf8mb4;` 먼저, PR에 완료 댓글). 팀에 "무응답 배치가 첫 실행 때 시드의 오래된 매칭(2026-11-06 확인 15건)을 정리한다"고 알린다
- 확인 방법 메모: 배치 수동 실행은 대상 전체를 바로 처리한다. 사람 확인 전에 대상 건수부터 조회한다
- 다음 작업: 로그아웃 때 채팅 소켓 끊기는 백엔드 작업 없음(사람 결정 2026-11-11). 로그아웃한 창은 프론트가 이미 끊고(`UserHeader`의 `useChatUnreadCount(Boolean(me))` → `deactivate`), 다른 탭·채팅 창에 로그아웃을 알리는 것은 프론트 저장소에서 /start. 탈퇴 회원 표시는 커뮤니티 끝남(문의·신고는 관리자 전용이라 대상 없음), 남은 후보는 공동 보호자 목록(`PetMapper.selectCaregivers`, pet 담당 확인)·세 회원 DB 테스트(새 의존성, 팀 합의)
- 사람이 할 일: 프론트에 「작성자를 눌러 이동하는 곳이 있나, 탈퇴 회원을 막아야 하나」를 묻는다(필요하면 후속으로 그 응답에만 탈퇴 여부 필드. 상세·댓글 응답에는 `mbrSn`이 그대로 나간다). LJS에게 `PostMapper.xml`·`CommentMapper.xml` 관리자 쿼리 병합 충돌을 미리 알린다
- 메모: 시드 사진은 첨부 루트(`app.file.root-dir`, 비우면 `FileRootResolver`의 OS별 기본 경로) 아래 `seed/`에 있어야 보인다. 이 PC는 `uploads/seed`를 복사했고 완료 사진 78장은 있는 펫돌봄 사진을 돌려 채웠다(2026-11-09). 아직 파일 없는 시드 사진 `seed/chat` 159·`seed/inquiry` 102·`seed/news` 14는 채우지 않기로 함(사람 결정 2026-11-09). 팀원에게 `uploads/seed` 압축본과 복사 위치를 공유한다.
- 사람이 할 일: PR 병합 뒤 팀에 "공용 DB 펫돌봄·실종 시드 글 지역을 전북 읍면동으로 바꿨고, `GET /regions`가 전북만 준다"고 알린다. 로컬 `app.region.client`는 `kakao`로 팀에 공유함(2026-11-11, 키 필요)
- 사람이 할 일: 공지 정렬 확인으로 `UPEND_FIX_YN='Y'`로 바꾼 공지가 있으면 `UPDATE NTC_MTTR SET UPEND_FIX_YN='N' WHERE NTC_MTTR_SN=<번호>;`로 되돌린다. 공지 작성은 DB가 아닌 관리자 화면이 생기기 전까지 직접 넣는다
- 정리: 시험용 실종 글 101~104(공용 DB) 중 지우지 않은 것은 `DELETE /api/lost-reports/{lostTpofSn}`로 지운다(글 104 포함)
- 사람이 할 일: 디스코드에 공유한 `application.properties`와 팀원 각자 설정의 `spring.servlet.multipart.max-request-size`를 100MB로 바꾼다(배포 서버 설정이 있으면 같이)
- 정리: 검증·확인용으로 만든 문의 223·225·226·227·229·203(203은 삭제 확인 중 DEL_YN='Y'로 이미 지움, 공용 DB, 첨부 파일 4082~4085·4088~4097 포함)와 `uploads/`의 테스트 이미지. 본인 접수 상태 문의는 `DELETE /api/inquiries/{번호}`로 지울 수 있고, 답변된 문의는 직접 정한다
