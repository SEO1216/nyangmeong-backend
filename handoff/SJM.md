# SJM 핸드오프

## 지금 하는 것
SCR-MYP-020 포인트 관리/케어미션 · 케어미션 개편(등급 배점·출제 대상) — 끝남 (승인 2026-11-18 11:18, 시작 2026-11-18 11:09). 검증 3회차 통과(1·2회차는 `화면:` 확인 못 함으로 보관, 3회차에 새 반려동물 등록으로 확인). 사람이 Postman 11개 테스트 통과(회원 58, 반려동물 1069 삭제함: 8개·합 60P·조합 (1,2,5)·케어리스트 미션은 기본 4개 안). `v0.14 (21)` UPDATE는 공용 DB에 실행하지 않아 식사량 달성 기준 1·옛 이름 그대로가 정상
정책(※신규 정책, 마일스톤 절 기준): 등급별 고정 배점 쉬움 5·보통 10·어려움 15P, 하루 60P. 조합 (어려움,보통,쉬움) = (0,4,4)·(1,2,5)·(2,0,6) 중 후보로 만들 수 있는 것을 무작위로 골라 8개. 못 만들면 그날만 가중치 1·2·3 비율 배분. 케어리스트 미션은 보이는 기본 항목 중 매일 항목과 예정일이 된 정기 항목만. 정산은 50% 이하 0P 없이 진행률만큼. 미션 이름 4개 변경, 식사량 달성 기준 2회.
- FN-MYP-020-05 배점·등급: `pet/domain/CareMission.java`에 `Grade`(EASY 5·NORMAL 10·HARD 15, 가중치 1·2·3)와 미션별 등급, 이름 4개 변경(발 상태 확인하기·눈곱 닦기·발바닥 털 다듬기·잇몸·치석 확인하기), 기본 항목 없는 반려동물의 후보 4개(화장실 청소·양치·빗질·놀이) 상수. `CareMissionService`의 `DAILY_COUNT`·`ACTIVITY_COUNT`를 `DAILY_TOTAL_PNT = 60`·`ISSUE_COUNT = 8`로, `DAY_MSN.SCOR_PNT`는 `CMPN_PNT` 대신 등급 배점. `CareMissionPool` 주석만 고침(출제에 안 씀, `MSN_POOL_CD`는 그대로). 완료 조건 `명령: ./gradlew build -q`, `CareMissionServiceTest`: 등급 배점 합 60, 조합 3개 중 가능한 것만 고름, 조합 불가 시 가중치 배분 합 60(남는 포인트는 높은 등급부터·같은 등급은 출제 순서대로 1P씩), `CMPN_PNT`를 바꿔도 배점 같음
- FN-MYP-020-04 출제 후보: `health/service/CareItemService.java`에 `searchMissionCandidates(LocalDate today)` 추가(반환 `Map<Long, Set<CareMission>>`, 기본 항목이 있는 반려동물만 키). `mapper/health/CareItemMapper.xml`·`CareItemMapper.java`에 삭제 안 된(`DEL_YN = 'N'`) 기본 항목의 `PET_SN`·`CARE_ARTCL_NM`·`CARE_ZONE_CD`·`INDCT_YN`·`NXTM_PRNMNT_YMD` 조회. 보임 판정은 화면과 같게 `INDCT_YN = 'Y'`, `NULL`이면 추천 항목(`BasicCareItem.isRecommended`). 매일(`HLTC0036`)은 후보, 정기(`HLTC0037`)는 예정일 ≤ 오늘일 때만, 예정일 비면 제외. `scheduler/CareMissionJob.java`가 받아 `careMissionService.issue(today, 관찰 중, 후보)`로 넘김. `CareMissionService.issue`·`issueFor`는 후보 맵에 없는 반려동물(등록 직후 `PetCreatedEventListener` 포함)에 기본 4개를 씀. 기록·활동 미션 10개는 지금 조건(개·관찰 중·대표) 그대로. 완료 조건 `명령: ./gradlew build -q`, `CareItemServiceTest`(숨김·삭제·정기 예정일 전·예정일 빈 항목 제외, NULL 추천 항목 포함), `CareMissionJobTest`(후보 전달), `CareMissionServiceTest`(맵에 없는 반려동물은 기본 4개만 케어리스트 후보), `화면: POST 없음 — 배치를 로컬에서 켜고 시험 반려동물의 GET /pets/{petSn}/daily-missions 8개·scorPnt 합 60 확인`
- FN-MYP-020-05 정산: `CareMissionService.settledPoint`에서 50% 이하 0P 삭제(식사량 1/2 → 5P). 기존 테스트 `settledPoint(10, 1, 2)` 기대값 0 → 5(정책 변경). 완료 조건 `명령: ./gradlew build -q`
- 기준 데이터: `db/20261109_insert_base_data_v0.14.sql` 기준 행의 미션 이름 4개·식사량 `ACHV_CRTR_NMTM` 2, 맨 위 변경 절 `v0.14 (21)`(`CARE_MSN` `MSN_NM` 4건·식사량 `ACHV_CRTR_NMTM` UPDATE, 사람이 공용 DB에 실행). `db/seed/20261028_seed_data.sql` 118줄 `CARE_MSN` 이름 4개·식사량 2(660줄 포인트 메모는 그대로). `MasterSeeder`는 이 저장소에 없음 → LJS에게 알림을 "다음에 할 것"에. 완료 조건 `명령: ./gradlew build -q`
- 문서: `docs/api/pet.md` `DailyMissionSummary`의 `scorPnt`(등급 배점 5·10·15, 합 60), `achvCrtrNmtm`·`givePnt`(진행률만큼) 설명. `CareMission`·`CareItemService.MISSIONS` 주석. 기능명세서는 이 세션에서 고치지 않는다(문구는 "다음에 할 것")
- API 변경 없음: `GET /pets/{petSn}/daily-missions` 행·DTO 그대로. 미션 목록의 배점 표시는 이미 `scorPnt`를 읽어 바꿀 것 없음
- FN-MYP-020-05 프론트 연결: `features/point/guide/PointGuide.jsx` 6행 "미션마다 1~20P가 쌓여요"를 등급 배점(쉬움 5·보통 10·어려움 15P, 하루 60P) 기준 문구로 (프론트 저장소에서 /start)
- 관리자 영향: `PATCH` 관리자 미션 수정의 `cmpnPnt`는 출제에 안 쓰이게 됨(바꿔도 효과 없음). 이번엔 API를 그대로 두고 "다음에 할 것"에 남김. `useYn`·`achvCrtrNmtm`은 지금처럼 다음 출제부터 반영
- 배포 날 이미 출제된 미션: `DAY_MSN`에 복사된 값으로 정산, 따로 처리 없음
새 의존성: 없음

