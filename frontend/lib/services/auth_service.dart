import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:google_sign_in/google_sign_in.dart';

enum GoogleSignInFailureCategory {
  canceled,
  configuration,
  unavailable,
  interrupted,
  unknown,
}

class GoogleSignInFailure implements Exception {
  const GoogleSignInFailure(this.category);

  final GoogleSignInFailureCategory category;

  String get userMessage => switch (category) {
        GoogleSignInFailureCategory.configuration => 'Google 로그인 설정을 확인해주세요.',
        GoogleSignInFailureCategory.unavailable => 'Google 계정 선택 화면을 열 수 없습니다.',
        GoogleSignInFailureCategory.interrupted =>
          'Google 로그인이 중단되었습니다. 다시 시도해주세요.',
        GoogleSignInFailureCategory.canceled ||
        GoogleSignInFailureCategory.unknown =>
          'Google 로그인에 실패했습니다. 다시 시도해주세요.',
      };

  @override
  String toString() => 'GoogleSignInFailure(${category.name})';
}

class AuthService {
  AuthService._();

  static final AuthService instance = AuthService._();

  final FirebaseAuth _auth = FirebaseAuth.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn.instance;
  Future<void>? _googleSignInInitialization;

  Future<UserCredential> signInWithGoogle() async {
    if (!kIsWeb) {
      try {
        await _initializeGoogleSignIn();
        final googleUser = await _googleSignIn.authenticate();
        final googleAuth = googleUser.authentication;
        final idToken = googleAuth.idToken;

        if (idToken == null || idToken.isEmpty) {
          throw FirebaseAuthException(
            code: 'missing-google-id-token',
            message: 'Google authentication did not return an ID token.',
          );
        }

        final credential = GoogleAuthProvider.credential(idToken: idToken);
        return _auth.signInWithCredential(credential);
      } on GoogleSignInException catch (error) {
        throw GoogleSignInFailure(_failureCategoryFor(error.code));
      }
    }

    final provider = GoogleAuthProvider();
    provider.setCustomParameters({
      'prompt': 'select_account',
    });

    return _auth.signInWithPopup(provider);
  }

  Future<UserCredential> signInAnonymously() async {
    return _auth.signInAnonymously();
  }

  Future<void> signOut() async {
    final signedInWithGoogle = _auth.currentUser?.providerData.any(
          (provider) => provider.providerId == GoogleAuthProvider.PROVIDER_ID,
        ) ??
        false;

    if (!kIsWeb && signedInWithGoogle) {
      try {
        await _initializeGoogleSignIn();
        await _googleSignIn.signOut();
      } on GoogleSignInException {
        // Firebase 로그아웃은 Google SDK 상태와 관계없이 계속 진행한다.
      }
    }

    await _auth.signOut();
  }

  Future<void> _initializeGoogleSignIn() {
    return _googleSignInInitialization ??= _googleSignIn.initialize();
  }

  GoogleSignInFailureCategory _failureCategoryFor(
    GoogleSignInExceptionCode code,
  ) {
    return switch (code) {
      GoogleSignInExceptionCode.canceled =>
        GoogleSignInFailureCategory.canceled,
      GoogleSignInExceptionCode.clientConfigurationError ||
      GoogleSignInExceptionCode.providerConfigurationError =>
        GoogleSignInFailureCategory.configuration,
      GoogleSignInExceptionCode.uiUnavailable =>
        GoogleSignInFailureCategory.unavailable,
      GoogleSignInExceptionCode.interrupted =>
        GoogleSignInFailureCategory.interrupted,
      _ => GoogleSignInFailureCategory.unknown,
    };
  }
}
