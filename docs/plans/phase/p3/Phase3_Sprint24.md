# ZyntaPOS — Phase 3 Sprint 24: Integration QA & Release v2.0.0

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT24-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 24 of 24 | Week 24
> **Module(s):** All
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0

---

## Goal

End-to-end validation of all Phase 3 features, performance target verification, full CI pipeline passing, version bump to 2.0.0, and tagged release. This sprint is primarily QA and release engineering — no new features are added.

---

## Pre-Release Checklist

### 1. E2E Test Flows

Run the following manual + automated test flows:

#### Flow 24.1 — Staff Payroll (End-to-End)

```
1. Create employee: John Silva, position=Cashier, salary=80000, salaryType=MONTHLY, commissionRate=2%
2. Clock in at 08:00, clock out at 17:30 → expect totalHours=9.5, overtimeHours=1.5
3. Do this for 22 working days in February 2026
4. Generate payroll for Feb 2026:
   - baseSalary = 80,000 (monthly, fixed)
   - overtimePay = 1.5h/day × 22 days × (80000/160) × 1.5 = 18,562.50
   - commission = sum(February cashier orders × 0.02)
   - netPay = grossPay - deductions
5. Mark payroll as PAID with paymentRef="TXN-2026-02-JOHN"
6. Verify: payroll status = PAID, paidAt set, PayrollDetailScreen shows correct payslip
```

**Automated test:** `StaffPayrollE2ETest.kt` — uses in-memory SQLDelight

#### Flow 24.2 — Leave Approval (End-to-End)

```
1. Employee submits sick leave: Feb 26–28, 2026
2. Manager (with APPROVE_LEAVE) approves leave
3. March payroll: attendance for Feb 26-28 should show LEAVE status
4. Payroll baseSalary should NOT deduct for LEAVE days (monthly salary = fixed)
5. Verify: LeaveRecord.status = APPROVED, attendanceSummary.leaveCount includes these days
```

#### Flow 24.3 — Media Pipeline (End-to-End)

```
1. Create a new product "Test Product"
2. From ProductFormScreen, tap "Add Image"
3. Pick a test image (640×480 JPG, 1.2MB)
4. In ImageCropScreen, select 1:1 aspect ratio, apply crop
5. Upload: image compressed to < 500KB, thumbnail generated
6. Verify: MediaFile.uploadStatus = UPLOADED, remoteUrl set
7. Product.imageUrl updated to remoteUrl
8. Image visible in POS product grid
```

#### Flow 24.4 — E-Invoice (End-to-End with Mock IRD)

```
1. Enable e-invoicing in settings (settings.einvoice.enabled = true)
2. Create an order: 2 products × LKR 1,000 each, 15% VAT
3. Process payment
4. Auto-trigger: GenerateEInvoiceUseCase creates invoice
5. SubmitEInvoiceUseCase (mock IRD): returns ACCEPTED
6. Receipt shows: "IRD Invoice: IRD-ZYN01-20260224-0001"
7. EInvoiceListScreen shows ACCEPTED invoice
8. ComplianceReportScreen shows: totalTaxCollected = LKR 300.00
```

**Mock IRD:** Use Ktor MockEngine returning 200 OK with `{"success":true,"invoice_id":"IRD_123","response_code":"IRD_200","message":"Accepted"}`

#### Flow 24.5 — Backup/Restore (End-to-End)

```
1. Create 5 products, 3 customers, 2 orders
2. From AdminDashboardScreen → BackupManagement → "Create Backup"
3. Verify: backup file created, isEncrypted = true, status = SUCCESS, size > 0
4. Delete 2 products
5. Restore backup (confirm dialog)
6. App restarts (mock in test)
7. Verify: all 5 products present, encrypted backup file decrypts correctly
```

#### Flow 24.6 — Custom Role RBAC (End-to-End)

```
1. Admin creates custom role "POS Only" with permissions:
   [POS_ACCESS, CREATE_ORDER, PROCESS_PAYMENT]
2. Assign "POS Only" role to user "testcashier"
3. Login as testcashier
4. Verify: POS screen accessible
5. Verify: navigate to void order → RBAC blocks (VOID_ORDER not in role)
6. Verify: navigate to Inventory → RBAC blocks (VIEW_INVENTORY not in role)
7. Verify: nav items show only POS (no Staff, Admin, Reports, etc.)
```

