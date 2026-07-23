# Game Hub Backend

Flutter Web에서 Firebase Google 로그인한 사용자의 ID Token을 받아 검증하고,
게임 계정 정보를 저장하며 Lost Ark Open API의 실제 데이터를 조회하는 Spring Boot 백엔드입니다.

## 현재 구현

- Firebase Admin ID Token 검증
- Google 로그인 사용자 DB 동기화
- H2 로컬 DB에 게임 계정 저장
- 게임 계정 조회/등록/삭제/새로고침
- Lost Ark 실제 API 연결
  - 아이템 레벨
  - 전투력
  - 캐릭터 레벨 / 클래스
- LoL / TFT / Eternal Return은 등록 구조만 구현, API는 다음 단계

## 준비물

- Java 21
- Maven
- Firebase Service Account JSON
- Lost Ark Open API Token

## Firebase Service Account

Firebase Console:

프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성

예시 경로:

C:\secrets\game-hub-firebase-admin.json

절대 Git에 올리지 마세요.

PowerShell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\secrets\game-hub-firebase-admin.json"
```

## Lost Ark API Token

Lost Ark Open API Developer Portal에서 JWT 토큰 생성 후:

```powershell
$env:LOSTARK_API_TOKEN="YOUR_LOSTARK_API_TOKEN"
```

## 실행

```powershell
mvn spring-boot:run
```

기본 주소:

http://localhost:8080

## API

Authorization 헤더는 항상 Firebase ID Token을 사용합니다.

```text
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

### 내 게임 목록

GET /api/me/games

### 게임 등록

POST /api/me/games

```json
{
  "gameType": "LOST_ARK",
  "accountName": "캐릭터명"
}
```

### 새로고침

POST /api/me/games/{id}/refresh

### 삭제

DELETE /api/me/games/{id}
```
