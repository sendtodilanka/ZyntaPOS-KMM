# TODO-005: Modern Dashboard + Hierarchical Navigation Drawer

**Status:** Complete
**Priority:** HIGH — Production UX polish; current UI uses basic flat navigation
**Phase:** Phase 1 (MVP — required for production-ready visual quality)
**Created:** 2026-03-01

---

## Problem Statement

The current codebase has a **functional but basic** dashboard and navigation system:
- **Navigation:** Flat adaptive nav (bottom bar on COMPACT, rail on MEDIUM, drawer on EXPANDED) — no hierarchy, no collapsibility
- **Dashboard:** Static KPI cards, no animations, no gradient hero, no progress ring, no greeting

The enterprise design spec calls for a **modern, web-style admin dashboard** with a hierarchical sidebar and polished visual features. This gap is the difference between "works" and "looks professional enough for a high-cost enterprise product."

---

## Current State vs. Target

### Navigation

| Feature | Current | Target |
|---------|---------|--------|
| COMPACT layout | Bottom NavigationBar (4-5 items) | Modal overlay drawer (hamburger trigger) |
| MEDIUM layout | NavigationRail (72dp, always icons+labels) | Mini drawer (72dp icons-only) with expand toggle to 260dp |
| EXPANDED layout | PermanentNavigationDrawer (240dp, flat items) | Full drawer (260dp) with collapse toggle to 72dp mini |
| Item structure | Flat list of items | Hierarchical parent/child with expandable chevrons |
| Sticky header | None (spacer) | Logo + "ZyntaPOS" + collapse toggle |
| Sticky footer | None (spacer) | User avatar + name + role + overflow menu |
| Mini mode hover | N/A | Floating popout submenu (240dp, Level3 elevation) |
| Section headers | Present (OPERATIONS, MANAGEMENT, HR & FINANCE, SYSTEM) | Same but with uppercase styling + dividers |
| Active state | Basic highlight | 4dp left border accent + primary tonal bg |
| Animations | None | 250ms ease-out drawer expand/collapse |

### Dashboard

| Feature | Current | Target |
|---------|---------|--------|
| Hero KPI card | Flat surface ZyntaStatCard | Gradient card (Primary → lighter blue) with sparkline |
| Counter values | Static render | Animated 0 → value (800ms FastOutSlowIn, staggered) |
| Progress ring | Not present | 120dp/80dp circular progress for daily target |
| Greeting | Not present | "Good morning/afternoon/evening, {name}" |
| Entry animations | Instant appear | Staggered fade+slide (20dp, 300ms, 50ms stagger) |
| Section containers | Default surface | Tonal surfaceVariant containers with large corners |
| Activity chips | Text label for payment method | Colored chips (green CASH, blue CARD) |

---

## Part A: Hierarchical Navigation Drawer

### Drawer Behavior by Screen Size

