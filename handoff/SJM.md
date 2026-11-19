# SJM 핸드오프

## 지금 하는 것
SCR-MYP-003 반려동물 등록/수정 · 인식칩 번호 12·15자리 검증 — 끝남 (승인 2026-11-19 16:36, 시작 2026-11-19). 검증 1회차 판정 `확인 못 함 있음`(사람 확인만 남음), 사람 확인 완료
정책: 인식칩 번호는 숫자 12자리 또는 15자리만, 빈 값은 허용. 지금 `^[0-9]{0,20}$`. 기준 SQL은 바꾸지 않는다(`PET.CHIP_NO` varchar(20) NULL 그대로)
공용 DB 조회(읽기 전용, 2026-11-19): `CHIP_NO`가 NULL·빈 값이 아닌 354행 중 12·15자리가 아닌 행은 2건. PET_SN 1004 `1111111111111111111`(19자리, DEL_YN N, 시험값으로 보임), PET_SN 1031 `12345678912345`(14자리, DEL_YN Y)
- FN-MYP-003-05 검증: `pet/dto/PetCreateRequest.java`·`PetUpdateRequest.java`의 `chipNo`를 `@Pattern(regexp = "^([0-9]{12}|[0-9]{15})?$")`로(빈 문자열 허용, 서비스가 `emptyToNull`로 NULL 저장, 수정에서 `null`=안 바꿈은 그대로). 완료 조건 `명령: ./gradlew build -q`, `PetControllerTest`: 12자리·15자리·빈 값 통과, 11·13·14·16자리·문자 섞임 400(등록·수정 모두)
- 기존 값 처리(결정 필요): 수정 DTO 검증은 요청 값만 보므로 규칙에 안 맞는 기존 값(PET_SN 1004)이 있는 반려동물은 프론트가 그 값을 다시 보내는 순간(수정 모달은 매번 `chipNo`를 보냄) 400이 된다. 결정(권장안 승인): 1004는 사람이 화면에서 인식칩 번호를 지워 정리하고 코드는 예외를 두지 않는다(1031은 삭제된 행이라 둠). 대안: 서비스에서 저장된 값과 같으면 통과시키려면 `PetUpdateRequest`의 `@Pattern`을 빼야 하는데, AI 도구 목록이 이 `@Pattern`에서 규칙을 만들어서 AI 쪽 규칙이 사라진다
- AI매니저 경로: 있다. `draft_pet_update`(AI 저장소 `data/tool_prompts.yaml`)의 `chip_no`가 확인 카드를 거쳐 `PATCH /pets/{petSn}`(`PetUpdateRequest`)로 간다. 별도 검증 없이 같은 DTO 검증을 타고, AI 도구 목록(`PetCardAiToolCatalogTest` 86줄)이 `@Pattern`을 그대로 읽어 규칙으로 내보낸다. 그래서 DTO만 고치면 맞춰지고, `PetCardAiToolCatalogTest`의 기대값 `^[0-9]{0,20}$`를 새 정규식으로 고친다. AI 저장소의 `chip_no` 설명("숫자만")과 `tools.snapshot.json`의 `chipNo.pattern`은 그 저장소 몫이라 "다음에 할 것"에 남긴다
- 문서: `docs/api/pet.md` `PetCreateRequest`·`PetUpdateRequest`의 `chipNo` 행("숫자만, 최대 20자리")을 "숫자 12자리 또는 15자리, 빈 값 허용"으로. 응답 DTO(`PetResponse`·`PetSummary`)는 바꾸지 않는다(기존 값 그대로 돌려줌)
- API 변경 없음: 경로·필드 그대로, 검증만 좁아진다
- 프론트 연결: 등록·수정 모달 인식칩 입력 12·15자리 안내·검증, 수정 때 바꾸지 않은 값은 보내지 않기 (프론트 저장소에서 /start)
새 의존성: 없음

