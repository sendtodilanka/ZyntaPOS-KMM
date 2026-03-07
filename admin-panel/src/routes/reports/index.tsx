import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Download, BarChart3, TrendingUp, Package } from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, Legend,
} from 'recharts';
import { useSalesReport, useProductPerformance } from '@/api/metrics';
import { exportToCsv } from '@/lib/export';
import { formatCurrency } from '@/lib/utils';
import { cn } from '@/lib/utils';

export const Route = createFileRoute('/reports/')({
  component: ReportsPage,
});

type Period = '7d' | '30d' | '90d' | '12m';

const PERIODS: { value: Period; label: string }[] = [
  { value: '7d', label: 'Last 7 Days' },
  { value: '30d', label: 'Last 30 Days' },
  { value: '90d', label: 'Last 90 Days' },
  { value: '12m', label: 'Last 12 Months' },
];

const CUSTOM_TOOLTIP = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-surface-elevated border border-surface-border rounded-lg p-3 shadow-xl text-sm">
      <p className="text-slate-400 mb-2">{label}</p>
      {payload.map((entry: any) => (
        <div key={entry.name} className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full" style={{ backgroundColor: entry.color }} />
          <span className="text-slate-400">{entry.name}:</span>
          <span className="text-slate-100 font-medium">
            {entry.name === 'revenue' || entry.name === 'Revenue'
              ? formatCurrency(entry.value)
              : entry.value.toLocaleString()}
          </span>
        </div>
      ))}
    </div>
  );
};

