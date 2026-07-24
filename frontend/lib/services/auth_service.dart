import 'package:firebase_auth/firebase_auth.dart';

class AuthService {
  AuthService._();

  static final AuthService instance = AuthService._();

  final FirebaseAuth _auth = FirebaseAuth.instance;

  Future<UserCredential> signInWithGoogle() async {
    final provider = GoogleAuthProvider();
    provider.setCustomParameters({
      'prompt': 'select_account',
    });

    return _auth.signInWithPopup(provider);
  }

  Future<UserCredential> signInAnonymously() async {
    return _auth.signInAnonymously();
  }

  Future<void> signOut() => _auth.signOut();
}
