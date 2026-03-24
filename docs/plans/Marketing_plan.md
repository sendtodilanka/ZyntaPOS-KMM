# ZyntaPOS — Sri Lanka Market Entry: Pricing & Go-to-Market Strategy

> **Document type:** Marketing & Business Strategy
> **Last updated:** 2026-03-24
> **Status:** Final Draft
> **Target market:** Sri Lankan SME retail & F&B

---

## 1. Executive Summary

ZyntaPOS is a Kotlin Multiplatform, offline-first Point of Sale system targeting Sri Lankan SMEs. It runs on Android tablets and Desktop (Windows, macOS, Linux) from a single codebase, with AES-256 encrypted local storage and CRDT-based conflict-free sync.

This document defines ZyntaPOS's LKR-native pricing, competitive positioning, and go-to-market strategy for the Sri Lankan market.

**Three commercial tiers:** STARTER, PROFESSIONAL, ENTERPRISE — each available as one-time license, monthly subscription, or annual subscription.

### Pricing at a Glance

| | STARTER | PROFESSIONAL | ENTERPRISE |
|---|---|---|---|
| **One-Time** | LKR 35,000 | LKR 75,000 | LKR 150,000 |
| **Monthly** | LKR 2,500/mo | LKR 5,900/mo | LKR 11,900/mo |
| **Annual** | LKR 25,000/yr | LKR 59,000/yr | LKR 119,000/yr |

---

## 2. Market Landscape — Competitor Analysis

### 2.1 Competitor Pricing & Features

| Competitor | Model | Price Range (LKR) | Strengths | Weaknesses |
|---|---|---|---|---|
| **Microimage (HiPOS)** | One-time + 15-20% AMC | 75,000 – 300,000+ | 30+ year brand, Sinhala/Tamil UI, 100+ reports, chain management. Deployed at Cargills, Keells. | Windows-only, legacy architecture, expensive AMC, no offline CRDT sync. |
| **SalesPlay** | Subscription + free tier | Free – 15,000/mo per outlet | Cloud-based, CRM, loyalty, KDS for F&B, table management. | Partial offline only — unusable during internet outages. No local encryption. |
| **hSenid ERP** | Enterprise license | 500,000+ | Full ERP suite (HR, finance, procurement). | Overkill for SMEs. No standalone POS offering. |
| **BillMe / BillPOS** | Freemium subscription | 1,500 – 5,000/mo | Affordable, mobile-friendly, basic invoicing. | No offline mode, no encryption, no multi-store, no IRD compliance. |
| **Aboron POS** | Subscription | 3,000 – 10,000/mo | Simple cloud POS, pharmacy-focused. | Basic features, no offline, no encryption. |
| **Loyverse** | Freemium (international) | Free; add-ons 3,000-5,000/mo | Free entry point, basic POS. | No Sri Lankan compliance, no Sinhala UI, limited reporting, no encryption. |
| **PayHere** | Transaction-based (2.5-3.5%) | Per-transaction fee | LankaQR, payment links, e-commerce integration. | Not a POS system — payment gateway only. |

### 2.2 Key Takeaway

The Sri Lankan POS market has two extremes:
- **Budget cloud tools** (BillMe, Loyverse, Aboron) — cheap but lack offline reliability, encryption, and compliance
- **Legacy enterprise** (Microimage, hSenid) — feature-rich but expensive, Windows-bound, and architecturally outdated

**ZyntaPOS fills the gap:** enterprise-grade features at SME pricing, with true offline operation and modern cross-platform architecture.

---

## 3. Competitive Advantages (SWOT)

### 3.1 Strengths

| Advantage | Detail | No Competitor Matches |
|---|---|---|
| **True offline-first + CRDT sync** | SQLCipher AES-256 encrypted local DB with conflict-free replication (LWW, FIELD_MERGE, APPEND_ONLY). Data stays on-device, syncs when connectivity returns. | SalesPlay/Aboron have basic offline caching only. Microimage uses legacy client-server. |
| **Cross-platform (single codebase)** | Android tablet + Desktop (Windows, macOS, Linux) via Kotlin Multiplatform. Shop owner uses desktop at counter, Android tablet on the floor. | Microimage = Windows-only. SalesPlay = cloud/web. BillMe = web-only. |
| **Military-grade data security** | AES-256 database encryption, Android Keystore / JCE key management, SHA-256+salt PIN hashing, hardware-backed key storage. | Zero local competitors encrypt local databases. |
| **Native IRD e-invoicing** | Built-in pipeline for Sri Lanka Inland Revenue Department electronic invoice submission — ready for the upcoming mandate. | Only Microimage has partial IRD support. Startups lack it entirely. |
| **Multi-store with central KPI** | Store selector, inter-store transfers, central dashboard, warehouse stock management, region-based pricing rules. | Only Microimage offers comparable multi-store — at 2-4x the price. |
| **Modern architecture** | MVI pattern, Compose Multiplatform UI, type-safe navigation, 5-role RBAC, remote diagnostics. | Microimage runs legacy code. SalesPlay is standard web SaaS. |

