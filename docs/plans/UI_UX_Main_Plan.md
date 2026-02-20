# ZentaPOS — Enterprise UI/UX Master Blueprint

> **Document ID:** ZENTA-UI-UX-PLAN-v1.0  
> **Status:** APPROVED FOR EXECUTION  
> **Platforms:** Desktop (JVM — Win/Mac/Linux), Android Tablet, Android Phone  
> **UI Framework:** Compose Multiplatform + Material 3  
> **Author:** Lead Enterprise UI/UX Designer & HCI Expert  
> **Created:** 2026-02-19  

---

## Table of Contents

1. [Design Philosophy & Principles](#1-design-philosophy--principles)
2. [Responsive Breakpoint System](#2-responsive-breakpoint-system)
3. [Design System Tokens & Component Library](#3-design-system-tokens--component-library)
4. [Global Navigation Architecture](#4-global-navigation-architecture)
5. [Screen 1: Authentication & Session Control](#5-screen-1-authentication--session-control)
6. [Screen 2: Home / Central Dashboard](#6-screen-2-home--central-dashboard)
7. [Screen 3: POS / Checkout Screen](#7-screen-3-pos--checkout-screen)
8. [Screen 4: Payment Flow](#8-screen-4-payment-flow)
9. [Screen 5: Inventory Management](#9-screen-5-inventory-management)
10. [Screen 6: Cash Register Operations](#10-screen-6-cash-register-operations)
11. [Screen 7: Customer Relationship Management (CRM)](#11-screen-7-customer-relationship-management-crm)
12. [Screen 8: Reporting & Analytics](#12-screen-8-reporting--analytics)
13. [Screen 9: Coupons & Promotions](#13-screen-9-coupons--promotions)
14. [Screen 10: Multi-Store Management](#14-screen-10-multi-store-management)
15. [Screen 11: Staff Management](#15-screen-11-staff-management)
16. [Screen 12: Expenses & Accounting](#16-screen-12-expenses--accounting)
17. [Screen 13: Settings & Hardware Configuration](#17-screen-13-settings--hardware-configuration)
18. [Screen 14: System Administration](#18-screen-14-system-administration)
19. [Standardized Sub-Screens, Dialogs & Forms](#19-standardized-sub-screens-dialogs--forms)
20. [Micro-Interactions & Feedback Patterns](#20-micro-interactions--feedback-patterns)
21. [Offline-First UX Patterns](#21-offline-first-ux-patterns)
22. [Accessibility & Compliance](#22-accessibility--compliance)
23. [Keyboard Shortcut Map](#23-keyboard-shortcut-map)

---

## 1. Design Philosophy & Principles

### 1.1 Core UX Pillars

| Pillar | Principle | Application |
|--------|-----------|-------------|
| **Speed** | Every POS interaction must complete in ≤2 taps/clicks | Large touch targets (min 48dp), smart defaults, barcode-first input |
| **Clarity** | Zero ambiguity in financial operations | Explicit totals, confirm-before-destructive-action, clear currency formatting |
| **Resilience** | Offline-first confidence | Always show cached data; sync status is ambient, never blocking |
| **Adaptability** | One codebase, three form factors | Responsive layouts via Compose WindowSizeClass, not pixel hacking |
| **Consistency** | Predictable interaction patterns | Every list is searchable + filterable; every form is validatable; every action is undoable |

### 1.2 Cognitive Load Management

The POS checkout screen is the most performance-critical UI in the entire system. The design must obey Hick's Law (fewer choices = faster decisions) and Fitts's Law (larger + closer targets = faster motor response):

- **Primary actions** (Add to Cart, Pay) occupy the largest visual footprint with highest contrast.
- **Secondary actions** (Hold, Discount, Customer) are accessible but visually subordinate.
- **Destructive actions** (Void, Clear Cart) require explicit confirmation and are positioned away from primary zones.
- **Information density** scales by role: Cashiers see simplified POS; Managers see full dashboards.

### 1.3 Color Semantics (Functional Color Roles)

| Semantic Role | Light Mode Token | Dark Mode Token | Usage |
|---------------|-----------------|-----------------|-------|
| **Primary Action** | `md_primary` | `md_primary_dark` | Pay button, main CTAs |
| **Success / Synced** | `md_tertiary` (green tint) | `md_tertiary_dark` | Sync complete, payment success |
| **Warning / Pending** | `md_secondary` (amber tint) | `md_secondary_dark` | Low stock, pending sync |
| **Error / Destructive** | `md_error` | `md_error_dark` | Void, delete, failed sync |
| **Neutral / Surface** | `md_surface` | `md_surface_dark` | Backgrounds, cards, containers |
| **On-Surface Text** | `md_on_surface` | `md_on_surface_dark` | Primary text content |
| **Disabled** | `md_on_surface` @ 38% | `md_on_surface_dark` @ 38% | Inactive controls |

---

## 2. Responsive Breakpoint System

### 2.1 WindowSizeClass Mapping

| Class | Width Range | Target Devices | Layout Strategy |
|-------|------------|----------------|-----------------|
| **Compact** | < 600dp | Android phone (4"–6.5") | Single-pane, bottom navigation bar, stacked layouts, full-screen modals |
| **Medium** | 600–840dp | Android tablet (7"–10"), small laptop | Two-pane master/detail where applicable, navigation rail (side), bottom sheets for quick actions |
| **Expanded** | > 840dp | Desktop monitors (13"+), large tablets in landscape | Multi-pane split views, persistent side navigation drawer, inline dialogs, keyboard-shortcut-driven workflows |

### 2.2 Grid & Spacing System

| Token | Value | Usage |
|-------|-------|-------|
| `grid_base` | 4dp | Minimum spacing unit |
| `spacing_xs` | 4dp | Tight element separation (icon–label) |
| `spacing_sm` | 8dp | Intra-component padding |
| `spacing_md` | 16dp | Standard card padding, list item spacing |
| `spacing_lg` | 24dp | Section separation |
| `spacing_xl` | 32dp | Major layout gutters |
| `spacing_xxl` | 48dp | Page margins on Expanded |
| `touch_min` | 48dp | Minimum touch target (WCAG / Material 3) |
| `touch_preferred` | 56dp | Preferred POS button height |

### 2.3 Product Grid Column Rules

| WindowSizeClass | Grid Columns | Card Min Width | Card Aspect Ratio |
|-----------------|-------------|---------------|-------------------|
| Compact | 2 | 140dp | 1:1.2 (portrait) |
| Medium | 3–4 | 150dp | 1:1.1 |
| Expanded | 4–6 | 160dp | 1:1 (square tile) |

---

## 3. Design System Tokens & Component Library

### 3.1 Typography Scale (Material 3)

| Role | Font | Size | Weight | Line Height | Usage |
|------|------|------|--------|-------------|-------|
| Display Large | System Sans | 57sp | 400 | 64sp | Dashboard hero numbers (daily revenue) |
| Headline Large | System Sans | 32sp | 400 | 40sp | Screen titles |
| Headline Medium | System Sans | 28sp | 400 | 36sp | Section headers |
| Title Large | System Sans | 22sp | 500 | 28sp | Card titles |
| Title Medium | System Sans | 16sp | 500 | 24sp | List item primary text |
| Body Large | System Sans | 16sp | 400 | 24sp | Main content text |
| Body Medium | System Sans | 14sp | 400 | 20sp | Secondary content, descriptions |
| Label Large | System Sans | 14sp | 500 | 20sp | Button text, tab labels |
| Label Medium | System Sans | 12sp | 500 | 16sp | Badges, tags, chip labels |
| Label Small | System Sans | 11sp | 500 | 16sp | Captions, timestamps |

### 3.2 Elevation System (Material 3)

| Level | Elevation | Use Case |
|-------|-----------|----------|
| Level 0 | 0dp | Background surfaces |
| Level 1 | 1dp | Cards, navigation rail |
| Level 2 | 3dp | Floating action buttons, search bar |
| Level 3 | 6dp | Dialogs, bottom sheets |
| Level 4 | 8dp | Side sheets, drawer |
| Level 5 | 12dp | Modal overlay |

### 3.3 Core Component Library (:composeApp:designsystem)

| Component | Variants | Key Properties |
|-----------|----------|----------------|
| `ZentaButton` | Primary, Secondary, Tertiary, Danger, Ghost | Stateless, size: SM/MD/LG, loading state, icon slot |
| `ZentaIconButton` | Filled, Tonal, Outlined, Standard | 40dp/48dp, badge overlay |
| `ZentaTextField` | Standard, Outlined, Search, Numeric | Validation state (error/success), leading/trailing icons, character counter |
| `ZentaSearchBar` | Persistent, Expandable | Barcode icon trigger, voice input (future), debounced query emission |
| `ZentaProductCard` | Grid tile, List row, Compact chip | Image, name, price, stock badge, tap-to-add behavior |
| `ZentaCartItem` | Standard, Compact | Swipe-to-delete, inline qty stepper, discount badge |
| `ZentaNumericPad` | Calculator, Payment, PIN | Large 56dp buttons, backspace, decimal, clear, auto-format currency |
| `ZentaDialog` | Alert, Confirm, Input, Multi-Step | Title, body, action row (max 3 buttons), dismiss-on-outside-tap toggle |
| `ZentaBottomSheet` | Standard, Expanding, Full-screen | Drag handle, peek height, content slots |
| `ZentaTable` | Sortable, Paginated, Selectable | Column definitions, row click action, bulk action toolbar |
| `ZentaStatusChip` | Success, Warning, Error, Info, Neutral | Icon + label, compact sizing |
| `ZentaBadge` | Dot, Count, Status | Overlay positioning for icons/nav items |
| `ZentaSnackbar` | Info, Success, Warning, Error | Auto-dismiss (4s), action button, queue manager |
| `ZentaLoadingSkeleton` | Card, Row, Grid, Text | Shimmer animation, matches target component dimensions |
| `ZentaEmptyState` | Illustration + Title + Subtitle + CTA | Used when lists/grids have zero items |
| `ZentaSyncIndicator` | Inline, AppBar chip | Pulsing dot (syncing), checkmark (synced), warning (pending), error (failed) |
| `ZentaScaffold` | Adaptive navigation scaffold | Switches between bottom bar (Compact), rail (Medium), drawer (Expanded) |
| `ZentaSplitPane` | Horizontal, Vertical | Resizable divider, min/max constraints, for Desktop master-detail |
| `ZentaDatePicker` | Single, Range | Calendar view, preset ranges (Today, This Week, This Month, Custom) |
| `ZentaCurrencyText` | Standard, Large, Compact | Auto-format by locale, symbol positioning, negative in red |

---

## 4. Global Navigation Architecture

### 4.1 Navigation Model by WindowSizeClass

```
┌─────────────────────────────────────────────────────────────────────────┐
│  COMPACT (Phone)                                                        │
│                                                                         │
│  ┌─────────────────────────────────┐                                    │
│  │         Screen Content          │                                    │
│  │                                 │                                    │
│  │                                 │                                    │
│  └─────────────────────────────────┘                                    │
│  ┌─────────────────────────────────┐                                    │
│  │ POS │ Inventory │ Reports │ More│  ◄── Bottom Navigation Bar (4+1)  │
│  └─────────────────────────────────┘                                    │
│                                                                         │
│  "More" opens a full-screen drawer with all modules.                    │
│  Only 4 primary destinations in bottom bar.                             │
│  Active module highlight with filled icon + label.                      │
├─────────────────────────────────────────────────────────────────────────┤
│  MEDIUM (Tablet)                                                        │
│                                                                         │
│  ┌────┬────────────────────────────┐                                    │
│  │ ▪  │                            │                                    │
│  │ ▪  │       Screen Content       │                                    │
│  │ ▪  │                            │                                    │
│  │ ▪  │                            │                                    │
│  │ ▪  │                            │                                    │
│  │    │                            │                                    │
│  └────┴────────────────────────────┘                                    │
│  ◄── Navigation Rail (icons only, expand on hover/tap)                  │
│  Destinations: POS, Inventory, CRM, Register, Reports, Settings         │
│  Rail collapses to icons-only; FAB for quick-sale shortcut.             │
├─────────────────────────────────────────────────────────────────────────┤
│  EXPANDED (Desktop)                                                     │
│                                                                         │
│  ┌──────────────┬──────────────────────────────────────────┐            │
│  │  ZentaPOS     │                                          │            │
│  │  ──────────   │          Screen Content                  │            │
│  │  ▪ Dashboard  │                                          │            │
│  │  ▪ POS        │                                          │            │
│  │  ▪ Inventory  │                                          │            │
│  │  ▪ Customers  │                                          │            │
│  │  ▪ Register   │                                          │            │
│  │  ▪ Reports    │                                          │            │
│  │  ▪ Expenses   │                                          │            │
│  │  ▪ Coupons    │                                          │            │
│  │  ▪ Staff      │                                          │            │
│  │  ──────────   │                                          │            │
│  │  ▪ Settings   │                                          │            │
│  │  ▪ Admin      │                                          │            │
│  └──────────────┴──────────────────────────────────────────┘            │
│  ◄── Persistent side drawer (240dp), collapsible to 72dp rail           │
│  Full labels + icons. Active item has tonal fill.                        │
│  Drawer items are RBAC-filtered: Cashiers see only POS + Register.      │
│  Collapse toggle at top; remembers preference in settings.              │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Top App Bar Structure

| WindowSizeClass | App Bar Content |
|-----------------|-----------------|
| **Compact** | Screen title (center), hamburger menu (left), profile avatar (right) |
| **Medium** | Screen title (left), global search (center, expandable), sync indicator + profile (right) |
| **Expanded** | Screen title (left), global search bar (center, persistent 320dp), sync indicator chip, notifications bell, active register badge, profile dropdown (right) |

### 4.3 Navigation Destinations by Role (RBAC-Filtered)

| Destination | Admin | Store Manager | Cashier | Accountant | Stock Manager |
|------------|-------|---------------|---------|------------|---------------|
| Dashboard | ✅ | ✅ | ❌ | ✅ | ❌ |
| POS | ✅ | ✅ | ✅ | ❌ | ❌ |
| Inventory | ✅ | ✅ | ❌ | ❌ | ✅ |
| Customers | ✅ | ✅ | ✅ (read) | ❌ | ❌ |
| Cash Register | ✅ | ✅ | ✅ | ❌ | ❌ |
| Reports | ✅ | ✅ | ❌ | ✅ | ✅ (stock only) |
| Expenses | ✅ | ✅ | ❌ | ✅ | ❌ |
| Coupons | ✅ | ✅ | ❌ | ❌ | ❌ |
| Multi-Store | ✅ | ❌ | ❌ | ❌ | ❌ |
| Staff | ✅ | ✅ | ❌ | ❌ | ❌ |
| Settings | ✅ | ✅ (limited) | ❌ | ❌ | ❌ |
| Admin | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## 5. Screen 1: Authentication & Session Control

### 5.1 Login Screen

- **[SCREEN NAME]:** Login
- **[LAYOUT TOPOGRAPHY]:**
  - **Compact:** Single centered column. Logo at top (20% height), form fields center, CTA bottom-anchored.
  - **Medium/Expanded:** Centered card (400dp max width) overlaid on a branded gradient background. Logo above card.
- **[KEY UI COMPONENTS]:**
  - `ZentaTextField` — Email/username field with leading person icon
  - `ZentaTextField` — Password field with visibility toggle
  - `ZentaButton(Primary)` — "Sign In" (full width within card)
  - Text link — "Forgot Password?"
  - `ZentaStatusChip` — Offline mode indicator (when no network): "Offline — Cached login available"
  - Store selector dropdown — visible only for multi-store deployments
- **[USER FLOW & INTERACTIONS]:**
  1. User enters credentials → taps "Sign In"
  2. Button shows loading spinner → validates against local cache (offline) or API (online)
  3. Success → navigate to Dashboard (Manager/Admin) or POS (Cashier)
  4. Failure → inline error below relevant field + shake animation
  5. After 5 failed attempts → temporary lockout (30s) with countdown timer
  6. Offline + no cached credentials → show "Connect to network for first login" empty state
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Auto-focus on email field on mount. Enter key submits form. Tab navigates between fields.
  - Phone: Keyboard auto-opens on email field. Form scrolls above keyboard. Biometric login option (Android fingerprint).
  - Tablet: Same as Desktop but with touch-optimized field heights (56dp).

### 5.2 PIN Quick-Switch Screen

- **[SCREEN NAME]:** PIN Quick Switch (Session Lock)
- **[LAYOUT TOPOGRAPHY]:** Full-screen overlay. Current user avatar + name at top, 4–6 digit PIN pad center, "Switch User" text button below pad.
- **[KEY UI COMPONENTS]:**
  - User avatar circle (64dp) + name label
  - `ZentaNumericPad(PIN)` — 3×4 grid: digits 0–9, backspace, clear. No decimal.
  - PIN dot indicators (4–6 filled circles)
  - "Switch User" link → navigates to full Login screen
  - "Lock" button in app bar triggers this screen
- **[USER FLOW & INTERACTIONS]:**
  1. Triggered by: idle timeout (configurable 1–30 min), manual lock, or quick-switch tap
  2. PIN entered → dots fill → auto-submits on final digit
  3. Correct → dissolve overlay, resume previous screen
  4. Incorrect → dots shake + clear, error text "Incorrect PIN"
  5. 3 failures → force full login
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Numpad keys on physical keyboard map to PIN entry. Escape returns to full login.
  - Phone: Compact PIN pad fills 60% screen. Biometric bypass toggle.
  - Tablet: PIN pad centered in 320dp container.

---

## 6. Screen 2: Home / Central Dashboard

- **[SCREEN NAME]:** Home Dashboard
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded (Desktop):** Two-column layout.
    - Left column (60%): KPI summary cards row (Today's Sales, Orders, Revenue, Avg Ticket) → Sales chart (line/bar toggle) → Recent orders table (5 rows, paginated).
    - Right column (40%): Quick-action widget grid (2×3) → System alerts list → Sync status panel.
  - **Medium (Tablet):** Single-column scrollable. KPI cards in 2×2 grid → Chart (full width) → Quick actions (3-column grid) → Alerts → Recent orders.
  - **Compact (Phone):** Single-column scrollable. KPI cards as horizontal scroll chips → Spark chart (compact) → Quick actions (2-column) → Alerts (collapsible) → Recent orders (card list).
- **[KEY UI COMPONENTS]:**
  - `ZentaKpiCard` — Icon, label, large number (DisplayLarge for revenue), delta badge (+12.5% ↑)
  - `ZentaSalesChart` — Line chart (default), bar chart toggle. Uses recharts/compose-charts. Period selector (Today, 7D, 30D, Custom).
  - `ZentaQuickActionGrid` — Large icon tiles: "New Sale", "Open Register", "Add Product", "Stock Check", "View Reports", "Scan Barcode"
  - `ZentaAlertCard` — Priority-colored left border. Types: Low Stock, Sync Failure, Register Not Closed, Expiring Products.
  - `ZentaRecentOrdersTable` — Columns: Order#, Customer, Items, Total, Status, Time. Row click → order detail.
  - `ZentaSyncStatusPanel` — Last sync time, pending operations count, force-sync button.
- **[USER FLOW & INTERACTIONS]:**
  1. Dashboard auto-refreshes every 60s (configurable). Uses StateFlow from `DashboardViewModel`.
  2. Quick action tiles → navigate directly to respective screens.
  3. Alert cards are dismissible (swipe on mobile, X on desktop). Critical alerts are non-dismissible.
  4. Chart period change triggers data reload with skeleton loading.
  5. Pull-to-refresh on mobile/tablet.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Charts render with hover tooltips. Tables support column sorting by click.
  - Phone: Charts simplified to spark lines. Tables collapse to card lists.
  - Tablet: Full chart with touch-drag period selection.

---

## 7. Screen 3: POS / Checkout Screen

> **This is the highest-priority, most performance-critical screen in the entire system.**

### 7.1 POS Main — Expanded (Desktop/Large Tablet)

- **[SCREEN NAME]:** POS Checkout Main
- **[LAYOUT TOPOGRAPHY]:**
  ```
  ┌────────────────────────────────────────────────────────────────────────┐
  │  [☰]  ZentaPOS  [🔍 Search / Barcode Input (auto-focus)]   [Held:3]  │
  │  Category Tabs: [All] [Beverages] [Snacks] [Dairy] [...] [+ Custom]  │
  ├──────────────────────────────────────┬─────────────────────────────────┤
  │                                      │  👤 Customer: [Walk-in ▼]      │
  │        PRODUCT GRID (60%)            │  Order Type: [Takeaway ▼]      │
  │                                      ├─────────────────────────────────┤
  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌────┐  │                                │
  │  │ Prod │ │ Prod │ │ Prod │ │ P  │  │   CART / ACTIVE ORDER (40%)    │
  │  │ $2.5 │ │ $3.0 │ │ $1.5 │ │$4  │  │                                │
  │  │ ✅ 45│ │ ⚠️ 3 │ │ ✅120│ │✅8 │  │  ┌─ Item 1  ×2  $5.00  [−][+]│
  │  └──────┘ └──────┘ └──────┘ └────┘  │  │  Item 2  ×1  $25.00 [−][+]│
  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌────┐  │  │  Item 3  ×3  $9.00  [−][+]│
  │  │ Prod │ │ Prod │ │ Prod │ │ P  │  │  │  ── ── ── ── ── ── ── ── │
  │  │ $5.0 │ │ $2.0 │ │ $8.5 │ │$12 │  │  │  Subtotal:       $72.00  │
  │  │ ❌ 0 │ │ ✅ 88│ │ ✅ 22│ │✅5 │  │  │  Tax (15%):      $10.80  │
  │  └──────┘ └──────┘ └──────┘ └────┘  │  │  Discount:       −$5.00  │
  │                                      │  │  ━━━━━━━━━━━━━━━━━━━━━━  │
  │  Page: ◀ 1 of 12 ▶   View: Grid|List│  │  TOTAL:          $77.80  │
  │                                      │  └──────────────────────────│
  │                                      ├─────────────────────────────────┤
  │                                      │ [🗑️Clear] [⏸️Hold] [💲Disc]   │
  │                                      │ [🧾Order Info] [🔖Coupon]      │
  │                                      │                                │
  │                                      │  ┌──────────────────────────┐  │
  │                                      │  │   💳 PAY  $77.80         │  │
  │                                      │  └──────────────────────────┘  │
  └──────────────────────────────────────┴─────────────────────────────────┘
  ```
  - **Left pane (60%):** Category tab bar (horizontal scroll) → Product grid (lazy grid, 4–6 columns) → Pagination/view toggle.
  - **Right pane (40%):** Customer selector → Order type selector → Cart item list (scrollable) → Totals summary → Action buttons → PAY button (largest element, primary color).

- **[KEY UI COMPONENTS]:**
  - `ZentaSearchBar` — Persistent at top. Dual-mode: text search (FTS5) OR barcode input. Barcode icon toggles camera scan (mobile) or keeps focus for USB scanner wedge input (desktop). Auto-clears after product added.
  - `ZentaCategoryTabRow` — Horizontally scrollable chip tabs. "All" is default. Fetched from categories table. Supports nested sub-categories via long-press dropdown.
  - `ZentaProductCard(Grid)` — Image (or placeholder initial), product name (max 2 lines), price (bold), stock badge (green ✅ / amber ⚠️ / red ❌). Tap → add 1 to cart with haptic feedback. Long-press → variant selector bottom sheet if product has variations.
  - `ZentaCartItemRow` — Product name, unit, quantity stepper (−/+), line total, swipe-left to reveal delete. Tap on name → edit item dialog (discount, notes, unit change).
  - `ZentaOrderSummary` — Subtotal, tax breakdown, discount line (tap to modify), delivery fee (if applicable), bold total.
  - `ZentaButton(Primary, LG)` — "PAY $77.80" — full width of right pane, 64dp height, primary color. Disabled + grayed when cart is empty.
  - `ZentaActionButtonRow` — Secondary buttons: Clear Cart, Hold Order, Apply Discount, Order Info, Apply Coupon. Icon + label, wrapped row.
  - `ZentaHeldOrdersBadge` — Displayed in app bar showing count of held orders. Tap → bottom sheet listing held orders with resume/delete actions.

- **[USER FLOW & INTERACTIONS]:**
  1. **Add product:** Tap product card → item appears in cart with qty 1. Subsequent taps increment qty.
  2. **Barcode scan:** Focus search bar → scan → product auto-identified → added to cart → search clears → ready for next scan. Sub-200ms response target.
  3. **Search:** Type in search bar → debounced 300ms → FTS5 results filter grid. ESC clears search.
  4. **Quantity edit:** Tap +/− on cart item. Long-press + → opens numeric input for bulk qty.
  5. **Item discount:** Tap item name in cart → dialog with Flat/Percentage toggle, amount input, auth PIN if discount exceeds role limit.
  6. **Hold order:** Tap Hold → order saved locally with timestamp + optional name → cart clears → new order starts.
  7. **Retrieve held:** Tap "Held: N" badge → bottom sheet with held orders → tap to resume → items load back into cart.
  8. **Customer assign:** Tap customer dropdown → search overlay → select existing or "+ Quick Add" (name + phone minimal form).
  9. **Pay:** Tap PAY → navigates to Payment screen (Screen 4).

- **[KMP / RESPONSIVE NOTES]:**
  - **Desktop:** Keyboard shortcuts active (see §23). F2=Search, F3=Customer, F5=Pay, F8=Hold, F9=Retrieve, +/−=qty, Delete=remove item. Product grid supports mouse-wheel scroll.
  - **Tablet:** Same split layout but product grid uses 3 columns. Swipe gestures on cart items. Category tabs are touch-scrollable.
  - **Phone:** See §7.2 below.

### 7.2 POS Main — Compact (Phone)

- **[SCREEN NAME]:** POS Checkout (Phone)
- **[LAYOUT TOPOGRAPHY]:** Single-pane with tab toggle.
  ```
  ┌──────────────────────────────┐
  │  🔍 Search / Scan            │
  │  [Products] [Cart (3)]       │  ◄── Tab toggle
  ├──────────────────────────────┤
  │                              │
  │  Category chips (scroll)     │
  │  ┌──────┐ ┌──────┐          │
  │  │ Prod │ │ Prod │          │
  │  │ $2.5 │ │ $3.0 │          │
  │  └──────┘ └──────┘          │
  │  ┌──────┐ ┌──────┐          │
  │  │ Prod │ │ Prod │          │
  │  │ $5.0 │ │ $2.0 │          │
  │  └──────┘ └──────┘          │
  │                              │
  │  ... (scrollable grid 2-col) │
  ├──────────────────────────────┤
  │  Total: $77.80   [PAY →]    │  ◄── Sticky bottom bar
  └──────────────────────────────┘
  ```
  - **Products tab:** Full-screen product grid (2 columns) with search bar and category chips.
  - **Cart tab:** Full-screen cart list with summary and actions.
  - **Sticky bottom bar:** Always visible showing total and PAY button, regardless of active tab.

---

## 8. Screen 4: Payment Flow

- **[SCREEN NAME]:** Payment Screen
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded (Desktop):** Two-pane. Left (50%): Payment method selector + numeric input pad. Right (50%): Order summary (read-only cart, totals).
  - **Medium (Tablet):** Same two-pane with 55/45 split.
  - **Compact (Phone):** Full-screen. Order summary collapsible at top → payment method chips → numeric pad → confirm.
  ```
  EXPANDED LAYOUT:
  ┌──────────────────────────────────┬──────────────────────────────────┐
  │  PAYMENT INPUT                   │  ORDER SUMMARY                   │
  │                                  │                                  │
  │  Payment Method:                 │  Customer: John Smith            │
  │  [💵Cash] [💳Card] [📱Mobile]    │  Order #: POS-2026-0042          │
  │  [🏦Bank] [💰Credit] [⊕Split]   │  ──────────────────────          │
  │                                  │  Item 1   ×2    $10.00           │
  │  Amount Due: $77.80              │  Item 2   ×1    $25.00           │
  │                                  │  Item 3   ×3    $27.00           │
  │  ┌──────────────────────┐        │  ──────────────────────          │
  │  │      $ 100.00        │        │  Subtotal:     $72.00            │
  │  └──────────────────────┘        │  Tax (15%):    $10.80            │
  │                                  │  Discount:     −$5.00            │
  │  Quick amounts:                  │  ──────────────────────          │
  │  [$50] [$80] [$100] [Exact]      │  TOTAL:        $77.80            │
  │                                  │  ──────────────────────          │
  │  ┌─────┬─────┬─────┐            │  Paid:         $100.00           │
  │  │  7  │  8  │  9  │            │  Change:       $22.20            │
  │  ├─────┼─────┼─────┤            │                                  │
  │  │  4  │  5  │  6  │            │                                  │
  │  ├─────┼─────┼─────┤            │                                  │
  │  │  1  │  2  │  3  │            │                                  │
  │  ├─────┼─────┼─────┤            │                                  │
  │  │  .  │  0  │  ⌫  │            │                                  │
  │  └─────┴─────┴─────┘            │                                  │
  │                                  │                                  │
  │  ┌──────────────────────┐        │                                  │
  │  │  ✅ CONFIRM PAYMENT   │        │                                  │
  │  └──────────────────────┘        │                                  │
  └──────────────────────────────────┴──────────────────────────────────┘
  ```
- **[KEY UI COMPONENTS]:**
  - `ZentaPaymentMethodSelector` — Large icon chips for each method. Active chip highlighted. "Split" opens multi-tender mode.
  - `ZentaNumericPad(Calculator)` — Full numeric pad with decimal, backspace, clear. 56dp buttons.
  - `ZentaQuickAmountRow` — Preset denomination buttons calculated contextually (nearest round-ups to total).
  - `ZentaCurrencyText(Large)` — Entered amount, auto-formatted with currency symbol.
  - `ZentaChangeDisplay` — Shows "Change: $22.20" in green when overpaid. Shows "Remaining: $27.80" in amber when underpaid.
  - `ZentaButton(Primary, LG)` — "CONFIRM PAYMENT" — disabled until amount ≥ total (for cash) or gateway confirmed (for card).
  - `ZentaSplitPaymentPanel` — Shown when "Split" selected: list of tender lines, each with method + amount, running remaining balance.
- **[USER FLOW & INTERACTIONS]:**
  1. Select payment method → numeric pad activates for Cash/Bank Transfer.
  2. For Card: tap "Card" → triggers payment gateway flow (external SDK dialog or redirect).
  3. For Mobile: tap "Mobile" → shows QR code or redirects to mobile payment SDK.
  4. Cash entered ≥ total → change auto-calculates → CONFIRM enables.
  5. Confirm → receipt auto-prints (if setting enabled) → cash drawer opens (if HAL configured) → success animation → navigate back to POS with empty cart.
  6. Split payment: add first tender line → remaining decreases → add subsequent methods → confirm when remaining = 0.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Numpad on physical keyboard types into amount field. Enter = confirm. Escape = back to POS.
  - Phone: Numeric pad fills bottom 50%, summary scrollable above. Quick-amounts are most prominent.
  - All: Gateway integrations use platform `expect/actual` (Android SDK vs Desktop REST).

---

## 9. Screen 5: Inventory Management

### 9.1 Product List / Catalog

- **[SCREEN NAME]:** Product Catalog
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Master-detail split. Left (40%): searchable, filterable product table. Right (60%): selected product detail/edit form.
  - **Medium:** Full-width product table. Row tap → navigate to product detail screen.
  - **Compact:** Card list (single column). Tap → full-screen detail.
- **[KEY UI COMPONENTS]:**
  - `ZentaSearchBar` — With barcode scan trigger, category filter chip row, stock status filter (All, In Stock, Low, Out)
  - `ZentaTable` (Expanded/Medium) / `ZentaProductCard(List)` (Compact) — Columns: Image (48dp thumb), Name, SKU, Category, Price, Stock Qty, Status
  - `ZentaFAB` — "+ Add Product" floating action button
  - `ZentaFilterChipRow` — Category, Stock Status, Product Type (Stockable/Service/Non-stockable)
  - Bulk action toolbar (on multi-select): Delete, Change Category, Adjust Price, Export
  - View toggle: Grid / List
- **[USER FLOW & INTERACTIONS]:**
  1. Default view loads all products (paginated, 50/page) sorted by name.
  2. Search is instant (FTS5, <200ms on 50K products).
  3. Filter chips combine additively (Category=Beverages AND Status=Low Stock).
  4. Tap row/card → detail view with all product info tabs: General, Pricing, Stock, Media, Variations.
  5. FAB → Product Creation wizard (multi-step form, see §19.1).
  6. Swipe-to-delete on mobile → confirmation dialog.
  7. Long-press (mobile) / checkbox (desktop) → multi-select for bulk actions.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Table with sortable column headers. Right-click context menu (Edit, Duplicate, Delete, View Stock).
  - Phone: Infinite scroll card list. Pull-to-refresh. FAB at bottom-right.

### 9.2 Stock Adjustment

- **[SCREEN NAME]:** Stock Adjustment
- **[LAYOUT TOPOGRAPHY]:** Form-centric. Product search/select → Adjustment type (Increase/Decrease/Transfer) → Quantity input → Reason selector → Notes → Submit.
- **[KEY UI COMPONENTS]:**
  - Product search autocomplete field
  - Radio group: Increase / Decrease / Transfer
  - `ZentaNumericPad` or standard number input for quantity
  - Reason dropdown (Received Stock, Damaged, Expired, Count Correction, Other)
  - Warehouse selector (if multi-warehouse enabled)
  - Notes text area
  - `ZentaButton(Primary)` — "Submit Adjustment"
- **[USER FLOW & INTERACTIONS]:**
  1. Search product → select → current stock displayed.
  2. Choose adjustment type → enter qty → select reason.
  3. Transfer: additional warehouse destination picker appears.
  4. Submit → confirmation dialog showing before/after stock values → confirm → saved locally → queued for sync.
- **[KMP / RESPONSIVE NOTES]:**
  - All platforms: Form is single-column, scrollable. Desktop auto-focuses search field.

---

## 10. Screen 6: Cash Register Operations

- **[SCREEN NAME]:** Cash Register Management
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Left panel: Register list with status badges (Open/Closed). Right panel: Active register detail (current session, cash movements, balance).
  - **Medium/Compact:** Register list → tap to open detail.
- **[KEY UI COMPONENTS]:**
  - `ZentaRegisterCard` — Register name, assigned user, status badge (Open=green, Closed=neutral), opening balance, current balance.
  - Open Register dialog: Starting cash input via `ZentaNumericPad`, denominations breakdown (optional).
  - Close Register dialog: Multi-step wizard — physical count input → system-calculated expected amount → discrepancy display → notes → confirm close → generates closing report.
  - Cash In/Out dialog: Amount input, reason dropdown (Petty Cash, Tip Deposit, Bank Run, etc.), notes.
  - `ZentaTransactionHistory` — Chronological list of all cash movements in current session.
- **[USER FLOW & INTERACTIONS]:**
  1. Cashier must open register before POS is accessible (enforced).
  2. Open → enter float amount → confirm → register session starts.
  3. During shift: Cash In/Out buttons accessible from POS action bar.
  4. Close → count cash → system shows expected vs actual → discrepancy highlighted in red if mismatch → manager override if needed → generates report.
  5. Closed register → read-only session history.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Denomination breakdown uses a clean table layout. Keyboard number entry.
  - Phone: Numeric pad for all amount inputs. Steps wizard uses horizontal swipe pager.

---

## 11. Screen 7: Customer Relationship Management (CRM)

- **[SCREEN NAME]:** Customer Management
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Master-detail. Left: Searchable customer table (Name, Phone, Group, Balance, Points). Right: Customer 360° profile.
  - **Medium/Compact:** Customer list → tap for full profile screen.
- **[KEY UI COMPONENTS]:**
  - `ZentaSearchBar` — Search by name, phone, email, customer code.
  - `ZentaTable` / `ZentaCustomerCard(List)` — Name, phone, group, wallet balance, loyalty points, last purchase date.
  - Customer 360° Profile view: Tabbed — Overview (contact + stats), Orders (purchase history), Wallet (balance + transaction log), Loyalty (points, tier, rewards), Notes.
  - `ZentaFAB` — "+ Add Customer"
  - Quick Add Customer bottom sheet: Name, Phone, Email (minimal). Full form accessible from profile edit.
  - Wallet actions: Add Funds, Deduct, Transfer. Each requires amount + reason.
- **[USER FLOW & INTERACTIONS]:**
  1. List loads with pagination. Search filters in real-time.
  2. Tap customer → 360° profile loads.
  3. From POS, customer search auto-links to order.
  4. Wallet operations logged with timestamps and linked to relevant orders.
  5. Loyalty tier auto-calculated from points. Manual tier override with manager auth.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Table supports multi-column sort. Profile uses tabbed layout with side summary card.
  - Phone: Profile tabs become horizontal swipe pager.

---

## 12. Screen 8: Reporting & Analytics

- **[SCREEN NAME]:** Reports Hub
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Left sidebar: Report category list (Sales, Stock, Purchase, Financial, Customer, Register). Right: Selected report with controls + data display.
  - **Medium:** Category tabs at top → report content below.
  - **Compact:** Category dropdown → report content.
- **[KEY UI COMPONENTS]:**
  - Report category navigation (sidebar/tabs/dropdown)
  - `ZentaDatePicker(Range)` — Period selector with presets (Today, Yesterday, This Week, This Month, Last 30 Days, Custom)
  - Filter panel: Store, Cashier, Payment Method, Product Category (varies per report type)
  - `ZentaSalesChart` — Line, Bar, Pie chart options. Interactive with tooltips.
  - `ZentaTable(Sortable, Paginated)` — Tabular data display for detailed reports.
  - Export actions bar: CSV, Excel, PDF, Print buttons.
  - `ZentaKpiCard` row — Summary metrics at top of each report (Total Revenue, # Orders, Avg Ticket, etc.)
- **[USER FLOW & INTERACTIONS]:**
  1. Select report category → default report loads with "Today" period.
  2. Adjust date range → data reloads with skeleton loading.
  3. Apply filters → additive filtering. Active filters shown as chips above data.
  4. Click table header → sort ascending/descending.
  5. Export → generate file → download dialog (Desktop) or share sheet (Mobile).
  6. Print → sends formatted report to configured thermal printer or system printer.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Full table view with many columns. Charts with hover tooltips. Print sends to system printer.
  - Phone: Charts simplified. Tables become scrollable cards. Export triggers share intent.

---

## 13. Screen 9: Coupons & Promotions

- **[SCREEN NAME]:** Coupon Management
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Table list of coupons with inline status badges → side panel for creation/edit.
  - **Medium/Compact:** Card list → full-screen form.
- **[KEY UI COMPONENTS]:**
  - `ZentaTable` / Card list — Code, Description, Type (Flat/Percentage/BOGO), Value, Status (Active/Expired/Disabled), Uses/Limit, Validity dates.
  - `ZentaFAB` — "+ Create Coupon"
  - Coupon creation form: Code (auto-generate or manual), Type picker, Value, Min order amount, Applicable products/categories, Usage limits (per customer, total), Date range, Customer restrictions.
  - `ZentaStatusChip` — Active (green), Scheduled (blue), Expired (gray), Disabled (red).
- **[USER FLOW & INTERACTIONS]:**
  1. List shows all coupons sorted by creation date.
  2. Status filter tabs: All, Active, Scheduled, Expired.
  3. Tap → edit form.
  4. Create → multi-field form with live preview of coupon summary.
  5. Toggle active/inactive with switch.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Inline editing in side panel. Bulk status change supported.
  - Phone: Full-screen form. Collapsible sections for advanced options.

---

## 14. Screen 10: Multi-Store Management

- **[SCREEN NAME]:** Multi-Store Dashboard
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Store comparison grid. Each store as a card with KPIs (Revenue, Orders, Stock Health). Click → store detail drilldown.
  - **Medium:** Store cards in 2-column grid.
  - **Compact:** Scrollable card list.
- **[KEY UI COMPONENTS]:**
  - `ZentaStoreCard` — Store name, address, status (Online/Offline), today's revenue, today's orders, active registers count.
  - Store detail view: tabbed — Overview (KPIs + chart), Inventory (stock summary), Staff (active users), Transfers (pending/completed).
  - Inter-store transfer wizard: Source store → Product selection → Quantity → Destination store → Confirm.
  - `ZentaComparisonChart` — Multi-line chart overlaying metrics from selected stores.
  - Store creation form: Name, address, phone, tax settings, warehouse assignment.
- **[USER FLOW & INTERACTIONS]:**
  1. Admin sees all stores at a glance. Clickable for drilldown.
  2. Transfer initiation from any store card.
  3. Filters: By status, region (if many stores).
  4. Real-time sync status per store shown as connectivity indicator.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Comparison mode with side-by-side store metrics. Drag to reorder store cards.
  - Phone: Single column. Transfer wizard uses stepped bottom sheets.

---

## 15. Screen 11: Staff Management

- **[SCREEN NAME]:** Staff Hub
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Master-detail. Employee list → profile detail (tabbed: Info, Attendance, Payroll, Schedule).
  - **Medium/Compact:** Employee list → full-screen profile.
- **[KEY UI COMPONENTS]:**
  - Employee list: Avatar, Name, Role, Store, Status (Active/Leave/Inactive).
  - Clock In/Out widget (prominent at top if current user is Employee): Big toggle button with current status.
  - Attendance calendar: Month view with color-coded days (Present=green, Absent=red, Leave=blue, Holiday=gray).
  - Payroll table: Period, Gross, Deductions, Commission, Net, Status (Pending/Paid).
  - Shift scheduler: Week view calendar with drag-to-assign shifts.
- **[USER FLOW & INTERACTIONS]:**
  1. List loads with store filter for managers.
  2. Add employee → multi-step form (Personal, Employment, Compensation).
  3. Clock In/Out → logs timestamp + location (mobile GPS, desktop IP).
  4. Payroll generation → automated calculation → review → approve → mark paid.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Calendar and scheduler use full-width layouts. Drag-and-drop shift assignment.
  - Phone: Calendar simplified to list view per week. Payslips as downloadable PDFs.

---

## 16. Screen 12: Expenses & Accounting

- **[SCREEN NAME]:** Expenses & Accounting
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Tabbed: Expenses (list + form), Chart of Accounts, Financial Statements.
  - **Medium/Compact:** Tab selector → full-screen content per tab.
- **[KEY UI COMPONENTS]:**
  - Expense list table: Date, Category, Description, Amount, Store, Status (Approved/Pending/Rejected).
  - Expense creation form: Category (from chart of accounts), Amount, Date, Recurrence toggle (one-time/daily/weekly/monthly), Receipt photo upload, Notes.
  - Chart of Accounts tree: Expandable tree view showing account hierarchy.
  - Financial statements: P&L, Cash Flow, Balance Sheet — rendered as formatted tables with period comparison.
  - `ZentaFAB` — "+ Record Expense"
- **[USER FLOW & INTERACTIONS]:**
  1. Default view: expenses for current period.
  2. Add expense → form with category autocomplete → optional receipt photo → submit.
  3. Recurring expenses auto-generate entries per schedule.
  4. Accountant can approve/reject pending expenses.
  5. Financial reports auto-generate from expense + revenue data.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Side-by-side account tree and transaction list. Statement export to Excel/PDF.
  - Phone: Receipt capture uses camera intent. Expense list as cards.

---

## 17. Screen 13: Settings & Hardware Configuration

- **[SCREEN NAME]:** Settings
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Left sidebar with settings categories. Right: selected category content.
  - **Medium/Compact:** Category list → full-screen settings page.
  - Categories: General, POS, Tax, Payment Methods, Order, Printing & Hardware, Backup, Sync, Integration, Appearance.
- **[KEY UI COMPONENTS]:**
  - Settings category list: Icon + Label for each category.
  - Settings content: Varies per category — toggle switches, dropdowns, text inputs, numeric steppers.
  - **Printing & Hardware sub-screen:**
    - Printer list: Name, Type (USB/Bluetooth/Network), Status (Connected/Disconnected), Default toggle.
    - Add Printer wizard: Connection type → discover/manual IP → test print → save.
    - Cash drawer config: Linked printer, kick command type.
    - Barcode scanner config: Input mode (Keyboard Wedge / Camera / Bluetooth).
    - Receipt template preview: Live preview of receipt with configurable logo, header, footer, T&C, QR code.
  - **Sync Settings:**
    - Sync frequency slider (1 min – 60 min).
    - Priority queue view (P0–P3 with pending counts).
    - Force sync button.
    - Conflict resolution log viewer.
  - **Tax Settings:**
    - Tax group CRUD. Add tax rates. Assign to products. Toggle inclusive/exclusive.
  - **Appearance:**
    - Theme toggle (Light/Dark/System).
    - Dynamic color toggle (Material You, Android only).
    - Language selector.
    - POS layout density toggle (Comfortable / Compact).
- **[USER FLOW & INTERACTIONS]:**
  1. Navigate to category → view/edit settings.
  2. Changes auto-save on toggle/select or explicit "Save" for text fields.
  3. Hardware test buttons: "Test Print" sends sample receipt. "Test Drawer" sends kick command.
  4. Printer discovery scans for network/bluetooth devices.
  5. Settings changes sync to cloud on next sync cycle.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop: Printer discovery includes USB/COM port detection. Receipt preview renders in side panel.
  - Phone: Printer discovery uses Bluetooth scan. Camera scanner uses ML Kit preview.
  - All: Settings are stored in local `settings` table with `expect/actual` for platform-specific entries (e.g., Android Bluetooth permissions).

---

## 18. Screen 14: System Administration

- **[SCREEN NAME]:** System Admin
- **[LAYOUT TOPOGRAPHY]:**
  - **Expanded:** Dashboard-style. Cards for: System Health, Error Logs, Activity Logs, DB Management, Updates, Module Management, Developer Tools.
  - **Medium/Compact:** Scrollable card list → tap for full-screen detail.
- **[KEY UI COMPONENTS]:**
  - System Health card: CPU/Memory/Disk indicators (for server), DB size, last backup time.
  - Error log viewer: Filterable table (severity, timestamp, module). Expandable rows for stack trace.
  - Activity log: Audit trail browser. Filter by user, action type, entity, date range.
  - Database management: Optimize button, migration status, data cleanup (purge deleted records older than N days).
  - Update panel: Current version, check for updates, changelog.
  - Module manager: List of installed modules with Enable/Disable toggle and update indicators.
- **[USER FLOW & INTERACTIONS]:**
  1. Admin-only screen. Not accessible to other roles.
  2. Health checks run on screen mount. Auto-refresh every 30s.
  3. Error log search + filter for debugging.
  4. Activity log supports export for compliance audits.
  5. DB optimization runs in background with progress indicator.
- **[KMP / RESPONSIVE NOTES]:**
  - Desktop-primary screen. Mobile provides simplified read-only views.

---

## 19. Standardized Sub-Screens, Dialogs & Forms

### 19.1 Form Standards

| Aspect | Standard |
|--------|----------|
| **Field height** | 56dp (touch-friendly) |
| **Field spacing** | 16dp vertical between fields |
| **Label position** | Floating label (inside outlined text field) — Material 3 standard |
| **Required indicator** | Red asterisk (*) after label |
| **Validation timing** | On blur (fields), on submit (form-level). Real-time for patterns (email, phone). |
| **Error display** | Below field, red text (Label Small), with leading error icon |
| **Form sections** | Grouped with Headline Medium divider + optional description |
| **Multi-step wizards** | Horizontal stepper (Expanded/Medium), vertical stepper (Compact). Back/Next/Submit buttons anchored to bottom. Progress: "Step 2 of 4". |
| **Save behavior** | Auto-save for settings toggles. Explicit "Save" button for entity forms. Unsaved changes → "Discard changes?" dialog on back-navigate. |

### 19.2 Product Creation Wizard (Example Multi-Step Form)

| Step | Title | Fields |
|------|-------|--------|
| 1 | Basic Info | Name*, Barcode (auto/manual), SKU, Product Type*, Category*, Description |
| 2 | Pricing & Units | Base Unit*, Unit Group, Selling Price*, Cost Price, Wholesale Price, Margin display (calculated) |
| 3 | Stock & Tax | Opening Stock, Alert Level, Expiry toggle + date, Tax Group, Inclusive/Exclusive toggle |
| 4 | Media & Variants | Product images (drag-drop desktop, camera/gallery mobile), Variations toggle → attribute builder (Size, Color, Custom) |
| 5 | Review | Summary card of all entered data → "Create Product" CTA |

### 19.3 Dialog Standards

| Dialog Type | Behavior | Max Width | Action Buttons |
|-------------|----------|-----------|----------------|
| **Alert** | Informational. Single "OK" action. | 400dp | 1 (OK) |
| **Confirm** | Destructive action guard. | 400dp | 2 (Cancel, Confirm — Confirm in danger color for destructive actions) |
| **Input** | Simple data collection (e.g., reason, name, PIN). | 400dp | 2 (Cancel, Submit) |
| **Multi-Step** | Complex workflows (Close Register, Split Payment). | 560dp | 2–3 (Back, Next, Submit) |
| **Full-Screen** (Compact only) | Replaces dialog on phone. Slide-up animation. | 100% | Top app bar with Close and Save/Submit |

### 19.4 Key Dialogs Catalog

| Dialog | Trigger | Content |
|--------|---------|---------|
| **Payment Confirmation** | PAY button → after gateway confirms | "Payment successful. Change: $X.XX. Print receipt?" [Print] [Skip] |
| **Shift Open** | Open Register action | Starting cash amount numpad + denominations (optional) |
| **Shift Close** | Close Register action | Multi-step: Physical count → Expected vs Actual → Discrepancy → Notes → Confirm |
| **Discount Application** | Discount button on cart/item | Type toggle (Flat/%), Amount input, Auth PIN (if exceeds limit), Preview impact |
| **Coupon Application** | Coupon button on POS | Code input field + "Apply" button → validation result → discount preview |
| **Void Order** | Manager action on completed order | Reason selection (required), Manager PIN auth, Confirmation with order total displayed |
| **Delete Confirmation** | Any destructive action | "Are you sure you want to delete [entity name]? This action cannot be undone." [Cancel] [Delete — red] |
| **Sync Conflict** | Conflict detected during sync | Side-by-side comparison of local vs server values, per-field. [Keep Local] [Keep Server] [Merge] |
| **Error State** | API failure, hardware error | Error icon, message, detail (expandable), [Retry] [Dismiss] |

---

## 20. Micro-Interactions & Feedback Patterns

### 20.1 Loading States

| Scenario | Pattern | Duration Threshold |
|----------|---------|-------------------|
| Screen load | `ZentaLoadingSkeleton` matching target layout shape | Show if > 200ms |
| Data refresh | Skeleton overlay on data region, keep chrome visible | Show if > 300ms |
| Button action | Button shows inline spinner, text changes to "Processing..." | Immediate on tap |
| Form submit | Inline spinner on submit button + disable all form fields | Immediate on submit |
| Background sync | `ZentaSyncIndicator` pulsing dot in app bar — non-blocking | Ambient, always visible |

### 20.2 Snackbar Notifications

| Event | Type | Message | Duration | Action |
|-------|------|---------|----------|--------|
| Product added to cart | Info | "{Product} added" | 2s | Undo |
| Order held | Info | "Order held: {name}" | 3s | View |
| Sync complete | Success | "Data synchronized" | 2s | — |
| Sync failed | Error | "Sync failed. {count} pending" | 4s | Retry |
| Payment successful | Success | "Payment received: ${amount}" | 3s | Print |
| Item deleted | Warning | "{Item} deleted" | 4s | Undo |
| Offline mode entered | Warning | "You're offline. Data saved locally." | 4s | — |
| Offline mode exited | Success | "Back online. Syncing..." | 3s | — |

### 20.3 Empty States

| Screen | Empty State Content |
|--------|-------------------|
| Cart | Illustration: empty basket + "Scan or tap products to start a sale" + [Browse Products] CTA |
| Product Catalog (filtered) | "No products match your filters" + [Clear Filters] CTA |
| Product Catalog (empty) | Illustration: package + "Add your first product to get started" + [Add Product] CTA |
| Customer List | Illustration: people + "No customers yet" + [Add Customer] CTA |
| Held Orders | "No held orders" (simple text, no CTA) |
| Reports (no data) | Illustration: chart + "No data for selected period" + [Adjust Date Range] CTA |
| Search Results | "No results for '{query}'" + "Try a different search term" |

### 20.4 Animations & Transitions

| Interaction | Animation |
|-------------|-----------|
| Screen navigation | Shared element transition (hero animation) for product images where applicable. Standard Material motion (forward/backward axis) for screen changes. |
| Cart item added | Slide-in from right + brief highlight pulse on new row |
| Cart item removed | Slide-out to left + height collapse |
| Cart quantity change | Number counter animation (roll up/down) |
| Dialog open | Fade + scale from center (Material 3 standard) |
| Bottom sheet | Slide up from bottom with spring physics |
| Tab switch | Crossfade content |
| Pull-to-refresh | Material 3 overscroll indicator → spinner → content reload |
| Payment success | Checkmark lottie/animated vector → confetti burst (subtle) |

---

## 21. Offline-First UX Patterns

### 21.1 Network Status Communication

The UX must never block the cashier due to network state. Offline-first means the network status is **ambient information**, not a modal barrier.

| Component | Location | Online State | Offline State | Syncing State |
|-----------|----------|-------------|---------------|---------------|
| `ZentaSyncIndicator` | App Bar (right) | Green dot + "Synced" | Amber dot + "Offline" | Pulsing blue dot + "Syncing..." |
| Sync detail panel | Settings → Sync, or long-press on sync indicator | Last sync: 2 min ago. Pending: 0. | Pending: 47 operations. [View Queue] | Progress: 23/47 operations. |
| Data freshness hint | Below lists/tables (optional) | Hidden | "Showing cached data from {timestamp}" | "Updating data..." |

### 21.2 Conflict Resolution UX

When CRDT auto-merge cannot resolve (rare):
1. Non-blocking notification: snackbar "1 data conflict detected" → [Resolve].
2. Tap "Resolve" → Conflict resolution screen.
3. Shows side-by-side: Local Value | Server Value per field.
4. Per-field radio selection: Keep Local / Keep Server.
5. Or bulk: "Keep All Local" / "Keep All Server".
6. Confirm → resolved, log entry created.

### 21.3 Queue Visibility

Pending sync operations should never be hidden from the user:
- App bar badge shows count of unsynced operations.
- Settings → Sync shows full queue: Entity Type, Operation (Create/Update/Delete), Timestamp, Retry Count, Status.
- Failed items (10+ retries) highlighted in red with "Manual Review" tag.

---

## 22. Accessibility & Compliance

### 22.1 WCAG 2.1 AA Compliance

| Requirement | Implementation |
|-------------|---------------|
| Color contrast | All text meets 4.5:1 (normal) and 3:1 (large) contrast ratios. Verified via Material 3 color system. |
| Touch targets | Minimum 48×48dp for all interactive elements. POS buttons at 56dp preferred. |
| Focus management | Logical tab order for all forms. Focus ring visible on keyboard navigation (Desktop). |
| Screen reader | `contentDescription` on all icons and images. `semantics` blocks on Compose composables. |
| Motion | Respect system "reduce motion" preference. Disable animations when enabled. |
| Text scaling | Support up to 200% system text size without layout breakage. |
| Error identification | Errors communicated by color AND text AND icon (never color alone). |

### 22.2 Dark Mode

- Full Material 3 dark theme with proper surface tonal elevations.
- Toggle: Settings → Appearance, or system-follow.
- POS screen tested for readability in both modes.
- Semantic colors (error, success, warning) maintain contrast in dark mode.

---

## 23. Keyboard Shortcut Map

> Desktop power-user efficiency. All shortcuts use common POS industry conventions.

### 23.1 Global Shortcuts

| Key | Action |
|-----|--------|
| `Ctrl + N` | New Sale (navigate to POS) |
| `Ctrl + F` / `F2` | Focus global search / POS search |
| `Ctrl + P` | Print (context: last receipt, current report) |
| `Ctrl + ,` | Open Settings |
| `Ctrl + L` | Lock screen (PIN quick-switch) |
| `Ctrl + Q` | Logout |
| `F1` | Help overlay (shows shortcut cheat sheet) |
| `F11` | Toggle fullscreen |
| `Escape` | Close current dialog/overlay, or navigate back |

### 23.2 POS Screen Shortcuts

| Key | Action |
|-----|--------|
| `F2` | Focus search/barcode input |
| `F3` | Open customer selector |
| `F4` | Apply discount dialog |
| `F5` | Proceed to Pay |
| `F6` | Apply coupon dialog |
| `F7` | Order info dialog |
| `F8` | Hold current order |
| `F9` | Retrieve held orders |
| `F10` | Toggle calculator |
| `Delete` | Remove selected cart item |
| `+` / `−` | Increment/decrement selected cart item qty |
| `Ctrl + Delete` | Clear entire cart (with confirmation) |
| `Ctrl + H` | View held orders |
| `Enter` (in search) | Add first result to cart |

### 23.3 Payment Screen Shortcuts

| Key | Action |
|-----|--------|
| `1`–`9`, `0`, `.` | Numeric input |
| `Backspace` | Delete last digit |
| `Enter` | Confirm payment |
| `Escape` | Back to POS |
| `C` | Cash method |
| `D` | Card method |
| `M` | Mobile method |
| `S` | Split payment |

---

## Appendix A: Screen Priority Matrix

| Priority | Screen | Phase | Rationale |
|----------|--------|-------|-----------|
| P0 | POS Checkout | 1 | Revenue-generating screen, used 80% of the time |
| P0 | Payment Flow | 1 | Completes every transaction |
| P0 | Authentication | 1 | Required for all access |
| P0 | Cash Register | 1 | Required before POS usage |
| P1 | Inventory (Product List) | 1 | Core data management |
| P1 | Dashboard | 1 | Operational overview |
| P1 | Reports (Sales + Stock) | 1 | Business intelligence |
| P1 | Settings (Core + Hardware) | 1 | System configuration |
| P2 | CRM | 2 | Customer relationship depth |
| P2 | Coupons | 2 | Revenue optimization |
| P2 | Multi-Store | 2 | Enterprise scaling |
| P2 | Expenses | 2 | Financial management |
| P3 | Staff | 3 | HR operations |
| P3 | System Admin | 3 | Operational tooling |
| P3 | Media Manager | 3 | Asset management |

---

## Appendix B: Design-to-Code Component Mapping

| Design Component | Compose Composable | Module |
|------------------|--------------------|--------|
| ZentaButton | `@Composable fun ZentaButton(text, onClick, variant, size, enabled, loading)` | `:composeApp:designsystem` |
| ZentaTextField | `@Composable fun ZentaTextField(value, onValueChange, label, error, leadingIcon, trailingIcon)` | `:composeApp:designsystem` |
| ZentaProductCard | `@Composable fun ZentaProductCard(product, onClick, onLongClick, variant)` | `:composeApp:designsystem` |
| ZentaNumericPad | `@Composable fun ZentaNumericPad(onDigit, onClear, onBackspace, onDecimal, mode)` | `:composeApp:designsystem` |
| ZentaScaffold | `@Composable fun ZentaScaffold(windowSizeClass, navigationItems, content)` | `:composeApp:designsystem` |
| ZentaSplitPane | `@Composable fun ZentaSplitPane(firstWeight, firstContent, secondContent)` | `:composeApp:designsystem` |
| ZentaSyncIndicator | `@Composable fun ZentaSyncIndicator(syncState)` | `:composeApp:designsystem` |
| ZentaCartItemRow | `@Composable fun ZentaCartItemRow(item, onQtyChange, onRemove, onItemClick)` | `:composeApp:feature:pos` |

---

*End of UI/UX Master Blueprint — ZentaPOS v1.0*
