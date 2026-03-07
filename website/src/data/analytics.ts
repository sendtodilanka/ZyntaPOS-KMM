// ─────────────────────────────────────────────────────────────────────────────
// Analytics & Webmaster Verification Configuration
//
// HOW TO FILL IN:
//   GA4_MEASUREMENT_ID  → Google Analytics 4 → Admin → Data Streams → Web → Measurement ID
//   GSC_VERIFICATION    → Google Search Console → Settings → Ownership verification → HTML tag → content value
//   BING_VERIFICATION   → Bing Webmaster Tools → Settings → Site Verification → Meta Tag → content value
//
// Leave a value as empty string ('') to disable that service.
// ─────────────────────────────────────────────────────────────────────────────

export const analytics = {
  // Google Analytics 4 — format: 'G-XXXXXXXXXX'
  GA4_MEASUREMENT_ID: '',

  // Google Search Console — HTML meta tag verification code (content= value only)
  // Example: 'abc123xyz_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX'
  GSC_VERIFICATION: '',

  // Bing Webmaster Tools — meta tag verification code (content= value only)
  BING_VERIFICATION: '',
};
