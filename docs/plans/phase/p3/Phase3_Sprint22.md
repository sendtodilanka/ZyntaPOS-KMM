# ZyntaPOS — Phase 3 Sprint 22: Advanced Analytics

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT22-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 22 of 24 | Week 22
> **Module(s):** `:composeApp:feature:reports`, `:composeApp:designsystem`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001 | ADR-002

---

## Goal

Implement advanced analytics screens extending `:composeApp:feature:reports`: sales trend line chart with linear regression forecast, product performance bar chart, hourly sales heatmap, and category trend comparison. All charts are built with Compose Canvas (no third-party charting library). Performance target: 12-month trend loads in < 3s.

---

## New Domain Models

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`

```kotlin
// TrendDataPoint.kt
data class TrendDataPoint(
    val period: String,     // "2026-02" for monthly, "2026-02-24" for daily
    val value: Double,
    val label: String
)

// Forecast.kt
data class Forecast(
    val period: String,
    val predictedValue: Double,
    val confidence: Float   // 0.0–1.0
)

// SalesTrend.kt
data class SalesTrend(
    val points: List<TrendDataPoint>,
    val forecast: List<Forecast>,       // next 3 periods
    val growthRate: Double              // period-over-period % change (last 2 periods)
)

// KpiDrilldown.kt
data class KpiDrilldown(
    val dimension: String,
    val value: Double,
    val percentageOfTotal: Double,
    val trend: Float                    // positive = growth, negative = decline
)

// HeatmapCell.kt
data class HeatmapCell(
    val dayOfWeek: Int,     // 0=Monday, 6=Sunday
    val hour: Int,          // 0–23
    val value: Double,      // average sales revenue
    val count: Int          // number of transactions
)
```

---

## New Use Case Interfaces

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/analytics/`

```kotlin
// GetSalesTrendUseCase.kt
fun interface GetSalesTrendUseCase {
    suspend operator fun invoke(storeId: String, periodType: PeriodType, periods: Int): SalesTrend
}

// GetProductPerformanceUseCase.kt
fun interface GetProductPerformanceUseCase {
    suspend operator fun invoke(storeId: String, fromDate: String, toDate: String, topN: Int): List<KpiDrilldown>
}

// GetHourlyHeatmapUseCase.kt
fun interface GetHourlyHeatmapUseCase {
    suspend operator fun invoke(storeId: String, fromDate: String, toDate: String): List<HeatmapCell>
}

// GetCategoryTrendUseCase.kt
fun interface GetCategoryTrendUseCase {
    suspend operator fun invoke(storeId: String, fromDate: String, toDate: String): List<KpiDrilldown>
}

// GetStoreComparisonUseCase.kt
fun interface GetStoreComparisonUseCase {
    suspend operator fun invoke(fromDate: String, toDate: String): List<KpiDrilldown>
}

enum class PeriodType { DAILY, WEEKLY, MONTHLY }
```

---

