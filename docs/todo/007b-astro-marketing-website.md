# TODO-007b — Astro Marketing Website (www.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P1
**Status:** ✅ Implementation complete — pending Cloudflare Pages DNS cutover
**Effort:** ~5 working days (1 developer)
**Related:** TODO-007 (infrastructure), TODO-008 (SEO/ASO), TODO-010 (Cloudflare security)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-07
**Completed:** 2026-03-07 (code + CI merged to main; Cloudflare Pages setup pending user action)

---

## 1. Overview

Build a static marketing website for ZyntaPOS using **Astro 5** + **Tailwind CSS 4** and deploy it to **Cloudflare Pages**. The site lives at `zyntapos.com` / `www.zyntapos.com`, entirely separate from the VPS (no Docker, no Caddy involvement for this subdomain).

### Goals
- Convert visitors → trial signups / demo requests / Play Store installs
- Rank on Google for Sri Lanka POS-related queries (see TODO-008)
- Lighthouse ≥90 on all 4 axes (Performance, Accessibility, Best Practices, SEO)
- Zero client-side JavaScript by default (Astro islands only where needed)

### Non-Goals (deferred)
- i18n (Sinhala/Tamil) — Phase 3
- Blog content — just the structural skeleton for now
- GA4/GTM integration — wired in TODO-008 after site goes live
- Payment/checkout on the website — app handles all transactions

---

## 2. Technology Choices

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Framework | **Astro 5.x** | Static-first, zero JS by default, excellent CWV |
| Styling | **Tailwind CSS 4** | Utility-first, tree-shaken, matches dark-theme brand |
| Hosting | **Cloudflare Pages** | Free tier, global CDN, auto HTTPS, Git-connected deploy |
| Icons | **Lucide** (via `astro-icon`) | MIT-licensed, tree-shakable SVG icons |
| Fonts | **Self-hosted Inter** | No Google Fonts DNS lookup; `font-display: swap` |
| Images | **Astro `<Image>`** | Auto WebP/AVIF, responsive srcset, lazy-load |
| Sitemap | `@astrojs/sitemap` | Auto-generated on build |
| Analytics | Deferred to TODO-008 | GTM container added later |

---

## 3. Project Location & Structure

**Root:** `website/` (top-level in ZyntaPOS-KMM monorepo)

