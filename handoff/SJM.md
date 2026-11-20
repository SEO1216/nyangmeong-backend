# SJM 핸드오프

## 지금 하는 것
SCR-MYP-004 공동양육자 목록 · 연동 해제 인앱 알림 — 끝남 (승인 2026-11-20 11:29, 시작 2026-11-20 00:00). 검증 1회차 판정 `통과`, 사람 확인 완료(Postman으로 강제·자진 해제, 알림 문구·경로·재해제 404 미발송 확인)
정책: 공동양육 연동을 해제하면 상대에게 인앱 알림을 보낸다. `NotificationEvent(상대 mbrSn, NotificationType.CO_PARENTING, 문구, null, PET_LIST_PATH)`, 이메일 없음. 반려동물 삭제 때와 같이 문구는 `notiTtl`, `notiCn`은 null
- FN-MYP-004-01 강제 해제(주양육자가 해제): `pet/service/PetService.java` `unlinkCaregiver` 맨 끝(`insertLinkHistory` 뒤)에서 해제된 공동양육자(`target.getMbrSn()`)에게 "{주양육자 닉네임}님이 {반려동물 이름}의 공동양육 연동을 해제했어요". 닉네임 `memberService.getMember(mbrSn).mbrNcnm()`(주양육자=요청자), 이름 `loadPet(petSn).getPetNm()`
- FN-MYP-006-02 자진 해제(공동양육자 본인이 해제): 같은 위치에서 주양육자에게 "{공동양육자 닉네임}님이 {반려동물 이름}의 공동양육을 그만뒀어요". 주양육자 mbrSn은 `petMapper.selectPrimaryCaregiverMbrSn(petSn, BrupRole.PRIMARY.getCode())`(이미 있는 메서드, 없거나 삭제면 PET_NOT_FOUND라 `getPrimaryCaregiverMbrSn` 재사용). 닉네임은 요청자 것
- 미발송: 0건 갱신(`CAREGIVER_NOT_FOUND`)·권한 오류(`PET_ACCESS_DENIED`·`PET_PRIMARY_ONLY`)는 예외로 끝나므로 발행 코드에 닿지 않는다. 알림 조회(`getMember`·`loadPet`)는 갱신 성공 뒤에만 한다
- 커밋 뒤 발송: `@Transactional` 메서드 안에서 기존 `EventPublisher.publish()`(리스너가 커밋 뒤에 받음, 롤백이면 안 나감). `ApplicationEventPublisher` 직접 사용 없음
- 테스트 `pet/service/PetServiceTest.java`: ① 강제 해제 → `events.publish` 1회, 받는 사람 7L(해제된 사람), 유형 CO_PARENTING, 문구·경로 `/mypage/pets` ② 자진 해제 → 받는 사람 주양육자, 문구 ③ 0건 갱신·다른 공동양육자 해제 시도(`PET_PRIMARY_ONLY`)·주양육자 행 해제 시도(`CAREGIVER_NOT_FOUND`)·접근 거부(`PET_ACCESS_DENIED`)에서 `events` publish never. 완료 조건 `명령: ./gradlew build -q`
- API 변경 없음: `DELETE /pets/{petSn}/caregivers/{brupRelSn}` 경로·요청·응답 그대로(`docs/api/pet.md` 고칠 것 없음). 새 알림 유형·DB 코드·SQL 없음
- 공용 DB 시험 데이터: SQL 안 쓴다. 확인은 사람이 화면에서 초대 → 수락 → 해제(강제·자진 각각)로 만들고 알림함(COM-004)에서 본다
- 프론트 연결: 없음(알림함은 기존 CO_PARENTING 표시·이동 경로 그대로)
- 결정 메모: 닉네임 조회는 `memberService.getMember`(복호화까지 하는 무거운 메서드)지만 `CaregiverInvitationService`가 이미 같은 방식으로 쓴다. 해제는 드문 동작이라 그대로 쓴다
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


## 중단 2026-11-20 11:59 (5시간 한도 97%)
- 자동 커밋한 변경 4개: handoff/SJM.md, milestones/SJM.md, src/main/java/com/nyangmeong/pet/service/PetService.java, src/test/java/com/nyangmeong/pet/service/PetServiceTest.java
- 커밋 안 한 새 파일 0개 (사람이 판단): 없음
- 이어서: 이 파일의 "지금 하는 것" 과 `git log -1` 을 본다
