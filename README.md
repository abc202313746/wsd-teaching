# 백엔드 프레임워크 실습 과제

Spring Boot로 상품(Item)을 등록, 조회, 수정, 삭제하는 REST API를 만들었다. DB는 연결하지 않고 서버 메모리의 Map에 저장하기 때문에 서버를 다시 실행하면 데이터가 초기화된다.

## 개발 환경

- Java 25
- Spring Boot 4.1.1
- Gradle
- Postman

## 실행 방법

IntelliJ에서 `WsdTeachingApplication`을 실행하거나 터미널에서 아래 명령어를 실행한다. 서버는 `http://localhost:8080`에서 실행된다.

```powershell
.\gradlew.bat bootRun
```

## 1. API 구현

GET, POST, PUT, DELETE를 2개씩, 총 8개 만들었다.

| 메서드 | 경로 | 설명 | 데이터 전달 |
|---|---|---|---|
| GET | `/api/v1/items` | 상품 목록 조회 (검색, 페이지) | `@RequestParam` (keyword, page, size) |
| GET | `/api/v1/items/{id}` | 상품 하나 조회 | `@PathVariable` |
| POST | `/api/v1/items` | 상품 등록 | `@RequestBody` |
| POST | `/api/v3/items/with-header` | 헤더 정보와 함께 상품 등록 | `@RequestBody`, `@RequestHeader` (X-USER-ID, Authorization) |
| PUT | `/api/v1/items/{id}` | 상품 이름, 가격 수정 | `@PathVariable`, `@RequestBody` |
| PUT | `/api/v1/items/{id}/price` | 상품 가격만 수정 | `@PathVariable`, `@RequestBody` |
| DELETE | `/api/v1/items/{id}` | 상품 하나 삭제 | `@PathVariable` |
| DELETE | `/api/v1/items?ids=1,3` | 상품 여러 개 삭제 | `@RequestParam` (ids) |

- 이름이 비어 있거나 가격이 음수면 400을 반환한다.
- 없는 ID를 조회, 수정, 삭제하면 404를 반환한다.
- 여러 개를 삭제할 때 없는 ID가 하나라도 섞여 있으면 아무것도 삭제하지 않는다.
- page와 size를 보내지 않으면 기본값 0, 10을 사용한다.

코드 구성은 다음과 같다.

- `api/v1/ItemController`, `api/v3/ItemController3`: 요청을 받아 응답을 반환하는 컨트롤러
- `service/ItemService`: 상품 저장, 조회, 입력 검사 (두 컨트롤러가 함께 사용)
- `api/dto`, `api/request`, `api/response`: 데이터를 주고받는 클래스
- `config`: 인터셉터와 설정
- `api/error`: 예외 처리

## 2. 미들웨어

Spring의 인터셉터(`HandlerInterceptor`)로 요청 로그를 남기는 `LoggingInterceptor`를 만들었다. `/api/**`로 요청이 들어오면 메서드와 경로를 출력하고, 처리가 끝나면 응답 코드와 처리 시간을 출력한다.

```
요청 시작: POST /api/v1/items
요청 완료: POST /api/v1/items, status=201, durationMs=41
```

## 3. 응답 코드

| 분류 | 코드 | 상황 |
|---|---|---|
| 2xx | 200 OK | 조회, 수정, 삭제 성공 |
| 2xx | 201 Created | 상품 등록 성공 |
| 4xx | 400 Bad Request | 빈 이름, 음수 가격 등 잘못된 입력 |
| 4xx | 404 Not Found | 없는 상품 |
| 5xx | 500 Internal Server Error | 서버 내부 오류 |
| 5xx | 503 Service Unavailable | 서비스 이용 불가 |

5xx는 정상적인 요청으로는 발생하지 않는다. 그래서 요청 헤더에 `X-Demo-Error: 500` 또는 `X-Demo-Error: 503`을 넣으면 해당 오류가 발생하도록 `DemoErrorInterceptor`를 만들었다. 확인용 기능이라 `application.properties`에서 `app.demo-errors-enabled=false`로 끌 수 있다.

## 4. 응답 포맷

과제 공지의 예시처럼 모든 응답을 `status`와 `data` 형식으로 통일했다. 오류가 나면 `status`가 `error`가 되고, `data`에 오류 코드와 메시지를 담는다. 오류 응답은 `GlobalExceptionHandler`에서 한곳에 모아 처리한다.

성공:

```json
{
  "status": "success",
  "data": {"id": 1, "name": "Coffee", "price": 1000}
}
```

실패:

```json
{
  "status": "error",
  "data": {"code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다: 999"}
}
```

## 테스트

### Postman

서버를 재시작한 뒤 Postman에서 아래 순서로 요청을 보냈다. 주소는 모두 `http://localhost:8080`으로 시작하고, Body는 raw JSON으로 보냈다.

| 순서 | 요청 | 입력 | 결과 |
|---|---|---|---|
| 1 | `POST /api/v1/items` | `{"name": "Coffee", "price": 1000}` | 201, Coffee 등록 (id 1) |
| 2 | `POST /api/v3/items/with-header` | `{"name": "Tea", "price": 2000}`<br>헤더 `X-USER-ID: student01`, `Authorization: Bearer demo-token` | 201, Tea 등록 (id 2) |
| 3 | `POST /api/v1/items` | `{"name": "Milk", "price": 3000}` | 201, Milk 등록 (id 3) |
| 4 | `GET /api/v1/items` | | 200, 상품 3개 |
| 5 | `GET /api/v1/items/1` | | 200, Coffee |
| 6 | `PUT /api/v1/items/1` | `{"name": "Americano", "price": 1800}` | 200, 이름과 가격 변경 |
| 7 | `PUT /api/v1/items/1/price` | `{"price": 2200}` | 200, 가격만 변경 |
| 8 | `DELETE /api/v1/items/2` | | 200, Tea 삭제 |
| 9 | `DELETE /api/v1/items?ids=1,3` | | 200, Americano, Milk 삭제 |
| 10 | `POST /api/v1/items` | `{"name": " ", "price": 100}` | 400 |
| 11 | `GET /api/v1/items/999` | | 404 |
| 12 | `GET /api/v1/items` | 헤더 `X-Demo-Error: 500` | 500 |
| 13 | `GET /api/v1/items` | 헤더 `X-Demo-Error: 503` | 503 |

캡처할 때 Body나 헤더로 값을 보낸 요청은 해당 탭을 열어 입력값이 같이 보이게 했다. 요청 로그 맨 앞의 GET 200, POST 400 두 줄은 순서를 시작하기 전에 확인용으로 보낸 요청이다.

같은 API를 확인할 수 있는 Postman 요청 모음을 `docs/wsd-assignment.postman_collection.json`에 저장해 두었다. Postman의 Import로 가져와서 사용할 수 있다. 준비 요청부터 실행하기 때문에 상품 ID와 요청 순서는 위 캡처와 다르다.

### 자동 테스트

```powershell
.\gradlew.bat test
```

8개 API의 동작, 입력 검사, 오류 응답 형식, 요청 로그를 확인하는 테스트 7개를 작성했고 모두 통과했다.

## 실행 화면

### 1. 서버 실행

![서버 실행](docs/screenshots/01-server.png)

### 2. POST - 상품 등록 (201)

![상품 등록](docs/screenshots/02-post-create.png)

### 3. POST - 헤더와 함께 상품 등록 (201)

![헤더와 함께 상품 등록](docs/screenshots/03-post-header.png)

### 4. GET - 상품 목록 조회 (200)

![상품 목록 조회](docs/screenshots/04-get-list.png)

### 5. GET - 상품 하나 조회 (200)

![상품 하나 조회](docs/screenshots/05-get-detail.png)

### 6. PUT - 상품 이름, 가격 수정 (200)

![상품 수정](docs/screenshots/06-put-item.png)

### 7. PUT - 상품 가격 수정 (200)

![상품 가격 수정](docs/screenshots/07-put-price.png)

### 8. DELETE - 상품 하나 삭제 (200)

![상품 하나 삭제](docs/screenshots/08-delete-item.png)

### 9. DELETE - 상품 여러 개 삭제 (200)

![상품 여러 개 삭제](docs/screenshots/09-delete-batch.png)

### 10. 400 Bad Request

![400](docs/screenshots/10-error-400.png)

### 11. 404 Not Found

![404](docs/screenshots/11-error-404.png)

### 12. 500 Internal Server Error

![500](docs/screenshots/12-error-500.png)

### 13. 503 Service Unavailable

![503](docs/screenshots/13-error-503.png)

### 14. 요청 로그 (미들웨어)

![요청 로그](docs/screenshots/14-middleware.png)

### 15. 자동 테스트 결과

![테스트 결과](docs/screenshots/15-tests.png)