## 다음에 할 것
- 프론트 연결(SCR-MYP-003, FN-MYP-003-05): 등록·수정 모달 인식칩 입력의 12·15자리 안내·검증, 수정 때 바꾸지 않은 값은 보내지 않기(프론트 저장소에서 `/start`, 프론트 마일스톤에 대기 절을 넣어 뒀다)
- LJS에게 알리기(케어미션 개편): `MasterSeeder` 미션 목록(이 저장소에 없음)의 이름 4개·식사량 달성 기준
- 관리자 케어미션 수정(FN-ADM-071-02): `cmpnPnt`는 출제 배점에 안 쓰이게 된다. 수정 화면·API에서 뺄지 정한다
- LJS에게 알리기(SCR-MYP-003 운영 설정값 연결): 공통 오류 처리 파일 4개를 바꾼다(`BaseException`·`CustomException`·`GlobalExceptionHandler`·`ApiResponse`, 문구를 받는 `withMessage`·`errorWithData` 오버로드 추가, 기존 동작은 그대로). 이 외에 공용 `ErrorCode`의 `PET_LIMIT_EXCEEDED`·`CAREGIVER_LIMIT_EXCEEDED` 문구가 `%d` 서식이 된다. PR 전에 알린다
- 받은 초대 목록 API를 만들 때: 취소·삭제된 초대는 넣지 않는다(대기 중만)
- JAJ에게 전달(SCR-HLT-002): `PetService.syncWeight`는 건강기록 체중을 `PET_BDWG`에 그대로 넣는다. 건강기록 체중 입력에도 소수 둘째 자리·200kg 이하 검증이 필요하다(회의 확정). 건강기록 체중 때 범위 갱신은 `PetBdwgRange.of()`가 200에서 자른다
- 로컬 설정(SJM): `application.properties` permit-all-paths의 `/api/files/public/**`는 PR #234 병합 뒤 develop을 합칠 때 다시 지운다(git에 올라가지 않음)
- JAJ에게 전달(건강기록 체중 범위 갱신, FN-MYP-003-04): 정상 범위 계산은 `com.nyangmeong.pet.service.PetBdwgRange`(public record `min`·`max`)에 있다. 사용: `PetBdwgRange r = PetBdwgRange.of(기준체중);` 후 `r.min()`·`r.max()`를 `PET.MIN_BDWG`·`MAX_BDWG`에 저장한다(다른 도메인이므로 `pet/service` 클래스를 바로 쓴다). 규칙: 최소 = 기준 체중 × 0.9, 최대 = × 1.1, 둘 다 소수 둘째 자리 HALF_UP(4.25 → 3.83 ~ 4.68). 아주 작은 체중은 최소를 0.01 이상, 최대를 최소+0.01 이상으로 보정한다(`CK_PET_WGHT_RANGE` 유지). 체중이 null이거나 0 이하면 `IllegalArgumentException`. 최대는 200kg(`PetBdwgRange.MAX_BDWG_LIMIT`)에서 자른다. 최소가 200 이상이면 199.99로 자른다. 갱신 전에 `PET.BDWG_RNG_DRCT_INPT_YN`을 확인해 `'Y'`(사용자 직접 입력)이면 범위를 바꾸지 않는다. 마이페이지(등록·수정)는 이미 이 메서드를 쓴다
- 팀에 알린다(시드): `db/seed/20261028_seed_data.sql`의 PET·체중 기록(HLTC0014)을 정리했고(품종 직접 입력명, 품종별 털색, 얼룩 미사용, 체중 소수 첫째 자리) `db/20261109_insert_base_data_v0.14.sql` 변경 절 v0.14 (8)로 얼룩(PETC0011)을 사용 중지했다. 공용 DB는 2026-11-10 반영 완료. 이미 시드를 넣은 로컬 DB는 시드를 다시 넣지 말고 팀에서 받은 정리 SQL로 맞춘다
- 확인: 등록 직후 출제(`PetCreatedEventListener`)가 UK_DAY_MSN_1 등으로 실패하면 `issueFor`가 리스너의 REQUIRES_NEW 트랜잭션에 합류해 롤백 전용이 되어, 커밋 때 `UnexpectedRollbackException` 로그가 함께 찍힐 수 있다(등록 응답에는 영향 없음, 다음 정각 배치가 채움). 거슬리면 `issueFor`를 `REQUIRES_NEW`로 바꾸는 것을 고려
- Postman 컬렉션: 3번 회원이 정지(`MEMBER_INACTIVE`)라 그 회원을 쓰는 변수(`mbrSnB`·`emailB`·`delCoMbrSn`·`delCoEmail`·`invReceiverMbrSn`·`infoOtherMbrSn`)가 있는 폴더는 403. 돌릴 때 정상 회원으로 바꾼다(이메일은 `GET /members/me`의 `acntEml`)
- YAY와 정하기(SCR-MYP-040): 본인 확인(6-1)을 통과했는지 `PATCH /members/me`에서 서버가 검사하지 않는다. `POST /members/me/password-verification`을 만들 때 통과 표시를 어떻게 남길지 함께 정한다
- 화면 확인 필요(FN-MYP-002-08): 알림함 COM-004에 공동양육 연동 해제 알림(CO_PARENTING, 이동 경로 `/mypage/pets`)이 보이는지. 백엔드는 `PetService.java:273`에서 끝
- 팀 확인: 공동양육 강제 해제·자진 해제(`PetService.unlinkCaregiver`)에는 알림 발행이 없다. 기획이 이 경로의 알림까지 요구하는지 정하기
- 화면 확인 필요(FN-MYP-030-08): 알림 목록 COM-004에 가계부 한도 알림이 보이는지. 백엔드는 `ExpenseService.java:313·316` 발행으로 끝
- LJS에게 알리기: `ReceiptService.markSavedByExpense`를 더함(인식 실패 후 직접 입력도 영수증을 저장완료로, LJS 파일)
- 배포 후 케어미션 출제·정산 배치가 느리면 개선: 지금은 한 행씩 INSERT·UPDATE하고 전체가 한 트랜잭션이다(로컬 시드 약 960마리로 30분, 2026-10-27 확인). 여러 행을 한 번에 넣고 묶음마다 커밋하도록 `/start`로 기획. 배치는 prod에서만 켜지므로 그 전에는 하지 않는다
- LJS에게 `MasterSeeder` 미션 목록 변경 알리기(21개로 바뀜, `CharacterSeeder`는 그대로)
- JAJ 확인: 케어미션 `reflect` 중 일정은 직접 입력(`DIRECT`)일 때만 반영한다(`CareScheduleService.java:87`). 명세와 같은지 확인
- LJS에게 알리기(SCR-MYP-003 인식칩 12·15자리, AI 저장소): `data/tool_prompts.yaml`의 `chip_no` 설명("숫자만")과 `data/tools.snapshot.json`의 `chipNo.pattern`(`^[0-9]{0,20}$`)을 새 규칙으로. 백엔드 PR 병합 뒤