```
website/
├── astro.config.mjs              # Astro config (site URL, integrations)
├── tailwind.config.mjs           # Tailwind theme tokens (brand colors)
├── tsconfig.json                 # TypeScript config
├── package.json                  # Dependencies + scripts
├── wrangler.toml                 # Cloudflare Pages config (optional — can use dashboard)
├── public/
│   ├── favicon.svg               # ZyntaPOS logomark
│   ├── favicon.ico               # Fallback ICO
│   ├── apple-touch-icon.png      # 180×180 iOS icon
│   ├── robots.txt                # Crawl rules
│   ├── fonts/
│   │   ├── inter-latin-400.woff2
│   │   ├── inter-latin-500.woff2
│   │   ├── inter-latin-600.woff2
│   │   ├── inter-latin-700.woff2
│   │   └── inter-latin-800.woff2
│   └── images/
│       ├── og/                   # Open Graph images (1200×630) per page
│       │   ├── home.png
│       │   ├── features.png
│       │   ├── pricing.png
│       │   └── about.png
│       ├── screenshots/          # App screenshots (WebP)
│       │   ├── pos-screen.webp
│       │   ├── dashboard.webp
│       │   ├── inventory.webp
│       │   ├── reports.webp
│       │   └── login.webp
│       ├── hero-tablet.webp      # Hero section device mockup
│       ├── logo.svg              # Full "ZyntaPOS" wordmark
│       └── logomark.svg          # Icon-only mark
├── src/
│   ├── layouts/
│   │   └── BaseLayout.astro      # HTML shell, <head>, fonts, footer
│   ├── components/
│   │   ├── Header.astro          # Sticky nav bar
│   │   ├── Footer.astro          # Footer with links, copyright, social
│   │   ├── Hero.astro            # Homepage hero section
│   │   ├── FeatureCard.astro     # Reusable feature grid card
│   │   ├── FeatureSection.astro  # Section with heading + FeatureCard grid
│   │   ├── PricingCard.astro     # Single pricing tier card
│   │   ├── PricingTable.astro    # 3-tier pricing layout
│   │   ├── FaqAccordion.astro    # Expandable FAQ item (zero JS — <details>)
│   │   ├── FaqSection.astro      # FAQ grid with JSON-LD injection
│   │   ├── CtaBanner.astro       # Reusable CTA strip (demo/download)
│   │   ├── Testimonial.astro     # Customer quote card (Phase 2+)
│   │   ├── IndustryCard.astro    # Industry vertical card
│   │   ├── StatsBar.astro        # Animated stat counters
│   │   ├── SeoHead.astro         # Canonical, OG, Twitter, JSON-LD slot
│   │   ├── Breadcrumbs.astro     # Breadcrumb nav + BreadcrumbList JSON-LD
│   │   └── MobileMenu.astro      # Hamburger menu (Astro island — minimal JS)
│   ├── content/
│   │   ├── config.ts             # Astro content collection schema
│   │   └── blog/                 # Markdown blog posts (empty for now)
│   │       └── .gitkeep
│   ├── data/
│   │   ├── features.ts           # Feature list data (icon, title, description)
│   │   ├── pricing.ts            # Tier data (name, price, features, CTA)
│   │   ├── faq.ts                # FAQ Q&A pairs
│   │   ├── industries.ts         # Industry verticals (retail, restaurant, etc.)
│   │   ├── navigation.ts         # Nav links + footer links
│   │   └── company.ts            # Company info constants
│   ├── pages/
│   │   ├── index.astro           # Homepage
│   │   ├── features.astro        # Features page
│   │   ├── pricing.astro         # Pricing page
│   │   ├── industries.astro      # Industries page
│   │   ├── about.astro           # About / Company page
│   │   ├── support.astro         # FAQ / Support page
│   │   ├── blog/
│   │   │   └── index.astro       # Blog index (empty state for now)
│   │   ├── privacy.astro         # Privacy policy
│   │   ├── terms.astro           # Terms of service
│   │   └── 404.astro             # Custom 404
│   ├── styles/
│   │   └── global.css            # @tailwind directives, font-face, custom utilities
│   └── utils/
│       └── seo.ts                # Helper to build OG/Twitter/JSON-LD meta objects
├── lighthouserc.json             # Lighthouse CI budget thresholds
└── .nvmrc                        # Node version pin (e.g., 22)
```

---

## 4. Brand Design Tokens

Extracted from the existing canary pages + ZyntaTheme in the KMM codebase.

```js
// tailwind.config.mjs
export default {
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',   // primary — cyan/sky blue
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },
        slate: {
          850: '#1a2332',   // custom mid-tone for cards
          900: '#0f172a',   // body background
          800: '#1e293b',   // card background
          700: '#334155',   // card border
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
    },
  },
}
```

**Logo rendering:**
```html
<span class="font-extrabold text-white tracking-tight">
  Zynta<span class="text-brand-500">POS</span>
</span>
```

---

## 5. Page-by-Page Specification

### 5.1 Homepage (`/`)

**Purpose:** First impression + conversion funnel entry point
**Target keyword:** "POS system Sri Lanka"

**Sections (top to bottom):**

