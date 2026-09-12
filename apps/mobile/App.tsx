/**
 * R2-I Mobile Action/Decision Center — application root
 *
 * Classic Expo entry (package.json "main": node_modules/expo/AppEntry.js
 * expects ./App). Extends the existing Expo/RN foundation:
 *   - NavigationContainer + native stack: Login → Action Center
 *   - Session gate: SecureStore refresh-token presence decides the
 *     initial route; logout returns to Login.
 *   - Deep-link validation: ONLY https:// links are handed to the
 *     navigator (custom schemes / http / malformed are ignored).
 */

import * as React from 'react';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Linking, StyleSheet, Text, View } from 'react-native';
import {
  LinkingOptions,
  NavigationContainer,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { getTokenManager } from './src/auth/token-manager';
import { isSafeDeepLink } from './src/workflow/deep-link';
import { ActionCenterScreen } from './src/screens/ActionCenterScreen';
import { LoginScreen } from './src/screens/LoginScreen';

export type RootStackParamList = {
  Login: undefined;
  ActionCenter: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

type SessionStatus = 'checking' | 'signedOut' | 'signedIn';

/**
 * Linking config that ignores every non-HTTPS deep link: both the cold-start
 * URL and runtime URL events are validated before reaching the navigator.
 */
export const linking: LinkingOptions<RootStackParamList> = {
  prefixes: ['https://sanad.app', 'https://www.sanad.app'],
  getInitialURL: async () => {
    const url = await Linking.getInitialURL();
    return isSafeDeepLink(url) ? url : null;
  },
  subscribe: (listener: (url: string) => void) => {
    const subscription = Linking.addEventListener('url', ({ url }) => {
      if (isSafeDeepLink(url)) {
        listener(url);
      }
    });
    return () => subscription.remove();
  },
  config: {
    screens: {
      Login: 'login',
      ActionCenter: 'action-center',
    },
  },
};

export default function App() {
  const [status, setStatus] = useState<SessionStatus>('checking');

  useEffect(() => {
    let cancelled = false;
    getTokenManager()
      .hasValidRefreshToken()
      .then((valid) => {
        if (!cancelled) setStatus(valid ? 'signedIn' : 'signedOut');
      })
      .catch(() => {
        if (!cancelled) setStatus('signedOut');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (status === 'checking') {
    return (
      <View testID="boot-splash" style={styles.splash}>
        <ActivityIndicator size="large" />
        <Text style={styles.splashText}>Loading…</Text>
      </View>
    );
  }

  return (
    <NavigationContainer linking={linking}>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {status === 'signedOut' ? (
          <Stack.Screen name="Login">
            {(props) => (
              <LoginScreen {...props} onLoginSuccess={() => setStatus('signedIn')} />
            )}
          </Stack.Screen>
        ) : (
          <Stack.Screen name="ActionCenter">
            {(props) => (
              <ActionCenterScreen
                {...props}
                onSignOut={async () => {
                  await getTokenManager().logout();
                  setStatus('signedOut');
                }}
              />
            )}
          </Stack.Screen>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  splash: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f5f6f8' },
  splashText: { marginTop: 8, color: '#667085', fontSize: 14 },
});
