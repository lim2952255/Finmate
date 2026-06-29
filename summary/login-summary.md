# Login Summary

마지막 정리일: 2026-06-29

이 문서는 FinMate의 회원가입, 로그인, 로그아웃, 세션, 인터셉터 구조를 정리한다.

## 구현 범위

현재 로그인 파트에서 구현된 기능은 다음과 같다.

- 회원가입
- 로그인
- 로그아웃
- 세션 기반 로그인 상태 유지
- 로그인 필요 페이지 접근 제어
- 로그인 성공 후 원래 접근하려던 URL로 redirect
- 공통 헤더에 로그인 사용자 정보 노출

## 주요 클래스

### LoginController

역할:

- 회원가입 화면 제공
- 회원가입 요청 처리
- 로그인 화면 제공
- 로그인 요청 처리
- 로그아웃 처리

주요 endpoint:

- `GET /signup`
- `POST /signup`
- `GET /login`
- `POST /login`
- `POST /logout`

### UserService

역할:

- 사용자 저장
- 사용자 로그인 검증
- 사용자 조회

주요 메서드:

- `save(SignupRequest signupRequest)`
- `login(LoginRequest loginRequest)`
- `findUser(LoginDTO loginDTO)`

설계 포인트:

- 회원가입 시 아이디 중복을 검사한다.
- 비밀번호는 `PasswordEncoder`로 인코딩한 뒤 저장한다.
- 로그인 시 입력 비밀번호와 저장된 인코딩 비밀번호를 `passwordEncoder.matches()`로 비교한다.
- `findUser()`는 `LoginDTO` 인터페이스를 받아 `LoginRequest`, `SignupRequest`, `SessionUser`에서 공통으로 사용할 수 있게 했다.

### User

주요 필드:

- `username`
- `telephone`
- `email`
- `userId`
- `password`
- `accountList`

검증:

- 이름은 필수값이다.
- 전화번호는 `010-0000-0000` 형식을 요구한다.
- 이메일은 기본 이메일 형식을 요구한다.
- 아이디는 영문/숫자 8~20자 형식이다.
- 비밀번호는 영문, 숫자, 특수문자를 포함하고 10자 이상이어야 한다.

보완할 점:

- 현재 `User` 엔티티에 `@Setter`가 열려 있다.
- 계좌 도메인처럼 사용자 정보 변경도 도메인 메서드로 제한하는 방향을 나중에 검토할 수 있다.

## DTO 구조

### LoginDTO

`LoginDTO`는 `getUserId()`를 가진 공통 인터페이스다.

사용처:

- `LoginRequest`
- `SignupRequest`
- `SessionUser`

이렇게 만든 이유:

- 사용자 조회는 결국 `userId` 기준으로 수행된다.
- 로그인 요청, 회원가입 요청, 세션 사용자 모두 `userId`를 갖고 있으므로 `UserService.findUser()`를 하나의 메서드로 통합할 수 있다.

### SignupRequest

회원가입 form 입력값을 받는 DTO다.

주요 필드:

- `username`
- `telephone`
- `email`
- `userId`
- `password`

### LoginRequest

로그인 form 입력값을 받는 DTO다.

주요 필드:

- `userId`
- `password`

### SessionUser

세션에 저장할 사용자 정보만 담는 DTO다.

주요 필드:

- `id`
- `userId`
- `username`

설계 포인트:

- `User` 엔티티를 그대로 세션에 넣지 않는다.
- 세션에는 화면과 인증 흐름에 필요한 최소 정보만 저장한다.
- 세션 key는 `Const.LOGIN_USER`로 공통 관리한다.

## 회원가입 흐름

```text
GET /signup
-> 빈 SignupRequest를 model에 담음
-> home/signup 렌더링

POST /signup
-> @Valid로 입력 검증
-> UserService.save()
-> 아이디 중복 검사
-> 비밀번호 인코딩
-> User 저장
-> redirect:/
```

예외 처리:

- 아이디 중복 시 `bindingResult.rejectValue("userId", ...)`
- 기타 실패 시 global error로 처리

설계 포인트:

- form 검증 오류는 `BindingResult`를 통해 Thymeleaf에서 렌더링한다.
- POST 성공 후에는 새로고침 중복 제출을 막기 위해 redirect한다.

## 로그인 흐름

```text
GET /login
-> 빈 LoginRequest를 model에 담음
-> redirectURL, message가 있으면 model에 추가
-> home/login 렌더링

POST /login
-> @Valid로 입력 검증
-> UserService.login()
-> 비밀번호 검증
-> 로그인 성공 시 HttpSession 생성
-> Const.LOGIN_USER에 SessionUser 저장
-> redirectURL이 있으면 원래 페이지로 redirect
-> 없으면 redirect:/
```

로그인 실패:

- `LoginException` 발생
- `bindingResult.reject("loginFail", "아이디 또는 비밀번호가 올바르지 않습니다.")`
- 로그인 화면으로 다시 이동

## 로그아웃 흐름

```text
POST /logout
-> 기존 세션 조회
-> 세션이 있으면 invalidate()
-> redirect:/
```

설계 포인트:

- `getSession(false)`를 사용해 세션이 없을 때 새 세션을 만들지 않는다.
- 세션 무효화로 로그인 상태를 제거한다.

## LoginInterceptor

역할:

- 로그인하지 않은 사용자가 보호된 페이지에 접근하는 것을 막는다.

동작:

```text
1. request.getSession(false)로 기존 세션 확인
2. 세션이 없거나 Const.LOGIN_USER가 없으면 로그인 페이지로 redirect
3. redirectURL에 원래 접근 URI 전달
4. message=loginFirst 전달
5. 로그인 후 원래 페이지로 이동 가능
```

제외 경로:

- `/`
- `/home`
- `/login`
- `/signup`
- 정적 리소스
- `/error`

설계 포인트:

- 인증이 필요한 모든 페이지를 인터셉터에서 공통 제어한다.
- 컨트롤러마다 로그인 여부를 직접 검사하지 않아도 된다.
- 원래 접근하려던 URL을 쿼리 파라미터로 전달해 로그인 후 UX를 개선했다.

## UserInterceptor

역할:

- View 렌더링 직전에 로그인 사용자 정보를 model에 넣는다.

동작:

```text
1. 세션에서 Const.LOGIN_USER 조회
2. SessionUser가 있으면 modelAndView.addObject("user", user)
3. 공통 header에서 ${user}로 로그인 여부와 사용자명을 표시
```

설계 포인트:

- 모든 컨트롤러에서 `model.addAttribute("user", user)`를 반복하지 않아도 된다.
- 공통 header에서 로그인 상태에 따라 회원가입/로그인 또는 사용자명/로그아웃을 표시할 수 있다.

## 공통 Header

`layout/header.html`에 공통 header fragment를 둔다.

현재 구성:

- `FinMate` 로고
- 로고 클릭 시 `/home` 이동
- 로그인 사용자명 표시
- 로그아웃 버튼
- 마이페이지 링크
- 비로그인 상태의 회원가입/로그인 링크

설계 포인트:

- 각 페이지에 중복으로 있던 `FinMate` 로고를 공통 header로 이동했다.
- 모든 페이지에서 `th:replace="~{/layout/header :: header}"`로 공통 헤더를 사용한다.

## 현재 보완점

- `User` 엔티티의 setter 제한 검토
- 로그인 실패 횟수 제한
- 계정 잠금 정책
- 비밀번호 변경 기능
- 세션 만료 시간 명시
- CSRF/보안 설정 점검