**Automated test:** `CustomRbacE2ETest.kt` in `shared/security/src/commonTest/`

#### Flow 24.7 — Rack Pick List (End-to-End)

```
1. Warehouse: "Main Warehouse"
2. Create racks: A1, A2, B1, B2
3. Assign products: Product-1 → A1 (qty 50), Product-2 → A2 (qty 30), Product-3 → B1 (qty 10)
4. Generate pick list: [Product-1:5, Product-2:35, Product-3:10, Product-4:2]
5. Verify pick list order: A1 first, A2 second, B1 third, missing product last
6. Verify: Product-2 canFulfill=false (30 available, 35 requested) → shown in red
7. Verify: Product-4 not in warehouse → excluded from list
```

#### Flow 24.8 — Advanced Analytics (Performance Test)

```
1. Seed 365 days × 10 orders/day = 3,650 orders with random amounts
2. Run GetSalesTrendUseCase(storeId, PeriodType.MONTHLY, 12)
3. Measure time: must complete in < 3,000ms
4. Verify: 12 TrendDataPoint objects returned
5. Verify: 3 Forecast objects with decreasing confidence
6. Run GetHourlyHeatmapUseCase for same 365 days
7. Measure time: must complete in < 3,000ms
8. Verify: 168 HeatmapCell objects (7 days × 24 hours)
```

---

## Automated Test Suite

### Full Test Run Commands

```bash
# Run all unit + integration tests in parallel
./gradlew clean test --parallel --continue

# Domain-specific (highest coverage targets)
./gradlew :shared:domain:test
./gradlew :shared:security:test
./gradlew :shared:data:jvmTest

# Feature ViewModels
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:feature:admin:test
./gradlew :composeApp:feature:media:test
./gradlew :composeApp:feature:reports:test
./gradlew :composeApp:feature:settings:test

# E2E tests
./gradlew :shared:data:jvmTest --tests "*.E2ETest"
```

### Coverage Targets Verification

| Module | Target | Verification Command |
|--------|--------|---------------------|
| `:shared:domain` (payroll calc) | 95% | `./gradlew :shared:domain:koverReport` |
| `:shared:domain` (e-invoice gen) | 95% | (same) |
| `:shared:security` (custom RBAC) | 95% | `./gradlew :shared:security:koverReport` |
| `:shared:data` (IRD API mock) | 85% | `./gradlew :shared:data:koverReport` |
| ViewModels (Staff, Admin, Media) | 80% | (same as feature test) |

---

## Code Quality

```bash
# Static analysis — must pass with 0 new violations
./gradlew detekt

# Android Lint
./gradlew lint

# Full quality check
./gradlew clean test lint detekt --parallel --continue --stacktrace
```

---

## Build

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Android release APK (requires signing config in local.properties)
./gradlew :androidApp:assembleRelease

# Desktop distributable (current OS)
./gradlew :composeApp:packageDistributionForCurrentOS

# Desktop uber JAR
./gradlew :composeApp:packageUberJarForCurrentOS
```

---

## Version Bump

### `version.properties`

```properties
VERSION_NAME=2.0.0
VERSION_CODE=2
BUILD=3
```

Update in `build.gradle.kts`:

```kotlin
val versionName = "2.0.0"
val versionCode = 2
```

---

## Execution Log Update

Update `docs/ai_workflows/execution_log.md` with Phase 3 completion:

```markdown
## Phase 3 — Enterprise (Months 13–18) ✅ COMPLETE