## Use Case Implementations

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/analytics/`

### `GetSalesTrendUseCaseImpl.kt`

```kotlin
class GetSalesTrendUseCaseImpl(
    private val orderRepository: OrderRepository
) : GetSalesTrendUseCase {

    override suspend fun invoke(storeId: String, periodType: PeriodType, periods: Int): SalesTrend {
        // 1. Query orders by date range (last N periods)
        // 2. Aggregate revenue per period (GROUP BY period)
        // 3. Apply simple linear regression for forecast (next 3 periods)
        // 4. Compute growth rate = (last_period - prev_period) / prev_period * 100

        val points = aggregateByPeriod(storeId, periodType, periods)
        val forecast = linearRegressionForecast(points, futureCount = 3)
        val growthRate = computeGrowthRate(points)

        return SalesTrend(points = points, forecast = forecast, growthRate = growthRate)
    }

    /**
     * Simple linear regression: y = a + bx
     * x = period index (0, 1, 2, ...)
     * y = revenue value
     * Returns next [futureCount] predicted values.
     */
    private fun linearRegressionForecast(points: List<TrendDataPoint>, futureCount: Int): List<Forecast> {
        if (points.size < 2) return emptyList()
        val n = points.size.toDouble()
        val xs = points.indices.map { it.toDouble() }
        val ys = points.map { it.value }
        val xMean = xs.average()
        val yMean = ys.average()
        val b = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) } /
                xs.sumOf { (it - xMean) * (it - xMean) }
        val a = yMean - b * xMean

        return (0 until futureCount).map { i ->
            val xFuture = n + i
            val predicted = maxOf(0.0, a + b * xFuture)
            Forecast(
                period          = futureLabels(points.last().period, i + 1),
                predictedValue  = predicted,
                confidence      = maxOf(0f, 1f - (i * 0.15f))   // confidence decreases with distance
            )
        }
    }

    private fun computeGrowthRate(points: List<TrendDataPoint>): Double {
        if (points.size < 2) return 0.0
        val last = points.last().value
        val prev = points[points.size - 2].value
        return if (prev > 0) ((last - prev) / prev * 100.0) else 0.0
    }

    private suspend fun aggregateByPeriod(storeId: String, periodType: PeriodType, periods: Int): List<TrendDataPoint> {
        // Pure SQL aggregation: SELECT SUM(total), period_label FROM orders WHERE store_id=? GROUP BY period_label
        return emptyList()  // implemented via OrderRepository.getSalesByPeriod()
    }

    private fun futureLabels(lastPeriod: String, stepsAhead: Int): String = lastPeriod  // impl per PeriodType
}
```

### `GetHourlyHeatmapUseCaseImpl.kt`

```kotlin
class GetHourlyHeatmapUseCaseImpl(
    private val orderRepository: OrderRepository
) : GetHourlyHeatmapUseCase {

    override suspend fun invoke(storeId: String, fromDate: String, toDate: String): List<HeatmapCell> {
        // SQL: SELECT AVG(total), COUNT(*), day_of_week, hour
        //      FROM orders WHERE store_id=? AND created_at BETWEEN ? AND ?
        //      GROUP BY day_of_week, hour
        // Returns 7×24 = 168 cells (some may be 0/missing)
        return (0..6).flatMap { day ->
            (0..23).map { hour ->
                HeatmapCell(dayOfWeek = day, hour = hour, value = 0.0, count = 0)
            }
        }
    }
}
```

---

## New Screen Files

**Location:** `composeApp/feature/reports/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/reports/screen/`

```
screen/
├── AdvancedReportsDashboard.kt    # Entry point with nav cards to each analytics screen
├── SalesTrendScreen.kt            # Line chart with forecast overlay
├── ProductPerformanceScreen.kt    # Horizontal bar chart of top N products
├── HourlyHeatmapScreen.kt         # 7×24 grid heatmap
└── CategoryTrendScreen.kt         # Category revenue comparison
```

---

## Design System Chart Components

**Location:** `composeApp/designsystem/src/commonMain/kotlin/com/zyntasolutions/zyntapos/designsystem/component/charts/`

### `ZyntaLineChart.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component.charts

/**
 * Canvas-based line chart composable.
 *
 * Features:
 * - Animated line drawing (drawAnimated = true → 600ms ease-out)
 * - Optional forecast overlay: dashed line with lower opacity
 * - X-axis labels (period strings), Y-axis labels (currency formatted)
 * - Touch/tap: shows value tooltip at nearest data point
 * - Grid lines: subtle horizontal lines at Y intervals
 *
 * @param points        Historical data points
 * @param forecast      Forecast data points (dashed extension)
 * @param labelX        X-axis label provider
 * @param labelY        Y-axis label provider (e.g. CurrencyUtils.format)
 * @param lineColor     Defaults to MaterialTheme.colorScheme.primary
 */
@Composable
fun ZyntaLineChart(
    points: List<TrendDataPoint>,
    forecast: List<Forecast> = emptyList(),
    labelX: (TrendDataPoint) -> String = { it.period.takeLast(5) },
    labelY: (Double) -> String = { CurrencyUtils.format(it) },
    lineColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    // Canvas implementation:
    // 1. Compute bounds: minY=0, maxY = max(points+forecast) * 1.1
    // 2. Map data coordinates → canvas pixels
    // 3. drawPath for historical line + filled area (gradient under line)
    // 4. drawPath for forecast line (PathEffect.dashPathEffect)
    // 5. drawCircle at each data point (4dp radius)
    // 6. drawText for axis labels
    // 7. Animate path drawing on first composition (graphPath(progress))
}
```

### `ZyntaBarChart.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component.charts

