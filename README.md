
<img width="500" height="400" alt="tribe-main" src="https://github.com/user-attachments/assets/793d1dc9-d765-445b-817a-006a9bcf40d3" />

# 🌏 Tribe Backend Server
## 개발 환경

| Category | Stack |
| --- | --- |
| **Language** | Kotlin (JDK 17) |
| **Framework & Runtime** | Spring Boot 3.x, Spring Data JPA |
| **Database** | MySQL, Redis (Invitation & Token Management) |
| **Infra & Messaging** | WebSocket (STOMP), ApplicationEventPublisher, Cloudinary |
| **LLM & AI Framework** | Google Gemini AI (Vertex AI/API) |
| **DevOps & CI/CD** | Docker, GitHub Actions |

## Key Dependencies and Features

### 1. Real-time Collaborative Planning (WebSocket)

* **실시간 일정 편집**: `ApplicationEventPublisher`와 `STOMP` 프로토콜을 활용하여 다수의 사용자가 동시에 일정을 수정하고 즉시 공유할 수 있는 환경 제공
* **이벤트 기반 아키텍처**: 도메인 로직과 메시지 발행 로직을 분리하여 시스템 결합도를 낮추고 확장성 확보

### 2. Smart Expense Settlement (Greedy Algorithm)

* **최소 송금 경로 계산**: 복잡한 다자간 채무 관계를 Greedy 알고리즘을 통해 분석하여 송금 횟수를 최소화하는 최적의 정산 로직 구현
* **실시간 환율 동기화**: 외화 지출 시 지출 시점의 가장 가까운 환율을 자동으로 적용하여 KRW로 정밀 변환

### 3. AI-Driven Itinerary Optimization

* **Gemini AI 연동**: 사용자 일정에 대한 AI 피드백 및 이미지 분석을 통한 자동 콘텐츠 생성 기능 탑재
* **Google Maps API 연동**: 일정 간 이동 수단별(도보, 대중교통 등) 거리 및 소요 시간을 실시간 계산하여 경로 최적화 지원

### 4. High-Performance Messaging (Cursor-based Paging)

* **커서 기반 페이징**: 대규모 채팅 데이터 조회 시 성능 저하를 방지하기 위해 Cursor-based Pagination을 적용하여 안정적인 데이터 스트리밍 구현

---

## 아키텍처

### 소프트웨어 아키텍처

본 시스템은 여행의 각 단계(계획, 소통, 정산)를 독립적인 도메인 서비스로 분리하여 관리합니다. 각 도메인은 책임에 따라 계층화되어 있으며, 서비스 간 연동은 이벤트를 통해 유기적으로 이루어집니다.

| 서비스명 | 설명 |
| --- | --- |
| **Trip Service** | 여행 생성, 멤버 권한 관리(OWNER/ADMIN), Redis 기반 초대 시스템 담당 |
| **Itinerary Service** | 일차별 장소 등록, 순서 변경 및 Google Maps 기반 경로 계산 담당 |
| **Expense Service** | 지출 기록 관리 및 다국적 통화 지원 스마트 정산 알고리즘 처리 |
| **Chat Service** | STOMP 기반 실시간 그룹 채팅 및 커서 기반 메시지 이력 관리 |
| **AI Hub Service** | Gemini API를 활용한 여행 계획 피드백 및 멀티모달 데이터 분석 |

---

## Core Logic: Smart Settlement & Invitation

### 1. Settlement Strategy (Greedy)

정산 서비스는 모든 멤버의 순 잔액(지불액 - 분담액)을 계산한 뒤, 가장 많이 줄 사람(Debtor)과 가장 많이 받을 사람(Creditor)을 매칭하여 송금 횟수를 최소화합니다.

```kotlin
// SettlementService.kt 중 발췌
val transferAmountOriginal = debtorBalance.abs().min(creditorBalance)
val krwAmount = transferAmountOriginal.multiply(exchangeRate).setScale(SCALE, RoundingMode.HALF_UP)
// ... 관계 생성 및 잔액 업데이트

```

### 2. Redis-based Invitation Flow

보안성과 만료 관리를 위해 여행 초대 링크는 Redis를 활용하여 7일간 유효한 토큰 기반으로 동작합니다.

1. **초대 생성**: 16바이트 SecureRandom 토큰 생성 후 Redis에 저장 (`INVITE:token` -> `tripId`)
2. **참여 요청**: 클라이언트가 토큰과 함께 조인 요청
3. **검증 및 가입**: Redis에서 토큰 확인 후 멤버 역할(MEMBER) 부여 및 기존 탈퇴자 복구 처리

---

## Component & API URI Collection (Preview)

### Trip Component API

| URI | Method | 설명 |
| --- | --- | --- |
| `/trips` | POST | 새로운 여행 생성 |
| `/trips/{tripId}/invite` | POST | Redis 기반 초대 링크 생성 |
| `/trips/join` | POST | 초대 토큰을 이용한 그룹 참여 |
| `/trips/import` | POST | 커뮤니티 포스트의 일정을 내 여행으로 복제 |

### Itinerary & Route API

| URI | Method | 설명 |
| --- | --- | --- |
| `/categories/{categoryId}/itinerary` | POST | 특정 일차에 새로운 일정 추가 |
| `/trips/{tripId}/order` | PATCH | 일정/카테고리 순서 실시간 일괄 변경 |
| `/trips/{tripId}/routes` | GET | 구글 맵 기반 전체 이동 경로 및 소요시간 조회 |

### Expense & Settlement API

| URI | Method | 설명 |
| --- | --- | --- |
| `/expenses` | POST | 지출 내역 등록 (분담 멤버 설정 가능) |
| `/settlement/{tripId}/daily` | GET | 특정 날짜의 일별 정산 현황 및 송금 관계 조회 |
| `/settlement/{tripId}/total` | GET | 여행 전체 기간에 대한 최종 정산 리포트 반환 |
