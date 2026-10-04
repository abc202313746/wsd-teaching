# 백엔드 프레임워크 실습

Spring Boot로 상품 관리 REST API를 구현했다. GET, POST, PUT, DELETE를 각각 2개씩 만들고, 요청 로그와 공통 응답 형식을 적용했다.

DB 대신 `LinkedHashMap`에 상품을 저장한다. 두 컨트롤러가 같은 서비스를 사용하며, 서버를 다시 실행하면 저장된 상품은 초기화된다.

## 실행 방법

- Java 25
- Spring Boot 4.1.1
- Gradle Wrapper

IntelliJ에서 프로젝트 SDK와 Gradle JVM을 JDK 25로 설정한 뒤 `WsdTeachingApplication`의 main 메서드를 실행한다. 터미널에서는 `JAVA_HOME`을 JDK 25 설치 경로로 설정하고 다음 명령을 실행한다.

```powershell
.\gradlew.bat bootRun
```

기본 주소는 `http://localhost:8080`이다. 포트가 사용 중이면 다음과 같이 바꿀 수 있다.

```powershell
.\gradlew.bat bootRun --args="--server.port=18080"
```

## API 목록

| 메서드 | 경로 | 기능 | 성공 코드 |
|---|---|---|---|
| GET | /api/v1/items | 상품 목록, 검색, 페이지 조회 | 200 |
| GET | /api/v1/items/{id} | 상품 상세 조회 | 200 |
| POST | /api/v1/items | 상품 등록 | 201 |
| POST | /api/v3/items/with-header | 요청 헤더와 함께 상품 등록 | 201 |
| PUT | /api/v1/items/{id} | 상품 이름과 가격 수정 | 200 |
| PUT | /api/v1/items/{id}/price | 상품 가격 수정 | 200 |
| DELETE | /api/v1/items/{id} | 상품 한 개 삭제 | 200 |
| DELETE | /api/v1/items?ids=1,2 | 상품 여러 개 삭제 | 200 |

등록과 전체 수정에는 다음 JSON을 사용한다. 가격 수정에는 `price`만 보낸다.

```json
{"name": "Coffee", "price": 1000}
```

- 이름은 앞뒤 공백을 제외하고 1~100자, 가격은 0 이상의 정수로 제한했다.
- 목록 조회는 `keyword`, `page`, `size`를 받는다. page는 0부터 시작하고 기본 size는 10이다.
- 검색은 이름에 keyword가 포함되는지 확인하며 대소문자를 구분한다.
- 전체 수정은 이름과 가격을 모두 보내야 한다.
- 여러 상품을 삭제할 때는 중복 없이 ID 1~100개를 보낸다. 없는 ID가 포함되면 아무 상품도 삭제하지 않는다.
- 헤더를 받는 POST는 `X-USER-ID`와 `Authorization`을 선택적으로 받는다. 응답에는 사용자 ID와 Authorization 제공 여부만 담으며, 별도의 인증 처리는 없다.
- 등록 응답에는 생성된 상품의 조회 주소를 `Location` 헤더로 반환한다.

## 응답과 상태 코드

공지에 나온 `status`, `data` 형식으로 성공과 오류 응답을 통일했다.

성공 응답:

```json
{
  "status": "success",
  "data": {"id": 1, "name": "Coffee", "price": 1000}
}
```

오류 응답:

```json
{
  "status": "error",
  "data": {"code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다: 999"}
}
```

| 상태 코드 | 발생 상황 |
|---|---|
| 200 OK | 조회, 수정, 삭제 성공 |
| 201 Created | 상품 등록 성공 |
| 400 Bad Request | 빈 이름, 음수 가격 등 잘못된 입력 |
| 404 Not Found | 존재하지 않는 상품 조회 |
| 500 Internal Server Error | 예상하지 못한 예외 또는 실습용 오류 |
| 503 Service Unavailable | 실습용 서비스 이용 불가 오류 |

5xx 응답을 확인하기 위해 `GET /api/v1/items` 요청에 `X-Demo-Error: 500` 또는 `X-Demo-Error: 503` 헤더를 넣는 기능을 만들었다. 실제 서버 장애 대신 오류 응답을 재현하는 용도다. 503 응답에는 `Retry-After: 5`도 포함한다.

`application.properties`의 `app.demo-errors-enabled`를 false로 바꾸면 이 헤더를 무시한다.

## 요청 로그와 예외 처리

`LoggingInterceptor`에서 요청 메서드, 경로, 응답 상태 코드, 처리 시간을 콘솔에 기록한다. `WebConfig`에서 로깅 인터셉터를 먼저 등록하여 실습용 오류가 발생해도 완료 로그를 남기도록 했다.

`GlobalExceptionHandler`에서 입력 오류, 없는 상품, 서버 오류 등을 공통 JSON 응답으로 처리한다. 상품 저장과 입력 검사는 `ItemService`에서 담당한다.

## 테스트

```powershell
.\gradlew.bat test bootJar
```

자동 테스트 7개를 통과했다. API 동작, 입력 검사, 일괄 삭제 실패 시 데이터 보존, 오류 응답과 로그를 확인했다.

Postman에서는 [요청 모음](docs/wsd-assignment.postman_collection.json)을 Import하고 `baseUrl`을 실행 중인 서버 주소로 설정한다. 준비 → API 8개 → 오류 응답 순서로 실행하면 된다. 등록된 상품의 ID는 요청 스크립트가 변수에 저장한다.

실제 HTTP 요청 22개를 확인한 결과와 캡처는 다음 파일에 정리했다.

| 파일 | 내용 |
|---|---|
| [apis.png](docs/verification/apis.png) | API 8개 실행 결과 |
| [response-codes.png](docs/verification/response-codes.png) | 200, 201, 400, 404, 500, 503 응답 |
| [middleware.png](docs/verification/middleware.png) | 요청 시작과 완료 로그 |
| [results.json](docs/verification/results.json) | 실제 요청과 응답 데이터 |
| [server.log](docs/verification/server.log) | 서버 로그 원본 |

캡처는 실제 요청 결과를 정리한 HTML 보고서의 브라우저 화면이다. 검증과 캡처를 다시 생성하려면 JDK 25의 `JAVA_HOME`을 설정하고 위의 빌드 명령 실행 후 다음 명령을 사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1 -JavaExe "$env:JAVA_HOME\bin\java.exe" -CaptureScreenshots
```

검증 스크립트는 18082 포트에서 서버를 잠시 실행하고 종료한다. 화면 캡처에는 설치된 Edge 또는 Chrome을 사용한다.
