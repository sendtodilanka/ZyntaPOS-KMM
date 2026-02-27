package com.zyntasolutions.zyntapos.feature.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import com.zyntasolutions.zyntapos.feature.dashboard.screen.DashboardScreenContent
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// DashboardScreenUiTest — Compose Desktop rendering tests for DashboardScreen.
//
// Tests call DashboardScreenContent() directly — the stateless, Koin-free
// composable extracted to enable testability. All tests use WindowSize.COMPACT
// for predictable single-column rendering.
//
// Run headlessly on Linux:
//   xvfb-run -a ./gradlew :composeApp:feature:dashboard:jvmTest --no-daemon
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTestApi::class)
class DashboardScreenUiTest {

    // ── State / domain helpers ────────────────────────────────────────────────

    private val formatter = CurrencyFormatter()
    private val epoch = Instant.fromEpochMilliseconds(0)

    private fun testUser(name: String = "John Doe") = User(
        id = "u1",
        name = name,
        email = "john@example.com",
        role = Role.CASHIER,
        storeId = "s1",
        createdAt = epoch,
        updatedAt = epoch,
    )

    private fun makeState(
        isLoading: Boolean = false,
        totalOrders: Long = 10L,
        lowStockCount: Long = 0L,
        lowStockNames: List<String> = emptyList(),
        activeRegisters: Long = 1L,
        recentOrders: List<RecentOrderItem> = emptyList(),
        currentUser: User? = testUser(),
    ) = DashboardState(
        isLoading = isLoading,
        totalOrders = totalOrders,
        lowStockCount = lowStockCount,
        lowStockNames = lowStockNames,
        activeRegisters = activeRegisters,
        recentOrders = recentOrders,
        currentUser = currentUser,
    )

    private fun content(
        state: DashboardState = makeState(),
        onNavigateToPos: () -> Unit = {},
        onNavigateToRegister: () -> Unit = {},
        onNavigateToReports: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onLogout: () -> Unit = {},
    ): @androidx.compose.runtime.Composable () -> Unit = {
        ZyntaTheme {
            DashboardScreenContent(
                state = state,
                currencyFormatter = formatter,
                windowSize = WindowSize.COMPACT,
                onNavigateToPos = onNavigateToPos,
                onNavigateToRegister = onNavigateToRegister,
                onNavigateToReports = onNavigateToReports,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToNotifications = {},
                onLogout = onLogout,
            )
        }
    }

    // ── KPI stat card labels ──────────────────────────────────────────────────

    @Test
    fun `compact layout shows Today's Sales KPI label`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Today's Sales").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Orders KPI label`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Orders").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Low Stock KPI label`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Low Stock").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Registers KPI label`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Registers").assertIsDisplayed()
    }

    // ── Quick action buttons visible ──────────────────────────────────────────

    @Test
    fun `compact layout shows New Sale quick action`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("New Sale").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Reports quick action`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Reports").assertIsDisplayed()
    }

    // ── Activity list ─────────────────────────────────────────────────────────

    @Test
    fun `shows empty activity card when no orders`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(recentOrders = emptyList())))
        onNodeWithText("No orders yet today").assertIsDisplayed()
    }

    @Test
    fun `shows order number text when recent orders present`() = runDesktopComposeUiTest {
        val order = RecentOrderItem(orderNumber = "ORD-001", total = 250.0, method = "CASH", timestamp = 0L)
        setContent(content(state = makeState(recentOrders = listOf(order))))
        onNodeWithText("ORD-001").assertIsDisplayed()
    }

    // ── Alert cards (conditional) ─────────────────────────────────────────────

    @Test
    fun `low stock warning card shown when lowStockCount is positive`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(lowStockCount = 3L, lowStockNames = listOf("Widget A"))))
        onNodeWithText("3 items running low").assertIsDisplayed()
    }

    @Test
    fun `low stock warning card not shown when lowStockCount is zero`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(lowStockCount = 0L)))
        assertTrue(
            onAllNodesWithText("items running low", substring = true).fetchSemanticsNodes().isEmpty(),
            "Low stock card should be absent when lowStockCount is 0",
        )
    }

    @Test
    fun `no register card shown when activeRegisters is zero`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(activeRegisters = 0L)))
        onNodeWithText("No register is open").assertIsDisplayed()
    }

    @Test
    fun `no register card hidden when activeRegisters is positive`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(activeRegisters = 1L)))
        assertTrue(
            onAllNodesWithText("No register is open").fetchSemanticsNodes().isEmpty(),
            "No-register card should be absent when registers are active",
        )
    }

    // ── Loading overlay ────────────────────────────────────────────────────────

    @Test
    fun `loading state hides dashboard content`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(isLoading = true)))
        assertTrue(
            onAllNodesWithText("Today's Sales").fetchSemanticsNodes().isEmpty(),
            "KPI cards should be hidden behind loading overlay",
        )
        assertTrue(
            onAllNodesWithText("New Sale").fetchSemanticsNodes().isEmpty(),
            "Quick actions should be hidden behind loading overlay",
        )
    }

    // ── Callback invocations ───────────────────────────────────────────────────

    @Test
    fun `tapping New Sale calls onNavigateToPos`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onNavigateToPos = { called = true }))
        onNodeWithText("New Sale").performClick()
        assertTrue(called, "onNavigateToPos should be invoked when New Sale is tapped")
    }

    @Test
    fun `tapping Reports quick action calls onNavigateToReports`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onNavigateToReports = { called = true }))
        onNodeWithText("Reports").performClick()
        assertTrue(called, "onNavigateToReports should be invoked when Reports is tapped")
    }

    @Test
    fun `tapping Logout icon calls onLogout`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onLogout = { called = true }))
        onNodeWithContentDescription("Logout").performClick()
        assertTrue(called, "onLogout should be invoked when Logout icon is tapped")
    }

    // ── Profile header ─────────────────────────────────────────────────────────

    @Test
    fun `profile header shows initials for two-word name`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = testUser("John Doe"))))
        onNodeWithText("JD").assertIsDisplayed()
    }

    @Test
    fun `profile header shows M fallback when user is null`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = null)))
        onNodeWithText("M").assertIsDisplayed()
    }

    @Test
    fun `profile header displays user name and welcome text`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = testUser("Jane Smith"))))
        onNodeWithText("Welcome back,").assertIsDisplayed()
        onNodeWithText("Jane Smith").assertIsDisplayed()
    }
}
