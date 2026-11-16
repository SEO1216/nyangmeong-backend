# SJM 핸드오프

## 지금 하는 것
마이페이지 · 공동양육 포함 반려동물 10마리 한도(SCR-MYP-003, 기능 ID는 FN-MYP-003-01·FN-MYP-005-05에 붙임) — 끝남 (승인 2026-11-16 12:33). 검증 1회차 통과, 사람 확인 완료(Postman 24/24: user0006 등록 400·새 문구, 상세 남은 자리 0, 수락 400·대기중 유지·연동 없음, 시험 초대 241 취소·삭제)

확정 정책: 계정당 반려동물은 주양육 + 공동양육 합쳐 최대 10마리(`BRUP_REL.USE_YN='Y'`이고 `PET.DEL_YN='N'`인 관계 수). 초대 보내기는 막지 않는다. 이미 10마리를 넘은 회원(공용 DB user0006)의 데이터는 그대로 두고 새 등록·수락만 막는다. 남은 자리 = max(0, 10 − 합계). DMS 명세는 열지 않았다(정책은 사람이 확정, 명세 문구는 /end 문서 작업).

- 공통(한도 계산·잠금): `pet/mapper/PetMapper.java`·`mapper/pet/PetMapper.xml`에 `countLinkedPets(mbrSn)`(역할 무관, `countPrimaryPets`는 첫 등록 대표 지정에 계속 쓴다)와 `lockMemberForPetLimit(mbrSn)`(`SELECT MBR_SN FROM MBR WHERE MBR_SN = ? FOR UPDATE`, 같은 회원의 등록·수락을 줄 세움). `pet/service/PetLimit`(패키지 전용 상수 10과 `remaining(count)`)을 두고 `PetService`·`CaregiverInvitationService`가 같이 쓴다. 잠금을 `MemberService`가 아니라 pet Mapper에 두는 이유: member는 다른 담당 도메인이고 잠금은 반려동물 한도 검사에만 쓴다. 완료 조건 `명령:` `./gradlew build -q`, `PetMapperXmlTest`에 두 SQL(`USE_YN = 'Y'`·`DEL_YN = 'N'`, `FOR UPDATE`) 검사
- FN-MYP-003-01 등록 `POST /pets`(SJM): `PetService.createPet`이 잠금 → `countLinkedPets` ≥ 10이면 400 `PET_LIMIT_EXCEEDED`. `global/error/ErrorCode.java` 문구를 "반려동물은 공동양육을 포함해 계정당 10마리까지 함께할 수 있습니다."로(등록·수락 둘 다에 맞는 문구, 승인 때 확정. 공용 파일, `docs/convention/shared-files.md` 따름. `GlobalExceptionHandlerTest`가 `ErrorCode.PET_LIMIT_EXCEEDED.message()`를 쓰므로 문구 상수는 따라 바뀐다). 요청·응답 DTO는 api-row 그대로(PetCreateRequest·PetResponse). 완료 조건 `명령:` `PetServiceTest`(주양육 9 + 공동양육 1 → 거절, 주양육 10 + 공동양육 2 → 거절, 합계 9 → 등록, 잠금이 count보다 먼저 호출). `화면:` Postman으로 공동양육 포함 10마리 회원의 등록이 400과 새 문구
- FN-MYP-005-05 수락 `POST /caregiver-invitations/{sn}/accept`(SJM): `CaregiverInvitationService.acceptInvitation`이 `checkRespondable`·대상 검사 뒤, `respond`·연동 전에 잠금 → 합계 + 고른 수(중복 제거) > 10이면 아무것도 바꾸지 않고 400 `PET_LIMIT_EXCEEDED`, data `{ "remainingPetCount": n }`. 발송 결과처럼 예외 대신 결과로 돌려준다: 반환형 `void` → 새 `pet/dto/CaregiverInvitationAcceptResult`(error·`PetLimitExceededResponse(int remainingPetCount)`), `CaregiverInvitationController.acceptInvitation`이 실패면 `ApiResponse.errorWithData`. 요청 DTO는 api-row 그대로, 성공 응답은 지금처럼 data 없음. 완료 조건 `명령:` `CaregiverInvitationServiceTest`(합계 8 + 2 → 수락, 8 + 3 → 실패·remaining 2·`respond`/`link`/이력 호출 없음, 합계 12 + 1 → remaining 0), `CaregiverInvitationControllerTest`(실패 결과 → 400, `$.data.remainingPetCount`). `화면:` Postman으로 넘치는 수락 400과 data, 초대 상태가 대기중 그대로
- FN-MYP-005-05 받은 초대 상세 `GET /caregiver-invitations/{sn}`(SJM): `CaregiverInvitationResponse`에 `Integer remainingPetCount`를 더한다. 대기중이고 이메일이 맞을 때만 채우고(`pets`와 같은 조건, 아니면 JSON에서 빠짐) max(0, 10 − 로그인 회원 합계). 발송 응답(`POST /caregiver-invitations`)은 null로 빠진다. api-row와 다른 점이라 구현 전에 `docs/api/pet.md`의 CaregiverInvitationResponse 표에 행을 더한다. 완료 조건 `명령:` `CaregiverInvitationServiceTest`(합계 7 → 3, 합계 12 → 0, 이메일 불일치 → null). `화면:` Postman으로 user0006 로그인 상세의 `remainingPetCount` 0
- 문서 `docs/api/pet.md`: POST /pets 행 "계정당 10마리" → "공동양육 포함 계정당 10마리", accept 행에 한도 400과 data, CaregiverInvitationResponse에 `remainingPetCount` 행. 구현 전에 고친다
- 프론트 연결: FN-MYP-003-01 (프론트 연결 SCR-MYP-003 등록 카드 "모두 등록했어요"를 공동양육 포함 합계로 판정, 프론트 저장소에서 /start)
- 프론트 연결: FN-MYP-005-05 (프론트 연결 SCR-MYP-005 수락 화면이 `remainingPetCount`만큼만 고르게 막고 안내, 400 data 처리, 프론트 저장소에서 /start)

새 의존성: 없음

## 다음에 할 것
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

## 중단 2026-11-16 12:51 (5시간 한도 97%)
- 자동 커밋한 변경 14개: docs/api/pet.md, handoff/SJM.md, milestones/SJM.md, src/main/java/com/nyangmeong/global/error/ErrorCode.java, src/main/java/com/nyangmeong/pet/controller/CaregiverInvitationController.java, src/main/java/com/nyangmeong/pet/dto/CaregiverInvitationResponse.java, src/main/java/com/nyangmeong/pet/mapper/PetMapper.java, src/main/java/com/nyangmeong/pet/service/CaregiverInvitationService.java, src/main/java/com/nyangmeong/pet/service/PetService.java, src/main/resources/mapper/pet/PetMapper.xml, src/test/java/com/nyangmeong/pet/controller/CaregiverInvitationControllerTest.java, src/test/java/com/nyangmeong/pet/mapper/PetMapperXmlTest.java, src/test/java/com/nyangmeong/pet/service/CaregiverInvitationServiceTest.java, src/test/java/com/nyangmeong/pet/service/PetServiceTest.java
- 커밋 안 한 새 파일 3개 (사람이 판단): src/main/java/com/nyangmeong/pet/dto/CaregiverInvitationAcceptResult.java, src/main/java/com/nyangmeong/pet/dto/PetLimitExceededResponse.java, src/main/java/com/nyangmeong/pet/service/PetLimit.java
- 이어서: 이 파일의 "지금 하는 것" 과 `git log -1` 을 본다
