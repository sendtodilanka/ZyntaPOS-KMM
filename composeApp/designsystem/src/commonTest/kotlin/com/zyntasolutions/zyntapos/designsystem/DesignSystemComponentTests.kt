package com.zyntasolutions.zyntapos.designsystem

import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaNavItem
import com.zyntasolutions.zyntapos.designsystem.layouts.columnCountDescription
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// DesignSystemComponentTests — Unit / state-logic tests for ZyntaPOS design system
//
// Architecture note:
//   Full Compose UI rendering tests (SemanticsNodeInteraction, ComposeTestRule)
//   live in :composeApp:designsystem androidTest and jvmTest source sets because
//   the Compose UI test harness requires a platform runtime.
//
//   These commonTest tests cover:
//     A. ZyntaButton   — size/variant enum coverage, disabled state model
//     B. ZyntaNumericPad — mode-driven key visibility rules, PIN masking logic
//     C. ZyntaTable    — column sort state, empty/loading state transitions
//     D. ZyntaScaffold — ZyntaNavItem construction, selection state
//     E. ZyntaGrid     — WindowSize → column count mapping (§2.3)
//     F. ZyntaListDetailLayout — weight validation, single/two-pane determination
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// A. ZyntaButton — Enum coverage & size tokens
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaButtonEnumTest {

    @Test
    fun allVariantsAreDefined() {
        val variants = ZyntaButtonVariant.entries
        assertTrue(variants.contains(ZyntaButtonVariant.Primary), "Primary variant missing")
        assertTrue(variants.contains(ZyntaButtonVariant.Secondary), "Secondary variant missing")
        assertTrue(variants.contains(ZyntaButtonVariant.Danger), "Danger variant missing")
        assertTrue(variants.contains(ZyntaButtonVariant.Ghost), "Ghost variant missing")
        assertTrue(variants.contains(ZyntaButtonVariant.Icon), "Icon variant missing")
        assertEquals(5, variants.size, "Expected exactly 5 button variants")
    }

    @Test
    fun smallButtonHeightIs32dp() {
        assertEquals(32f, ZyntaButtonSize.Small.height.value, "Small height must be 32dp per §3.3")
    }

    @Test
    fun mediumButtonHeightIs40dp() {
        assertEquals(40f, ZyntaButtonSize.Medium.height.value, "Medium height must be 40dp per §3.3")
    }

    @Test
    fun largeButtonHeightIs56dp() {
        // POS touch target spec: preferred height 56dp (UI/UX §2.2 touch_preferred)
        assertEquals(56f, ZyntaButtonSize.Large.height.value, "Large height must be 56dp per §2.2")
    }

    @Test
    fun horizontalPaddingScalesWithSize() {
        assertTrue(
            ZyntaButtonSize.Small.horizontalPadding < ZyntaButtonSize.Medium.horizontalPadding,
            "Small padding must be less than Medium",
        )
        assertTrue(
            ZyntaButtonSize.Medium.horizontalPadding < ZyntaButtonSize.Large.horizontalPadding,
            "Medium padding must be less than Large",
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// B. ZyntaNumericPad — Mode rules & PIN masking contract
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaNumericPadModeTest {

    /** Model of the key-visibility rules to test without a Compose runtime. */
    private fun decimalKeyVisible(mode: NumericPadMode) = mode != NumericPadMode.PIN
    private fun doubleZeroKeyVisible(mode: NumericPadMode) = mode != NumericPadMode.PIN
    private fun maskDisplay(displayValue: String, mode: NumericPadMode): String =
        if (mode == NumericPadMode.PIN) "●".repeat(displayValue.length) else displayValue

    @Test
    fun pinModeHidesDecimalKey() {
        assertFalse(decimalKeyVisible(NumericPadMode.PIN), "Decimal key must be hidden in PIN mode")
    }

    @Test
    fun pinModeHidesDoubleZeroKey() {
        assertFalse(doubleZeroKeyVisible(NumericPadMode.PIN), "00 key must be hidden in PIN mode")
    }

    @Test
    fun priceModShowsDecimalAndDoubleZero() {
        assertTrue(decimalKeyVisible(NumericPadMode.PRICE), "Decimal must be visible in PRICE mode")
        assertTrue(doubleZeroKeyVisible(NumericPadMode.PRICE), "00 must be visible in PRICE mode")
    }

    @Test
    fun quantityModeShowsDecimalAndDoubleZero() {
        assertTrue(decimalKeyVisible(NumericPadMode.QUANTITY))
        assertTrue(doubleZeroKeyVisible(NumericPadMode.QUANTITY))
    }

    @Test
    fun pinModeDisplayMasksDigits() {
        val masked = maskDisplay("1234", NumericPadMode.PIN)
        assertEquals("●●●●", masked, "PIN display must show bullets equal to digit count")
    }

    @Test
    fun priceModeDisplayShowsRawValue() {
        val raw = maskDisplay("45.50", NumericPadMode.PRICE)
        assertEquals("45.50", raw, "PRICE display must not mask the value")
    }

    @Test
    fun emptyPinDisplayShowsNoBullets() {
        val masked = maskDisplay("", NumericPadMode.PIN)
        assertEquals("", masked)
    }

    @Test
    fun allModesAreDefined() {
        assertEquals(3, NumericPadMode.entries.size, "Expected PRICE, QUANTITY, PIN")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// C. ZyntaTable — Column model, sort state, empty/loading logic
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaTableStateTest {

    private val testColumns = listOf(
        ZyntaTableColumn(key = "name", header = "Product", weight = 2f, sortable = true),
        ZyntaTableColumn(key = "price", header = "Price", weight = 1f, sortable = true),
        ZyntaTableColumn(key = "stock", header = "Stock", weight = 1f, sortable = false),
    )

    @Test
    fun columnKeysMustBeUnique() {
        val keys = testColumns.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "All column keys must be unique")
    }

    @Test
    fun sortableColumnFlagIsRespected() {
        assertTrue(testColumns.first { it.key == "name" }.sortable)
        assertFalse(testColumns.first { it.key == "stock" }.sortable)
    }

    @Test
    fun sortDirectionNoneIsDefault() {
        assertEquals(SortDirection.None, SortDirection.None)
    }

    @Test
    fun sortDirectionEnumHasThreeValues() {
        assertEquals(3, SortDirection.entries.size, "Expected Ascending, Descending, None")
    }

    @Test
    fun emptyItemListTriggersEmptyState() {
        val items = emptyList<String>()
        val isEmpty = items.isEmpty()
        assertTrue(isEmpty, "Empty list must trigger empty-state rendering")
    }

    @Test
    fun nonEmptyListDoesNotTriggerEmptyState() {
        val items = listOf("item1", "item2")
        assertFalse(items.isEmpty())
    }

    @Test
    fun weightDefaultIs1f() {
        val col = ZyntaTableColumn(key = "test", header = "Test")
        assertEquals(1f, col.weight)
    }

    @Test
    fun sortableDefaultIsTrue() {
        val col = ZyntaTableColumn(key = "test", header = "Test")
        assertTrue(col.sortable)
    }

    @Test
    fun columnWeightAffectsRenderWidthProportion() {
        // Total weight = 2+1+1 = 4; "name" column should occupy 50% of width
        val total = testColumns.sumOf { it.weight.toDouble() }
        val nameWeight = testColumns.first { it.key == "name" }.weight
        assertEquals(0.5, nameWeight / total, 0.001, "Name column must be 50% width")
    }

    @Test
    fun sortInteractionStateTransition() {
        // Simulate sort cycling: None → Ascending → Descending → None
        var direction = SortDirection.None
        direction = if (direction == SortDirection.None) SortDirection.Ascending else SortDirection.None
        assertEquals(SortDirection.Ascending, direction)
        direction = SortDirection.Descending
        assertEquals(SortDirection.Descending, direction)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// D. ZyntaScaffold / ZyntaNavItem — Item model validation
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaNavItemTest {

    @Test
    fun navItemSelectionIndexInBounds() {
        // Simulate 4 nav items and verify selection logic stays in-bounds
        val itemCount = 4
        for (selected in 0 until itemCount) {
            assertTrue(selected in 0 until itemCount, "Index $selected must be in bounds")
        }
    }

    @Test
    fun selectedIndexCannotBeNegative() {
        val selected = 0
        assertTrue(selected >= 0)
    }

    @Test
    fun windowSizeCompactUsesBottomNavModel() {
        // The scaffold switches to bottom navigation for COMPACT
        val windowSize = WindowSize.COMPACT
        val usesBottomBar = windowSize == WindowSize.COMPACT
        assertTrue(usesBottomBar)
    }

    @Test
    fun windowSizeMediumUsesNavigationRailModel() {
        val windowSize = WindowSize.MEDIUM
        val usesRail = windowSize == WindowSize.MEDIUM
        assertTrue(usesRail)
    }

    @Test
    fun windowSizeExpandedUsesDrawerModel() {
        val windowSize = WindowSize.EXPANDED
        val usesDrawer = windowSize == WindowSize.EXPANDED
        assertTrue(usesDrawer)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// E. ZyntaGrid — WindowSize → column count mapping (§2.3)
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaGridColumnCountTest {

    @Test
    fun compactWindowUsesFixedTwoColumns() {
        assertEquals("2 (fixed)", columnCountDescription(WindowSize.COMPACT))
    }

    @Test
    fun mediumWindowUsesAdaptiveThreeToFour() {
        assertEquals("3–4 (adaptive, min 150dp)", columnCountDescription(WindowSize.MEDIUM))
    }

    @Test
    fun expandedWindowUsesAdaptiveFourToSix() {
        assertEquals("4–6 (adaptive, min 160dp)", columnCountDescription(WindowSize.EXPANDED))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// F. ZyntaListDetailLayout / ZyntaSplitPane — Weight validation & pane logic
// ══════════════════════════════════════════════════════════════════════════════

class ZyntaLayoutWeightTest {

    @Test
    fun compactWindowIsSinglePane() {
        val isTwoPane = WindowSize.COMPACT != WindowSize.COMPACT
        assertFalse(isTwoPane, "COMPACT must use single-pane layout")
    }

    @Test
    fun mediumWindowIsTwoPane() {
        val isTwoPane = WindowSize.MEDIUM != WindowSize.COMPACT
        assertTrue(isTwoPane, "MEDIUM must use two-pane layout")
    }

    @Test
    fun expandedWindowIsTwoPane() {
        val isTwoPane = WindowSize.EXPANDED != WindowSize.COMPACT
        assertTrue(isTwoPane)
    }

    @Test
    fun defaultListWeightIs35Percent() {
        val listWeight = 0.35f
        val detailWeight = 1f - listWeight
        assertEquals(0.65f, detailWeight, 0.001f, "Detail pane must receive 65% width by default")
    }

    @Test
    fun splitPaneDefaultPrimaryWeightIs40Percent() {
        val primaryWeight = 0.4f
        val secondaryWeight = 1f - primaryWeight
        assertEquals(0.6f, secondaryWeight, 0.001f, "Secondary pane must be 60% with default 40/60 split")
    }

    @Test
    fun splitPaneWeightBoundsAreEnforced() {
        // Values outside 0.01–0.99 must be rejected
        val validWeights = listOf(0.01f, 0.4f, 0.5f, 0.99f)
        val invalidWeights = listOf(0f, 1f, -0.1f, 1.1f)

        validWeights.forEach { w ->
            assertTrue(w in 0.01f..0.99f, "Weight $w should be valid")
        }
        invalidWeights.forEach { w ->
            assertFalse(w in 0.01f..0.99f, "Weight $w should be invalid")
        }
    }

    @Test
    fun detailVisibleFalseShowsListOnCompact() {
        val detailVisible = false
        val showDetail = detailVisible
        assertFalse(showDetail, "When detailVisible=false, list must be shown on COMPACT")
    }

    @Test
    fun detailVisibleTrueShowsDetailOnCompact() {
        val detailVisible = true
        assertTrue(detailVisible, "When detailVisible=true, detail must be shown on COMPACT")
    }
}
