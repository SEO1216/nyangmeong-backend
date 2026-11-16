# LJS 핸드오프

## 지금 하는 것

SCR-ADM-110 운영 설정 · 거래완료 글 숨김 기준 일수 검토·검증 서버 반영 — 끝 (2026-11-16). 코드 수정 없음. 8081 검증 백엔드(14:41 기동, KSJ 13794f1 포함)를 그대로 두고 사람이 관리자 운영 설정 화면에서 거래완료 글 숨김 기준 칸을 확인했다(사람 확인 2026-11-16, 시작 2026-11-16 14:49)

## 다음에 할 것

- SCR-MYP-010 연결 남음: FN-ADM-042-07 (프론트 연결 관리자 1:1 문의 상세 캐릭터 오류 처리 뒤 Python `POST /ai/admin/characters/jobs/{regenJobSn}/regenerate` 호출·재생성 상태 표시·실패 때 다시 처리 버튼, 프론트 저장소에서 /start. 프론트 마일스톤 절 "SCR-ADM-042 1:1 문의 · 캐릭터 무료 재생성 Python 호출 — 대기" 넣음)
- SCR-MYP-010 연결 남음: FN-MYP-010-23 (ai 연결 첫 생성 success에 `photo` 파트(확장자 jpg·png·webp, 업로드 원본 그대로) 보내기와 사진 문제 failRsn `CHARACTER_INPUT_ERROR`, ai 저장소 다음 세션. 재생성 입구는 ai-39가 구현함). 백엔드 develop 병합 뒤 ai-39·frontend-07에 화면 확인해도 된다고 알린다

- SCR-MYP-011 연결 남음: FN-MYP-011-05 (프론트 연결 `features/pet/character/ItemShopModal.jsx`가 `IDEMPOTENCY_KEY_REUSED`를 받으면 새 `idmptDmndId`를 만들고 상점을 다시 읽기, `PURCHASE_ERRORS` 문구, 프론트 저장소에서 /start. 프론트 마일스톤 절 "SCR-MYP-011 아이템 상점 모달 · 구매 멱등키 재사용 409 처리 — 대기" 넣음). 그 전까지는 이 오류를 받으면 모달을 닫았다 열어야 다시 살 수 있다
- 남김(SCR-MYP-010 파일 저장 경로 재구성 범위 밖): 기존 공지·뉴스 이미지(`public/…`) 이동(파일 이동·`PHYS_STRG_PATH` UPDATE·공지 본문 URL 교체, 운영 디스크 작업이라 사람), 영수증 원본 저장 중단(따로 기획), 지운·교체한 파일 정리 배치, `AdminNewsService.create`의 `atchFileSn` 존재·위치 검사, `byte[]` 저장의 `FILE_MAX_SZ` 검사, 채팅·문의 이미지 상한 상수 10
- 정하기(사람): 기존 아이템을 그림 참고로 바꿀 때 이미 구운 착용 시트를 다시 굽게 할지(지금은 `bakeImgRefYn`을 바꿔도 시트를 지우지 않는다. 굽기 지시를 바꾸면 지우는 기존 동작과 다르다)
- 사람: 관리자 기능명세서 FN-ADM-110-01 비고 "대시보드 케어 최대 노출 수 포함(2026-10-30 재결정)" 정리(ERDCloud 컬럼은 2026-11-16 삭제함)
- 참고(검증 에이전트, 2026-11-10): `application-local.properties`의 `jdbc:log4jdbc:` 주소로 bootRun하면 "Failed to determine a suitable driver class"로 뜨지 않았다. 검증은 `--spring.datasource.url=jdbc:mysql://...`로 덮어써 띄웠다. 원인은 확인하지 않음