| # | Section | Content | Component |
|---|---------|---------|-----------|
| 1 | **Hero** | Headline: "The Offline-First POS for Modern Retail" · Subtext: 1 sentence value prop · 2 CTAs: "Start Free Trial" (primary), "Watch Demo" (secondary) · Hero image: tablet mockup showing POS screen | `Hero.astro` |
| 2 | **Trust bar** | "Trusted by 100+ retailers across Sri Lanka" + stat counters (orders processed, uptime %, terminals active) | `StatsBar.astro` |
| 3 | **Key features** | 6-card grid: Offline-First, Encrypted Database, Multi-Store, Receipt Printing, Barcode Scanning, Role-Based Access | `FeatureSection.astro` + `FeatureCard.astro` |
| 4 | **How it works** | 3-step visual: 1. Download → 2. Set up your store → 3. Start selling | Custom section |
| 5 | **Industries** | 4-card grid: Retail, Restaurant/Café, Grocery, Pharmacy | `IndustryCard.astro` |
| 6 | **Pricing preview** | 3 tiers side-by-side with "View full pricing →" link | `PricingTable.astro` |
| 7 | **FAQ** (3 items) | Top 3 questions (offline, security, tablets) | `FaqSection.astro` |
| 8 | **CTA banner** | "Ready to modernize your checkout?" + "Start Free Trial" button | `CtaBanner.astro` |

**JSON-LD:** Organization + WebSite + SoftwareApplication (from TODO-008 §2.1, §2.2, §2.3)

---

### 5.2 Features (`/features`)

**Purpose:** Deep dive into all product capabilities
**Target keyword:** "POS features"

**Sections:**

| # | Section | Content |
|---|---------|---------|
| 1 | Page hero | "Everything You Need to Run Your Business" |
| 2 | **POS & Checkout** | Product grid, cart, discounts, split payment, hold/resume orders, refunds |
| 3 | **Inventory Management** | Stock tracking, barcode scanning, low-stock alerts, category management, stock adjustments |
| 4 | **Customers & Loyalty** | Customer directory, loyalty points, purchase history, GDPR export |
| 5 | **Reports & Analytics** | Sales summary, product performance, stock report, CSV/PDF export, Z-report |
| 6 | **Security** | AES-256 encryption, Android Keystore, RBAC (5 roles), PIN quick-switch, full audit log |
| 7 | **Multi-Store** | Central dashboard, inter-store transfers, per-outlet reports |
| 8 | **Hardware** | ESC/POS printers (USB + network), barcode scanners (USB HID + camera), cash drawer |
| 9 | **Offline-First** | Visual diagram: local DB → sync engine → cloud. "Never lose a sale" messaging |
| 10 | CTA banner | "See it in action — Start your free trial" |

Each section: icon + heading + 2-3 sentence description + screenshot (when available)

**JSON-LD:** SoftwareApplication + BreadcrumbList

---

### 5.3 Pricing (`/pricing`)

**Purpose:** Convert visitors to trial users
**Target keyword:** "POS pricing Sri Lanka"

**3 Tiers:**

| | STARTER | PROFESSIONAL | ENTERPRISE |
|---|---------|-------------|------------|
| **Price** | Free | $29/mo | $79/mo |
| **Terminals** | 1 | Up to 5 | Unlimited |
| **Stores** | 1 | 1 | Unlimited |
| **Users** | 2 | 10 | Unlimited |
| **Core POS** | ✅ | ✅ | ✅ |
| **Inventory** | Basic | Advanced | Advanced + inter-store |
| **Reports** | Daily summary | Full suite + export | Full + custom + API |
| **Customers** | — | ✅ | ✅ + loyalty |
| **Coupons** | — | ✅ | ✅ |
| **Multi-store** | — | — | ✅ |
| **Audit log** | — | Basic | Full + integrity chain |
| **E-Invoice (IRD)** | — | — | ✅ |
| **Support** | Community | Email (48h) | Priority (4h) + dedicated |
| **CTA** | "Start Free" | "Start Trial" | "Contact Sales" |

**Below pricing:** FAQ section (5 items: billing, upgrades, cancellation, data export, what happens if trial ends)

**JSON-LD:** SoftwareApplication with Offer array + FAQPage + BreadcrumbList

---

### 5.4 Industries (`/industries`)

**Purpose:** Vertical-specific messaging for organic search
**Target keywords:** "restaurant POS Android", "retail POS tablet", "supermarket POS software"

**4 Verticals:**

