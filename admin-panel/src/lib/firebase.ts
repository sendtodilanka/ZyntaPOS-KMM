import { initializeApp, FirebaseApp } from 'firebase/app';
import {
  getAnalytics,
  Analytics,
  logEvent as fbLogEvent,
  setUserId as fbSetUserId,
  setUserProperties,
  isSupported,
} from 'firebase/analytics';

/**
 * Firebase JS SDK initialization for the ZyntaPOS Admin Panel.
 *
 * Analytics are only activated when all VITE_FIREBASE_* env vars are present.
 * No-ops gracefully in local dev and test environments where Firebase is not
 * configured. Config is injected at build time via Vite env var substitution.
 *
 * TODO-011 (Phase 2): Firebase JS SDK for web admin panel GA4 analytics.
 * Mirrors the Android Firebase Analytics SDK and Desktop GA4 Measurement Protocol
 * actuals — all three platforms report to the same GA4 property.
 */

interface FirebaseConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
  measurementId: string;
}

let app: FirebaseApp | null = null;
let analytics: Analytics | null = null;

function buildConfig(): FirebaseConfig | null {
  const apiKey = import.meta.env.VITE_FIREBASE_API_KEY as string | undefined;
  const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined;

  // Require at minimum apiKey and projectId to attempt init.
  if (!apiKey || !projectId) return null;

  return {
    apiKey,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? `${projectId}.firebaseapp.com`,
    projectId,
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? `${projectId}.appspot.com`,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
    appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '',
    measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID ?? '',
  };
}

/**
 * Initialise Firebase app and Analytics. Safe to call multiple times — idempotent.
 * Must be called once at application startup before any analytics calls.
 */
export async function initFirebase(): Promise<void> {
  const config = buildConfig();
  if (!config) return; // Not configured — local dev or CI without secrets

  try {
    app = initializeApp(config);

    const supported = await isSupported();
    if (supported) {
      analytics = getAnalytics(app);
    }
  } catch (e) {
    // Firebase init should never crash the app. Degrade silently.
    console.warn('[Firebase] Initialisation failed:', e);
  }
}

/**
 * Log a GA4 analytics event. No-ops when analytics is not initialised.
 * Event names follow Firebase GA4 conventions: snake_case, max 40 chars.
 */
export function logAnalyticsEvent(
  eventName: string,
  params?: Record<string, string | number | boolean>,
): void {
  if (!analytics) return;
  try {
    fbLogEvent(analytics, eventName, params);
  } catch (e) {
    console.warn('[Firebase] logEvent failed:', e);
  }
}

/**
 * Set the authenticated user ID for cross-platform attribution.
 * Pass null on logout to clear attribution.
 */
export function setAnalyticsUserId(userId: string | null): void {
  if (!analytics) return;
  try {
    fbSetUserId(analytics, userId);
  } catch (e) {
    console.warn('[Firebase] setUserId failed:', e);
  }
}

/**
 * Set user properties for GA4 audience segmentation.
 * E.g. role = "ADMIN", edition = "ENTERPRISE".
 */
export function setAnalyticsUserProperties(
  properties: Record<string, string>,
): void {
  if (!analytics) return;
  try {
    setUserProperties(analytics, properties);
  } catch (e) {
    console.warn('[Firebase] setUserProperties failed:', e);
  }
}