function ReportsPage() {
  const [period, setPeriod] = useState<Period>('30d');
  const [activeTab, setActiveTab] = useState<'sales' | 'products'>('sales');

  const { data: salesData, isLoading: salesLoading } = useSalesReport({ period });
  const { data: productsData, isLoading: productsLoading } = useProductPerformance({ period, limit: 20 });

  const handleExportSales = () => {
    if (!salesData?.length) return;
    exportToCsv(salesData, `sales-report-${period}-${new Date().toISOString().slice(0, 10)}.csv`, {
      date: 'Date',
      revenue: 'Revenue (LKR)',
      orders: 'Orders',
      avgOrderValue: 'Avg Order Value',
      storeId: 'Store ID',
      storeName: 'Store Name',
    });
  };

  const handleExportProducts = () => {
    if (!productsData?.length) return;
    exportToCsv(productsData, `product-performance-${period}-${new Date().toISOString().slice(0, 10)}.csv`, {
      productId: 'Product ID',
      name: 'Product Name',
      category: 'Category',
      unitsSold: 'Units Sold',
      revenue: 'Revenue (LKR)',
      returns: 'Returns',
      storeId: 'Store ID',
      storeName: 'Store Name',
    });
  };

  // Aggregate sales by date for chart
  const salesChartData = (salesData ?? []).reduce<Record<string, { date: string; Revenue: number; Orders: number }>>((acc, row) => {
    if (!acc[row.date]) acc[row.date] = { date: row.date, Revenue: 0, Orders: 0 };
    acc[row.date].Revenue += row.revenue;
    acc[row.date].Orders += row.orders;
    return acc;
  }, {});
  const salesSeries = Object.values(salesChartData).sort((a, b) => a.date.localeCompare(b.date));

  // Aggregate top products
  const productMap = (productsData ?? []).reduce<Record<string, { name: string; 'Units Sold': number; Revenue: number }>>((acc, p) => {
    if (!acc[p.productId]) acc[p.productId] = { name: p.name, 'Units Sold': 0, Revenue: 0 };
    acc[p.productId]['Units Sold'] += p.unitsSold;
    acc[p.productId].Revenue += p.revenue;
    return acc;
  }, {});
  const topProducts = Object.values(productMap)
    .sort((a, b) => b.Revenue - a.Revenue)
    .slice(0, 10);

  // Summary stats
  const totalRevenue = salesSeries.reduce((s, r) => s + r.Revenue, 0);
  const totalOrders = salesSeries.reduce((s, r) => s + r.Orders, 0);
  const avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;
  const totalUnitsSold = (productsData ?? []).reduce((s, p) => s + p.unitsSold, 0);

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Reports</h1>
          <p className="text-sm text-slate-400 mt-1">Sales performance and product analytics</p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {/* Period Selector */}
          <div className="flex gap-1 bg-surface-elevated rounded-lg p-1">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                onClick={() => setPeriod(p.value)}
                className={cn(
                  'px-3 py-1.5 rounded-md text-xs font-medium transition-colors min-h-[36px]',
                  period === p.value
                    ? 'bg-surface-card text-slate-100'
                    : 'text-slate-400 hover:text-slate-100',
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
          {/* Export */}
          <button
            onClick={activeTab === 'sales' ? handleExportSales : handleExportProducts}
            className="flex items-center gap-2 px-3 py-2 bg-surface-elevated hover:bg-surface-card text-slate-300 text-sm rounded-lg min-h-[44px] border border-surface-border transition-colors"
          >
            <Download className="w-4 h-4" />
            Export CSV
          </button>
        </div>
      </div>

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { label: 'Total Revenue', value: formatCurrency(totalRevenue), icon: TrendingUp, color: 'text-green-400' },
          { label: 'Total Orders', value: totalOrders.toLocaleString(), icon: BarChart3, color: 'text-blue-400' },
          { label: 'Avg Order Value', value: formatCurrency(avgOrderValue), icon: TrendingUp, color: 'text-brand-400' },
          { label: 'Units Sold', value: totalUnitsSold.toLocaleString(), icon: Package, color: 'text-purple-400' },
        ].map((stat) => (
          <div key={stat.label} className="bg-surface-card border border-surface-border rounded-xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <stat.icon className={cn('w-4 h-4', stat.color)} />
              <span className="text-xs text-slate-500">{stat.label}</span>
            </div>
            {salesLoading ? (
              <div className="h-7 bg-surface-elevated rounded animate-pulse" />
            ) : (
              <p className="text-xl font-bold text-slate-100">{stat.value}</p>
            )}
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-elevated rounded-xl p-1">
        {[
          { id: 'sales' as const, label: 'Sales Report', icon: TrendingUp },
          { id: 'products' as const, label: 'Product Performance', icon: Package },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors flex-1 justify-center min-h-[44px]',
              activeTab === tab.id
                ? 'bg-surface-card text-slate-100 shadow-sm'
                : 'text-slate-400 hover:text-slate-100',
            )}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Sales Report Tab */}
      {activeTab === 'sales' && (
        <div className="space-y-6">
          {/* Revenue Line Chart */}
          <div className="bg-surface-card border border-surface-border rounded-xl p-4 md:p-6">
            <h2 className="text-base font-semibold text-slate-100 mb-4">Revenue Over Time</h2>
            {salesLoading ? (
              <div className="h-64 bg-surface-elevated rounded animate-pulse" />
            ) : salesSeries.length === 0 ? (
              <div className="h-64 flex items-center justify-center text-slate-400">No data for selected period</div>
            ) : (
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={salesSeries} margin={{ top: 4, right: 4, bottom: 4, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" />
                  <XAxis
                    dataKey="date"
                    tick={{ fill: '#94a3b8', fontSize: 11 }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => v.slice(5)} // MM-DD
                  />
                  <YAxis
                    tick={{ fill: '#94a3b8', fontSize: 11 }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
                    width={40}
                  />
                  <Tooltip content={<CUSTOM_TOOLTIP />} />
                  <Legend wrapperStyle={{ color: '#94a3b8', fontSize: 12 }} />
                  <Line
                    type="monotone"
                    dataKey="Revenue"
                    stroke="#38bdf8"
                    strokeWidth={2}
                    dot={false}
                    activeDot={{ r: 4 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Orders Bar Chart */}
          <div className="bg-surface-card border border-surface-border rounded-xl p-4 md:p-6">
            <h2 className="text-base font-semibold text-slate-100 mb-4">Order Volume</h2>
            {salesLoading ? (
              <div className="h-48 bg-surface-elevated rounded animate-pulse" />
            ) : salesSeries.length === 0 ? (
              <div className="h-48 flex items-center justify-center text-slate-400">No data</div>
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={salesSeries} margin={{ top: 4, right: 4, bottom: 4, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" />
                  <XAxis
                    dataKey="date"
                    tick={{ fill: '#94a3b8', fontSize: 11 }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => v.slice(5)}
                  />
                  <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
                  <Tooltip content={<CUSTOM_TOOLTIP />} />
                  <Bar dataKey="Orders" fill="#7c3aed" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Data Table */}
          <div className="bg-surface-card border border-surface-border rounded-xl overflow-hidden">
            <div className="p-4 border-b border-surface-border">
              <h2 className="text-base font-semibold text-slate-100">Daily Breakdown</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px]">
                <thead>
                  <tr className="border-b border-surface-border bg-surface-elevated">
                    {['Date', 'Store', 'Revenue', 'Orders', 'Avg Order'].map((h) => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {salesLoading ? (
                    Array.from({ length: 5 }).map((_, i) => (
                      <tr key={i} className="border-b border-surface-border">
                        {[1, 2, 3, 4, 5].map((j) => (
                          <td key={j} className="px-4 py-3">
                            <div className="h-4 bg-surface-elevated rounded animate-pulse" />
                          </td>
                        ))}
                      </tr>
                    ))
                  ) : (salesData ?? []).slice(0, 50).map((row, i) => (
                    <tr key={i} className="border-b border-surface-border hover:bg-surface-elevated transition-colors">
                      <td className="px-4 py-3 text-sm text-slate-300 font-mono">{row.date}</td>
                      <td className="px-4 py-3 text-sm text-slate-300">{row.storeName ?? row.storeId}</td>
                      <td className="px-4 py-3 text-sm text-slate-100 font-medium">{formatCurrency(row.revenue)}</td>
                      <td className="px-4 py-3 text-sm text-slate-300">{row.orders.toLocaleString()}</td>
                      <td className="px-4 py-3 text-sm text-slate-300">
                        {row.orders > 0 ? formatCurrency(row.revenue / row.orders) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Product Performance Tab */}
      {activeTab === 'products' && (
        <div className="space-y-6">
          {/* Top Products Bar Chart */}
          <div className="bg-surface-card border border-surface-border rounded-xl p-4 md:p-6">
            <h2 className="text-base font-semibold text-slate-100 mb-4">Top 10 Products by Revenue</h2>
            {productsLoading ? (
              <div className="h-64 bg-surface-elevated rounded animate-pulse" />
            ) : topProducts.length === 0 ? (
              <div className="h-64 flex items-center justify-center text-slate-400">No data for selected period</div>
            ) : (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={topProducts}
                  layout="vertical"
                  margin={{ top: 4, right: 4, bottom: 4, left: 80 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" horizontal={false} />
                  <XAxis
                    type="number"
                    tick={{ fill: '#94a3b8', fontSize: 11 }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
                  />
                  <YAxis
                    type="category"
                    dataKey="name"
                    tick={{ fill: '#94a3b8', fontSize: 11 }}
                    tickLine={false}
                    axisLine={false}
                    width={80}
                  />
                  <Tooltip content={<CUSTOM_TOOLTIP />} />
                  <Bar dataKey="Revenue" fill="#38bdf8" radius={[0, 3, 3, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Product Table */}
          <div className="bg-surface-card border border-surface-border rounded-xl overflow-hidden">
            <div className="p-4 border-b border-surface-border">
              <h2 className="text-base font-semibold text-slate-100">Product Details</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px]">
                <thead>
                  <tr className="border-b border-surface-border bg-surface-elevated">
                    {['Product', 'Category', 'Store', 'Units Sold', 'Revenue', 'Returns'].map((h) => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {productsLoading ? (
                    Array.from({ length: 5 }).map((_, i) => (
                      <tr key={i} className="border-b border-surface-border">
                        {[1, 2, 3, 4, 5, 6].map((j) => (
                          <td key={j} className="px-4 py-3">
                            <div className="h-4 bg-surface-elevated rounded animate-pulse" />
                          </td>
                        ))}
                      </tr>
                    ))
                  ) : (productsData ?? []).map((p, i) => (
                    <tr key={i} className="border-b border-surface-border hover:bg-surface-elevated transition-colors">
                      <td className="px-4 py-3">
                        <span className="text-sm font-medium text-slate-100">{p.name}</span>
                        <br />
                        <span className="text-xs text-slate-500 font-mono">{p.productId}</span>
                      </td>
                      <td className="px-4 py-3 text-sm text-slate-300">{p.category}</td>
                      <td className="px-4 py-3 text-sm text-slate-300">{p.storeName ?? p.storeId}</td>
                      <td className="px-4 py-3 text-sm text-slate-100 font-medium">{p.unitsSold.toLocaleString()}</td>
                      <td className="px-4 py-3 text-sm text-slate-100 font-medium">{formatCurrency(p.revenue)}</td>
                      <td className="px-4 py-3 text-sm text-slate-300">{p.returns?.toLocaleString() ?? '0'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
