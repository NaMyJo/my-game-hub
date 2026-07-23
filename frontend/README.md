# MY GAME HUB - Flutter Web Prototype

Google 로그인 후 개인 게임 계정을 등록하고,
게임별 데이터를 카드 형태로 보여주는 Flutter Web 초기 프로젝트입니다.

## 포함된 기능

- Firebase Google 로그인
- 로그인/로그아웃 상태 자동 전환
- 반응형 다크 테마 대시보드
- 게임 추가 Dialog
- Lost Ark / League of Legends / TFT / Eternal Return 등록
- 계정 등록 직후 Mock 데이터 카드 표시
- 게임 삭제
- 모바일/좁은 브라우저 반응형 레이아웃

## 1. 프로젝트 생성

빈 Flutter 프로젝트를 만든 뒤 이 프로젝트의 `lib/`와 `pubspec.yaml`을 사용하거나,
현재 폴더에서 필요한 플랫폼 파일을 생성합니다.

```bash
flutter create .
flutter pub get
```

## 2. Firebase 설정

Firebase Console에서 Web App을 만든 뒤 Authentication > Sign-in method에서
Google 로그인을 활성화합니다.

FlutterFire CLI 설치:

```bash
dart pub global activate flutterfire_cli
```

Firebase 설정 생성:

```bash
flutterfire configure
```

이 명령이 실제 `lib/firebase_options.dart`를 생성합니다.
현재 포함된 placeholder 파일은 그 파일로 교체하세요.

## 3. 실행

```bash
flutter run -d chrome
```

## 4. 다음 단계 - 백엔드 연결

현재 `lib/services/game_repository.dart`는 Mock 데이터를 반환합니다.

다음 REST API 형태로 백엔드를 구성하면 Flutter UI를 거의 수정하지 않고
실데이터로 전환할 수 있습니다.

```text
GET    /api/me/games
POST   /api/me/games
DELETE /api/me/games/{gameId}
POST   /api/me/games/{gameId}/refresh
```

예시 등록 Body:

```json
{
  "gameType": "LOST_ARK",
  "accountName": "캐릭터명"
}
```

백엔드에서는 사용자 Firebase ID Token을 검증한 뒤
Lost Ark / Riot / Eternal Return 공식 API를 호출합니다.

> 게임 API Key를 Flutter Web 소스에 직접 넣으면 브라우저에서 노출됩니다.
