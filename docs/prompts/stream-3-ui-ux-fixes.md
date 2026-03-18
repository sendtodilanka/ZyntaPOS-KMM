# Stream 3: UI/UX Fixes — REMAINING ITEMS ONLY

**Master Plan:** `todo/missing-features-implementation-plan.md` (Sections G16, G17, G18, G21)
**Size:** M-L (1-2 sessions) — all original Batch 1-4 items are DONE; remaining items are deferred/larger scope
**Conflict Risk:** LOW — touches KMM feature module UI files only
**Dependencies:** None — all items are independent

> **STATUS (2026-03-18):** All 12 original items are VERIFIED COMPLETE in the codebase:
>
> **Batch 1 (G18 Navigation Routes):** ✅ ALL 4 DONE
> - WarehouseRackList(warehouseId) — ZyntaRoute.kt:582-583
> - WarehouseRackDetail(rackId, warehouseId) — ZyntaRoute.kt:591-595
> - CategoryDetail(categoryId) — ZyntaRoute.kt:156-157
> - SupplierDetail(supplierId) — ZyntaRoute.kt:164-165
>
> **Batch 2 (G16 Multistore):** ✅ ALL 3 DONE
> - MS-1: ProductSearchDropdown in NewStockTransferScreen.kt:214-274
> - MS-2: warehouseMap lookup in StockTransferListScreen.kt:72-153
> - MS-4: TopAppBar back icon in WarehouseRackDetailScreen.kt:38-51
>
> **Batch 3 (G17 Inventory):** ✅ ALL 3 DONE
> - INV-4: AsyncImage preview in ProductDetailScreen.kt:392-404
> - INV-8: Search result count in ProductListScreen.kt:116-124
> - INV-9: BackHandler + AlertDialog in ProductDetailScreen.kt:61-84
>
> **Batch 4 (G21 Quick Wins):** ✅ BOTH DONE
> - Sparkline: DashboardViewModel.kt:61-68 (hourlyBuckets → todaySparkline)
> - Timezone offset: GeneralSettingsScreen.kt:60-96 (formatTimezoneWithOffset)

---

## ✅ COMPLETED — Do NOT re-implement

All items from the original prompt are done. See STATUS section above for exact file paths and line numbers.

---

## What's STILL MISSING (remaining G-series + INV/MS items)

### HIGH Priority — Phase 2 Must-Have

#### 1. INV-1: Barcode Scanner HAL Integration (HIGH)

**Problem:** `ProductDetailScreen` has QR icon with TODO comment. `StocktakeScreen` has scanner toggle but handler may be stubbed.

**Implementation:**
- Wire HAL `BarcodeScanner` interface from `:shared:hal`
- Implement `actual` for Android: ML Kit Vision barcode scanning
- Implement `actual` for JVM: HID keyboard emulation (USB scanner acts as keyboard)
- Connect to `ProductDetailScreen` (scan → lookup product by barcode)
- Connect to `StocktakeScreen` (scan → add item to stocktake)

**Key Files:**
- `shared/hal/src/commonMain/.../BarcodeScanner.kt` (interface exists)
- `shared/hal/src/androidMain/` (ML Kit actual)
- `shared/hal/src/jvmMain/` (HID actual)
- `composeApp/feature/inventory/.../ProductDetailScreen.kt`
- `composeApp/feature/inventory/.../stocktake/StocktakeScreen.kt`

#### 2. INV-2: Variant Persistence (HIGH)

**Problem:** Product variants are added/edited in UI form state but NEVER saved to domain. `CreateProductUseCase` and `UpdateProductUseCase` ignore variants.

**Implementation:**
- Add variant list to `CreateProductUseCase.Params` and `UpdateProductUseCase.Params`
- Persist via `product_variants.sq` table (schema already exists)
- Load variants when opening `ProductDetailScreen` for existing product
- Wire save in InventoryViewModel `SaveProduct` intent handler

**Key Files:**
- `shared/domain/src/commonMain/.../usecase/inventory/CreateProductUseCase.kt`
- `shared/domain/src/commonMain/.../usecase/inventory/UpdateProductUseCase.kt`
- `shared/data/src/commonMain/sqldelight/.../product_variants.sq`
- `composeApp/feature/inventory/.../InventoryViewModel.kt`