- SCR-AIM-012 쓰기 도구 연결 남음: FN-AIM-012-05·06·07·13, FN-AIM-013 (프론트 연결 쓰기 도구 범용 확인 카드, 프론트 저장소에서 /start. 프론트 `milestones/LJS.md`에 `쓰기 도구 범용 확인 카드 — 대기` 절 넣음): `call.contentType` multipart면 form-data·`type: file`은 사진 선택, `type: array`는 `items` 줄 목록, `generated: "uuid"`는 실행 때 채움, `draft_expense` 전에 `/api/expenses/duplicate-check`
- 정하기(사람): 대화로 쓴 기록의 출처 코드를 일정 외 테이블에도 남길지(DB 변경, 2026-11-06 범위 밖으로 뺌)
- SCR-AIM-011 가계부·반려동물·커뮤니티·플레이스·FAQ 읽기 도구 ai 연결 남음 (ai 저장소에서 /start, 아래 건강 READ 실행 경로 ① 뒤): 27개 도구 설명 `data/tool_prompts.yaml`·스냅샷 갱신. 설계 `docs/design/tool-catalog.md` 2-1절 고칠 것: `/{pixelCharSn}/held-items`는 GET이 없다(보유는 `get_pixel_character`·`list_shop_items`의 `hldYn`), `GET /api/caregiver-invitations`는 보낸 초대(`list_sent_caregiver_invitations`)
- 선택(가계부 담당 SJM, 테스트 중 문제가 생기면 진행. 사람 2026-11-06): `get_expense_statistics.yearMonth`는 `String`이라 도구 목록에 `pattern`이 안 실리고, 틀린 형식은 서비스 `YearMonth.parse` 실패로 칸 이름 없는 `INVALID_INPUT_VALUE` 400이 된다(`ExpenseQueryService.parseMonth`). ai 도구 설명에 YYYY-MM이 이미 있고 Python은 `pattern`을 쓰지 않는다. `@Pattern`을 붙이면 400에 칸 이름이 실린다

- 판단 필요(SCR-ADM-001): 대시보드 전체 게시글 수에 삭제 글을 넣을지. 지금은 기획 완료 조건(관리자 게시글 목록 기본 건수와 같게)대로 넣는다. 빼려면 `PostMapper.countAll`·`selectRecentPosts`에 상태 조건을 넣고 `docs/api/admin.md`를 고친다
- SCR-ADM-082 연결 남음: FN-ADM-082-03 (프론트 연결 `features/adminProcessing/api.js` `getAdminPdfAnalyses` 검사결과지 분석 탭, 프론트 저장소에서 /start). 프론트 마일스톤에 절 넣음(`SCR-ADM-082 처리 현황 · 검사결과지 분석 현황 API 연결`). 프론트에 알릴 것: 경로·필드는 가짜 그대로, 검사결과지가 아닌 파일은 서버가 따로 빼지 않고 Python이 성공으로 통지해 `scsCnt`·`totCnt`에 들어가고 실패 목록에는 없다, 계약은 `docs/api/global.md` `/admin/processing/pdf-analyses` 행
- SCR-ADM-082 연결 남음: FN-ADM-082-03 (ai 연결 `app/checkup` PDF 분석 호출마다 `POST /internal/ai/external-api-calls` `{ extlApiSeCd: "SYSC0025", callRsltCd, rspnsMs, httpSttsVl, failRsn }` 통지, 검사결과지 아님 판정은 SYSC0021(성공), 시간초과 SYSC0023, API 오류 SYSC0022, ai 저장소에서 /start). 지금 ai 저장소는 이 통지를 보내지 않는다
- SCR-ADM-081 연결 남음: FN-ADM-081-01·02 (프론트 연결 `features/adminAiKeyword/api.js` 목록·등록/수정 모달·삭제, 프론트 저장소에서 /start). 프론트 마일스톤에 절 넣음(`SCR-ADM-081 키워드 사전 · 관리자 키워드 사전 API 연결`). 프론트에 알릴 것: `useYn`은 true/false, 행에 `mpngCdNm` 추가, 404 `AI_KEYWORD_NOT_FOUND`(가짜 `KEYWORD_NOT_FOUND` 아님), 서버가 `kwrdClsfNm`을 글자 그대로 검사하므로 쉬운 말 바꾸기는 화면 표시만, 계약은 `docs/api/ai.md` "관리자 API" 절
- SCR-ADM-081 연결 남음: FN-ADM-081-01 내부 제공 (ai 연결 `app/chat/rules.py`가 `data/register_dict.yaml` 대신 `GET /internal/ai/keywords?dictUsgCd=AIMC0037`을 읽게, ai 저장소에서 /start). ai에 알릴 것: 응답 행 `{ kwrdDictSn, kwrdClsfNm, artclNm, kwrdCn, intrpRsltCn, mpngCd }`, 사용 중 행만 `sortSeq` 순, '판정 규칙'·'시간' 행도 온다, `kwrdCn`은 예시 문구(`○일 후` 등)라 정규식 변환 규칙은 ai가 정한다(결정 ① 추천안). 가계부 분류(AIMC0038)를 누가 쓸지(OCR 분류 등)도 함께 정한다