### 3.2 Weaknesses (gaps to address)

| Gap | Impact | Mitigation | Priority |
|---|---|---|---|
| No Sinhala/Tamil UI | Excludes ~60% of SMEs outside Colombo | Localization sprint — critical for STARTER adoption | Phase 4 — High |
| No LankaQR integration | Table stakes for Sri Lankan POS in 2026 | Partner with LankaClear | Phase 3/4 |
| No free tier | Loyverse/SalesPlay Free capture micro-retailers at zero cost | 14-day free trial + limited STARTER Free mode | Launch |
| No hardware bundle | SMEs expect turnkey solutions (terminal + software) | Partner with Singer, Softlogic for bundle deals | Phase 4 |
| No WhatsApp integration | Sri Lankan SMEs heavily use WhatsApp for customer comms | Phase 4 add-on | Phase 4 |
| Zero brand awareness | Microimage has 30+ years; SalesPlay has regional presence | Aggressive digital marketing + channel partnerships | Launch |

### 3.3 Market Opportunities

| Opportunity | Strategy |
|---|---|
| **IRD e-invoicing mandate** | Position as "the only POS that's IRD-ready out of the box" — government compliance as a sales trigger. |
| **Post-crisis digital acceleration** | SMEs are seeking affordable digital tools — STARTER at LKR 35,000 undercuts Microimage by 2x+. |
| **Unreliable internet nationwide** | "Works without internet" = #1 messaging angle. No competitor matches CRDT sync quality. |
| **Multi-store gap at SME pricing** | Microimage charges 300K+ for multi-store. ZyntaPOS ENTERPRISE at 150K captures growing chains at 50% savings. |
| **Desktop + Android dual-platform** | Unique story: desktop at counter + Android tablet for floor sales — no competitor offers this from one codebase. |
| **Data sovereignty concerns** | "Your data is encrypted on YOUR device" — resonates in a market with growing cloud data sovereignty concerns. |

---

## 4. Pricing Strategy

### 4.1 Design Principles

1. **Three payment models** — One-time (perpetual license), Monthly subscription, Annual subscription
2. **17% annual discount** — industry-standard SaaS incentive (effectively 2 months free)
3. **~13-14 month breakeven** — one-time license pays for itself vs. monthly in just over a year, creating clear purchase incentive
4. **Price anchoring** — ENTERPRISE at 150K makes PROFESSIONAL at 75K look like a great deal (exactly half), driving most customers to the "sweet spot" tier
5. **Premium positioning** — not the cheapest, but the best value. Encryption, offline sync, and IRD compliance justify the premium over BillMe/Loyverse

### 4.2 Tier-to-Edition Mapping (Internal)

| Marketing Tier | Internal Edition | Gated Features (ZyntaFeature enum) |
|---|---|---|
| **STARTER** | STANDARD | AUTH, POS_CORE, INVENTORY_CORE, REGISTER, SETTINGS, REPORTS_STANDARD, DASHBOARD, ONBOARDING |
| **PROFESSIONAL** | PREMIUM | All STANDARD + POS_ADVANCED, INVENTORY_ADVANCED, REGISTER_ADVANCED, COUPONS, CRM_LOYALTY, EXPENSES, MEDIA, REPORTS_PREMIUM |
| **ENTERPRISE** | ENTERPRISE | All PREMIUM + STAFF_HR, ACCOUNTING, E_INVOICE, ADMIN, MULTISTORE, CUSTOM_RBAC, REPORTS_ENTERPRISE, REMOTE_DIAGNOSTICS |

---

### 4.3 STARTER — LKR 35,000

> **Target:** Single-store small businesses — bakeries, salons, pharmacies, boutiques, small restaurants

| Payment Model | Price (LKR) | Rationale |
|---|---|---|
| **One-Time License** | **LKR 35,000** | 50% below Microimage's entry (75K). Above the "cheap = low quality" threshold. Breakeven vs. monthly at ~14 months. |
| **Monthly** | **LKR 2,500/mo** | 35,000 ÷ 14 = 2,500. Premium over BillMe's low end (1,500) justified by encrypted offline-first architecture. |
| **Annual** | **LKR 25,000/yr** | 2,500 × 12 = 30,000 → 17% discount → 25,000. Effectively LKR 2,083/mo. |

**Plan Limits:**

| Resource | Limit |
|---|---|
| Terminals | 1 |
| Stores | 1 |
| User accounts | 2 (Admin + 1 Cashier) |
| Products | 500 |