| Sprint | Goal | Status | Date |
|--------|------|--------|------|
| Sprint 1  | Staff & HR SQLDelight schema (5 tables + migration v5) | ✅ | Week 1 |
| Sprint 2  | Media + Accounting + Infrastructure schema (4 tables + migration v6) | ✅ | Week 2 |
| Sprint 3  | Staff & HR domain models + 18 use case interfaces | ✅ | Week 3 |
| Sprint 4  | Media + Admin + E-Invoice domain models + 24 use case interfaces | ✅ | Week 4 |
| Sprint 5  | Staff & HR repository implementations + use cases | ✅ | Week 5 |
| Sprint 6  | Media repository + ImageProcessor HAL (Android/Desktop) | ✅ | Week 6 |
| Sprint 7  | Admin + Accounting repos + Phase 3 navigation (30 routes) | ✅ | Week 7 |
| Sprint 8  | Staff feature: Employee CRUD screens + MVI scaffold | ✅ | Week 8 |
| Sprint 9  | Staff feature: Attendance clock-in/out + daily log | ✅ | Week 9 |
| Sprint 10 | Staff feature: Leave management + approval workflow | ✅ | Week 10 |
| Sprint 11 | Staff feature: Shift scheduling + ZyntaWeekCalendar | ✅ | Week 11 |
| Sprint 12 | Staff feature: Payroll + payslip view + commission | ✅ | Week 12 |
| Sprint 13 | Admin feature: System health dashboard + audit log viewer | ✅ | Week 13 |
| Sprint 14 | Admin feature: Database management + backup/restore | ✅ | Week 14 |
| Sprint 15 | Admin feature: Module control + developer console | ✅ | Week 15 |
| Sprint 16 | Media feature: Image picker + crop + compression | ✅ | Week 16 |
| Sprint 17 | Media feature: Media library + entity assignment | ✅ | Week 17 |
| Sprint 18 | E-Invoice: IRD API client + certificate management | ✅ | Week 18 |
| Sprint 19 | E-Invoice: Generation engine + SHA-256 signature | ✅ | Week 19 |
| Sprint 20 | E-Invoice: IRD submission + compliance reports + POS integration | ✅ | Week 20 |
| Sprint 21 | Warehouse racks CRUD + pick list generation | ✅ | Week 21 |
| Sprint 22 | Advanced analytics: trend, heatmap, product performance | ✅ | Week 22 |
| Sprint 23 | Custom RBAC role editor + full SI/TA translations | ✅ | Week 23 |
| Sprint 24 | Integration QA + release v2.0.0 | ✅ | Week 24 |

**Phase 3 Deliverables:**
- M17 :composeApp:feature:staff — Employee management, attendance, leave, shifts, payroll ✅
- M19 :composeApp:feature:admin — System health, audit log, backup/restore, module control ✅
- M20 :composeApp:feature:media — Image picker, crop, compression, media library ✅
- E-Invoicing — IRD API client, generation engine, digital signature, compliance reports ✅
- Warehouse Racks Manager — CRUD, product location, pick list generation ✅
- Advanced Analytics — Sales trend (linear regression), hourly heatmap, product performance ✅
- Custom RBAC — Role editor with permission tree, tri-state module toggles ✅
- Full i18n — Complete Sinhala (SI) and Tamil (TA) translations (800+ keys) ✅
- DB Migrations: v5 (Staff), v6 (Media+Accounting+Racks), v7 (rack_id column) ✅
- Version: 2.0.0 / BUILD=3 ✅
```

---

## Git Tag & Release

```bash
# Verify all tests pass
./gradlew clean test lint detekt --parallel --continue

# Version bump commit
git add version.properties
git commit -m "build(gradle): bump version to 2.0.0 for Phase 3 release"

