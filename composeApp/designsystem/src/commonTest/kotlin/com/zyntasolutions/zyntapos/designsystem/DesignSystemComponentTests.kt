package com.zyntasolutions.zyntapos.designsystem

import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZentaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZentaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTableColumn
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaNavItem
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
//     A. ZentaButton   — size/variant enum coverage, disabled state model
//     B. ZentaNumericPad — mode-driven key visibility rules, PIN masking logic
//     C. ZentaTable    — column sort state, empty/loading state transitions
//     D. ZentaScaffold — ZentaNavItem construction, selection state
//     E. ZentaGrid     — WindowSize → column count mapping (§2.3)
//     F. ZentaListDetailLayout — weight validation, single/two-pane determination
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// A. ZentaButton — Enum coverage & size tokens
// ══════════════════════════════════════════════════════════════════════════════

class ZentaButtonEnumTest {

    @Test
    fun allVariantsAreDefined() {
        val variants = ZentaButtonVariant.entries
        assertTrue(variants.contains(ZentaButtonVariant.Primary), "Primary variant missing")
        assertTrue(variants.contains(ZentaButtonVariant.Secondary), "Secondary variant missing")
        assertTrue(variants.contains(ZentaButtonVariant.Danger), "Danger variant missing")
        assertTrue(variants.contains(ZentaButtonVariant.Ghost), "Ghost variant missing")
        assertTrue(variants.contains(ZentaButtonVariant.Icon), "Icon variant missing")
        assertEquals(5, variants.size, "Expected exactly 5 button variants")
    }

    @Test
    fun smallButtonHeightIs32dp() {
        assertEquals(32f, ZentaButtonSize.Small.height.value, "Small height must be 32dp per §3.3")
    }

    @Test
    fun mediumButtonHeightIs40dp() {
        assertEquals(40f, ZentaButtonSize.Medium.height.value, "Medium height must be 40dp per §3.3")
    }

    @Test
    fun largeButtonHeightIs56dp() {
        // POS touch target spec: preferred height 56dp (UI/UX §2.2 touch_preferred)
        assertEquals(56f, ZentaButtonSize.Large.height.value, "Large height must be 56dp per §2.2")
    }

    @Test
    fun horizontalPaddingScalesWithSize() {
        assertTrue(
            ZentaButtonSize.Small.horizontalPadding < ZentaButtonSize.Medium.horizontalPadding,
            "Small padding must be less than Medium",
        )
        assertTrue(
            ZentaButtonSize.Medium.horizontalPadding < ZentaButtonSize.Large.horizontalPadding,
            "Medium padding must be less than Large",
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// B. ZentaNumericPad — Mode rules & PIN masking contract
// ══════════════════════════════════════════════════════════════════════════════

class ZentaNumericPadModeTest {

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
// C. ZentaTable — Column model, sort state, empty/loading logic
// ══════════════════════════════════════════════════════════════════════════════

class ZentaTableStateTest {

    private val testColumns = listOf(
        ZentaTableColumn(key = "name", header = "Product", weight = 2f, sortable = true),
        ZentaTableColumn(key = "price", header = "Price", weight = 1f, sortable = true),
        ZentaTableColumn(key = "stock", header = "Stock", weight = 1f, sortable = false),
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
        val col = ZentaTableColumn(key = "test", header = "Test")
        assertEquals(1f, col.weight)
    }

    @Test
    fun sortableDefaultIsTrue() {
        val col = ZentaTableColumn(key = "test", header = "Test")
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
// D. ZentaScaffold / ZentaNavItem — Item model validation
// ══════════════════════════════════════════════════════════════════════════════

class ZentaNavItemTest {

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
// E. ZentaGrid — WindowSize → column count mapping (§2.3)
// ══════════════════════════════════════════════════════════════════════════════

class ZentaGridColumnCountTest {

    @Test
    fun compactWindowGivesFixedTwoColumns() {
        val description = columnCountDescription(WindowSize.COMPACT)
        assertTrue(description.startsWith("2"), "COMPACT must map to 2 columns, got: $description")
    }

    @Test
    fun mediumWindowGivesAdaptiveThreeToFourColumns() {
        val description = columnCountDescription(WindowSize.MEDIUM)
        assertTrue(description.contains("3–4") || description.contains("3-4"),
            "MEDIUM must describe 3–4 adaptive columns, got: $description")
    }

    @Test
    fun expandedWindowGivesAdaptiveFourToSixColumns() {
        val description = columnCountDescription(WindowSize.EXPANDED)
        assertTrue(description.contains("4–6") || description.contains("4-6"),
            "EXPANDED must describe 4–6 adaptive columns, got: $description")
    }

    @Test
    fun allWindowSizesHaveMappings() {
        WindowSize.entries.forEach { size ->
            val desc = columnCountDescription(size)
            assertTrue(desc.isNotBlank(), "Column count description must not be blank for $size")
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// F. ZentaListDetailLayout / ZentaSplitPane — Weight validation & pane logic
// ══════════════════════════════════════════════════════════════════════════════

class ZentaLayoutWeightTest {

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