**Included Features:**

| Feature | Status |
|---|---|
| Core POS & checkout | Included |
| Basic inventory management | Included |
| Cash register sessions & Z-reports | Included |
| Daily/weekly sales summary | Included |
| Receipt printing (ESC/POS) | Included |
| Barcode scanning | Included |
| Offline-first with encrypted DB | Included |
| Basic settings & store profile | Included |
| Customer directory & CRM | — |
| Coupons & promotions | — |
| Advanced reports + CSV/PDF export | — |
| Multi-store | — |
| E-Invoice (IRD) | — |
| Staff management & payroll | — |

**Positioning:** Undercuts SalesPlay paid plans. Beats Loyverse on security and offline reliability. Beats BillMe on feature depth while commanding a slight premium justified by AES-256 encryption and CRDT sync. The one-time perpetual license is unique — no competitor offers this at this price point.

---

### 4.4 PROFESSIONAL — LKR 75,000

> **Target:** Growing retail businesses — multi-terminal shops, cafes, small supermarkets, clothing stores

| Payment Model | Price (LKR) | Rationale |
|---|---|---|
| **One-Time License** | **LKR 75,000** | Matches Microimage's entry-level but with far more features included. Breakeven vs. monthly at ~12.7 months. |
| **Monthly** | **LKR 5,900/mo** | Psychological pricing (5,900 vs 5,500 — negligible perception difference, +LKR 4,800/yr revenue per customer). 35-55% below SalesPlay's equivalent tier. |
| **Annual** | **LKR 59,000/yr** | 5,900 × 12 = 70,800 → 17% discount → 59,000. Effectively LKR 4,917/mo. |

**Plan Limits:**

| Resource | Limit |
|---|---|
| Terminals | Up to 5 |
| Stores | 1 |
| User accounts | 10 |
| Products | Unlimited |

**Included Features:**

| Feature | Status |
|---|---|
| Everything in STARTER | Included |
| Advanced POS (hold orders, split payment, refunds) | Included |
| Advanced inventory (stock adjustments, supplier management) | Included |
| Advanced register (cash in/out, detailed Z-reports) | Included |
| Customer directory + loyalty program | Included |
| Coupons & promotions (BOGO, %, threshold) | Included |
| Expense tracking + P&L statements | Included |
| Product image management (crop, compress) | Included |
| Full reports + CSV/PDF export | Included |
| Email support (48h SLA) | Included |
| Multi-store | — |
| E-Invoice (IRD) | — |
| Staff HR & payroll | — |
| Custom RBAC roles | — |

**Positioning:** The "sweet spot" tier — expected to capture the majority of customers. At LKR 75K one-time, it matches Microimage's entry price but includes CRM, loyalty, coupons, expenses, and advanced reporting that Microimage charges separately for. Price-anchored against ENTERPRISE (150K) — appears as a great deal at exactly half the price.

---

### 4.5 ENTERPRISE — LKR 150,000

> **Target:** Multi-store retail chains, supermarkets, franchise operators, businesses needing IRD e-invoicing compliance

| Payment Model | Price (LKR) | Rationale |
|---|---|---|
| **One-Time License** | **LKR 150,000** | 50% cheaper than Microimage's enterprise (300K+). Enterprise customers value features over price — 150K for unlimited everything + IRD e-invoicing is a compelling deal. Breakeven vs. monthly at ~12.6 months. |
| **Monthly** | **LKR 11,900/mo** | Positions as premium enterprise. "Too cheap" monthly pricing undermines enterprise credibility. Still dramatically below Microimage's annual AMC costs. |
| **Annual** | **LKR 119,000/yr** | 11,900 × 12 = 142,800 → 17% discount → 119,000. Effectively LKR 9,917/mo. |

**Plan Limits:**

| Resource | Limit |
|---|---|
| Terminals | Unlimited |
| Stores | Unlimited |
| User accounts | Unlimited |
| Products | Unlimited |

**Included Features:**

| Feature | Status |
|---|---|
| Everything in PROFESSIONAL | Included |
| Multi-store central dashboard | Included |
| Inter-store transfers & warehouse stock | Included |
| Staff management (shifts, scheduling, attendance, payroll) | Included |
| Accounting module | Included |
| E-Invoice (IRD) submission pipeline | Included |
| System admin tools (health, audit log, DB maintenance) | Included |
| Custom RBAC role configuration | Included |
| Enterprise reports + API access | Included |
| Remote diagnostics (Zynta technician access) | Included |
| Priority support (4h SLA) + dedicated account manager | Included |