- 데이터 문제(SCR-ADM-071, SJM·팀과 정한다): 공용 DB `CARE_MSN`을 다시 넣어 번호가 22부터라 옛 `DAY_MSN` 22,772행이 없는 `CARE_MSN_SN`(1~21)을 가리킨다. 케어미션 배치는 11-10 이후 다시 성공한다(2026-11-16 확인, 실패는 11-11~13의 3건). 남은 것은 이 옛 행을 지울지 정하는 것

- SCR-ADM-051 남김: FN-ADM-051-04 기능별 동의 2종(펫돌봄 이용수칙 `CARE_RULE`·실종 개인정보 공개 동의 `LOST_PRIVACY`). 커뮤니티가 동의 문구를 `TRMS`에서 읽게 될 때(KSJ) 넣는다. 그때 가입 쪽 쿼리 4개(`TermsMapper.xml` 가입 목록·가입 동의 검사·복구 빠진 필수 약관·관리자 회원 상세 약관 동의)가 섞이지 않게 가르는 방법(컬럼 추가 등)을 YAY와 정한다
- 판단 필요(SCR-ADM-051): `GET /api/admin/terms/history`에 `trmsFixIdntfKey`를 빼면 문서(`docs/api/member.md`)는 400 `INVALID_INPUT_VALUE`인데 실제는 400 `BAD_REQUEST`(필수 파라미터 누락). 문서를 고치거나 `@RequestParam(required = false)`로 바꾼다