| Vertical | Headline | Key Features |
|----------|----------|-------------|
| **Retail / Fashion** | "Built for Modern Retail" | Barcode scanning, size/color variants, stock alerts, customer loyalty |
| **Restaurant / Café** | "Speed Up Your Service" | Quick-add menu items, table management (future), split bills, kitchen receipt |
| **Grocery / Supermarket** | "Handle High Volume with Ease" | FTS product search, weight-based items, multi-barcode, daily Z-report |
| **Pharmacy** | "Accurate Dispensing, Every Time" | Batch/expiry tracking, regulated product flags, prescription log (future) |

Each vertical: hero image + 4 feature bullets + "Start Free Trial" CTA

**JSON-LD:** BreadcrumbList

---

### 5.5 About (`/about`)

**Purpose:** Build trust (E-E-A-T signal for Google)
**Target keyword:** "Zynta Solutions"

**Sections:**
1. Company story — "Founded in Sri Lanka, built for the world"
2. Mission — "Make enterprise-grade POS affordable for every retailer"
3. Team (optional — can be placeholder "Meet the team" section)
4. Tech stack highlights — KMM, offline-first, encrypted
5. Contact info — support@zyntasolutions.com, company registration number

**JSON-LD:** Organization + LocalBusiness + BreadcrumbList

---

### 5.6 Support / FAQ (`/support`)

**Purpose:** Answer common questions, reduce support load, capture FAQ rich results
**Target keyword:** "POS help"

**Content:**
- 12-15 FAQ items organized in 4 categories:
  - **Getting Started** (download, setup, first sale)
  - **Billing & Licensing** (plans, upgrades, cancellation)
  - **Technical** (offline mode, sync, hardware compatibility)
  - **Security & Privacy** (encryption, data export, GDPR)
- Contact section: email, response time by tier
- Link to `docs.zyntapos.com` (when available)

**Implementation:** `<details>/<summary>` HTML elements (zero JavaScript accordion)

**JSON-LD:** FAQPage (all items) + BreadcrumbList

---

### 5.7 Blog (`/blog`)

**Purpose:** Content marketing hub (structural skeleton only for now)

**Implementation:**
- Astro content collection with Markdown (.md) files in `src/content/blog/`
- Blog index page with card grid (empty state: "Coming soon — subscribe for updates")
- Individual post layout: title, date, author, reading time, body, related posts
- RSS feed via `@astrojs/rss` (wired but no content yet)

**JSON-LD:** Article schema in post layout template

---

### 5.8 Legal Pages (`/privacy`, `/terms`)

- Simple Markdown-rendered pages
- Privacy Policy covering: data collection, encryption, GDPR rights, cookie policy
- Terms of Service covering: SaaS license terms, acceptable use, liability
- Both pages linked from footer

---

### 5.9 404 Page (`/404`)

- Brand-consistent dark theme
- "Page not found" message
- Search bar (link to `/`) and navigation links
- Fun illustration or ZyntaPOS logomark

---

## 6. Component Specifications

### 6.1 `SeoHead.astro`

The SEO brain of the site. Every page passes props to this component.

```astro
---
interface Props {
  title: string           // Page <title>
  description: string     // <meta name="description">
  canonical: string       // Full URL for canonical tag
  ogImage?: string        // OG image path (default: /images/og/home.png)
  ogType?: string         // "website" | "article"
  noindex?: boolean       // robots noindex (for legal pages)
  jsonLd?: object[]       // Array of JSON-LD objects to inject
  breadcrumbs?: { name: string; url: string }[]
}
---
<!-- Renders: canonical, OG, Twitter Card, JSON-LD scripts, breadcrumb JSON-LD -->
```

### 6.2 `Header.astro`

- Sticky top, glassmorphism background (`backdrop-blur-lg bg-slate-900/80`)
- Logo left, nav links center, "Start Free Trial" button right
- Mobile: hamburger → slide-out menu (`MobileMenu.astro` island)
- Active page indicator (underline on current route)

### 6.3 `Footer.astro`