**Positioning:** 50% cheaper than Microimage's chain management solution (300K+) and includes IRD e-invoicing, CRDT sync, and remote diagnostics that no competitor offers. The monthly at 11,900 commands enterprise credibility while remaining dramatically below Microimage's AMC costs. Enterprise buyers expect to pay more — pricing too low signals "not enterprise-ready".

---

### 4.6 Pricing Summary

| | STARTER | PROFESSIONAL | ENTERPRISE |
|---|---|---|---|
| **One-Time** | LKR 35,000 | LKR 75,000 | LKR 150,000 |
| **Monthly** | LKR 2,500/mo | LKR 5,900/mo | LKR 11,900/mo |
| **Annual** | LKR 25,000/yr | LKR 59,000/yr | LKR 119,000/yr |
| **Annual savings** | 17% (save LKR 5,000) | 17% (save LKR 11,800) | 17% (save LKR 23,800) |
| **One-time breakeven** | ~14 months | ~12.7 months | ~12.6 months |
| **Terminals** | 1 | Up to 5 | Unlimited |
| **Stores** | 1 | 1 | Unlimited |
| **Users** | 2 | 10 | Unlimited |
| **Products** | 500 | Unlimited | Unlimited |

---

## 5. Add-on Revenue

| Add-on | Price (LKR) | Available to |
|---|---|---|
| Additional terminal license | 5,000 one-time / 500/mo | STARTER (above 1 terminal) |
| Additional store | 15,000 one-time / 1,500/mo | PROFESSIONAL |
| Hardware bundle (tablet + printer + scanner + cash drawer) | 85,000 – 150,000 | All tiers |
| Premium support upgrade (4h SLA) | 3,000/mo | STARTER, PROFESSIONAL |
| On-site setup & training (per visit) | 10,000 | All tiers |
| Data migration from legacy POS | 15,000 – 50,000 (one-time) | All tiers |
| Custom receipt/report templates | 5,000 per template | All tiers |

---

## 6. Go-to-Market Strategy

### 6.1 Launch Tactics

| Element | Strategy |
|---|---|
| **Free trial** | 14-day full-feature trial of PROFESSIONAL — no credit card required. Converts to STARTER Free-tier or paid plan after expiry. |
| **Freemium hook** | Limited STARTER Free mode: 1 terminal, 50 products, 30-day sales history. Converts manual-ledger users who aren't ready to pay. |
| **Launch promotion** | "Early adopter" 25% discount on annual plans for first 500 customers. Creates urgency + builds initial user base. |
| **Upgrade triggers** | Product limit hit (500 → unlimited), need for coupons/CRM, second store opening, IRD e-invoicing mandate. |

### 6.2 Messaging Pillars

| Pillar | Message |
|---|---|
| **Offline-first** | "Your business never stops — even when the internet does. ZyntaPOS works without Wi-Fi, with military-grade encryption." |
| **IRD compliance** | "Be IRD-ready before the mandate — ZyntaPOS is the only affordable POS with built-in e-invoicing." |
| **Data security** | "Your data is encrypted on YOUR device — not on someone else's cloud server." |
| **Value** | "Enterprise features at SME prices — 50% less than legacy POS systems, with modern technology." |
| **Dual-platform** | "Desktop at the counter. Tablet on the floor. One system, perfectly synced." |

### 6.3 Channel Partnerships

| Partner Type | Examples | Value |
|---|---|---|
| **Electronics retailers** | Singer, Softlogic | Hardware + software bundles, nationwide reach |
| **Telecom / mPOS** | Dialog | Merchant network, mobile-first SME customers |
| **Accounting firms** | Local CA firms | Trusted advisors to SMEs, IRD compliance angle |
| **Industry associations** | Chamber of Commerce, SLASSCOM | Credibility, bulk licensing opportunities |
| **Payment providers** | PayHere, LankaPay | Integration partnerships, co-marketing |

### 6.4 Digital Marketing Channels

| Channel | Approach |
|---|---|
| **Google Ads (SL)** | Target "POS system Sri Lanka", "billing software Sinhala", "shop management software" |
| **Facebook/Instagram** | SME owner testimonials, demo videos, before/after stories |
| **YouTube** | Sinhala/English product walkthroughs, "How to set up ZyntaPOS in 5 minutes" |
| **SEO** | Blog content: "Best POS systems in Sri Lanka 2026", "IRD e-invoicing guide" |
| **WhatsApp Business** | Direct sales outreach to retail associations, trade groups |

---

## 7. Implementation Notes

This is a **marketing and business strategy document only**. No source code changes are proposed in this document.

The pricing values defined here should be reflected in:
1. `website/src/data/pricing.ts` — update from USD to LKR, add monthly/annual/one-time toggle
2. Marketing materials and sales collateral
3. License backend configuration (edition → pricing tier mapping)
4. In-app upgrade prompts and feature gate messaging