- 정하기: 뉴스 제외 키워드(AIMC0040) 편집 화면. 기준 데이터 주석에 "관리자 SCR-ADM-050에서 편집"이라고 돼 있지만 기능명세서·화면설계서·프론트 가정에 없어 만들지 않았다. 넣는다면 명세에 기능 ID를 추가하고 같은 키워드 API에 용도 구분을 더한다
- SCR-ADM-022 연결 남음: FN-ADM-022-02 완료 사진 보기. `/api/files/{atchFileSn}`은 로그인 회원 누구나 여는 분류(`FileCategory` SHARED)만 열어 지금 완료 사진(시드 3543·3544)은 404다. 완료 사진 업로드가 아직 없어(KSJ) 저장 경로를 정할 때 관리자가 볼 방법(공개 경로 또는 관리자 이미지 API)을 같이 정한다
- SCR-MYP-031 연결 남음: FN-AIM-013-06 가계부 출처 영수증 다시 열기 (프론트 연결 가계부 화면의 영수증 다시 열기, 화면 SJM, 프론트 저장소에서 /start). SJM에게 알린다: `GET /api/receipts/{recptSn}`을 `petSn` 없이 부르면 된다(응답 `petSn` null, `docs/api/receipt.md`). 프론트 저장소 마일스톤 절은 옆 폴더가 없어 넣지 못했다(아래 정리 항목)
- 프론트 마일스톤(사람): 프론트 저장소 가계부 담당(SJM) 마일스톤 `## 제외` 앞에 `## SCR-MYP-031 영수증 업로드 · 가계부 출처 영수증 다시 열기 연결 — 대기` 절(기능: 명세 기능 ID 추가 없음, 걸치는 기능 FN-AIM-013-06·FN-MYP-031-03 / 기다리는 것: 백엔드 SCR-MYP-031 가계부 출처 다시 열기(LJS, 끝) / 완료 조건: 가계부 화면에서 올린 영수증을 `petSn` 없이 다시 열어 인식 결과 확인 모달이 뜬다)을 넣는다
- 판단 필요(SCR-ADM-070, ai-47 검토 2026-11-03, 사람이 보류): 곰돌이 후드티 굽기 지시는 모자를 쓴 모습으로 구워져 귀·머리 아이템과 겹칠 수 있어 `a brown pullover dog hoodie with the hood down and small round bear ears on the hood`로 고치기를 권함. 선택: 빨간 리본·작은 왕관은 위치 말 빼기(`a red satin ribbon bow`, `a small shiny golden crown`), 꼬까 한복은 끝에 `with a tied front ribbon`. 고치면 공용 DB `UPDATE ITEM SET BAKE_HINT=... WHERE ITEM_NM=...`와 변경 절 새 항목·시드 파일을 같이 고친다.
- 판단 필요(SCR-ADM-070): 순서 저장 400 `INVALID_INPUT_VALUE`는 어떤 번호가 빠졌는지 알려 주지 않는다(사람 확인 때 헷갈림). 메시지를 붙일지 정한다. 새 아이템 12개는 이미지가 빨간 리본·노란 우비와 같다(디자인 이미지가 오면 교체)
- SCR-ADM-042 연결 남음: FN-ADM-042-07 무료 재생성. 생성 흐름이 원본 사진(`FILC0006`)을 Java에 남기지 않아 관리자 [무료 재생성]은 404 FILE_NOT_FOUND로 롤백된다(복원만 실제 동작). FN-MYP-010-23 계약과 함께 정한다(LJS·ai)
- SCR-ADM-042 연결 남음: FN-ADM-042-08 (작성자 매칭 목록. SCR-ADM-022 상세는 모집글 번호 기준이라 문의 작성자 기준 목록(`?mbrSn=`)이 필요하면 따로 만든다, KSJ 협의)
- 정하기: 옛 흐름으로 만든 캐릭터(시드 포함)는 맨몸 시트가 없어 미리보기가 404 `PIXEL_CHARACTER_NOT_FOUND`이고, 결과 사진이 있어 다시 생성도 `PIXEL_CHARACTER_ALREADY_EXISTS`로 막힌다(ai-d1 확인, 2026-11-02). 시트를 채울지(재생성), 시드를 새 흐름으로 다시 만들지 정한다
- 굽기 켜기 전(AI 저장소와 함께): 백그라운드 호출이 쿠키를 쓰므로 `access_token`(30분)이 생성·굽기 중에 만료되면 성공 보고·시트 저장이 401이다. 성공 보고를 잃은 작업은 10분 뒤 시간 초과로 실패하고 다시 요청할 수 있다
- 테스트 부족: 시간 초과 조회(`PRCS_BGNG_DT < NOW() - INTERVAL 10 MINUTE`) 경계를 실제 DB에서 돌려 보지 않았다(매퍼 목)
- 판단 필요(시드): 시드의 자동 블라인드 이력 18건은 사유가 `신고 누적으로 블라인드`라 `신고 누적`과 달라 자동 블라인드 탭에 나오지 않는다. 시드를 고칠지 탭 판별을 넓힐지 정한다. 이 코드 이전에 자동 삭제된 댓글도 나오지 않는다
- 명세 정리(사람): 설정 화면 FN-COM-008-11 항상 받는 알림에 신고 처리 결과를 넣을지 정한다(2026-11-02 결정: 댓글 알림 설정과 무관하게 보낸다. 앱 알림 채널을 끄면 안 간다)
- 테스트 부족: 복구 뒤 재삭제 판별(`autoTargetCondition`의 `MDFCN_DT` 비교)은 SQL 조건이라 자동 테스트가 없다
- 실제 DB 확인 범위(SCR-ADM-020): 사람 확인 내용은 `확인`이라고만 기록됐다. 검증 에이전트는 공용 DB라 블라인드·복구·댓글 삭제 성공 경로를 실행하지 않았다
- 실행해 보지 못한 SQL(SCR-ADM-011): 재동의 시 최신 1행, 동의 기록 없는 약관(`TermsMapper.selectAdminAgreementList`), 해제된 연동 제외(`PetMapper.selectAdminList`). 자동 테스트는 매퍼 목이고 공용 DB에 해당 데이터가 없어 SQL 읽기로만 확인했다. 파기 회원 상세도 데이터가 없어 코드로만 확인했다
- 판단 필요(SCR-ADM-113): 지정 기록이 없는 기존 관리자는 `apntDt`가 null이다. `MBR_AUTHRT_CD`에 인덱스가 없어 해제 트랜잭션 동안 `MBR`의 많은 행이 잠길 수 있다(부하 상황 확인 안 함)
- 탈퇴 파기 남은 범위: 채팅 원문(REQ-NFR-002, 채팅 구현 뒤 KSJ와 `scheduler`로 묶는다), 프로필 파일 실물(`FileStore`에 삭제 없음, 지금은 연결만 끊는다), 게시물·댓글·포인트·반려동물·건강기록(정책 미확정, `docs/api/issues.md` 8번)
- 테스트 부족: `selectWithdrawnList`의 보관 기간 경계를 검사하는 매퍼 테스트가 없다. 검증은 같은 조건을 바꾼 읽기 전용 SELECT로 했다
- 판단 필요: 구현 계획에는 정지 안내 알림이 있으나 기능명세서에 없어 넣지 않았다. `suspnDayCnt`가 빠진 정지 요청은 영구 정지로 처리된다(프론트 계약). 만료 배치는 하루 한 번이라 00:00에 서버가 내려가 있으면 수동 실행 전까지 정지가 이어진다
- 판단 필요: 잘못 인코딩된 `q`(UTF-8 아님)는 400이 아니라 500이다(Tomcat 디코딩 실패, 이번 기획 밖). 검증의 `dev-tokens`(회원 1·2)가 공용 DB에 세션을 남겼는지 확인 못 함
- SCR-ADM-112 연결 남음: FN-ADM-112-01·02·03 (프론트 연결 관리자 외부 API 사용량 화면 SCR-ADM-112, 프론트 저장소에서 /start. 프론트 마일스톤에 절 있음), FN-ADM-112-02 (Python이 `POST /internal/ai/external-api-calls`를 부른다, 호출마다 쿠키가 있어야 하고 쿠키 없는 백그라운드 호출은 기록되지 않는다, ai 세션)
- 배치 위험(판단 필요): 서버 기동 때 공용 DB의 실행중 행을 서버 구분 없이 실패로 바꾼다(끝나면 실제 결과로 덮어씀). 수동 실행 풀(스레드 4·큐 0)이 거부하면 잠금과 실행중 행이 재시작까지 남는다(코드 확인, 재현 안 함). `SchedulingConfig.java:11` 주석의 "@Scheduled 배치"는 낡음
- 관리자 문서 후속(사람): 기능명세서 FN-ADM-111-02와 화면설계서 SCR-ADM-111에서 실패 건수를 뺀다(2026-11-01 결정, 컬럼 없음)
- PR 전 확인(사람): `LoginService`에 관리자 로그인 기록을 넣은 것을 YAY와 협의. 저장소에 협의 기록 없음
- 공통코드 화면(SCR-ADM-100) 제외 후속(사람, 2026-11-01 결정: 코드 변경이 드물어 만들지 않고 SQL로 처리): 요구사항정의서 REQ-ADM-010, 관리자 기능명세서·화면설계서 SCR-ADM-100, IA 구조도(DMS)를 고치고 프론트 사이드바 메뉴를 뺀다. `docs/admin/implementation-plan.md`는 반영함. 다시 만들게 되면: 공통코드 enum 44개 중 값마다 로직이 갈리는 enum은 추가해도 쓸 수 없고, `InspectionArticle` 등 허용 목록 enum은 DB 검사로 바꿔야 추가한 코드를 쓸 수 있다
- 관리자 다음 마일스톤 전 확인(사람): 구현 계획 6절 확인 항목(인기 포토 제외·RSS 피드·알림 기본값 저장 위치, 기준정보 28번). REQ-ADM-014~022 요구사항정의서 반영은 별도 에이전트가 맡는다(2026-10-30 사람). 다른 담당 도메인 화면은 계획 2절 "협의" 표대로 먼저 이야기한다. 감사 로그가 필요한 관리자 기능은 `AdminTaskLogService.record`를 `@Transactional` 안에서 부른다
- 관리자 문서 후속(사람): DMS 기능명세서 폴더에 "관리자" 하위 폴더를 만들어 명세서를 옮긴다(도구로는 폴더 생성 불가). 공통레이아웃 FN-COM-006-04 건강기록 2차 메뉴명과 랜딩 화면 ID(SCR-LAN-001 vs SCR-MAN-002) 정리. 프론트 저장소 `milestone.md` 담당 표에도 관리자 추가(frontend-83에 전달함)
- SCR-AIM-002 연결 남음: FN-AIM-002-02·11 (Python AI 서버가 `GET /internal/ai/settings`를 붙인다, ai 세션). PR을 올리면 ai 세션에 PR 번호와 계약(경로·응답 `AiSettingResponse`)을 알린다(ai 저장소 `docs/api/internal.md`와 같은 때 병합)
- 시드 사진(파일 저장): 공용 DB(`pawever`)는 옛 시드라 사진 경로가 날짜 폴더여서 `/api/files/{번호}`로 404다. 다음에 공용 시드를 넣을 때 반영. 기존 `uploads/` 파일은 새 루트로 옮기거나 `seedFiles` 다시
- 운영(사람): 서버에 저장 폴더와 쓰기 권한, nginx `client_max_body_size` 10m 이상. SJM에게 알린다: 캐릭터 결과·착용 이미지는 로그인 회원 누구나(`FileCategory` SHARED), 착용 시트는 도메인 API 전용이다. 공동양육자에게만 보이게 하려면 `FileCategory`를 OWNER로 바꾸고 캐릭터 이미지 API로 준다
- 프론트 저장소: 같은 이름 스크립트에 같은 macOS 수정(`$( )` 안 `case (패턴)`, `sed -E`, `sed -i` 대신 임시 파일·`mv`, `{…;}`, 한글 앞 `cut -c` 대신 `sed`)을 적용하고, 커밋 전인 `verify-loop.sh` `scripts` 보호·verify·end 스킬 수정과 함께 프론트 PR
- 하네스 아직 못 돌려 본 것: `pr-prep.sh --allow-dirty`(다음 PR 때), 검증 3회 실패 넘기기(다음 화면 작업)
- 프론트 `start` 스킬에도 `docs/api`(백엔드 저장소) 기준 줄을 넣을지 정한다
- 정하기: 휴대폰 일정 가져오기(기획에 없음, JAJ 영역). 넣는다면 QR 짝짓기 `.ics` 업로드, iPhone 단축어, 웹 → 휴대폰은 `webcal://` 구독 QR
- 정하기: 파일 저장소 운영(지금 서버 로컬 디스크 폴더. 서버 여러 대·재배포 대비 영구 볼륨 또는 S3), 영상 업로드 형식·용량
- 정하기: HLD_ITEM 유일 키 `UK_HLD_ITEM_1`에 DEL_YN이 없다. 보유 아이템 소프트 삭제가 생기면 재구매가 막히므로 그때 키를 고친다
- 디스코드 공유 `application.properties`: `app.front-base-url=http://localhost:5173` 추가, 팀 공용 `app.crypto.*`·`app.jwt.secret-base64` 키를 정해 넣고 `mybatis.configuration.map-underscore-to-camel-case`는 넣지 않는다
- 팀 안건: application.properties를 일반 설정(커밋) + git 제외 `application-secret.properties`로 나눌지 `docs/OPEN_ISSUES.md`에 올린다