- 4-column grid: Product, Company, Support, Legal
- Bottom row: © 2026 Zynta Solutions Pvt Ltd · Social icons (LinkedIn, Twitter, Facebook)
- "Built with ❤️ in Sri Lanka"

### 6.4 `PricingCard.astro`

```astro
---
interface Props {
  name: string        // "STARTER" | "PROFESSIONAL" | "ENTERPRISE"
  price: string       // "Free" | "$29" | "$79"
  period?: string     // "/mo" (omit for Free)
  description: string // One-line tier summary
  features: string[]  // Checkmark list
  cta: string         // Button label
  ctaHref: string     // Button link
  popular?: boolean   // Highlight border for recommended tier
}
---
```

### 6.5 `FaqAccordion.astro`

Zero-JS accordion using native `<details>/<summary>`:

```html
<details class="group border-b border-slate-700">
  <summary class="flex cursor-pointer items-center justify-between py-4 text-white font-medium">
    <span>{question}</span>
    <svg class="h-5 w-5 transition-transform group-open:rotate-180">...</svg>
  </summary>
  <div class="pb-4 text-slate-400">{answer}</div>
</details>
```

---

## 7. SEO Infrastructure

### 7.1 `robots.txt` (static in `public/`)

```
User-agent: *
Allow: /
Disallow: /admin
Disallow: /api
Disallow: /panel

Sitemap: https://www.zyntapos.com/sitemap-index.xml
```

### 7.2 Sitemap

Auto-generated by `@astrojs/sitemap` integration. Config:

```js
// astro.config.mjs
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://www.zyntapos.com',
  integrations: [sitemap()],
});
```

### 7.3 Canonical Tags

Every page gets `<link rel="canonical" href="...">` via `SeoHead.astro`. Prevents duplicate content from www vs non-www or trailing slashes.

### 7.4 JSON-LD Per Page

| Page | JSON-LD Types |
|------|--------------|
| `/` | Organization, WebSite, SoftwareApplication |
| `/features` | SoftwareApplication, BreadcrumbList |
| `/pricing` | SoftwareApplication (with Offer[]), FAQPage, BreadcrumbList |
| `/industries` | BreadcrumbList |
| `/about` | Organization, LocalBusiness, BreadcrumbList |
| `/support` | FAQPage, BreadcrumbList |
| `/blog/*` | Article, BreadcrumbList |

All JSON-LD data sourced from TODO-008 §2.1–§2.7 (already written).

### 7.5 Open Graph + Twitter Cards

Every page includes via `SeoHead.astro`:
- `og:title`, `og:description`, `og:image` (1200×630), `og:url`, `og:site_name`, `og:locale`
- `twitter:card=summary_large_image`, `twitter:site=@zyntapos`

### 7.6 Performance Budget

```
LCP  ≤ 2.5s
INP  ≤ 200ms
CLS  ≤ 0.1
FCP  ≤ 1.8s
TTFB ≤ 800ms
```

Enforced by Lighthouse CI in GitHub Actions (see §10).

---

## 8. Deployment — Cloudflare Pages

### 8.1 Build Configuration

| Setting | Value |
|---------|-------|
| Framework preset | Astro |
| Build command | `cd website && npm run build` |
| Build output directory | `website/dist` |
| Root directory | `/` (monorepo root) |
| Node.js version | 22 (via `.nvmrc`) |
| Environment variables | `SITE_URL=https://www.zyntapos.com` |

### 8.2 Custom Domains

Configure in Cloudflare Pages dashboard:
1. `zyntapos.com` → Pages project (CNAME to `<project>.pages.dev`)
2. `www.zyntapos.com` → Pages project (CNAME to `<project>.pages.dev`)

Cloudflare automatically handles:
- HTTPS certificate provisioning
- www ↔ non-www redirect (configurable)
- Global CDN distribution
- Brotli compression

### 8.3 Headers (`public/_headers`)

