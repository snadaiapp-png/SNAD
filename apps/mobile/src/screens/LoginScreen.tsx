/**
 * R2-I Mobile Action/Decision Center — Login screen
 *
 * Email + password sign-in against POST /api/v1/auth/login (real backend
 * LoginRequest field names). Errors are displayed inline; tokens are
 * persisted through TokenManager (refresh token in SecureStore only).
 */

import * as React from 'react';
import { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { TokenManager, getTokenManager } from '../auth/token-manager';

export interface LoginScreenProps {
  tokenManager?: TokenManager;
  onLoginSuccess?: () => void;
}

export function LoginScreen({ tokenManager, onLoginSuccess }: LoginScreenProps) {
  const auth = tokenManager ?? getTokenManager();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleLogin() {
    if (submitting) return;
    if (email.trim().length === 0 || password.length === 0) {
      setError('Enter your email and password.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await auth.login(email.trim(), password);
      onLoginSuccess?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed. Try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View testID="login-screen" style={styles.screen}>
      <Text style={styles.title}>SANAD Action Center</Text>
      <Text style={styles.subtitle}>Sign in to your workflow tasks</Text>

      <TextInput
        testID="login-email"
        style={styles.input}
        placeholder="Email"
        placeholderTextColor="#8a8f98"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={setEmail}
        editable={!submitting}
      />
      <TextInput
        testID="login-password"
        style={styles.input}
        placeholder="Password"
        placeholderTextColor="#8a8f98"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
        editable={!submitting}
      />

      {error ? (
        <Text testID="login-error" style={styles.error} accessibilityRole="alert">
          {error}
        </Text>
      ) : null}

      <Pressable
        testID="login-submit"
        style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
        onPress={handleLogin}
        disabled={submitting}
        accessibilityRole="button"
      >
        <Text style={styles.buttonLabel}>{submitting ? 'Signing in…' : 'Sign in'}</Text>
      </Pressable>

      {submitting ? <ActivityIndicator testID="login-spinner" style={styles.spinner} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#f5f6f8',
    alignItems: 'stretch',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    color: '#101828',
    textAlign: 'center',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#667085',
    textAlign: 'center',
    marginBottom: 28,
  },
  input: {
    backgroundColor: '#ffffff',
    borderColor: '#d0d5dd',
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    marginBottom: 12,
  },
  error: {
    color: '#b42318',
    fontSize: 13,
    marginBottom: 12,
  },
  button: {
    backgroundColor: '#175cd3',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  buttonPressed: {
    backgroundColor: '#194185',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
  },
  spinner: {
    marginTop: 12,
  },
});
