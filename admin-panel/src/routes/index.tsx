import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/')({
  component: DashboardPage,
});

function DashboardPage() {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-slate-100">Dashboard</h1>
      <p className="text-slate-400 mt-2">Welcome to ZyntaPOS Admin Panel</p>
    </div>
  );
}