```
/*
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload

/fonts/*
  Cache-Control: public, max-age=31536000, immutable

/images/*
  Cache-Control: public, max-age=31536000, immutable

/_astro/*
  Cache-Control: public, max-age=31536000, immutable
```

### 8.4 Redirects (`public/_redirects`)

```
# Redirect non-www to www (or vice-versa — choose one)
# Cloudflare Pages handles this via custom domain settings

# Old URL redirects (add as needed)
/demo  /pricing  301
```

### 8.5 `wrangler.toml` (optional — for CLI deploys)

```toml
name = "zyntapos-website"
compatibility_date = "2026-03-01"

[site]
bucket = "./dist"
```

---

## 9. DNS Configuration

### Current State
- `api.zyntapos.com` → A record → VPS IP (proxied via Cloudflare)
- `license.zyntapos.com` → A record → VPS IP (proxied)
- `sync.zyntapos.com` → A record → VPS IP (DNS-only for WebSocket)
- `status.zyntapos.com` → Cloudflare Tunnel
- `panel.zyntapos.com` → Cloudflare Tunnel

### Changes Needed
| Record | Type | Value | Proxy |
|--------|------|-------|-------|
| `zyntapos.com` | CNAME | `<project>.pages.dev` | Proxied (orange) |
| `www.zyntapos.com` | CNAME | `<project>.pages.dev` | Proxied (orange) |

**Important:** The root domain (`zyntapos.com`) currently points to the VPS (showing the canary page). After Cloudflare Pages is connected, update this to point to Pages instead. The VPS Caddy does NOT need to serve the marketing site — Cloudflare Pages handles it entirely.

---

## 10. GitHub Actions — Lighthouse CI

### New Workflow: `.github/workflows/ci-website.yml`

```yaml
name: Website CI

on:
  push:
    branches: [main, develop]
    paths: ['website/**']
  pull_request:
    branches: [main]
    paths: ['website/**']

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version-file: website/.nvmrc
          cache: npm
          cache-dependency-path: website/package-lock.json

      - name: Install dependencies
        working-directory: website
        run: npm ci

      - name: Build
        working-directory: website
        run: npm run build
        env:
          SITE_URL: https://www.zyntapos.com

      - name: Upload build artifact
        uses: actions/upload-artifact@v4
        with:
          name: website-dist
          path: website/dist/
          retention-days: 7

  lighthouse:
    needs: build
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - uses: actions/download-artifact@v4
        with:
          name: website-dist
          path: website/dist/

      - name: Lighthouse CI
        uses: treosh/lighthouse-ci-action@v12
        with:
          configPath: website/lighthouserc.json
          uploadArtifacts: true
          staticDistDir: website/dist/
```

### `lighthouserc.json`

```json
{
  "ci": {
    "collect": {
      "staticDistDir": "./dist",
      "url": ["/", "/features", "/pricing", "/support"]
    },
    "assert": {
      "assertions": {
        "categories:performance": ["error", { "minScore": 0.90 }],
        "categories:accessibility": ["error", { "minScore": 0.90 }],
        "categories:best-practices": ["error", { "minScore": 0.90 }],
        "categories:seo": ["error", { "minScore": 0.95 }]
      }
    }
  }
}
```

---

## 11. Implementation Order (Day-by-Day)

### Day 1 — Scaffolding & Base Layout

| # | Task | Files |
|---|------|-------|
| 1.1 | `npm create astro@latest website` — select empty template | `website/package.json`, `astro.config.mjs` |
| 1.2 | Add integrations: `@astrojs/tailwind`, `@astrojs/sitemap`, `astro-icon` | `astro.config.mjs` |
| 1.3 | Configure Tailwind with brand tokens | `tailwind.config.mjs` |
| 1.4 | Self-host Inter font (woff2) | `public/fonts/`, `src/styles/global.css` |
| 1.5 | Create `BaseLayout.astro` with full `<head>` | `src/layouts/BaseLayout.astro` |
| 1.6 | Create `SeoHead.astro` (canonical, OG, Twitter, JSON-LD) | `src/components/SeoHead.astro` |
| 1.7 | Create `Header.astro` + `MobileMenu.astro` | `src/components/Header.astro`, `MobileMenu.astro` |
| 1.8 | Create `Footer.astro` | `src/components/Footer.astro` |
| 1.9 | Create `robots.txt` + `public/_headers` + `public/_redirects` | `public/robots.txt`, `public/_headers` |
| 1.10 | Add `.nvmrc`, `lighthouserc.json` | Root-level config |
| 1.11 | Create 404 page | `src/pages/404.astro` |