/**
 * Canvas-based horizontal bar chart.
 *
 * Features:
 * - Animated bar fill (600ms)
 * - Labels on left side (product/category name, truncated to 20 chars)
 * - Value label on right side of each bar
 * - Bars colored by rank (primary, secondary, tertiary gradients)
 * - Optional percentage label inside bar
 *
 * @param items     KpiDrilldown list (sorted by value descending)
 * @param maxItems  Maximum bars to display (default 10)
 * @param barColor  Defaults to MaterialTheme.colorScheme.primary
 */
@Composable
fun ZyntaBarChart(
    items: List<KpiDrilldown>,
    maxItems: Int = 10,
    labelValue: (KpiDrilldown) -> String = { CurrencyUtils.format(it.value) },
    barColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
)
```

### `ZyntaHeatmapGrid.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component.charts

/**
 * 7×24 heatmap grid composable.
 *
 * Rows: Days of week (Mon–Sun)
 * Columns: Hours (0–23, labeled every 4h: "00:00", "04:00", etc.)
 *
 * Color scale: white (0) → orange (mid) → deep red (max)
 * Cell size: adaptive to available width
 *
 * Features:
 * - Tap a cell → shows tooltip: "Tue 14:00–15:00: LKR X,XXX avg, N orders"
 * - Legend bar at bottom showing color scale
 * - Day labels on left, hour labels on top
 */
@Composable
fun ZyntaHeatmapGrid(
    cells: List<HeatmapCell>,
    labelValue: (HeatmapCell) -> String = { CurrencyUtils.format(it.value) },
    modifier: Modifier = Modifier
)
```

---

## Advanced Reports Dashboard

### `AdvancedReportsDashboard.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.reports.screen

/**
 * Entry screen for advanced analytics.
 *
 * Displays summary KPI cards at the top, then navigation cards to:
 * 1. Sales Trend (line chart + forecast)
 * 2. Product Performance (top 10 by revenue)
 * 3. Hourly Sales Heatmap
 * 4. Category Revenue Trends
 * 5. Multi-Store Comparison (ADMIN only)
 */
