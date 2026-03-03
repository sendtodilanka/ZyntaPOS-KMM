package com.zyntasolutions.zyntapos.feature.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
import com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import com.zyntasolutions.zyntapos.feature.dashboard.screen.DashboardScreenContent
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// DashboardScreenUiTest — Compose Desktop rendering tests for DashboardScreen.
//
// Tests call DashboardScreenContent() directly — the stateless, Koin-free
// composable extracted to enable testability. Tests cover all three responsive
// layouts (COMPACT, MEDIUM, EXPANDED) and all conditional UI branches.
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
        todaysSales: Double = 0.0,
        totalOrders: Long = 10L,
        lowStockCount: Long = 0L,
        lowStockNames: List<String> = emptyList(),
        activeRegisters: Long = 1L,
        recentOrders: List<RecentOrderItem> = emptyList(),
        weeklySalesData: List<ChartDataPoint> = emptyList(),
        todaySparkline: List<Float> = emptyList(),
        currentUser: User? = testUser(),
    ) = DashboardState(
        isLoading = isLoading,
        todaysSales = todaysSales,
        totalOrders = totalOrders,
        lowStockCount = lowStockCount,
        lowStockNames = lowStockNames,
        activeRegisters = activeRegisters,
        recentOrders = recentOrders,
        weeklySalesData = weeklySalesData,
        todaySparkline = todaySparkline,
        currentUser = currentUser,
        // Compute initials from the user name (mirrors DashboardViewModel logic)
        userInitials = currentUser?.name
            ?.split(" ")
            ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            ?.joinToString("") ?: "M",
        greetingText = "Welcome back,",
    )

    private fun makeOrders(count: Int) = (1..count).map { i ->
        RecentOrderItem(
            orderNumber = "ORD-%03d".format(i),
            total = 100.0 * i,
            method = "CASH",
            timestamp = 1_700_000_000_000L + i * 3_600_000L,
        )
    }

    private fun content(
        state: DashboardState = makeState(),
        windowSize: WindowSize = WindowSize.COMPACT,
        onNavigateToPos: () -> Unit = {},
        onNavigateToRegister: () -> Unit = {},
        onNavigateToReports: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToNotifications: () -> Unit = {},
        onLogout: () -> Unit = {},
    ): @androidx.compose.runtime.Composable () -> Unit = {
        ZyntaTheme {
            DashboardScreenContent(
                state = state,
                currencyFormatter = formatter,
                windowSize = windowSize,
                onNavigateToPos = onNavigateToPos,
                onNavigateToRegister = onNavigateToRegister,
                onNavigateToReports = onNavigateToReports,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToNotifications = onNavigateToNotifications,
                onLogout = onLogout,
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // COMPACT LAYOUT — KPI stat card labels
    // ════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════
    // COMPACT LAYOUT — Quick action buttons visible
    // ════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════
    // Activity list
    // ════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════
    // Alert cards (conditional)
    // ════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════
    // Loading overlay
    // ════════════════════════════════════════════════════════════════════════════

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

    @Test
    fun `loading state in expanded layout hides content`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(isLoading = true), windowSize = WindowSize.EXPANDED))
        assertTrue(
            onAllNodesWithText("Today's Sales").fetchSemanticsNodes().isEmpty(),
            "Expanded KPI cards should be hidden behind loading overlay",
        )
    }

    @Test
    fun `loading state in medium layout hides content`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(isLoading = true), windowSize = WindowSize.MEDIUM))
        assertTrue(
            onAllNodesWithText("Today's Sales").fetchSemanticsNodes().isEmpty(),
            "Medium KPI cards should be hidden behind loading overlay",
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Callback invocations — COMPACT
    // ════════════════════════════════════════════════════════════════════════════

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
    fun `tapping Logout in profile menu calls onLogout`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onLogout = { called = true }))
        // Open profile avatar menu then tap Logout
        onNodeWithText("JD").performClick()
        onNodeWithText("Logout").performClick()
        assertTrue(called, "onLogout should be invoked when Logout menu item is tapped")
    }

    @Test
    fun `tapping Register quick action calls onNavigateToRegister`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onNavigateToRegister = { called = true }))
        onNodeWithText("Register").performClick()
        assertTrue(called, "onNavigateToRegister should be invoked when Register is tapped")
    }

    @Test
    fun `tapping Settings in profile menu calls onNavigateToSettings`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onNavigateToSettings = { called = true }))
        // Open profile avatar menu then tap Settings
        onNodeWithText("JD").performClick()
        onNodeWithText("Settings").performClick()
        assertTrue(called, "onNavigateToSettings should be invoked when Settings menu item is tapped")
    }

    @Test
    fun `tapping Notifications in profile menu calls onNavigateToNotifications`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(onNavigateToNotifications = { called = true }))
        // Open profile avatar menu then tap Notifications
        onNodeWithText("JD").performClick()
        onNodeWithText("Notifications").performClick()
        assertTrue(called, "onNavigateToNotifications should be invoked via profile menu")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Callback invocations — EXPANDED layout specific
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `expanded tapping See All calls onNavigateToReports`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(windowSize = WindowSize.EXPANDED, onNavigateToReports = { called = true }))
        onNodeWithText("See All").performClick()
        assertTrue(called, "onNavigateToReports should be invoked via See All")
    }

    @Test
    fun `expanded tapping no-register alert calls onNavigateToRegister`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(
            state = makeState(activeRegisters = 0L),
            windowSize = WindowSize.EXPANDED,
            onNavigateToRegister = { called = true },
        ))
        onNodeWithText("No register is open").performClick()
        assertTrue(called, "onNavigateToRegister should be invoked via no-register alert")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Profile header
    // ════════════════════════════════════════════════════════════════════════════

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
    fun `profile menu displays user name and welcome text`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = testUser("Jane Smith"))))
        // Open profile avatar menu (initials "JS")
        onNodeWithText("JS").performClick()
        onNodeWithText("Welcome back,").assertIsDisplayed()
        onNodeWithText("Jane Smith").assertIsDisplayed()
    }

    @Test
    fun `profile header shows single initial for single-word name`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = testUser("Alice"))))
        onNodeWithText("A").assertIsDisplayed()
    }

    @Test
    fun `profile menu shows Manager fallback name when user is null`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(currentUser = null)))
        // Open profile avatar menu (fallback "M")
        onNodeWithText("M").performClick()
        onNodeWithText("Manager").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // KPI value display
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `displays formatted currency for Today's Sales`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(todaysSales = 1500.50)))
        onNodeWithText("Rs. 1,500.50").assertIsDisplayed()
    }

    @Test
    fun `displays total order count value`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(totalOrders = 42L)))
        onNodeWithText("42").assertIsDisplayed()
    }

    @Test
    fun `displays low stock item count in stat card`() = runDesktopComposeUiTest {
        // In compact layout, ZyntaCompactStatCard shows just the count string
        setContent(content(state = makeState(lowStockCount = 7L)))
        onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun `displays active registers count value`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(activeRegisters = 2L)))
        onNodeWithText("2").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // KPI values in EXPANDED layout (includes subtitle and "X items" format)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `expanded layout shows low stock items count with unit`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(lowStockCount = 3L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("3 items").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Completed today subtitle`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Completed today").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Requires attention when low stock positive`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(lowStockCount = 5L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("Requires attention").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows All stocked when low stock zero`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(lowStockCount = 0L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("All stocked").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Ready for sales when registers active`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(activeRegisters = 1L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("Ready for sales").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows No register open subtitle when none active`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(activeRegisters = 0L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("No register open").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // EXPANDED layout — structure and sections
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `expanded layout shows Today's Sales KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Today's Sales").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Total Orders KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Total Orders").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Low Stock KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Low Stock").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Active Registers KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Active Registers").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Weekly Sales Trend chart title`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Weekly Sales Trend").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Quick Actions section header`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Quick Actions").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows Recent Activity section header`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("Recent Activity").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows See All button`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        onNodeWithText("See All").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows up to 8 recent orders`() = runDesktopComposeUiTest {
        val orders = makeOrders(10)
        setContent(content(state = makeState(recentOrders = orders), windowSize = WindowSize.EXPANDED))
        // First 8 should be rendered (may be off-screen, so check node existence)
        onNodeWithText("ORD-001").assertIsDisplayed()
        assertTrue(
            onAllNodesWithText("ORD-008").fetchSemanticsNodes().isNotEmpty(),
            "Expanded layout should render 8th order",
        )
        // 9th and 10th should not be rendered
        assertTrue(
            onAllNodesWithText("ORD-009").fetchSemanticsNodes().isEmpty(),
            "Expanded layout should cap at 8 recent orders",
        )
    }

    @Test
    fun `expanded layout shows empty activity card when no orders`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(recentOrders = emptyList()),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("No orders yet today").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // MEDIUM layout — structure and sections
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `medium layout shows Today's Sales KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("Today's Sales").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows Total Orders KPI label`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("Total Orders").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows Weekly Sales Trend chart title`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("Weekly Sales Trend").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows See All button`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("See All").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows Recent Activity section header`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("Recent Activity").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows up to 5 recent orders`() = runDesktopComposeUiTest {
        val orders = makeOrders(10)
        setContent(content(state = makeState(recentOrders = orders), windowSize = WindowSize.MEDIUM))
        onNodeWithText("ORD-001").assertIsDisplayed()
        // Verify 5th order exists in the tree (may be off-screen in LazyColumn)
        assertTrue(
            onAllNodesWithText("ORD-005").fetchSemanticsNodes().isNotEmpty(),
            "Medium layout should render 5 recent orders",
        )
        assertTrue(
            onAllNodesWithText("ORD-006").fetchSemanticsNodes().isEmpty(),
            "Medium layout should cap at 5 recent orders",
        )
    }

    @Test
    fun `medium layout shows New Sale quick action`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("New Sale").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows Register quick action`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        onNodeWithText("Register").assertIsDisplayed()
    }

    @Test
    fun `medium tapping See All calls onNavigateToReports`() = runDesktopComposeUiTest {
        var called = false
        setContent(content(windowSize = WindowSize.MEDIUM, onNavigateToReports = { called = true }))
        onNodeWithText("See All").performClick()
        assertTrue(called, "onNavigateToReports should be invoked via See All in medium layout")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // MEDIUM layout — conditional alerts
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `medium layout shows low stock alert when count positive`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(lowStockCount = 4L, lowStockNames = listOf("Item A")),
            windowSize = WindowSize.MEDIUM,
        ))
        onNodeWithText("4 items running low").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows no register alert when none active`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(activeRegisters = 0L),
            windowSize = WindowSize.MEDIUM,
        ))
        onNodeWithText("No register is open").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Recent order detail display
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `shows formatted total on recent order`() = runDesktopComposeUiTest {
        val order = RecentOrderItem(orderNumber = "ORD-001", total = 250.0, method = "CASH", timestamp = 0L)
        setContent(content(state = makeState(recentOrders = listOf(order))))
        onNodeWithText("Rs. 250.00").assertIsDisplayed()
    }

    @Test
    fun `shows payment method on recent order`() = runDesktopComposeUiTest {
        val order = RecentOrderItem(orderNumber = "ORD-001", total = 100.0, method = "CARD", timestamp = 0L)
        setContent(content(state = makeState(recentOrders = listOf(order))))
        onNodeWithText("CARD", substring = true).assertIsDisplayed()
    }

    @Test
    fun `shows multiple order numbers when multiple orders present`() = runDesktopComposeUiTest {
        val orders = listOf(
            RecentOrderItem(orderNumber = "ORD-001", total = 100.0, method = "CASH", timestamp = 0L),
            RecentOrderItem(orderNumber = "ORD-002", total = 200.0, method = "CARD", timestamp = 3_600_000L),
            RecentOrderItem(orderNumber = "ORD-003", total = 300.0, method = "CASH", timestamp = 7_200_000L),
        )
        setContent(content(state = makeState(recentOrders = orders)))
        onNodeWithText("ORD-001").assertIsDisplayed()
        onNodeWithText("ORD-002").assertIsDisplayed()
        onNodeWithText("ORD-003").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows formatted total on recent order`() = runDesktopComposeUiTest {
        val order = RecentOrderItem(orderNumber = "ORD-050", total = 1234.56, method = "CASH", timestamp = 0L)
        setContent(content(
            state = makeState(recentOrders = listOf(order)),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("Rs. 1,234.56").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Alert card content
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `low stock alert shows product names`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(
            lowStockCount = 2L,
            lowStockNames = listOf("Widget A", "Bolt B"),
        )))
        onNodeWithText("Widget A, Bolt B").assertIsDisplayed()
    }

    @Test
    fun `expanded low stock alert shows ellipsis for more than 5 items`() = runDesktopComposeUiTest {
        val names = listOf("A", "B", "C", "D", "E")
        setContent(content(
            state = makeState(lowStockCount = 7L, lowStockNames = names),
            windowSize = WindowSize.EXPANDED,
        ))
        // Expanded layout appends ", ..." when lowStockCount > 5
        onNodeWithText("...", substring = true).assertIsDisplayed()
    }

    @Test
    fun `expanded low stock alert does not show ellipsis for 5 or fewer`() = runDesktopComposeUiTest {
        val names = listOf("A", "B", "C")
        setContent(content(
            state = makeState(lowStockCount = 3L, lowStockNames = names),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("A, B, C").assertIsDisplayed()
        // Should not have "..." since count <= 5
        assertEquals(
            0,
            onAllNodesWithText("...").fetchSemanticsNodes().size,
            "No ellipsis should appear when lowStockCount <= 5",
        )
    }

    @Test
    fun `no register alert shows description text in expanded`() = runDesktopComposeUiTest {
        setContent(content(
            state = makeState(activeRegisters = 0L),
            windowSize = WindowSize.EXPANDED,
        ))
        onNodeWithText("Open a register to start processing sales").assertIsDisplayed()
    }

    @Test
    fun `no register alert shows description text in compact`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(activeRegisters = 0L)))
        onNodeWithText("Open a register to start").assertIsDisplayed()
    }

    @Test
    fun `low stock alert title reflects count`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(lowStockCount = 5L, lowStockNames = listOf("X"))))
        onNodeWithText("5 items running low").assertIsDisplayed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Empty and loading edge cases
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `empty activity card shows subtitle text`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(recentOrders = emptyList())))
        onNodeWithText("Complete your first sale to see activity here").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Weekly Sales Trend title`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Weekly Sales Trend").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Recent Activity section header`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Recent Activity").assertIsDisplayed()
    }

    @Test
    fun `compact layout shows Register quick action`() = runDesktopComposeUiTest {
        setContent(content())
        onNodeWithText("Register").assertIsDisplayed()
    }

    @Test
    fun `expanded layout shows welcome text in profile menu`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.EXPANDED))
        // Open profile avatar menu then verify welcome text
        onNodeWithText("JD").performClick()
        onNodeWithText("Welcome back,").assertIsDisplayed()
        onNodeWithText("John Doe").assertIsDisplayed()
    }

    @Test
    fun `medium layout shows welcome text in profile menu`() = runDesktopComposeUiTest {
        setContent(content(windowSize = WindowSize.MEDIUM))
        // Open profile avatar menu then verify welcome text
        onNodeWithText("JD").performClick()
        onNodeWithText("Welcome back,").assertIsDisplayed()
        onNodeWithText("John Doe").assertIsDisplayed()
    }

    @Test
    fun `zero sales displays Rs 0 00`() = runDesktopComposeUiTest {
        setContent(content(state = makeState(todaysSales = 0.0)))
        onNodeWithText("Rs. 0.00").assertIsDisplayed()
    }
}