**Deliverable:** Running `npm run dev` shows a styled page shell with header + footer.

### Day 2 — Homepage + Reusable Components

| # | Task | Files |
|---|------|-------|
| 2.1 | Create data files: `features.ts`, `pricing.ts`, `faq.ts`, `navigation.ts`, `company.ts`, `industries.ts` | `src/data/*.ts` |
| 2.2 | Build `Hero.astro` | `src/components/Hero.astro` |
| 2.3 | Build `FeatureCard.astro` + `FeatureSection.astro` | `src/components/Feature*.astro` |
| 2.4 | Build `StatsBar.astro` | `src/components/StatsBar.astro` |
| 2.5 | Build `IndustryCard.astro` | `src/components/IndustryCard.astro` |
| 2.6 | Build `PricingCard.astro` + `PricingTable.astro` | `src/components/Pricing*.astro` |
| 2.7 | Build `FaqAccordion.astro` + `FaqSection.astro` | `src/components/Faq*.astro` |
| 2.8 | Build `CtaBanner.astro` | `src/components/CtaBanner.astro` |
| 2.9 | Build `Breadcrumbs.astro` | `src/components/Breadcrumbs.astro` |
| 2.10 | Assemble homepage (`index.astro`) with all sections + JSON-LD | `src/pages/index.astro` |

**Deliverable:** Complete homepage with all 8 sections, responsive, dark theme.

### Day 3 — Interior Pages

| # | Task | Files |
|---|------|-------|
| 3.1 | Features page — 8 feature sections with screenshots | `src/pages/features.astro` |
| 3.2 | Pricing page — 3-tier table + FAQ + JSON-LD | `src/pages/pricing.astro` |
| 3.3 | Industries page — 4 vertical cards | `src/pages/industries.astro` |
| 3.4 | About page — company story + mission + contact | `src/pages/about.astro` |
| 3.5 | Support/FAQ page — 15 FAQ items in 4 categories | `src/pages/support.astro` |
| 3.6 | Blog index page — empty state skeleton | `src/pages/blog/index.astro` |
| 3.7 | Privacy + Terms pages | `src/pages/privacy.astro`, `terms.astro` |

**Deliverable:** All 10 pages functional and linked from nav.

### Day 4 — SEO, Performance, Polish

| # | Task | Files |
|---|------|-------|
| 4.1 | Audit all pages for unique `<title>` + `<meta description>` | All page files |
| 4.2 | Add JSON-LD to every page (per §7.4 table) | Via `SeoHead.astro` props |
| 4.3 | Create placeholder OG images (1200×630) — brand gradient + page title | `public/images/og/*.png` |
| 4.4 | Optimize all images: WebP + responsive srcset via `<Image>` | All image refs |
| 4.5 | Add preconnect/preload hints for LCP image | `BaseLayout.astro` |
| 4.6 | Test responsive at 375px, 768px, 1024px, 1440px | Manual + DevTools |
| 4.7 | Run local Lighthouse — fix any scores below 90 | Iterate |
| 4.8 | Verify sitemap generation (`npm run build` → check `dist/sitemap-index.xml`) | — |
| 4.9 | Verify `_headers` + `_redirects` in build output | — |
| 4.10 | Accessibility audit: alt text, ARIA labels, contrast ratios, focus states | All components |

**Deliverable:** Lighthouse ≥90 on all 4 axes locally.

### Day 5 — CI + Deployment