#### 3. INV-10: TaxGroup + UnitManagement Screens (MEDIUM)

**Problem:** `InventoryIntent.OpenTaxGroupDetail` and `InventoryIntent.OpenUnitManagement` exist but target screens don't exist.

**Implementation:**
- Create `TaxGroupScreen.kt` — CRUD form for tax groups (name, rate, isInclusive, isActive)
- Create `UnitManagementScreen.kt` — CRUD for units of measure (name, abbreviation, conversionRate)
- Add `TaxGroupList`, `TaxGroupDetail`, `UnitManagementList`, `UnitDetail` routes to `ZyntaRoute.kt`
- Register in `ZyntaNavGraph.kt`
- Follow MVI pattern with existing `InventoryViewModel` or create separate VMs

**Key Files:**
- `composeApp/navigation/.../ZyntaRoute.kt` (add 4 routes)
- `composeApp/feature/inventory/` (create 2 new screen files)
- `shared/data/src/commonMain/sqldelight/.../tax_groups.sq` (queries exist)
- `shared/data/src/commonMain/sqldelight/.../units_of_measure.sq` (queries exist)

#### 4. G21 Remaining Quick Wins (LOW-MEDIUM)

From the Phase 1.5 Quick Wins list, 3 items remain:

- [ ] **Printer test button** — Add visible "Test Print" button to PrinterSettingsScreen
- [ ] **Remember Me persistence** — Auth login collects checkbox but doesn't persist; wire to `SecurePreferences`
- [ ] **Employee name/badge on POS screen header** — Show logged-in user name/role in POS TopAppBar

### MEDIUM Priority — Multistore Polish

#### 5. MS-5: Warehouse Image/Logo (LOW)

- Add optional `imageUrl` field to `Warehouse` domain model
- Display `AsyncImage` in warehouse cards for visual identity

#### 6. MS-6: Rack Capacity Enforcement (LOW)

- Add stock-vs-capacity check in `WarehouseRepositoryImpl.commitTransfer()`
- Prevent overstocking beyond rack capacity limits

### Phase 2 Design System Components (G1)

These new components are needed before multi-store launch:

- [ ] `ZyntaStoreSelector` — active store picker in drawer footer + toolbar (CRITICAL)
- [ ] `ZyntaCurrencyPicker` — currency selection dropdown
- [ ] `ZyntaTimezonePicker` — timezone with UTC offset (partially exists in GeneralSettingsScreen)
- [ ] `ZyntaTransferStatusBadge` — transfer status states
- [ ] `ZyntaDateRangeSelector` — two-date picker for report filters
- [ ] `ZyntaLoyaltyBadge` — customer loyalty tier indicator

### Phase 2 Onboarding Steps (G2)

- [ ] Step 3: Currency & Timezone selection (defaults: LKR + Asia/Colombo)
- [ ] Step 4: Basic Tax Setup (optional)
- [ ] Step 5: Receipt Format (optional)

---

## Architecture Rules (MUST follow for ALL UI work)

- ALL screens use MVI: `State` + `Intent` + `Effect`
- Every ViewModel MUST extend `BaseViewModel<S,I,E>` (ADR-001)
- For new UI elements on existing screens: ADD new Intent — do NOT create new VMs
- Use `Zynta*` design system components (27+ exist)
- Use `CurrencyUtils`, `DateTimeUtils`, `ValidationUtils` from `:shared:core`
- Always add `contentDescription` to icons and images

---

## Pre-Implementation (MANDATORY)

1. Read `CLAUDE.md` fully
2. Read ADR-001 (BaseViewModel) and ADR-002 (domain naming)
3. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Commit + Push (per batch)

```bash
git fetch origin main && git merge origin/main --no-edit
git add composeApp/ shared/ todo/missing-features-implementation-plan.md
git commit -m "feat(inventory): wire barcode scanner HAL + variant persistence [INV-1, INV-2]

- BarcodeScanner actual implementations for Android (ML Kit) and JVM (HID)
- ProductDetailScreen scan → product lookup
- Variant list persisted via CreateProduct/UpdateProduct use cases

Plan file updated: INV-1, INV-2 marked complete"
git push -u origin $(git branch --show-current)
```
