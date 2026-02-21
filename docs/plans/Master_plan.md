# ZyntaPOS — Enterprise Master Blueprint

> **Document ID:** ZENTA-MASTER-PLAN-v1.0  
> **Status:** APPROVED FOR EXECUTION  
> **Architecture:** Kotlin Multiplatform (KMP) — Desktop (JVM) + Android  
> **Author:** Senior KMP Architect & Lead Engineer  
> **Created:** 2026-02-19  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Vision & Objectives](#2-system-vision--objectives)
3. [Architecture Overview](#3-architecture-overview)
4. [Module Decomposition](#4-module-decomposition)
5. [Feature Specification Matrix](#5-feature-specification-matrix)
6. [Security Architecture](#6-security-architecture)
7. [Data Architecture & Persistence](#7-data-architecture--persistence)
8. [Synchronization & Offline-First Strategy](#8-synchronization--offline-first-strategy)
9. [Hardware Abstraction Layer (HAL)](#9-hardware-abstraction-layer-hal)
10. [Multi-Store & Multi-Tenant Architecture](#10-multi-store--multi-tenant-architecture)
11. [API & Integration Layer](#11-api--integration-layer)
12. [UI/UX Architecture](#12-uiux-architecture)
13. [Testing Strategy](#13-testing-strategy)
14. [Phased Delivery Roadmap](#14-phased-delivery-roadmap)
15. [Tech Stack & Dependencies](#15-tech-stack--dependencies)
16. [Risk Matrix & Mitigation](#16-risk-matrix--mitigation)
17. [Performance & Scalability Targets](#17-performance--scalability-targets)
18. [Compliance & Governance](#18-compliance--governance)

---

## 1. Executive Summary

ZyntaPOS is an enterprise-grade, offline-first Point of Sale system built on Kotlin Multiplatform (KMP) targeting Android tablets and Desktop (JVM/Windows/macOS/Linux). The system encompasses **17 functional domains**, **87+ feature groups**, and **450+ individual features** spanning POS operations, inventory management, CRM, financial reporting, multi-store orchestration, staff management, and compliance with PCI-DSS, GDPR, and Sri Lanka E-Invoicing (2026).

The architecture enforces Clean Architecture with MVI state management, CRDT-based conflict resolution for offline sync, a Hardware Abstraction Layer for POS peripherals, and a 7-layer security model covering infrastructure through governance.

---

## 2. System Vision & Objectives

### 2.1 Vision Statement
Deliver a cross-platform POS system that operates seamlessly online and offline, scales from a single store to a multi-branch enterprise, and meets the strictest security and compliance requirements for retail and hospitality environments.

### 2.2 Key Objectives

| # | Objective | Success Metric |
|---|-----------|----------------|
| O1 | Offline-first operation | 100% POS functionality available without network |
| O2 | Sub-second transaction processing | Payment completion < 800ms on mid-range hardware |
| O3 | Multi-platform parity | Feature parity between Android and Desktop at launch |
| O4 | Enterprise security | PCI-DSS SAQ-B compliance, AES-256 local encryption |
| O5 | Multi-store scalability | Support 50+ stores with real-time centralized dashboard |
| O6 | Compliance readiness | Sri Lanka E-Invoicing 2026, GDPR data subject rights |
| O7 | Hardware abstraction | Support 3+ thermal printer brands, USB/Bluetooth scanners |

### 2.3 Target Users & Roles

| Role | Description | Primary Modules |
|------|-------------|-----------------|
| **Admin** | System owner, full access | All modules, settings, user management |
| **Store Manager** | Branch-level authority | POS, Inventory, Reports, Staff |
| **Cashier** | Front-line operator | POS, Cash Register, basic CRM |
| **Accountant** | Financial oversight | Reports, Expenses, Accounting |
| **Stock Manager** | Inventory specialist | Inventory, Procurement, Warehouse |

---

## 3. Architecture Overview

### 3.1 Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                     │
│         Compose Multiplatform + Material 3 + MVI          │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ POS Screen  │ │ Inventory    │ │ Reports Dashboard│  │
│  │ (feature)   │ │ (feature)    │ │ (feature)        │  │
│  └──────┬──────┘ └──────┬───────┘ └────────┬─────────┘  │
├─────────┼───────────────┼──────────────────┼─────────────┤
│         ▼               ▼                  ▼             │
│                    DOMAIN LAYER                           │
│         Use Cases · Domain Models · Repository            │
│         Interfaces · Business Rules · Validation          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  :shared:domain                                      │ │
│  │  ├── usecases/      (ProcessSaleUseCase, etc.)      │ │
│  │  ├── models/        (Order, Product, Customer, etc.)│ │
│  │  ├── repository/    (interfaces only)               │ │
│  │  └── validation/    (business rule validators)      │ │
│  └──────────────────────┬──────────────────────────────┘ │
├─────────────────────────┼────────────────────────────────┤
│                         ▼                                │
│                    DATA LAYER                             │
│         Repository Impl · Local DB · Remote API           │
│         Sync Engine · Mappers · Cache                     │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  :shared:data                                        │ │
│  │  ├── local/         (SQLDelight DAOs, encryption)   │ │
│  │  ├── remote/        (Ktor API client, DTOs)         │ │
│  │  ├── sync/          (CRDT engine, queue manager)    │ │
│  │  ├── repository/    (implementations)               │ │
│  │  └── mapper/        (Entity ↔ Domain mappers)       │ │
│  └──────────────────────┬──────────────────────────────┘ │
├─────────────────────────┼────────────────────────────────┤
│                         ▼                                │
│               PLATFORM / INFRASTRUCTURE                   │
│         HAL · DI · Security · Platform APIs               │
│  ┌───────────────────┐   ┌───────────────────────────┐  │
│  │  androidMain       │   │  desktopMain               │  │
│  │  ├── hal/          │   │  ├── hal/                  │  │
│  │  │  (USB/BT print) │   │  │  (ESC/POS TCP, COM)    │  │
│  │  ├── security/     │   │  ├── security/             │  │
│  │  │  (Keystore)     │   │  │  (JCE KeyStore)         │  │
│  │  └── di/           │   │  └── di/                   │  │
│  └───────────────────┘   └───────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 3.2 KMP Source Set Structure

```
:shared
├── :shared:core            → Constants, extensions, base classes, crypto
├── :shared:domain          → Use cases, domain models, repository interfaces
├── :shared:data            → SQLDelight, Ktor client, sync engine, repos
├── :shared:hal             → expect/actual hardware interfaces
└── :shared:security        → Encryption, token management, key storage

:composeApp
├── :composeApp:designsystem  → Material 3 theme, components, tokens
├── :composeApp:navigation    → Type-safe navigation graph
├── :composeApp:feature:auth  → Login, registration, session UI
├── :composeApp:feature:pos   → POS screen, cart, payment, receipt
├── :composeApp:feature:inventory → Products, categories, stock
├── :composeApp:feature:customers → Customer management, loyalty
├── :composeApp:feature:reports → Sales, stock, financial reports
├── :composeApp:feature:register → Cash register operations
├── :composeApp:feature:expenses → Expense & accounting UI
├── :composeApp:feature:coupons  → Coupon & promotion UI
├── :composeApp:feature:multistore → Store management, dashboard
├── :composeApp:feature:staff  → Employee, attendance, payroll
├── :composeApp:feature:settings → All configuration screens
└── :composeApp:feature:admin  → System admin, logs, backups
```

### 3.3 MVI State Management Pattern

```
┌──────────┐    Intent     ┌──────────┐    State     ┌──────────┐
│          │ ────────────► │          │ ────────────► │          │
│   VIEW   │               │ VIEWMODEL│               │    UI    │
│ (Compose)│ ◄──────────── │ (Shared) │ ◄──────────── │  STATE   │
│          │   Side Effect │          │   Reduce      │          │
└──────────┘               └──────────┘               └──────────┘
                                │
                                ▼
                         ┌──────────┐
                         │ USE CASE │
                         │ (Domain) │
                         └──────────┘
```

All feature ViewModels extend the **canonical** `BaseViewModel` in
`:composeApp:core` (`com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel`).
The canonical API uses `updateState {}` for atomic state mutations,
`sendEffect()` for one-shot Channel-backed effects, and
`abstract suspend fun handleIntent(I)` for intent processing.
Intents are dispatched from the UI via `viewModel.dispatch(intent)`.

```kotlin
// Canonical ViewModel contract (composeApp:core)
class PosViewModel(
    private val addItemUseCase: AddItemToCartUseCase,
) : BaseViewModel<PosState, PosIntent, PosEffect>(PosState()) {

    override suspend fun handleIntent(intent: PosIntent) {
        when (intent) {
            is PosIntent.AddToCart     -> onAddToCart(intent.product)
            is PosIntent.ApplyDiscount -> updateState { copy(discount = intent.value) }
            PosIntent.ProcessPayment   -> processPayment()
        }
    }

    private suspend fun onAddToCart(product: Product) {
        updateState { copy(isLoading = true) }
        val result = addItemUseCase(product)
        updateState { copy(cart = result, isLoading = false) }
    }

    private fun processPayment() {
        viewModelScope.launch {
            // … payment logic …
            sendEffect(PosEffect.PrintReceipt(orderId = currentState.cart.orderId))
        }
    }
}

// UI side — dispatch intents via the stable dispatch() entry-point
@Composable
fun PosScreen(viewModel: PosViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PosEffect.PrintReceipt -> printer.print(effect.orderId)
            }
        }
    }

    Button(onClick = { viewModel.dispatch(PosIntent.ProcessPayment) }) {
        Text("Charge ${state.cart.total}")
    }
}
```

> ⚠️ **Never** extend raw `androidx.lifecycle.ViewModel` directly in feature modules.
> Always extend `BaseViewModel<S, I, E>` to get `updateState {}`, `sendEffect()`,
> `currentState`, and Channel-backed effect delivery for free.

Each feature module also defines its contract types:

```kotlin
// Intent: User actions
sealed interface PosIntent {
    data class ScanBarcode(val code: String) : PosIntent
    data class AddToCart(val productId: Long, val qty: Double) : PosIntent
    data class ApplyDiscount(val type: DiscountType, val value: Double) : PosIntent
    data object ProcessPayment : PosIntent
}

// State: Single source of truth
data class PosState(
    val cart: Cart = Cart.empty(),
    val products: List<Product> = emptyList(),
    val customer: Customer? = null,
    val paymentState: PaymentState = PaymentState.Idle,
    val isLoading: Boolean = false,
    val error: UiError? = null
)

// Side Effects: One-shot events
sealed interface PosSideEffect {
    data class PrintReceipt(val orderId: Long) : PosSideEffect
    data class OpenCashDrawer(val registerId: Long) : PosSideEffect
    data class ShowToast(val message: String) : PosSideEffect
}
```

---

## 4. Module Decomposition

### 4.1 Complete Module Registry

| Module ID | Module Name | Layer | Dependencies | Phase |
|-----------|-------------|-------|--------------|-------|
| M01 | `:shared:core` | Infrastructure | — | 1 |
| M02 | `:shared:domain` | Domain | M01 | 1 |
| M03 | `:shared:data` | Data | M01, M02 | 1 |
| M04 | `:shared:hal` | Infrastructure | M01 | 1 |
| M05 | `:shared:security` | Infrastructure | M01 | 1 |
| M06 | `:composeApp:designsystem` | Presentation | M01 | 1 |
| M07 | `:composeApp:navigation` | Presentation | M02, M05, M06 | 1 |
| M08 | `:composeApp:feature:auth` | Feature | M02, M03, M05, M06, M21 | 1 |
| M09 | `:composeApp:feature:pos` | Feature | M02, M03, M04, M06, M21 | 1 |
| M10 | `:composeApp:feature:inventory` | Feature | M02, M03, M06, M21 | 1 |
| M11 | `:composeApp:feature:register` | Feature | M02, M03, M04, M06, M21 | 1 |
| M12 | `:composeApp:feature:reports` | Feature | M02, M03, M06, M21 | 1 |
| M13 | `:composeApp:feature:customers` | Feature | M02, M03, M06, M21 | 2 |
| M14 | `:composeApp:feature:coupons` | Feature | M02, M03, M06, M21 | 2 |
| M15 | `:composeApp:feature:multistore` | Feature | M02, M03, M06, M21 | 2 |
| M16 | `:composeApp:feature:expenses` | Feature | M02, M03, M06, M21 | 2 |
| M17 | `:composeApp:feature:staff` | Feature | M02, M03, M06, M21 | 3 |
| M18 | `:composeApp:feature:settings` | Feature | M02, M03, M04, M05, M06, M21 | 1 |
| M19 | `:composeApp:feature:admin` | Feature | M02, M03, M05, M06, M21 | 3 |
| M20 | `:composeApp:feature:media` | Feature | M02, M03, M06, M21 | 3 |
| M21 | `:composeApp:core` | Presentation | M02 | 1 |

### 4.2 Module Dependency Graph

```
                        ┌──────────────┐
                        │ :shared:core │  (M01)
                        └──────┬───────┘
              ┌────────────────┼───────────────┬────────────────┐
              ▼                ▼               ▼                ▼
        ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
        │ :domain  │  │  :security   │  │   :hal   │  │  :data   │
        │  (M02)   │  │   (M05)      │  │  (M04)   │  │  (M03)   │
        └────┬─────┘  └──────┬───────┘  └────┬─────┘  └────┬─────┘
             │               │               │              │
             │    ┌──────────┘               └──────────────┘
             │    │                                  ▼
             │    │               ┌───────────────────────┐
             │    │               │    :designsystem      │  (M06)
             │    │               └───────────┬───────────┘
             │    │                           │
             │    │           ┌───────────────┘
             ▼    ▼           ▼
             ┌───────────────────────┐
             │      :navigation      │  (M07)  ← depends on M02, M05, M06
             └───────────────────────┘
             │
             ▼
   ┌──────────────────────┐
   │  :composeApp:core    │  (M21)  ← canonical BaseViewModel,
   │                      │            UiState, UiEffect
   └──────────┬───────────┘
              │
              ▼  (all 13 feature modules depend on :composeApp:core)
   ┌──────┬──────┬───────────┬───────────┬──────────┬─────────┐
   ▼      ▼      ▼           ▼           ▼          ▼         ▼
:auth  :pos  :inventory :customers :register :reports :settings
:coupons  :multistore  :expenses  :staff  :admin  :media
                                                        ... (all via M21)
```

---

## 5. Feature Specification Matrix

### 5.1 Domain 1: Authentication & Authorization (16 features)

| Feature ID | Feature | Sub-Features | Security Layer | Priority |
|------------|---------|-------------|----------------|----------|
| F1.1.1 | User Registration | Email, Password, Role assignment | L3: Identity | P0 |
| F1.1.2 | User Login/Logout | JWT tokens, session creation/destruction | L3: Identity | P0 |
| F1.1.3 | Password Reset | Email-based recovery, token expiry | L3: Identity | P0 |
| F1.1.4 | Email Verification | Verification link, resend capability | L3: Identity | P1 |
| F1.1.5 | Profile Management | Avatar, personal info, language | L3: Identity | P1 |
| F1.1.6 | Session Management | Single/multi-device, force logout, idle timeout | L3: Identity | P0 |
| F1.2.1 | Custom Roles | Create, clone, edit, delete roles | L3: Identity | P0 |
| F1.2.2 | Permission Assignment | Module/Feature/Data level granularity | L3: Identity | P0 |
| F1.2.3 | Default Roles | Admin, Store Manager, Cashier, Accountant, Stock Manager | L3: Identity | P0 |
| F1.3.1 | Password Encryption | BCrypt hashing, salt generation | L1: Infrastructure | P0 |
| F1.3.2 | JWT Authentication | Access + Refresh token rotation | L3: Identity | P0 |
| F1.3.3 | API Authentication | Sanctum-style token auth for API | L5: Network | P0 |
| F1.3.4 | CSRF Protection | Token-based CSRF prevention | L6: Application | P0 |
| F1.3.5 | SQL Injection Prevention | Parameterized queries, input sanitization | L6: Application | P0 |
| F1.3.6 | XSS Protection | Output encoding, CSP headers | L6: Application | P0 |
| F1.3.7 | Rate Limiting | Per-endpoint throttling, brute force protection | L5: Network | P0 |

### 5.2 Domain 2: Point of Sale (55+ features)

| Feature ID | Feature | Key Sub-Features | Phase |
|------------|---------|-------------------|-------|
| F2.1.1 | Product Grid | Category nav, search, barcode input, image display, stock indicator | 1 |
| F2.1.2 | Shopping Cart | Add/update/remove items, item discounts, product notes, unit selection | 1 |
| F2.1.3 | Cart Actions | Hold, retrieve, cancel, clear, duplicate orders | 1 |
| F2.1.4 | Customer Selection | Existing, quick-add, walk-in default, search | 1 |
| F2.1.5 | UI Controls | Fullscreen, calculator, dark mode, keyboard shortcuts, responsive | 1 |
| F2.2.1 | Order Types | Takeaway, delivery, dine-in, quote/proforma | 1 |
| F2.2.2 | Payment Methods | Cash, card, mobile, bank transfer, credit, split payment | 1 |
| F2.2.3 | Payment Features | Partial, installment, change calc, exact amount | 1 |
| F2.2.4 | Payment Validation | Insufficient alert, overpayment, confirmation | 1 |
| F2.2.5 | Discounts & Coupons | Order/item level, coupon codes, role-based auth | 1 |
| F2.2.6 | Order Information | Name, date, reference, notes, shipping, quick products | 1 |
| F2.2.7 | Tax Calculation | Multi-group, per product, inclusive/exclusive, shipping tax | 1 |
| F2.3.1 | Receipt Generation | Auto-print, reprint, email, SMS | 1 |
| F2.3.2 | Receipt Customization | Logo, store info, header/footer, T&C, QR code | 1 |
| F2.3.3 | Thermal Printing | 80mm/58mm, ESC/POS, silent, cloud, local | 1 |
| F2.3.4 | Print Features | Cash drawer control, paper cut, kitchen print | 1 |
| F2.4.1 | Customer Display | Secondary display, item/price/total | 2 |

### 5.3 Domain 3: Inventory Management (40+ features)

| Feature ID | Feature | Key Sub-Features | Phase |
|------------|---------|-------------------|-------|
| F3.1.1 | Product Creation | Name, barcode, SKU, category, type, units, pricing, stock, expiry, tax, media | 1 |
| F3.1.2 | Product Management | List/grid, search/filter, bulk ops, actions, visibility | 1 |
| F3.1.3 | Product Variations | Size, color, weight, custom attributes | 1 |
| F3.2.1 | Categories | Hierarchy, unlimited sub-categories, image, display order, delete protection | 1 |
| F3.3.1 | Units of Measure | Unit groups, conversion, base unit, unit-specific pricing | 1 |
| F3.4.1 | Purchase Orders | Supplier selection, qty/price, dates, delivery/payment status | 1 |
| F3.4.2 | Procurement Mgmt | List, edit, delete, history, supplier-wise, status tracking | 1 |
| F3.5.1 | Stock Adjustment | Increase/decrease, transfer, reason, history, reconciliation | 1 |
| F3.6.1 | Warehouses | Multiple warehouses, creation, stock per warehouse, transfers | 2 |
| F3.6.2 | Racks Manager | Rack/shelf creation, product location, pick list generation | 3 |
| F3.7.1 | Suppliers | CRUD, contact info, payment terms, history, reports | 1 |

### 5.4 Domain 4: CRM (30+ features)

| Feature ID | Feature | Phase |
|------------|---------|-------|
| F4.1.1 | Customer CRUD | 2 |
| F4.1.2 | Customer Groups & Group Pricing | 2 |
| F4.2.1 | Customer Wallet (Add/Deduct/History) | 2 |
| F4.2.2 | Credit Sales & Limits | 2 |
| F4.2.3 | Installment Payments | 2 |
| F4.3.1 | Reward Points System | 2 |
| F4.3.2 | Loyalty Tiers & Benefits | 2 |
| F4.3.3 | Customer Coupons | 2 |
| F4.4.1 | SMS Notifications (Module) | 2 |
| F4.4.2 | Email Notifications | 2 |
| F4.5.1 | Customer Portal (Future) | 3 |

### 5.5 Domain 5: Reporting & Analytics (35+ features)

| Feature ID | Feature | Report Types | Phase |
|------------|---------|-------------|-------|
| F5.1 | Sales Reports | Daily/weekly/monthly, by category/product/customer/payment/cashier | 1 |
| F5.2 | Stock Reports | Current stock, low/out of stock, movement, value, expiring, dead stock | 1 |
| F5.3 | Purchase Reports | Procurement, supplier, analysis, purchase vs sales | 1 |
| F5.4 | Financial Reports | P&L, cash flow, tax, payment, expenses, journal | 2 |
| F5.5 | Customer Reports | List, history, credit, loyalty, group | 2 |
| F5.6 | Register Reports | Opening/closing, history, performance, transaction | 1 |
| F5.7 | Export & Print | CSV, Excel, PDF, Print | 1 |

### 5.6 Domains 6–17 (Summary)

| Domain | Feature Count | Phase | Key Capabilities |
|--------|--------------|-------|------------------|
| D6: Cash Register | 15+ | 1 | Setup, shift operations, cash in/out, closing reports |
| D7: Expenses & Accounting | 25+ | 2 | Expense CRUD, recurring, chart of accounts, financial statements |
| D8: Coupons & Promotions | 15+ | 2 | Coupon CRUD, types, BOGO, bundles, flash sales |
| D9: Multi-Store | 12+ | 2 | Store CRUD, separate inventory, inter-store transfer, central dashboard |
| D10: Settings | 50+ | 1–3 | General, POS, tax, payment, order, printing, backup, integration |
| D11: Multi-Platform | 8+ | 1 | Android, Desktop (Win/Mac/Linux), offline-first, KMP architecture |
| D12: Sync & Offline | 12+ | 1 | Local SQLite, offline processing, CRDT conflict resolution, queue mgmt |
| D13: Staff Management | 15+ | 3 | Employee records, attendance, payroll, scheduling |
| D14: Media Manager | 10+ | 3 | Upload, library, operations |
| D15: System Admin | 18+ | 3 | Monitoring, DB management, updates, modules, developer tools |
| D16: Localization | 10+ | 2 | Multi-language, multi-currency, regional settings, RTL (future) |
| D17: Project Phasing | — | — | Meta: delivery phases 1–3 |

---

## 6. Security Architecture

### 6.1 Seven-Layer Security Model

```
┌─────────────────────────────────────────────────────────────┐
│  L7: GOVERNANCE & COMPLIANCE                                │
│      PCI-DSS · GDPR · SL E-Invoicing 2026 · Audit Trails   │
├─────────────────────────────────────────────────────────────┤
│  L6: APPLICATION SECURITY                                   │
│      Input Validation (SQL/XSS/Path) · Output Encoding      │
│      Secure Coding · Dependency Scanning                     │
├─────────────────────────────────────────────────────────────┤
│  L5: NETWORK SECURITY                                       │
│      TLS 1.3 + Cert Pinning · Request Signing               │
│      IP Whitelisting · Firewall Rules                        │
├─────────────────────────────────────────────────────────────┤
│  L4: DATA SECURITY                                          │
│      SQLCipher (AES-256) · Column-Level Encryption           │
│      Data Masking · Retention Policies · Secure Export       │
├─────────────────────────────────────────────────────────────┤
│  L3: IDENTITY & ACCESS                                      │
│      MFA · RBAC · Session Mgmt · Password Policy · Lockout  │
├─────────────────────────────────────────────────────────────┤
│  L2: PHYSICAL SECURITY                                      │
│      Device Fingerprinting · USB Control · Screen Lock       │
│      Idle Timeout · Cash Drawer Access Control               │
├─────────────────────────────────────────────────────────────┤
│  L1: INFRASTRUCTURE SECURITY                                │
│      Key Management · Key Rotation · Secure Random           │
│      Code Obfuscation (ProGuard/R8)                          │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Security-Critical Feature Mapping

| Security Domain | Feature Count | Critical Data Assets |
|----------------|---------------|---------------------|
| Authentication & Authorization | 16 | User credentials, JWT tokens, session data |
| Payment Processing | 8 | Card data (PCI scope), payment tokens, transaction records |
| Sensitive Data | 45+ | Customer PII, financial reports, business intelligence |
| Network Operations | 12 | API keys, sync payloads, remote backup credentials |
| Hardware Integration | 6 | Cash drawer access, printer commands, scanner input |
| Compliance | 4 | Audit logs, tax records, e-invoice data |

### 6.3 Encryption Strategy

```
┌──────────────────────────────────────────────────────┐
│              ENCRYPTION AT REST                       │
│                                                       │
│  Database:     SQLCipher AES-256-CBC                 │
│  Columns:      AES-256-GCM for PII fields            │
│  Key Storage:  Android Keystore / JCE KeyStore        │
│  Key Rotation: 90-day cycle, versioned keys           │
│  Backup:       AES-256 encrypted archives             │
├──────────────────────────────────────────────────────┤
│              ENCRYPTION IN TRANSIT                     │
│                                                       │
│  Protocol:     TLS 1.3 (minimum)                     │
│  Pinning:      Certificate pinning via OkHttp/Ktor   │
│  Signing:      HMAC-SHA256 request signing            │
│  API Auth:     Bearer JWT + refresh rotation          │
└──────────────────────────────────────────────────────┘
```

### 6.4 RBAC Permission Matrix

```
Permission Structure:
  Module Level  →  feature:pos, feature:inventory, ...
  Feature Level →  pos:create_order, pos:apply_discount, pos:void_order
  Data Level    →  own_store_only, all_stores, own_orders_only

Example Encoding:
  "pos.order.create"          → Can create orders
  "pos.discount.apply"        → Can apply discounts
  "pos.order.void"            → Can void orders (requires manager approval)
  "inventory.stock.adjust"    → Can adjust stock
  "reports.financial.view"    → Can view financial reports
  "settings.tax.edit"         → Can modify tax settings
```

---

## 7. Data Architecture & Persistence

### 7.1 Database Strategy

| Aspect | Technology | Detail |
|--------|-----------|--------|
| Local DB | SQLDelight | KMP-native, compile-time SQL verification |
| Encryption | SQLCipher | AES-256, transparent encryption |
| Schema Migrations | SQLDelight migrations | Numbered .sqm files, forward-only |
| Remote DB | PostgreSQL (Server) | Via REST API (Ktor server) |
| Cache | In-memory LRU | Product catalog, category tree |
| Search | FTS5 (SQLite) | Full-text search on products, customers |

### 7.2 Core Entity Catalog

| Entity | Table | Relationships | Sync Priority |
|--------|-------|--------------|---------------|
| User | `users` | → roles, → stores | HIGH |
| Role | `roles` | → permissions | HIGH |
| Permission | `permissions` | → roles (M2M) | HIGH |
| Store | `stores` | → users, → registers, → warehouses | HIGH |
| Product | `products` | → category, → tax_group, → units, → variations | HIGH |
| Category | `categories` | → parent (self-ref), → products | HIGH |
| UnitGroup | `unit_groups` | → units | MEDIUM |
| Unit | `units` | → unit_group | MEDIUM |
| ProductVariation | `product_variations` | → product | HIGH |
| Customer | `customers` | → customer_group, → addresses, → wallet | HIGH |
| CustomerGroup | `customer_groups` | → customers | MEDIUM |
| CustomerWallet | `customer_wallets` | → customer | HIGH |
| CustomerAddress | `customer_addresses` | → customer | MEDIUM |
| Order | `orders` | → customer, → store, → register, → order_items | CRITICAL |
| OrderItem | `order_items` | → order, → product, → unit | CRITICAL |
| Payment | `payments` | → order, → payment_method | CRITICAL |
| PaymentMethod | `payment_methods` | → payments | LOW |
| CashRegister | `cash_registers` | → store, → user | HIGH |
| RegisterSession | `register_sessions` | → register, → user | HIGH |
| CashMovement | `cash_movements` | → register_session | HIGH |
| Supplier | `suppliers` | → procurements | MEDIUM |
| Procurement | `procurements` | → supplier, → procurement_items | MEDIUM |
| ProcurementItem | `procurement_items` | → procurement, → product | MEDIUM |
| Warehouse | `warehouses` | → store, → stock_entries | MEDIUM |
| StockEntry | `stock_entries` | → product, → warehouse | HIGH |
| StockAdjustment | `stock_adjustments` | → product, → warehouse | HIGH |
| StockTransfer | `stock_transfers` | → source_warehouse, → dest_warehouse | MEDIUM |
| Expense | `expenses` | → expense_category, → store | MEDIUM |
| ExpenseCategory | `expense_categories` | → expenses | LOW |
| TaxGroup | `tax_groups` | → taxes, → products | LOW |
| Tax | `taxes` | → tax_group | LOW |
| Coupon | `coupons` | → orders (usage), → customers | MEDIUM |
| RewardPoints | `reward_points` | → customer | MEDIUM |
| LoyaltyTier | `loyalty_tiers` | → customer_group | LOW |
| Employee | `employees` | → user, → store | MEDIUM |
| Attendance | `attendance_records` | → employee | MEDIUM |
| Payroll | `payroll_records` | → employee | MEDIUM |
| AuditLog | `audit_logs` | → user, → entity | HIGH |
| SyncQueue | `sync_queue` | → entity (polymorphic) | CRITICAL |
| MediaFile | `media_files` | → various entities | LOW |
| Setting | `settings` | → store (optional) | MEDIUM |
| Notification | `notifications` | → user, → customer | LOW |

### 7.3 Database Partitioning Strategy

```
Local SQLite (Per Device):
├── Core Tables (always present)
│   ├── users, roles, permissions
│   ├── products, categories, units
│   ├── customers, customer_groups
│   ├── orders, order_items, payments
│   ├── cash_registers, register_sessions
│   ├── settings, audit_logs
│   └── sync_queue
│
├── Store-Scoped Tables (filtered by active store)
│   ├── stock_entries (warehouse-specific)
│   ├── procurements
│   ├── expenses
│   └── employee records
│
└── Sync Metadata
    ├── sync_state (last sync timestamps)
    ├── conflict_log
    └── crdt_versions
```

---

## 8. Synchronization & Offline-First Strategy

### 8.1 Offline-First Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    DEVICE (LOCAL)                         │
│                                                          │
│  ┌─────────┐    ┌──────────┐    ┌───────────────────┐  │
│  │   UI    │───►│ Use Case │───►│  Local Repository  │  │
│  │ (MVI)  │◄───│          │◄───│  (SQLDelight)      │  │
│  └─────────┘    └──────────┘    └────────┬──────────┘  │
│                                          │              │
│                                          ▼              │
│                                 ┌───────────────────┐  │
│                                 │    SYNC ENGINE     │  │
│                                 │  ┌─────────────┐  │  │
│                                 │  │ Outbox Queue │  │  │
│                                 │  │ (Operations) │  │  │
│                                 │  └──────┬──────┘  │  │
│                                 │         │         │  │
│                                 │  ┌──────▼──────┐  │  │
│                                 │  │ CRDT Engine │  │  │
│                                 │  │ (Conflict   │  │  │
│                                 │  │  Resolution)│  │  │
│                                 │  └──────┬──────┘  │  │
│                                 └─────────┼─────────┘  │
│                                           │             │
└───────────────────────────────────────────┼─────────────┘
                                            │
                              ┌──────────────▼──────────────┐
                              │       CLOUD SERVER           │
                              │  ┌───────────────────────┐  │
                              │  │   REST API (Ktor)     │  │
                              │  │   WebSocket / gRPC    │  │
                              │  │   PostgreSQL           │  │
                              │  │   Conflict Resolver    │  │
                              │  └───────────────────────┘  │
                              └─────────────────────────────┘
```

### 8.2 CRDT Strategy for Conflict Resolution

| Data Type | CRDT Strategy | Example |
|-----------|--------------|---------|
| Order (immutable after payment) | Last-Write-Wins Register | Timestamp-based, server wins ties |
| Cart (mutable, multi-device) | OR-Set (Observed-Remove Set) | Items can be added/removed concurrently |
| Stock Quantity | PN-Counter (Positive-Negative Counter) | Increments/decrements merge correctly |
| Customer Balance | PN-Counter | Credits and debits merge |
| Settings | Last-Write-Wins Register | Most recent update wins |
| Product Data | Last-Write-Wins with field-level merge | Per-field timestamps |

### 8.3 Sync Priority & Queue Management

```
Priority Levels:
  P0 (CRITICAL):  Orders, Payments        → Sync immediately when online
  P1 (HIGH):      Stock changes, Cash ops  → Sync within 30 seconds
  P2 (MEDIUM):    Customer updates, Procs  → Sync within 5 minutes
  P3 (LOW):       Settings, Media          → Sync on schedule (hourly)

Queue Behavior:
  - Operations are enqueued in sync_queue with priority + timestamp
  - Background worker processes queue by priority
  - Exponential backoff on failure (1s → 2s → 4s → ... → 5min max)
  - Dead letter queue for 10+ failures (manual review)
```

---

## 9. Hardware Abstraction Layer (HAL)

### 9.1 HAL Interface Design

```kotlin
// commonMain - Shared interfaces
expect interface ThermalPrinter {
    suspend fun connect(config: PrinterConfig): Result<Unit>
    suspend fun print(document: PrintDocument): Result<Unit>
    suspend fun openCashDrawer(): Result<Unit>
    suspend fun cutPaper(): Result<Unit>
    fun disconnect()
    val status: StateFlow<PrinterStatus>
}

expect interface BarcodeScanner {
    fun startListening(): Flow<BarcodeResult>
    fun stopListening()
    val isConnected: StateFlow<Boolean>
}

expect interface CashDrawer {
    suspend fun open(): Result<Unit>
    val isOpen: StateFlow<Boolean>
}

expect interface CustomerDisplay {
    suspend fun showItems(items: List<DisplayItem>): Result<Unit>
    suspend fun showTotal(total: Money): Result<Unit>
    suspend fun clear(): Result<Unit>
}
```

### 9.2 Platform Implementations

```
androidMain:
├── AndroidThermalPrinter
│   ├── USB (UsbManager + ESC/POS)
│   ├── Bluetooth (BluetoothSocket + ESC/POS)
│   └── Network (TCP Socket + ESC/POS)
├── AndroidBarcodeScanner
│   ├── Camera (ML Kit Barcode)
│   ├── USB HID (KeyEvent intercept)
│   └── Bluetooth SPP
├── AndroidCashDrawer
│   └── Via printer kick command
└── AndroidCustomerDisplay
    └── Presentation API (secondary display)

desktopMain:
├── DesktopThermalPrinter
│   ├── USB (javax.usb / libusb4j)
│   ├── COM/Serial (jSerialComm + ESC/POS)
│   └── Network (TCP Socket + ESC/POS)
├── DesktopBarcodeScanner
│   ├── USB HID (AWT KeyEvent)
│   └── COM/Serial
├── DesktopCashDrawer
│   └── Via printer kick command or serial
└── DesktopCustomerDisplay
    └── Secondary JFrame window
```

---

## 10. Multi-Store & Multi-Tenant Architecture

### 10.1 Data Isolation Strategy

```
┌─────────────────────────────────────────┐
│           CENTRAL SERVER                 │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Organization (Tenant)             │ │
│  │  ├── Store A                       │ │
│  │  │   ├── Users (store-scoped)     │ │
│  │  │   ├── Inventory                │ │
│  │  │   ├── Orders                   │ │
│  │  │   └── Cash Registers           │ │
│  │  ├── Store B                       │ │
│  │  │   ├── Users                    │ │
│  │  │   ├── Inventory                │ │
│  │  │   └── ...                      │ │
│  │  └── Shared Resources             │ │
│  │      ├── Product Catalog          │ │
│  │      ├── Customer Database        │ │
│  │      ├── Suppliers                │ │
│  │      └── Consolidated Reports     │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 10.2 Inter-Store Operations

| Operation | Flow | Sync Requirement |
|-----------|------|-----------------|
| Stock Transfer | Store A → Server → Store B | Real-time (WebSocket) |
| Price Sync | Central → All Stores | Scheduled + Push |
| Report Consolidation | All Stores → Central | Batch (end of day) |
| User Transfer | Admin → Store assignment update | Immediate |

---

## 11. API & Integration Layer

### 11.1 API Architecture

```
Client (KMP App)
    │
    ├── Ktor Client (commonMain)
    │   ├── Authentication Interceptor (JWT inject)
    │   ├── Request Signing Interceptor (HMAC)
    │   ├── Retry & Circuit Breaker
    │   ├── Logging & Metrics
    │   └── Response Caching
    │
    ▼
REST API Server (Ktor Server / Laravel)
    │
    ├── /api/v1/auth/*           → Authentication endpoints
    ├── /api/v1/products/*       → Product CRUD + search
    ├── /api/v1/orders/*         → Order processing
    ├── /api/v1/customers/*      → Customer management
    ├── /api/v1/inventory/*      → Stock operations
    ├── /api/v1/registers/*      → Cash register operations
    ├── /api/v1/reports/*        → Report generation
    ├── /api/v1/sync/*           → Sync endpoints (push/pull)
    ├── /api/v1/stores/*         → Multi-store management
    └── /api/v1/settings/*       → Configuration
```

### 11.2 Third-Party Integrations

| Integration | Purpose | Protocol |
|------------|---------|----------|
| Payment Gateway | Card/digital payments | REST API + SDK |
| SMS Gateway | Customer notifications | REST API |
| Email Service | Receipts, promotions | SMTP / API |
| Accounting Software | Data export | REST API / CSV |
| E-Invoicing (SL 2026) | Compliance | Government API |

---

## 12. UI/UX Architecture

### 12.1 Design System

```
ZyntaPOS Design System (:composeApp:designsystem)
├── Theme
│   ├── ZentaTheme (light/dark)
│   ├── ColorScheme (Material 3 dynamic)
│   ├── Typography (scale)
│   └── Shape (rounded corners)
│
├── Tokens
│   ├── Spacing (4dp grid)
│   ├── Elevation
│   ├── Duration (animations)
│   └── Breakpoints (compact/medium/expanded)
│
├── Components
│   ├── ZentaButton (primary, secondary, danger)
│   ├── ZentaTextField (with validation)
│   ├── ZentaCard (product, order, report)
│   ├── ZentaDialog (confirm, alert, input)
│   ├── ZentaTable (sortable, paginated)
│   ├── ZentaSearchBar (with barcode icon)
│   ├── ZentaNumericPad (for payment entry)
│   └── ZentaBadge (notification, status)
│
└── Layouts
    ├── ZentaScaffold (adaptive navigation)
    ├── ZentaSplitPane (master-detail for desktop)
    ├── ZentaGrid (responsive product grid)
    └── ZentaBottomSheet (mobile-friendly)
```

### 12.2 Responsive Layout Strategy

| Breakpoint | Width | Layout | Navigation |
|-----------|-------|--------|------------|
| Compact | < 600dp | Single pane, bottom nav | Bottom bar |
| Medium | 600–840dp | List + detail | Side rail |
| Expanded | > 840dp | Multi-pane, POS split view | Side navigation drawer |

### 12.3 POS Screen Layout (Expanded)

```
┌────────────────────────────────────────────────────────────┐
│  [☰ Menu]  ZyntaPOS  [🔍 Search] [📷 Scan] [👤 User] [⚙]  │
├─────────────────────────────┬──────────────────────────────┤
│                             │                              │
│    PRODUCT GRID             │    CART / ORDER               │
│  ┌─────┬─────┬─────┐      │  ┌──────────────────────────┐│
│  │ Cat │ Cat │ Cat │      │  │ Customer: [Walk-in ▼]    ││
│  ├─────┴─────┴─────┤      │  ├──────────────────────────┤│
│  │ ┌───┐ ┌───┐ ┌───┐│      │  │ Item 1    x2    $10.00 ││
│  │ │ P │ │ P │ │ P ││      │  │ Item 2    x1    $25.00 ││
│  │ └───┘ └───┘ └───┘│      │  │ Item 3    x3    $ 9.00 ││
│  │ ┌───┐ ┌───┐ ┌───┐│      │  ├──────────────────────────┤│
│  │ │ P │ │ P │ │ P ││      │  │ Subtotal:       $72.00 ││
│  │ └───┘ └───┘ └───┘│      │  │ Tax (15%):      $10.80 ││
│  │ ┌───┐ ┌───┐ ┌───┐│      │  │ Discount:       -$5.00 ││
│  │ │ P │ │ P │ │ P ││      │  │ ━━━━━━━━━━━━━━━━━━━━━━ ││
│  │ └───┘ └───┘ └───┘│      │  │ TOTAL:          $77.80 ││
│  └───────────────────┘      │  ├──────────────────────────┤│
│                             │  │ [Hold] [Clear] [Discount]││
│  [◀ Prev]  Page 1  [Next ▶]│  │ [💳 PAY $77.80]          ││
│                             │  └──────────────────────────┘│
└─────────────────────────────┴──────────────────────────────┘
```

---

## 13. Testing Strategy

### 13.1 Test Pyramid

```
                    ┌───────────┐
                    │   E2E     │  ← 5% (Critical flows only)
                    │ (Manual + │    Compose UI + Platform
                    │ Automated)│
                  ┌─┴───────────┴─┐
                  │  Integration  │  ← 25% (Repository + Sync + HAL)
                  │    Tests      │    SQLDelight + Ktor MockEngine
                ┌─┴───────────────┴─┐
                │    Unit Tests     │  ← 70% (Use Cases, Models, Rules)
                │ (Kotlin Test +    │    commonTest, androidTest, desktopTest
                │  Mockative)       │
                └───────────────────┘
```

### 13.2 Critical Test Domains

| Domain | Test Focus | Min Coverage |
|--------|-----------|-------------|
| Payment Processing | All payment methods, split, partial, edge cases | 95% |
| Tax Calculation | Multi-group, inclusive/exclusive, rounding | 95% |
| Stock Management | Adjustment, transfer, expiry, negative prevention | 90% |
| Sync Engine | CRDT merge, conflict resolution, queue integrity | 90% |
| RBAC | Permission checks, role inheritance, data scoping | 90% |
| Offline Operations | Queue, retry, idempotency | 85% |
| Cash Register | Open/close, cash in/out, discrepancy detection | 85% |

---

## 14. Phased Delivery Roadmap

### Phase 1: MVP (Months 1–6)

```
GOAL: Fully functional single-store POS with offline capability

Modules:
  ✅ M01 :shared:core
  ✅ M02 :shared:domain
  ✅ M03 :shared:data
  ✅ M04 :shared:hal
  ✅ M05 :shared:security
  ✅ M06 :composeApp:designsystem
  ✅ M07 :composeApp:navigation
  ✅ M08 :composeApp:feature:auth
  ✅ M09 :composeApp:feature:pos
  ✅ M10 :composeApp:feature:inventory
  ✅ M11 :composeApp:feature:register
  ✅ M12 :composeApp:feature:reports (sales + stock only)
  ✅ M18 :composeApp:feature:settings (core settings)

Deliverables:
  - Android APK + Desktop JAR
  - Offline POS operations
  - Thermal printing (80mm/58mm)
  - Barcode scanning
  - Cash register management
  - Basic reporting (sales, stock)
  - SQLCipher encrypted local DB
  - Cloud sync (basic push/pull)
```

### Phase 2: Growth (Months 7–12)

```
GOAL: Multi-store, CRM, promotions, financial tools

Modules:
  ✅ M13 :composeApp:feature:customers
  ✅ M14 :composeApp:feature:coupons
  ✅ M15 :composeApp:feature:multistore
  ✅ M16 :composeApp:feature:expenses
  ✅ M12 :composeApp:feature:reports (financial + customer)

Deliverables:
  - Multi-store management + central dashboard
  - Customer management + loyalty
  - Coupon & promotion engine
  - Expense tracking & accounting
  - Financial reports (P&L, cash flow)
  - Customer display support
  - CRDT-based advanced sync
  - Multi-language support
```

### Phase 3: Enterprise (Months 13–18)

```
GOAL: Full enterprise feature set, compliance, administration

Modules:
  ✅ M17 :composeApp:feature:staff
  ✅ M19 :composeApp:feature:admin
  ✅ M20 :composeApp:feature:media

Deliverables:
  - Staff management (attendance, payroll, scheduling)
  - System administration tools
  - Media manager
  - Sri Lanka E-Invoicing compliance
  - Advanced analytics
  - Customer portal (future)
  - Racks/warehouse location management
  - Module marketplace
```

### Phase Timeline

```
Month:  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18
        ├───┴───┴───┴───┴───┴───┤───┴───┴───┴───┴───┴───┤───┴───┴───┴───┴───┴───┤
Phase:  │      PHASE 1: MVP     │   PHASE 2: GROWTH     │  PHASE 3: ENTERPRISE  │
        │                       │                       │                       │
M1-M5:  ██████████░░░░░░░░░░░░░ │                       │                       │
M6-M7:  ░░░░██████░░░░░░░░░░░░░ │                       │                       │
M8:Auth:░░░░░░████░░░░░░░░░░░░░ │                       │                       │
M9:POS: ░░░░░░░░██████░░░░░░░░░ │                       │                       │
M10:Inv:░░░░░░░░░░████░░░░░░░░░ │                       │                       │
M11:Reg:░░░░░░░░░░░░████░░░░░░░ │                       │                       │
M12:Rpt:░░░░░░░░░░░░░░██████░░░ │░░░░████░░░░░░░░░░░░░ │                       │
M13:CRM:                        │██████░░░░░░░░░░░░░░░ │                       │
M14:Cpn:                        │░░████░░░░░░░░░░░░░░░ │                       │
M15:Mst:                        │░░░░░░██████░░░░░░░░░ │                       │
M16:Exp:                        │░░░░░░░░░░████░░░░░░░ │                       │
M17:Stf:                        │                       │██████░░░░░░░░░░░░░░░ │
M19:Adm:                        │                       │░░░░░░██████░░░░░░░░░ │
M20:Med:                        │                       │░░░░░░░░░░████░░░░░░░ │
E-Inv:                          │                       │░░░░░░░░░░░░░░████░░░ │
```

---

## 15. Tech Stack & Dependencies

### 15.1 Core Stack

> **Source of truth: `gradle/libs.versions.toml` — update this section when versions.toml changes.**

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Kotlin | 2.3.0 | Primary language |
| Build | Gradle (KTS) | 8.5+ | Build system |
| KMP Plugin | kotlin-multiplatform | 2.3.0 | Cross-platform compilation |
| UI | Compose Multiplatform | 1.10.0 | Shared UI framework |
| Design | Material 3 | Latest | Design system |
| Navigation | Compose Navigation | Latest | Type-safe routing |
| DI | Koin | 4.0.4 | Dependency injection |
| Networking | Ktor Client | 3.0.3 | HTTP client |
| Serialization | Kotlinx.Serialization | 1.8.0 | JSON/CBOR |
| Local DB | SQLDelight | 2.0.2 | Type-safe SQL |
| Encryption | SQLCipher | 4.5.4 | Database encryption |
| Async | Kotlinx.Coroutines | 1.10.2 | Concurrency |
| DateTime | Kotlinx.DateTime | 0.6.1 | Cross-platform dates |
| Image Loading | Coil (Compose) | 3.0.4 | Async image loading |
| Logging | Kermit | 2.0.4 | KMP-native logging |
| Testing | Kotlin Test + Mockative | Latest | Cross-platform tests |

### 15.2 Platform-Specific

| Platform | Technology | Purpose |
|----------|-----------|---------|
| Android | Android SDK 24+ | Min API level |
| Android | Android Keystore | Secure key storage |
| Android | ML Kit | Camera barcode scanning |
| Android | USB Host API | Printer/scanner USB |
| Desktop | JVM 17+ | Runtime |
| Desktop | JCE KeyStore | Secure key storage |
| Desktop | jSerialComm | Serial port access |
| Desktop | javax.usb / libusb4j | USB device access |

---

## 16. Risk Matrix & Mitigation

| # | Risk | Probability | Impact | Mitigation |
|---|------|------------|--------|------------|
| R1 | Offline sync conflicts cause data loss | Medium | Critical | CRDT implementation, conflict audit log, manual override UI |
| R2 | Hardware driver fragmentation | High | High | HAL abstraction, adapter pattern, vendor-specific modules |
| R3 | SQLCipher performance on large datasets | Medium | Medium | Indexed queries, pagination, background sync, WAL mode |
| R4 | KMP library ecosystem gaps | Medium | Medium | expect/actual bridges, JVM fallback for desktop |
| R5 | PCI-DSS compliance complexity | Low | Critical | Minimize card data scope (tokenization), SAQ-B only |
| R6 | E-Invoicing spec changes (SL 2026) | Medium | High | Abstracted invoice generation, configurable templates |
| R7 | Multi-store real-time sync latency | Medium | Medium | WebSocket for critical data, eventual consistency for rest |
| R8 | Build time regression with module growth | High | Medium | Gradle build cache, module boundaries, CI parallelism |

---

## 17. Performance & Scalability Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| App cold start | < 3 seconds | Time to interactive POS screen |
| Product search | < 200ms | FTS5 query on 50K products |
| Add to cart | < 50ms | UI response time |
| Payment processing | < 800ms | From tap to receipt trigger |
| Receipt printing | < 2 seconds | ESC/POS command execution |
| Sync cycle (incremental) | < 5 seconds | For 100 pending operations |
| Database size (100K products) | < 500MB | With images cached |
| Memory usage (POS active) | < 256MB | Android mid-range device |
| Concurrent users per store | 5+ | Cash registers active |
| Total stores supported | 50+ | Central dashboard responsive |

---

## 18. Compliance & Governance

### 18.1 PCI-DSS (Payment Card Industry)

| Requirement | Implementation |
|------------|---------------|
| Encrypt cardholder data | Tokenization via payment gateway (no local card storage) |
| Restrict access | RBAC with payment-specific permissions |
| Maintain security policy | Documented in security architecture |
| Regular testing | Automated security scans in CI |

### 18.2 GDPR / Data Protection

| Right | Implementation |
|-------|---------------|
| Right to Access | Customer data export API |
| Right to Erasure | Cascading soft-delete with anonymization |
| Right to Rectification | Customer profile editing |
| Data Portability | JSON/CSV export of customer data |
| Data Minimization | Only collect necessary fields |
| Consent Management | Opt-in for marketing communications |

### 18.3 Sri Lanka E-Invoicing (2026)

| Aspect | Implementation |
|--------|---------------|
| Invoice Format | Configurable template engine |
| Digital Signature | Government-approved certificate |
| Real-time Reporting | API integration with tax authority |
| Audit Trail | Immutable invoice log |

### 18.4 Audit Trail Strategy

```
Every security-sensitive operation generates an AuditLog entry:

{
  "id": "uuid",
  "timestamp": "ISO-8601",
  "user_id": "uuid",
  "store_id": "uuid",
  "action": "ORDER_VOIDED",
  "entity_type": "Order",
  "entity_id": "uuid",
  "old_value": { ... },
  "new_value": { ... },
  "ip_address": "...",
  "device_id": "...",
  "metadata": { "reason": "Customer request" }
}

Retention: 7 years (financial), 3 years (operational)
Storage: Append-only, tamper-evident (hash chain)
```

---

## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| CRDT | Conflict-free Replicated Data Type — data structure that can be merged without conflicts |
| ESC/POS | Epson Standard Code for Point of Sale — thermal printer command protocol |
| HAL | Hardware Abstraction Layer — interface isolating hardware specifics from business logic |
| KMP | Kotlin Multiplatform — Kotlin's cross-platform development technology |
| MVI | Model-View-Intent — unidirectional state management pattern |
| SAQ-B | Self-Assessment Questionnaire B — PCI-DSS compliance level for imprint/standalone terminals |
| SQLCipher | Open-source extension to SQLite providing AES-256 encryption |
| SQLDelight | KMP-native SQL library with compile-time verification |

---

## Appendix B: Document Cross-References

| Source Document | Sections Informing This Plan |
|----------------|------------------------------|
| COMPLETE_POS_SYSTEM_-_FEATURES_TREE_MAP.txt | §5 (Feature Matrix), §4 (Module Decomposition), §14 (Roadmap) |
| SECURITY_ARCHITECTURE_OVERVIEW.txt | §6 (Security Architecture), §6.1 (Seven-Layer Model) |
| SECURITY_STRATEGY_FRAMEWORK.txt | §6 (Security Architecture), §18 (Compliance) |
| Security-Critical_Features.txt | §6.2 (Security-Critical Mapping), §5.1 (Auth Features) |

---

*End of Master Blueprint — ZyntaPOS v1.0*