| # | Task | Files |
|---|------|-------|
| 5.1 | Create `.github/workflows/ci-website.yml` | `.github/workflows/ci-website.yml` |
| 5.2 | Connect GitHub repo to Cloudflare Pages project | CF dashboard |
| 5.3 | Configure build settings (see §8.1) | CF dashboard |
| 5.4 | Add custom domains: `zyntapos.com`, `www.zyntapos.com` | CF Pages + DNS |
| 5.5 | Update DNS records (see §9) | CF DNS dashboard |
| 5.6 | Verify production deploy — check all pages load at `www.zyntapos.com` | Manual |
| 5.7 | Verify HTTPS, security headers (via securityheaders.com) | Manual |
| 5.8 | Verify sitemap accessible at `www.zyntapos.com/sitemap-index.xml` | Manual |
| 5.9 | Submit PR, merge to main, confirm auto-deploy | GitHub |
| 5.10 | Update TODO-007 and TODO-008 status | `docs/todo/*.md` |

**Deliverable:** Live website at `www.zyntapos.com` with automated CI.

---

## 12. Post-Launch Checklist (TODO-008 Handoff)

After the site is live, these tasks are tracked in TODO-008:
- [ ] Google Search Console — verify domain, submit sitemap
- [ ] GA4 property + GTM container setup
- [ ] Conversion event wiring (demo request, Play Store click)
- [ ] Lighthouse CI in GitHub Actions (already wired in Day 5)
- [ ] Core Web Vitals monitoring via Search Console
- [ ] Google Play Store ASO (listing metadata, screenshots, Data Safety)

---

## 13. Dependencies

| Dependency | Status | Blocks |
|------------|--------|--------|
| Cloudflare DNS access | ✅ Ready | Domain pointing |
| Brand assets (logo SVG) | ⚠️ Need text-based SVG | Header, favicon |
| App screenshots | ⚠️ Need from running app | Features page, OG images |
| Legal text (privacy, terms) | ⚠️ Need legal review | Legal pages |
| Pricing confirmation | ⚠️ Need business sign-off | Pricing page |

**No hard blockers** — placeholder images/text can be used and swapped later.

---

## 14. Success Criteria

| Metric | Target | Measurement |
|--------|--------|------------|
| Lighthouse Performance | ≥ 90 | CI + manual |
| Lighthouse Accessibility | ≥ 90 | CI + manual |
| Lighthouse Best Practices | ≥ 90 | CI + manual |
| Lighthouse SEO | ≥ 95 | CI + manual |
| Total page weight (homepage) | < 500 KB | DevTools Network |
| Time to Interactive | < 3s | Lighthouse |
| Pages indexed by Google | 10+ within 2 weeks | Search Console |
| HTTPS + security headers | A+ rating | securityheaders.com |

---

## Appendix A: Package Dependencies

```json
{
  "dependencies": {
    "astro": "^5.x",
    "@astrojs/tailwind": "^6.x",
    "@astrojs/sitemap": "^3.x",
    "astro-icon": "^1.x",
    "@iconify-json/lucide": "^1.x"
  },
  "devDependencies": {
    "tailwindcss": "^4.x",
    "@astrojs/check": "^0.x",
    "typescript": "^5.x"
  }
}
```

## Appendix B: Relationship to Other TODOs

```
TODO-007 (Infrastructure)
    └── 7b: This plan ← YOU ARE HERE
         └── TODO-008 (SEO/ASO) — depends on site being live
              ├── 8a: robots.txt, sitemap ← built into this plan
              ├── 8b: Schema.org JSON-LD ← built into this plan
              ├── 8c: Core Web Vitals ← enforced by Lighthouse CI
              ├── 8d: GSC + GA4 + GTM ← post-launch
              ├── 8e: Play Store ASO ← independent
              └── 8f: Lighthouse CI ← built into this plan
```

**Key insight:** This plan front-loads TODO-008 items 8a, 8b, 8c, and 8f into the initial build so they don't become separate work items. Only 8d (analytics) and 8e (ASO) remain as post-launch tasks.