# Tag release
git tag v2.0.0 -m "ZyntaPOS v2.0.0 — Phase 3 Enterprise Release"
git push origin v2.0.0
```

---

## Performance Targets Final Verification

| Metric | Target | Test Method |
|--------|--------|------------|
| Payroll generation (50 employees, 1 month) | < 2s | `PayrollPerformanceTest` |
| Audit log query (10K rows, filtered) | < 500ms | `AuditLogPerformanceTest` |
| Media upload + compress + thumbnail | < 3s total | `MediaPipelineTest` (jvmTest) |
| E-Invoice generation + submit (mock) | < 5s | `EInvoiceE2ETest` |
| Pick list for 100 products | < 500ms | `PickListPerformanceTest` |
| Advanced analytics (12-month trend) | < 3s | `AnalyticsTrendPerformanceTest` |

---

## Tasks

- [ ] **24.1** Execute Staff Payroll E2E flow and fix any failures
- [ ] **24.2** Execute Leave Approval E2E flow and fix any failures
- [ ] **24.3** Execute Media Pipeline E2E flow and fix any failures
- [ ] **24.4** Execute E-Invoice E2E flow with Ktor MockEngine IRD and fix any failures
- [ ] **24.5** Execute Backup/Restore E2E flow and fix any failures
- [ ] **24.6** Execute Custom RBAC E2E flow (`CustomRbacE2ETest.kt`) and fix any failures
- [ ] **24.7** Execute Rack Pick List E2E flow and fix any failures
- [ ] **24.8** Run advanced analytics performance test (12-month trend < 3s)
- [ ] **24.9** Run full test suite: `./gradlew clean test lint detekt --parallel --continue`
- [ ] **24.10** Fix any Detekt violations introduced in Phase 3 sprints
- [ ] **24.11** Fix any Android Lint warnings at warning level or above
- [ ] **24.12** Build Android release APK: `./gradlew :androidApp:assembleRelease`
- [ ] **24.13** Build Desktop distributable: `./gradlew :composeApp:packageDistributionForCurrentOS`
- [ ] **24.14** Bump version: `version.properties` → VERSION_NAME=2.0.0, BUILD=3
- [ ] **24.15** Update `docs/ai_workflows/execution_log.md` with Phase 3 completion table
- [ ] **24.16** Create git tag `v2.0.0`

---

## Verification Commands (Final)

```bash
# Full CI pipeline (matches .github/workflows/ci.yml)
./gradlew clean test testDebugUnitTest jvmTest lint assembleDebug \
          :composeApp:packageUberJarForCurrentOS \
          --parallel --continue --stacktrace

# Coverage reports
./gradlew :shared:domain:koverReport
./gradlew :shared:security:koverReport

# Release builds
./gradlew :androidApp:assembleRelease
./gradlew :composeApp:packageDistributionForCurrentOS

# Tag
git tag v2.0.0 -m "ZyntaPOS v2.0.0 — Phase 3 Enterprise Release"
```

---

## Definition of Done (Phase 3)

- [ ] All 7 E2E test flows pass (Staff Payroll, Leave, Media, E-Invoice, Backup, RBAC, Racks)
- [ ] All coverage targets met (domain ≥ 95%, ViewModels ≥ 80%, IRD API ≥ 85%)
- [ ] `./gradlew detekt` passes with 0 new violations
- [ ] `./gradlew lint` passes with 0 new errors
- [ ] Android Release APK built successfully (signed)
- [ ] Desktop distributable built successfully
- [ ] `version.properties` bumped to 2.0.0 / BUILD=3
- [ ] `docs/ai_workflows/execution_log.md` updated with Phase 3 completion
- [ ] Git tag `v2.0.0` created
- [ ] Commit: `build(gradle): bump version to 2.0.0 for Phase 3 release`

---

## Phase 3 Summary

**New Modules Delivered:**
- M17: `:composeApp:feature:staff` — 5 screen groups, 20+ composables
- M19: `:composeApp:feature:admin` — 5 screens, system health + backup
- M20: `:composeApp:feature:media` — 4 screens, ImageProcessor HAL

**New Database Tables:** 9 (employees, attendance, leave, payroll, shifts, media_files, warehouse_racks, accounting_entries, version_vectors) + 1 column addition (stock_entries.rack_id)

**New Domain Models:** 25+ (Staff, Media, Admin, E-Invoice, Analytics, Racks, RBAC)

**New Use Cases:** 70+ (Staff:18, Media:3, Admin:8, E-Invoice:5, Racks:5, Analytics:5, RBAC:5 + implementations)

**New Design System Components:** ZyntaWeekCalendar, ZyntaLineChart, ZyntaBarChart, ZyntaHeatmapGrid, ZyntaImageCropper, ZyntaImageCard, ZyntaStatChip, ZyntaStatusBadge, ZyntaDateField, ZyntaTimeField

**New Navigation Routes:** 30+ routes across Staff, Admin, Media, E-Invoice, Racks, Analytics, Settings

**DB Migration Chain:** v4 (Phase 2) → v5 (Staff) → v6 (Media+Accounting) → v7 (rack_id) = **DB version 7**

**i18n:** Complete Sinhala + Tamil translations (800+ keys), Noto Sans font loading

**Release:** ZyntaPOS v2.0.0 / BUILD=3