@Composable
fun AdvancedReportsDashboard(
    viewModel: ReportsViewModel,    // existing reports ViewModel extended
    onNavigateToTrend: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToHeatmap: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToStoreComparison: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Sales Trend Screen

### `SalesTrendScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.reports.screen

/**
 * Line chart screen with 30/90/365-day view selectors.
 *
 * Layout:
 * - Period selector tabs: 30d | 90d | 1y
 * - Growth rate badge: "+12.3% vs previous period" (green/red)
 * - ZyntaLineChart (full width, height 240dp)
 *   - Historical line (solid)
 *   - Forecast extension (dashed, 3 periods ahead)
 * - Summary stats below chart:
 *   Total Revenue | Average per Period | Peak Period | Lowest Period
 */
@Composable
fun SalesTrendScreen(
    storeId: String,
    viewModel: ReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedPeriod by remember { mutableIntStateOf(30) }   // 30, 90, 365

    LaunchedEffect(storeId, selectedPeriod) {
        viewModel.handleIntent(ReportsIntent.LoadSalesTrend(storeId, selectedPeriod))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Trend") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding).padding(16.dp)) {
            // Period selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 Days", 90 to "90 Days", 365 to "1 Year").forEach { (days, label) ->
                    FilterChip(
                        selected = selectedPeriod == days,
                        onClick = { selectedPeriod = days },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Growth rate badge
            state.salesTrend?.let { trend ->
                val growthColor = if (trend.growthRate >= 0) Color(0xFF16A34A) else Color(0xFFEF4444)
                val growthIcon = if (trend.growthRate >= 0) "↑" else "↓"
                Text(
                    text = "$growthIcon ${String.format("%.1f", kotlin.math.abs(trend.growthRate))}% vs previous period",
                    style = MaterialTheme.typography.titleSmall,
                    color = growthColor
                )
                Spacer(Modifier.height(8.dp))

                // Line chart
                ZyntaLineChart(
                    points = trend.points,
                    forecast = trend.forecast,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
```

---

## Hourly Heatmap Screen

### `HourlyHeatmapScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.reports.screen

/**
 * 7×24 heatmap showing average sales per hour of day × day of week.
 *
 * Layout:
 * - Date range selector (last 30/90/365 days)
 * - ZyntaHeatmapGrid (full width, adaptive height)
 * - Color legend bar
 * - Peak hours summary: "Busiest: Friday 12:00–13:00 (LKR 45,200 avg)"
 */
@Composable
fun HourlyHeatmapScreen(
    storeId: String,
    viewModel: ReportsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## ReportsViewModel Updates

```kotlin
// Add to ReportsState:
val salesTrend: SalesTrend? = null
val productPerformance: List<KpiDrilldown> = emptyList()
val heatmapCells: List<HeatmapCell> = emptyList()
val categoryTrend: List<KpiDrilldown> = emptyList()

// Add to ReportsIntent:
data class LoadSalesTrend(val storeId: String, val days: Int) : ReportsIntent
data class LoadProductPerformance(val storeId: String, val fromDate: String, val toDate: String) : ReportsIntent
data class LoadHeatmap(val storeId: String, val fromDate: String, val toDate: String) : ReportsIntent
data class LoadCategoryTrend(val storeId: String, val fromDate: String, val toDate: String) : ReportsIntent
```

---

## Tasks

- [ ] **22.1** Create `TrendDataPoint.kt`, `Forecast.kt`, `SalesTrend.kt`, `KpiDrilldown.kt`, `HeatmapCell.kt` domain models
- [ ] **22.2** Create `GetSalesTrendUseCase`, `GetProductPerformanceUseCase`, `GetHourlyHeatmapUseCase`, `GetCategoryTrendUseCase`, `GetStoreComparisonUseCase` interfaces + `PeriodType` enum
- [ ] **22.3** Implement `GetSalesTrendUseCaseImpl` with linear regression forecast (3 periods ahead)
- [ ] **22.4** Implement `GetHourlyHeatmapUseCaseImpl` with GROUP BY day_of_week, hour
- [ ] **22.5** Implement `GetProductPerformanceUseCaseImpl` with TOP N query
- [ ] **22.6** Create `ZyntaLineChart.kt` Canvas composable in `:composeApp:designsystem/charts/`
- [ ] **22.7** Create `ZyntaBarChart.kt` Canvas composable in `:composeApp:designsystem/charts/`
- [ ] **22.8** Create `ZyntaHeatmapGrid.kt` Canvas composable in `:composeApp:designsystem/charts/`
- [ ] **22.9** Implement `AdvancedReportsDashboard.kt` with nav cards
- [ ] **22.10** Implement `SalesTrendScreen.kt` with period tabs and growth badge
- [ ] **22.11** Implement `HourlyHeatmapScreen.kt`
- [ ] **22.12** Implement `ProductPerformanceScreen.kt` and `CategoryTrendScreen.kt`
- [ ] **22.13** Update `ReportsViewModel` state + intent for analytics
- [ ] **22.14** Add `AdvancedReportsDashboard` to reports nav item + route
- [ ] **22.15** Write `GetSalesTrendUseCaseTest` — test linear regression output, growth rate calculation
- [ ] **22.16** Verify performance: 12-month trend query < 3s on JVM in-memory SQLDelight test

---

## Verification

```bash
./gradlew :shared:domain:test
./gradlew :composeApp:feature:reports:assemble
./gradlew :composeApp:designsystem:assemble
./gradlew :composeApp:feature:reports:detekt
```

---

## Definition of Done

- [ ] All 5 analytics domain models created (ADR-002 compliant)
- [ ] All 5 use case interfaces created (SAM, no defaults)
- [ ] `GetSalesTrendUseCaseImpl` linear regression produces sensible forecasts
- [ ] `ZyntaLineChart`, `ZyntaBarChart`, `ZyntaHeatmapGrid` render without third-party chart libraries
- [ ] All 4 analytics screens implemented with period selection
- [ ] Performance test: 12-month monthly trend (365 orders) aggregates in < 3s
- [ ] Commit: `feat(analytics): add sales trend, product performance, and hourly heatmap charts`