#### COMPACT (<600dp, Mobile Phone)
- **Hidden by default** — content takes full width
- **Hamburger icon** in top-left triggers modal overlay drawer
- **Semi-transparent backdrop** (#000000 at 40% opacity)
- **Drawer width:** Full width minus 56dp (max 320dp)
- **Dismiss:** Tap backdrop, swipe left, or tap a nav item
- **Full mode only** (icons + labels + expandable children)

#### MEDIUM (600-840dp, Tablet)
- **Mini mode by default** — icons only, 72dp wide
- **Persistent** — always visible alongside content
- **Expand toggle:** Chevron button at bottom or hamburger in top bar
- **Expanded width:** 260dp
- **Mini mode:** 72dp — icons centered, no labels
- **Hover on mini-mode parent:** Floating submenu popout (240dp, Level3 elevation)
- **Content area adjusts** — pushes right when drawer expands

#### EXPANDED (>=840dp, Desktop)
- **Full mode by default** — persistent, 260dp wide
- **Collapse toggle:** ChevronLeft icon in drawer header or footer
- **Collapsed to mini:** 72dp icons only (same hover popout behavior)
- **Content area adjusts** — expands when drawer collapses
- **Smooth transition:** 250ms ease-out animation

### Drawer Visual Structure

```
+-----------------------------+
|  [Logo/Icon]  ZyntaPOS      | <- STICKY HEADER (56dp)
|               [< Collapse]  |
+-----------------------------+
|                             | <- SCROLLABLE CONTENT
|  OPERATIONS (section label) |   (labelMedium, 12sp, uppercase,
|  -------------------------  |    onSurfaceVariant, 0.6 opacity,
|  # Dashboard               |    16dp left padding)
|  # Point of Sale           |
|  # Inventory           v   | <- Expandable parent (chevron)
|    +- Products              | <- Child item (24dp left indent)
|    +- Categories            |
|    +- Suppliers             |
|    +- Stock Adjustments     |
|    +- Barcode Labels        |
|  # Register             v  |
|    +- Register Status       |
|    +- Open Register         |
|    +- Close Register        |
|  # Reports              v  |
|    +- Sales Report          |
|    +- Stock Report          |
|    +- Customer Report       |
|    +- Expense Report        |
|                             |
|  MANAGEMENT                 |
|  -------------------------  |
|  # Customers            v  |
|    +- Directory             |
|    +- Groups                |
|    +- Loyalty / Wallet      |
|  # Coupons              v  |
|    +- Coupon List           |
|    +- Create Coupon         |
|  # Expenses             v  |
|    +- Expense List          |
|    +- Categories            |
|  # Warehouses           v  |
|    +- Warehouse List        |
|    +- Stock Transfers       |
|    +- Rack Management       |
|                             |
|  HR & FINANCE               |
|  -------------------------  |
|  # Staff                v  |
|    +- Employees             |
|    +- Attendance            |
|    +- Shifts                |
|    +- Leave Management      |
|    +- Payroll               |
|  # Accounting           v  |
|    +- Ledger                |
|    +- Chart of Accounts     |
|    +- Journal Entries       |
|    +- E-Invoices            |
|    +- Financial Statements  |
|    +- General Ledger        |
|                             |
|  SYSTEM                     |
|  -------------------------  |
|  # Admin                v  |
|    +- System Health         |
|    +- Database              |
|    +- Backup                |
|    +- Audit Log             |
|  # Notifications            | <- Leaf item (no children)
|  # Settings             v  |
|    +- Store Profile         |
|    +- Tax Settings          |
|    +- Printer Setup         |
|    +- User Management       |
|    +- Security              |
|    +- Appearance            |
|    +- About                 |
|                             |
+-----------------------------+
|  [KP]  Kasun Perera         | <- STICKY FOOTER (64dp)
|         Manager      [:]   |   (avatar + name + role + overflow)
+-----------------------------+
```

### Drawer Item Specs

#### Section Header
- Text: Uppercase, `labelMedium` (12sp), SemiBold
- Color: `onSurfaceVariant` at 60% opacity
- Padding: 24dp top, 8dp bottom, 16dp horizontal
- Divider: 1dp `outlineVariant` line above (except first section)

#### Parent Item (has children)
- Height: 48dp
- Padding: 12dp horizontal, 8dp vertical
- Icon: 24dp, `onSurfaceVariant` (inactive) or `primary` (active)
- Label: `bodyMedium` (14sp), `onSurface` (inactive) or `primary` (active)
- Trailing chevron: 18dp `ChevronRight` — rotates 90deg clockwise when expanded (200ms ease)
- Active state: `primary` at 8% opacity bg, `small` (8dp) rounded corners, **4dp left border accent** in `primary` + bold text
- Hover state: `surfaceVariant` at 50% opacity

#### Child Item (leaf)
- Height: 40dp
- Left indent: 24dp additional (total 36dp from left edge)
- Leading indicator: 8dp circle dot (filled `primary` if active, outlined `outlineVariant` if inactive)
- Label: `titleSmall` (14sp), `onSurface` (inactive) or `primary` (active)
- Active state: Same as parent but indented

#### Leaf Item (no children, like Dashboard, Notifications)
- Same as Parent Item but no trailing chevron
- Directly navigable on click

### Mini Mode (72dp collapsed)
- Icons only, 24dp, centered horizontally
- Active icon: `primary` color + circular `primaryContainer` background (40dp)
- Inactive icon: `onSurfaceVariant` color
- No labels, no section headers
- Tooltip on hover: Label text in popup (200ms delay)
- Parent items on hover: Floating popout submenu
  - 240dp wide, Level3 elevation, medium (12dp) corners
  - `surface` background with section header + children list
  - Fade-in 150ms, disappears when mouse leaves both icon and popout

### Drawer Footer (Sticky)
- Height: 64dp
- Avatar circle (36dp, `primaryContainer` bg, initials)
- Name + Role text
- Overflow menu (three dots): Opens profile dropdown
- In mini mode: Only avatar circle centered, tap opens profile dropdown

---

## Part B: Modern Dashboard (Content Area)

### Top App Bar (Content Area)
- **Left:** Title "Dashboard" (`titleLarge`, 22sp, Medium)
  - On COMPACT: Hamburger icon before the title (opens modal drawer)
- **Right:** Profile avatar circle (36dp, `primaryContainer`, initials)
  - Tap opens profile dropdown menu
- **Background:** `surface`, flat (no elevation)
- **Height:** 56dp

### Profile Dropdown Menu

```
+--------------------------+
|  Good afternoon,         |  bodySmall, onSurfaceVariant
|  Kasun Perera            |  titleSmall, Bold, onSurface
+--------------------------+  HorizontalDivider
|  [bell]  Notifications . |  Badge dot (error red)
|  [gear]  Settings        |
+--------------------------+  HorizontalDivider
|  [>]     Logout          |  error red text + icon
+--------------------------+
```

- Width: 220dp, Level3 elevation, medium corners
- Time-aware greeting: "Good morning," / "Good afternoon," / "Good evening,"

### Dashboard Data Model

```
currentUser:      { name: "Kasun Perera", role: "MANAGER", initials: "KP" }
todaysSales:      45,250.00 (Rs.)
totalOrders:      42
lowStockCount:    3
lowStockNames:    ["Widget A", "Bolt B", "Gasket C"]
activeRegisters:  2
dailySalesTarget: 75,000.00 (Rs.)
recentOrders: [
  { orderNumber: "ORD-001", total: 2500.00, method: "CASH", time: "14:30" },
  { orderNumber: "ORD-002", total: 1800.00, method: "CARD", time: "14:15" },
  { orderNumber: "ORD-003", total: 3200.00, method: "CASH", time: "13:50" },
  { orderNumber: "ORD-004", total: 1500.00, method: "CASH", time: "13:22" },
  { orderNumber: "ORD-005", total: 4400.00, method: "CARD", time: "12:45" },
]
weeklySalesData:  [Mon: 32K, Tue: 45K, Wed: 38K, Thu: 52K, Fri: 48K, Sat: 61K, Sun: 45.25K]
todaySparkline:   [8K, 12K, 18K, 24K, 31K, 38K, 45.25K] (hourly accumulation)
```

### 10 Dashboard Modernizations

#### A. Gradient Hero KPI Card (Today's Sales)
- **Background:** Horizontal gradient from `#1565C0` (Primary) to `#1976D2` (lighter blue)
- **Text:** White (#FFFFFF) — label and value
- **Value:** Animated counter — `displaySmall` (36sp) EXPANDED, `headlineSmall` (24sp) otherwise
- **Sparkline:** Full-width gradient fill (primary 20% to 0% opacity) behind the value
- **Progress ring:** 120dp (EXPANDED) on right side, 80dp elsewhere
- **Corner radius:** `large` (16dp), **Elevation:** Level2 (3dp)

Other 3 KPI cards (Orders, Low Stock, Registers):
- Standard `surface` bg, Level1 elevation, medium (12dp) corners
- **4dp left border accent** in their accent color

#### B. Animated Counter Values
- Duration: 800ms, FastOutSlowIn easing
- Stagger: Sales at 200ms, Orders at 300ms, Low Stock at 400ms, Registers at 500ms
- Rs. 0.00 to Rs. 45,250.00 counting up

#### C. Elevated Quick Action Buttons
- `surface` bg, Level2 elevation, medium (12dp) corners
- 1dp `outlineVariant` border
- Icon inside 40dp circular `primaryContainer` container above label
- Hover: scale 0.97x with spring animation, shadow increases to Level3
- Three buttons: "New Sale" (ShoppingCart), "Register" (PointOfSale), "Reports" (Assessment)

#### D. Status Chips on Activity Items
- **"CASH"** -> Green chip (`#B8F0BB` bg, `#002108` text)
- **"CARD"** -> Blue chip (`#D6E4FF` bg, `#001B47` text)
- `labelSmall` (11sp), SemiBold, extraSmall (4dp) corners

#### E. Staggered Entry Animation
- Each section: fade in + slide up from 20dp, 300ms, FastOutSlowIn
- 50ms stagger between items
- KPI cards stagger left-to-right, then Quick Actions, Chart, Alerts, Activity

#### F. Tonal Section Containers
- Quick Actions: `surfaceVariant` (#E0E7F5) container, large (16dp) corners, 16dp padding
- Activity: `surface` card with 1dp `outlineVariant` border
- Chart: `surface` card with Level1 elevation

#### G. Enhanced Charts
- Hero sparkline: Gradient fill inside Today's Sales card
- Weekly chart: Inside `surface` card, Level1 elevation
  - "This Week" trailing badge
  - Y-axis: "Rs. 30K", "Rs. 40K" abbreviated
  - X-axis: Mon-Sun
  - Primary Blue line with 25% to 0% gradient fill
  - Chart height: 280dp (EXPANDED), 180dp (MEDIUM), 160dp (COMPACT)

#### H. Alert Cards with Action Buttons
- **Low Stock:** Warning amber bg, "3 items running low", **"View Stock" button** (Ghost, amber)
- **No Register:** Blue bg, "No register is open", **"Open Register" button** (Ghost, primary blue)

#### I. Time-Aware Greeting
- Before 12 PM: "Good morning,"
- 12-5 PM: "Good afternoon,"
- After 5 PM: "Good evening,"

#### J. Daily Target Progress Ring
- 120dp (EXPANDED), 80dp (MEDIUM/COMPACT)
- 8dp stroke track (`surfaceVariant`), 8dp progress arc (`primary`, rounded caps)
- 60% filled (45250/75000)
- Center: "60%" (titleLarge, Bold, Primary), "of Rs. 75K" (labelSmall, onSurfaceVariant)
- Animates 0 deg to final over 1000ms at 400ms delay
- EXPANDED: Inside the hero gradient card (right side)
- MEDIUM/COMPACT: Standalone card below KPI grid

---

## Responsive Layouts

### COMPACT (<600dp, Mobile)

Content area: Full screen width. Drawer hidden (modal overlay).

```
+--------------------------------------+
| hamburger  Dashboard          [KP]   |  56dp top app bar
+--------------------------------------+
|                                      |  Scrollable LazyColumn
|  +-----------+  +-----------+        |
|  | HERO      |  | Orders    |        |  ~110dp
|  | GRADIENT  |  |    42     |        |  2x2 grid, 8dp gap
|  | Rs.45,250 |  | [green]   |        |
|  +-----------+  +-----------+        |
|  +-----------+  +-----------+        |
|  | Low Stk   |  | Regs      |        |  ~110dp
|  |    3      |  |    2      |        |
|  | [red]     |  | [green]   |        |
|  +-----------+  +-----------+        |
|                                      |
|  +------------------------------+    |
|  | 60% of Rs. 75K target        |    |  ~100dp
|  +------------------------------+    |
|                                      |
|  +- surfaceVariant container ---+    |
|  | Quick Actions                |    |
|  | [Sale] [Register] [Reports]  |    |  ~96dp
|  +------------------------------+    |
|                                      |
|  alert: 3 items low [View Stock]     |  ~64dp
|  alert: No register [Open Reg]       |  ~64dp
|                                      |
|  +------------------------------+    |
|  | Weekly Sales Trend            |    |
|  | +------------------------+   |    |
|  | |     chart 160dp        |   |    |  ~220dp
|  | +------------------------+   |    |
|  +------------------------------+    |
|                                      |
|  Recent Activity                     |
|  +------------------------------+    |
|  | ORD-001  14:30  [CASH] 2.5K  |    |
|  |------------------------------|    |  ~180dp (3 orders)
|  | ORD-002  14:15  [CARD] 1.8K  |    |
|  |------------------------------|    |
|  | ORD-003  13:50  [CASH] 3.2K  |    |
|  +------------------------------+    |
+--------------------------------------+
```

Note: NO bottom navigation bar. Hamburger menu only.

### MEDIUM (600-840dp, Tablet)

Mini drawer (72dp) + content area.

```
+----+------------------------------------------------------+
|    |  Dashboard                                 [KP]      | 56dp
| 72 +---------------------------+---------------------------+
| dp |    LEFT PANE (55%)        |    RIGHT PANE (45%)       |
|    |                           |                           |
| M  | +----------++----------+ | +- surfaceVariant ------+ |
| I  | |HERO      ||Orders    | | | Quick Actions          | |  ~80dp
| N  | |Rs.45,250 ||   42     | | | [Sale][Reg][Reports]   | |
| I  | +----------++----------+ | +------------------------+ |
|    | +----------++----------+ |                           |
| D  | |Low Stk   ||Regs      | | Recent Activity  See All>|
| R  | |   3      ||   2      | | +------------------------+|
| A  | +----------++----------+ | |ORD-001 14:30 [CASH]    ||
| W  |                          | |      Rs. 2,500.00      ||
| E  | +----------------------+ | |------------------------|
| R  | | 60% progress ring     | | |ORD-002 14:15 [CARD]   ||
|    | +----------------------+ | |      Rs. 1,800.00      ||
| [i]|                          | |------------------------|
| [i]| Weekly Sales Trend       | | ... up to 5 orders     ||
| [i]| +----------------------+ | +------------------------+|
| [i]| |   chart (180dp)      | |                           |
| ..|| +----------------------+ | alert: 3 items [View]     |
| >  |                          | alert: No register [Open] |
+----+                          |                           |
|[KP]|                          |                           |
+----+--------------------------+---------------------------+
```

### EXPANDED (>=840dp, Desktop)

Full persistent drawer (260dp) + content area. **Zero scrolling.**

```
+---------------------+-------------------------------------------------------------+
|                     |  Dashboard                                         [KP]     | 56dp
| [Logo] ZyntaPOS [<] |                                                             |
|---------------------+-------------------------------------------------------------+
|                     |                                                              |
|  OPERATIONS         | +-- HERO GRADIENT ------++--------++--------++--------++--+ |
|  ---------------    | | Today's Sales          || Total  || Low    || Regs   | QA| |
|  # Dashboard        | |                       || Orders || Stock  ||        |   | | ~140dp
|  # Point of Sale    | |  Rs. 45,250.00   [60%]||  42    || 3 itms ||  2     |[i]| |
|  # Inventory     v  | |  +12.5%          ring ||Compltd || Attn   || Ready  |[i]| |
|    +- Products      | |  sparkline            ||        ||        ||        |[i]| |
|    +- Categories    | +------------------------++--------++--------++--------++--+ |
|    +- Suppliers     |                                                              |
|    +- Stock         | +----------------------------------+ +----------------------+|
|    +- Barcodes      | |       LEFT ZONE (60%)            | |   RIGHT ZONE (40%)   ||
|  # Register      v  | |                                  | |                      ||
|    +- Status        | | Weekly Sales Trend   [This Week] | | Recent Activity      ||
|    +- Open          | | +------------------------------+ | | +------------------+ ||
|    +- Close         | | |                              | | | |ORD-001 14:30     | ||
|  # Reports       v  | | |     chart (280dp)            | | | | [CASH] Rs. 2,500 | ||
|    +- Sales         | | |                              | | | |------------------| || ~380dp
|    +- Stock         | | +------------------------------+ | | |ORD-002 14:15     | ||
|    +- Customers     | |                                  | | | [CARD] Rs. 1,800 | ||
|    +- Expenses      | | alert: 3 items running low       | | |------------------| ||
|                     | |   Widget A, Bolt B, Gasket C     | | | ... up to 8      | ||
|  MANAGEMENT         | |                   [View Stock] > | | |------------------| ||
|  ---------------    | |                                  | | |ORD-008 12:00     | ||
|  # Customers     v  | | alert: No register is open       | | | [CASH] Rs. 950   | ||
|  # Coupons       v  | |                 [Open Register]> | | +------------------+ ||
|  # Expenses      v  | +----------------------------------+ +----------------------+|
|  # Warehouses    v  |                                                              |
|                     |                                                              |
|  HR & FINANCE       |                                                              |
|  ---------------    |                                                              |
|  # Staff         v  |                                                              |
|  # Accounting    v  |                                                              |
|                     |                                                              |
|  SYSTEM             |                                                              |
|  ---------------    |                                                              |
|  # Admin         v  |                                                              |
|  # Notifications    |                                                              |
|  # Settings      v  |                                                              |
|---------------------|                                                              |
| [KP] Kasun Perera   |                                                              |
|      Manager    [:] |                                                              |
+---------------------+-------------------------------------------------------------+
```

---

## Brand Color Palette (Material 3 Tonal System)

### Light Theme

| Role | Hex | Usage |
|------|-----|-------|
| **Primary** | `#1565C0` (Deep Blue) | Main CTAs, active nav, Pay button, KPI accent |
| **On Primary** | `#FFFFFF` | Text/icons on primary |
| **Primary Container** | `#D6E4FF` (Light Blue) | Avatar bg, active nav bg, tonal surfaces |
| **On Primary Container** | `#001B47` (Dark Navy) | Text on primary container |
| **Secondary** | `#F57C00` (Amber/Orange) | Warnings, low-stock alerts, pending sync |
| **Secondary Container** | `#FFDCBB` (Light Amber) | Warning chip/alert bg |
| **Tertiary** | `#2E7D32` (Green) | Success, completed orders, active registers |
| **Tertiary Container** | `#B8F0BB` (Light Green) | Success chip bg |
| **Error** | `#C62828` (Red) | Destructive actions, void, logout |
| **Error Container** | `#FFDAD6` | Error alert bg |
| **Background / Surface** | `#FAFCFF` (Blue-tinted white) | Page bg, drawer bg |
| **On Surface** | `#1A1C22` (Near-black) | Primary text |
| **Surface Variant** | `#E0E7F5` (Cool grey-blue) | Section containers, hover states |
| **On Surface Variant** | `#43475F` (Dark grey) | Secondary text, labels |
| **Outline** | `#5B5F78` | Borders |
| **Outline Variant** | `#C4CAD9` | Subtle dividers, card borders |

### Dark Theme

| Role | Hex |
|------|-----|
| **Primary** | `#A8C7FA` |
| **Primary Container** | `#004A96` |
| **Background / Surface** | `#1A1C22` |
| **On Surface** | `#E1E2EE` |
| **Surface Variant** | `#43475F` |
| **On Surface Variant** | `#C4CAD9` |
| **Tertiary** | `#7EDB7E` |
| **Secondary** | `#FFB77A` |
| **Error** | `#FFB4AB` |

---

## Typography Scale

| Role | Size | Weight | Where Used |
|------|------|--------|------------|
| `displaySmall` | 36sp | Normal | Hero KPI animated value (EXPANDED only) |
| `headlineSmall` | 24sp | **Bold** | KPI card values (EXPANDED stat cards) |
| `titleLarge` | 22sp | Medium | Top app bar title, KPI values (COMPACT) |
| `titleMedium` | 16sp | **SemiBold** | Section headers, nav parent items, drawer section labels |
| `titleSmall` | 14sp | **SemiBold** | Alert card titles, nav child items |
| `bodyMedium` | 14sp | Medium | Activity item titles, nav labels (full mode) |
| `bodySmall` | 12sp | Normal | Alert descriptions, timestamps, greeting text |
| `labelLarge` | 14sp | Medium | Avatar initials, button text |
| `labelMedium` | 12sp | Medium | KPI labels, quick action labels, status chips |
| `labelSmall` | 11sp | Medium | KPI subtitles, trend labels, timestamps |

---

## Spacing & Shape Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 4dp | Tight gaps |
| `sm` | 8dp | Intra-component padding, nav item internal |
| `md` | 16dp | Standard card padding, drawer item padding |
| `lg` | 24dp | Section separation, page margins (EXPANDED) |
| `xl` | 32dp | Major layout gutters |

| Shape | Radius | Usage |
|-------|--------|-------|
| `extraSmall` | 4dp | Status chips, tooltips |
| `small` | 8dp | Icon containers, nav item highlight |
| `medium` | 12dp | Cards, menus, dropdown |
| `large` | 16dp | Hero card, section containers |
| `extraLarge` | 28dp | Bottom sheets |
| `full` | 50% | Avatar circle, progress ring |

---

## Files to Create or Modify

### New Files
- `composeApp/designsystem/.../layouts/ZyntaNavigationDrawer.kt` — Full hierarchical drawer composable
- `composeApp/designsystem/.../components/ZyntaProgressRing.kt` — Circular daily target progress
- `composeApp/designsystem/.../components/AnimatedCounter.kt` — Number animation composable
- `composeApp/designsystem/.../components/ZyntaStatusChip.kt` — Colored CASH/CARD chips

### Files to Modify
- `composeApp/designsystem/.../layouts/ZyntaScaffold.kt` — Replace adaptive nav with hierarchical drawer
- `composeApp/navigation/.../NavigationItems.kt` — Add parent/child hierarchy model
- `composeApp/feature/dashboard/.../screen/DashboardScreen.kt` — Add all 10 modernizations
- `composeApp/feature/dashboard/.../DashboardViewModel.kt` — Add dailySalesTarget, greeting logic
- `composeApp/feature/dashboard/.../mvi/DashboardState.kt` — Add new state fields
- `composeApp/designsystem/.../components/ZyntaStatCard.kt` — Add gradient variant
- `composeApp/designsystem/.../tokens/ZyntaTheme.kt` — Apply brand color palette

---

## Implementation Order

1. **Brand color palette** — Update ZyntaTheme with exact hex values
2. **ZyntaNavigationDrawer** — New hierarchical drawer composable
3. **Update ZyntaScaffold** — Replace bottom bar / rail / flat drawer with hierarchical drawer
4. **NavigationItems hierarchy model** — Add parent/child relationships
5. **Gradient hero KPI card** — ZyntaStatCard gradient variant
6. **AnimatedCounter** — Reusable number animation
7. **ZyntaProgressRing** — Circular daily target
8. **ZyntaStatusChip** — Payment method chips
9. **DashboardScreen modernizations** — Integrate all 10 features
10. **Staggered animations** — Entry animation system
11. **Dark theme** — Verify all components in dark mode
12. **Testing** — Visual regression, responsive breakpoints

---

## Validation Checklist

### Navigation Drawer
- [x] COMPACT: Modal overlay drawer with hamburger trigger (no bottom bar)
- [x] MEDIUM: Mini mode (72dp) with expand toggle to 260dp
- [x] EXPANDED: Full mode (260dp) with collapse toggle to 72dp
- [x] Parent/child items with expandable chevron animation
- [x] Sticky header with logo and collapse button
- [x] Sticky footer with user avatar, name, role, overflow
- [x] Floating popout submenu on mini-mode hover
- [x] 4dp left border accent on active items
- [x] Section headers (OPERATIONS, MANAGEMENT, HR & FINANCE, SYSTEM)
- [x] 250ms ease-out animation for expand/collapse
- [x] RBAC filtering preserved for all items
- [x] Drawer state persisted across navigation

### Dashboard
- [x] Gradient hero KPI card with sparkline
- [x] Animated counter values (800ms, staggered)
- [x] Daily target progress ring (120dp EXPANDED, 80dp others)
- [x] Time-aware greeting in profile dropdown
- [x] Staggered entry animations (fade+slide 20dp, 50ms stagger)
- [x] Tonal section containers (surfaceVariant)
- [x] Status chips on activity items (green CASH, blue CARD)
- [x] Quick action buttons with hover effect
- [x] Alert cards with action buttons
- [x] Weekly chart with gradient fill
- [x] EXPANDED layout: zero scrolling
- [x] MEDIUM layout: two-pane split
- [x] COMPACT layout: single-column scroll
- [x] Dark theme verified for all components
- [x] Loading state overlay
- [x] Empty state (no orders)