## 다음에 할 것
- 배포 순서(케어미션 개편): `v0.14 (21)` UPDATE(미션 이름 4개·식사량 달성 기준 2)는 PR 병합 후 배포 직전·직후에 바로 실행한다. enum과 `CARE_MSN`이 `MSN_NM`으로 짝지어져 있어서 한쪽만 바뀐 동안은 그 4개 미션(발 상태 확인하기·눈곱 닦기·발바닥 털 다듬기·잇몸·치석 확인하기)이 출제에서 빠진다
- 프론트 연결(SCR-MYP-020, FN-MYP-020-05): `features/point/guide/PointGuide.jsx` 6행 "미션마다 1~20P가 쌓여요"를 등급 배점(쉬움 5·보통 10·어려움 15P, 하루 60P) 기준 문구로(프론트 저장소에서 `/start`)
- 문서 작업(`/end` 때가 아니라 사람이 마지막에 한 번에, 케어미션 개편): 기능명세서 FN-MYP-020-03의 "일상케어 5 + 활동기반 3 출제"를 등급 조합(어려움·보통·쉬움 (0,4,4)·(1,2,5)·(2,0,6), 합 60P)으로, FN-MYP-020-05의 "진행률 50% 이하 미지급"을 "진행률만큼 지급"으로, "1~20P 배점"을 "등급 배점 5·10·15P, 하루 60P"로
- LJS에게 알리기(케어미션 개편): `MasterSeeder` 미션 목록(이 저장소에 없음)의 이름 4개·식사량 달성 기준
- 관리자 케어미션 수정(FN-ADM-071-02): `cmpnPnt`는 출제 배점에 안 쓰이게 된다. 수정 화면·API에서 뺄지 정한다
- 프론트 연결(SCR-MYP-003·005, FN-MYP-003-01·FN-MYP-005-05): `PetListPage`·`PetSlide`·`PetEmpty`·수락 화면 안내 문구의 "10마리"를 `GET /pets/limit`의 `petMaxCnt`로 표시, `MAX_OWN_PETS = 10` 제거(프론트 저장소에서 `/start`, 프론트 마일스톤에 대기 절 2개를 넣어 뒀다). 같은 상수를 쓰는 `PetSwitcher`(health)·`InvitationAccept`도 함께 본다
- 문서 작업(`/end` 때, 운영 설정값 연결): 기능명세서 FN-MYP-003-01·FN-MYP-005-05의 "10마리"·FN-MYP-005의 공동양육 5명·초대 7일 문구를 "운영 설정값(기본 10·5·7)"으로
- LJS에게 알리기(SCR-MYP-003 운영 설정값 연결): 공통 오류 처리 파일 4개를 바꾼다(`BaseException`·`CustomException`·`GlobalExceptionHandler`·`ApiResponse`, 문구를 받는 `withMessage`·`errorWithData` 오버로드 추가, 기존 동작은 그대로). 이 외에 공용 `ErrorCode`의 `PET_LIMIT_EXCEEDED`·`CAREGIVER_LIMIT_EXCEEDED` 문구가 `%d` 서식이 된다. PR 전에 알린다
- 프론트 연결(SCR-MYP-003, FN-MYP-003-01): 등록 카드 "모두 등록했어요"를 주양육 + 공동양육 합계 10으로 판정(백엔드 10마리 한도가 끝난 뒤 프론트 저장소에서 `/start`)
- 프론트 연결(SCR-MYP-005, FN-MYP-005-05): 수락 화면에서 `remainingPetCount`를 넘게 고르지 못하게 막고 안내, 수락 400(`PET_LIMIT_EXCEEDED`, data `remainingPetCount`) 처리(프론트 저장소에서 `/start`)
- 문서 작업(`/end` 때, 10마리 한도): 기능명세서 FN-MYP-003-01·FN-MYP-005-05에 "공동양육 포함 10마리" 문구
- 프론트 연결(SCR-MYP-005, FN-MYP-005-03): 보낸 초대 목록 대기중 행 [취소] 버튼·확인, 취소 행 [삭제] 버튼(프론트 저장소에서 `/start`, 프론트 마일스톤에 대기 절을 넣어 뒀다)
- 프론트 연결(SCR-MYP-005, FN-MYP-005-10): 수락 화면에서 상태 `PETC0035`면 "취소된 초대예요" 안내(프론트 저장소에서 `/start`)
- 문서 작업(`/end` 때): 기능명세서의 FN-MYP-005-03(보낸 초대는 조회 전용 → 취소·삭제 추가)·FN-MYP-005-10(취소 안내) 문구 수정. 최신 명세는 DMS 로그인이 풀려 못 봤고 v0.4 기준이다
- 받은 초대 목록 API를 만들 때: 취소·삭제된 초대는 넣지 않는다(대기 중만)
- PR 본문에 "DB 변경 있음: v0.14 (14)·(15)", 범위는 "~" 대신 "–". 두 SQL은 공용 DB 실행 완료라 완료 댓글만 남기고, 병합 뒤 다시 실행하지 않는다(중복 오류)
- 프론트 연결(SCR-MYP-003, FN-MYP-003-01·04): 등록·수정 모달의 체중·목표·최소·최대 체중 입력을 소수 둘째 자리·200kg 이하로 제한(프론트 저장소에서 `/start`, 프론트 마일스톤에 대기 절을 넣어 뒀다)
- JAJ에게 전달(SCR-HLT-002): `PetService.syncWeight`는 건강기록 체중을 `PET_BDWG`에 그대로 넣는다. 건강기록 체중 입력에도 소수 둘째 자리·200kg 이하 검증이 필요하다(회의 확정). 건강기록 체중 때 범위 갱신은 `PetBdwgRange.of()`가 200에서 자른다
- 로컬 설정(SJM): `application.properties` permit-all-paths의 `/api/files/public/**`는 PR #234 병합 뒤 develop을 합칠 때 다시 지운다(git에 올라가지 않음)
- 프론트 연결(SCR-MYP-003, FN-MYP-003-04): 등록·수정 모달이 `bdwgRngDrctInptYn`을 보내고 보여 주기(프론트 저장소에서 `/start`, 프론트 마일스톤에 대기 절을 넣어 뒀다)
- JAJ에게 전달(건강기록 체중 범위 갱신, FN-MYP-003-04): 정상 범위 계산은 `com.nyangmeong.pet.service.PetBdwgRange`(public record `min`·`max`)에 있다. 사용: `PetBdwgRange r = PetBdwgRange.of(기준체중);` 후 `r.min()`·`r.max()`를 `PET.MIN_BDWG`·`MAX_BDWG`에 저장한다(다른 도메인이므로 `pet/service` 클래스를 바로 쓴다). 규칙: 최소 = 기준 체중 × 0.9, 최대 = × 1.1, 둘 다 소수 둘째 자리 HALF_UP(4.25 → 3.83 ~ 4.68). 아주 작은 체중은 최소를 0.01 이상, 최대를 최소+0.01 이상으로 보정한다(`CK_PET_WGHT_RANGE` 유지). 체중이 null이거나 0 이하면 `IllegalArgumentException`. 최대는 200kg(`PetBdwgRange.MAX_BDWG_LIMIT`)에서 자른다. 최소가 200 이상이면 199.99로 자른다. 갱신 전에 `PET.BDWG_RNG_DRCT_INPT_YN`을 확인해 `'Y'`(사용자 직접 입력)이면 범위를 바꾸지 않는다. 마이페이지(등록·수정)는 이미 이 메서드를 쓴다
- DB 공용 반영(SCR-MYP-003): `db/20261109_create_tables_v0.14.sql` 변경 절 v0.14 (12)는 공용 DB에 반영 완료(2026-11-13, 사람이 실행). 병합 뒤 다시 실행하지 않는다(중복 컬럼 오류). PR 본문에 "DB 변경 있음: v0.14 (12)"를 적고 완료 댓글을 남긴다. 기존 PET 행은 DEFAULT로 모두 'Y'
- 팀에 알린다(시드): `db/seed/20261028_seed_data.sql`의 PET·체중 기록(HLTC0014)을 정리했고(품종 직접 입력명, 품종별 털색, 얼룩 미사용, 체중 소수 첫째 자리) `db/20261109_insert_base_data_v0.14.sql` 변경 절 v0.14 (8)로 얼룩(PETC0011)을 사용 중지했다. 공용 DB는 2026-11-10 반영 완료. 이미 시드를 넣은 로컬 DB는 시드를 다시 넣지 말고 팀에서 받은 정리 SQL로 맞춘다
- 확인: 등록 직후 출제(`PetCreatedEventListener`)가 UK_DAY_MSN_1 등으로 실패하면 `issueFor`가 리스너의 REQUIRES_NEW 트랜잭션에 합류해 롤백 전용이 되어, 커밋 때 `UnexpectedRollbackException` 로그가 함께 찍힐 수 있다(등록 응답에는 영향 없음, 다음 정각 배치가 채움). 거슬리면 `issueFor`를 `REQUIRES_NEW`로 바꾸는 것을 고려
- Postman 컬렉션: 3번 회원이 정지(`MEMBER_INACTIVE`)라 그 회원을 쓰는 변수(`mbrSnB`·`emailB`·`delCoMbrSn`·`delCoEmail`·`invReceiverMbrSn`·`infoOtherMbrSn`)가 있는 폴더는 403. 돌릴 때 정상 회원으로 바꾼다(이메일은 `GET /members/me`의 `acntEml`)
- YAY와 정하기(SCR-MYP-040): 본인 확인(6-1)을 통과했는지 `PATCH /members/me`에서 서버가 검사하지 않는다. `POST /members/me/password-verification`을 만들 때 통과 표시를 어떻게 남길지 함께 정한다
- 화면 확인 필요(FN-MYP-002-08): 알림함 COM-004에 공동양육 연동 해제 알림(CO_PARENTING, 이동 경로 `/mypage/pets`)이 보이는지. 백엔드는 `PetService.java:273`에서 끝
- 팀 확인: 공동양육 강제 해제·자진 해제(`PetService.unlinkCaregiver`)에는 알림 발행이 없다. 기획이 이 경로의 알림까지 요구하는지 정하기
- JAJ에게 전달(SCR-MYP-002): `pet/domain/PetDeletedEvent(petSn)`를 `@TransactionalEventListener(phase = BEFORE_COMMIT)`(또는 `@EventListener`)로 받아 DAILY_HLTH_RCD·OBSRV_ARTCL·OBSRV_CYCL·SCHDL·CKUP_RCD(+하위)·REPT_PRVNT_MNG·CARE_ARTCL을 `DEL_YN='Y'`로 바꾸는 리스너, health 조회의 `CKUP_RCD.DEL_YN='N'` 조건. AFTER_COMMIT이면 실패해도 삭제가 커밋되므로 안 된다
- PR 본문에 "DB 변경 있음: `db/migration/20261030_01_add_del_yn_for_pet_delete.sql`(공용 DB `pawever` 2026-10-30 적용 완료)"를 적는다
- LJS에게 알리기(SCR-MYP-004): `scheduler/CaregiverFake` → `PetService.searchCaregiverMbrSns(petSn)`, `expense/CaregiverPetFake` → `PetService.searchMyPets(mbrSn)`의 petSn·petNm으로 바꾸기
- 화면 확인 필요(FN-MYP-030-08): 알림 목록 COM-004에 가계부 한도 알림이 보이는지. 백엔드는 `ExpenseService.java:313·316` 발행으로 끝
- LJS에게 알리기: `ReceiptService.markSavedByExpense`를 더함(인식 실패 후 직접 입력도 영수증을 저장완료로, LJS 파일)
- 배포 후 케어미션 출제·정산 배치가 느리면 개선: 지금은 한 행씩 INSERT·UPDATE하고 전체가 한 트랜잭션이다(로컬 시드 약 960마리로 30분, 2026-10-27 확인). 여러 행을 한 번에 넣고 묶음마다 커밋하도록 `/start`로 기획. 배치는 prod에서만 켜지므로 그 전에는 하지 않는다
- LJS에게 `MasterSeeder` 미션 목록 변경 알리기(21개로 바뀜, `CharacterSeeder`는 그대로)
- KSJ·LJS에게 알리기(SCR-MYP-020): KSJ 펫이야기 글·댓글 → `PetService.reflectRepresentative`(아직 `community`에 호출 없음), LJS `pet/service/PointFake` → `PointService.use`(아직 `ItemService`가 `PointFake`를 씀)
- JAJ 확인: 케어미션 `reflect` 중 일정은 직접 입력(`DIRECT`)일 때만 반영한다(`CareScheduleService.java:87`). 명세와 같은지 확인
- 건강기록 기본 항목 "발 관리"의 이름을 JAJ와 정하기(후보: 발바닥 확인하기). 발톱 관리·발바닥 털 관리와 겹쳐 보이고 뜻이 넓다. 바뀌면 마이그레이션으로 `CARE_MSN` 미션 이름도 맞춘다

