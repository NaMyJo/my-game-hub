# 🎮 MY GAME HUB

> **내 모든 게임 기록을 한 곳에서.**

MY GAME HUB는 여러 게임에 흩어진 계정 및 전적 정보를 하나의 대시보드에서 확인하고 관리하기 위한 개인 게임 허브 서비스입니다.

현재 **Lost Ark, League of Legends, Teamfight Tactics(TFT), Eternal Return**을 지원하며, 게임별 API/데이터를 연동해 계정 정보를 카드 형태로 제공합니다.

## 주요 기능

- Firebase Google 로그인
- Firebase Anonymous Authentication 기반 게스트 이용
- 게임별 계정 등록 / 삭제 / 새로고침
- 사용자별 게임 데이터 관리
- 게임별 요약 카드 및 마지막 동기화 정보
- 게임 카드 순서 변경(데스크톱)
- PC 사이드바 / 모바일 하단 내비게이션 기반 반응형 UI
- 모바일 2열 요약 카드 및 게임 카드 정보 최적화
- 게임별 외부 전적 사이트 바로가기
- 초기 데이터 로딩 안내 및 자동 재시도
- MY GAME HUB 앱 아이콘 및 게임별 전용 아이콘

## 지원 게임

| 게임 | 연동 및 표시 |
| --- | --- |
| Lost Ark | Lost Ark Open API, KLOA / LOPEC 바로가기 |
| League of Legends | Riot Games API, OP.GG / LOL.PS 바로가기 |
| Teamfight Tactics | Riot Games TFT API, LOLCHESS.GG 바로가기 |
| Eternal Return | 티어/판수/평균 순위/선호 실험체 UI, DAK.GG 바로가기 |

> Eternal Return의 실제 API 연동 범위는 API 승인 및 제공 상태에 따라 달라질 수 있습니다.

## 기술 스택

### Frontend
- Flutter / Dart
- Flutter Web
- Firebase Authentication
- url_launcher

### Backend
- Java
- Spring Boot
- Spring MVC
- Spring Data JPA
- Firebase Admin SDK

### Authentication
- Google Authentication
- Anonymous Authentication
- Firebase ID Token을 Bearer Token으로 전달
- Firebase UID 기준 사용자 식별

### Deployment
- Frontend: Vercel
- Backend: Render
- Source Control: GitHub
- GitHub main 브랜치와 Vercel 연동

## 서비스 구조

```text
Flutter Web
    │
    ├── Firebase Authentication
    │      ├── Google Login
    │      └── Anonymous Login
    │
    ▼
Spring Boot API
    │
    ├── Firebase ID Token 검증
    ├── 사용자 UID 식별
    ├── 게임 계정 CRUD
    ├── 게임 계정 순서 변경
    └── 게임 API 데이터 갱신
    │
    ▼
Database / External Game APIs
```

## 인증 구조

```text
사용자 로그인
    ↓
Firebase ID Token 발급
    ↓
Flutter → Spring Boot
Authorization: Bearer <ID_TOKEN>
    ↓
FirebaseAuthInterceptor
    ↓
Firebase Admin SDK Token 검증
    ↓
AuthenticatedUser 생성
    ↓
UID 기준 사용자 데이터 접근
```

게스트도 Firebase Anonymous Authentication으로 별도의 UID를 발급받기 때문에 사용자별 데이터를 분리할 수 있습니다.

단, 게스트가 로그아웃하거나 브라우저 데이터를 삭제하면 기존 익명 계정에 다시 접근하기 어려울 수 있습니다. 향후 게스트 계정을 Google 계정과 연결해 데이터를 유지하는 기능을 추가할 예정입니다.

## 반응형 UI

### Desktop
- 좌측 사이드바
- 대시보드 / 게임 추가 / 도구 모음
- 사용자 프로필 및 로그아웃
- 게임 카드 관리 및 순서 변경

### Mobile
- 모바일 전용 헤더
- 하단 내비게이션
- 게임 추가 중심 버튼
- 요약 카드 2열 배치
- 작은 화면에 맞춘 게임 카드 정보 최적화
- 게스트 상태를 고려한 프로필/종료 UI

## 게임별 외부 서비스

- Lost Ark: KLOA, LOPEC
- League of Legends: OP.GG, LOL.PS
- TFT: LOLCHESS.GG
- Eternal Return: DAK.GG

## 개발 과정에서 해결한 문제

### Flutter Web 배포
기존 프로젝트에 Web 플랫폼을 추가하고 `flutter build web --release`가 가능하도록 구성했습니다.

### CORS
Vercel의 Flutter Web이 Render의 Spring Boot API에 접근할 수 있도록 개발/운영 Origin을 구분해 CORS를 구성했습니다.

### Render Cold Start 및 초기 로딩
장시간 미접속 후 API 응답이 늦어지는 상황을 고려해 로딩 안내와 자동 재시도를 추가했습니다. 이후 유료 인스턴스를 사용해 cold start 영향을 줄이는 방향으로 운영 환경을 구성했습니다.

### 모바일 UX
데스크톱 UI를 단순 축소하지 않고 모바일에서는 하단 내비게이션과 2열 요약 카드 레이아웃을 적용했습니다. 정보량이 많은 게임 카드는 모바일에서 핵심 정보 위주로 표시하도록 조정했습니다.

### 사용자별 데이터 분리
Firebase UID를 기준으로 백엔드와 DB에서 사용자를 식별하여 계정별 게임 데이터를 독립적으로 관리하도록 구현했습니다.

---

**MY GAME HUB** — 내가 플레이하는 여러 게임을 하나의 대시보드에서 관리하기 위한 통합 게임 허브.

NOTION : "https://app.notion.com/p/MY-GAME-HUB-3a7846c0e128803285d5cd491a342d08?source=copy_link"