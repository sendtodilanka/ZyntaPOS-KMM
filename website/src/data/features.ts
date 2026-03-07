export interface Feature {
  icon: string;
  title: string;
  description: string;
  href?: string;
}

export interface FeatureSection {
  id: string;
  title: string;
  subtitle: string;
  features: Feature[];
}

export const homeFeatures: Feature[] = [
  {
    icon: 'lucide:wifi-off',
    title: 'Offline-First',
    description:
      'Keep selling even when the internet goes down. Every transaction is saved locally and syncs automatically when you reconnect.',
  },
  {
    icon: 'lucide:shield-check',
    title: 'AES-256 Encrypted',
    description:
      'Your business data is protected with military-grade encryption at rest. Android Keystore ensures keys never leave the device.',
  },
  {
    icon: 'lucide:store',
    title: 'Multi-Store Ready',
    description:
      'Manage multiple outlets from a central dashboard. View consolidated KPIs, transfer stock, and compare performance.',
  },
  {
    icon: 'lucide:printer',
    title: 'Receipt Printing',
    description:
      'ESC/POS support for USB and network thermal printers. Print receipts, Z-reports, and barcode labels out of the box.',
  },
  {
    icon: 'lucide:scan-barcode',
    title: 'Barcode Scanning',
    description:
      'USB HID scanner support and built-in camera barcode scanning powered by ML Kit — no external app required.',
  },
  {
    icon: 'lucide:users',
    title: 'Role-Based Access',
    description:
      '5 built-in roles: Admin, Manager, Cashier, Customer Service, Reporter. Granular permissions with full audit logging.',
  },
];

export const featureSections: FeatureSection[] = [
  {
    id: 'pos',
    title: 'POS & Checkout',
    subtitle: 'Fast, reliable checkout for every retail scenario.',
    features: [
      {
        icon: 'lucide:shopping-cart',
        title: 'Smart Product Grid',
        description: 'FTS-powered search, category filters, and barcode lookup.',
      },
      {
        icon: 'lucide:receipt',
        title: 'Flexible Cart',
        description: 'Line-item discounts, global discounts, hold orders, and quick resume.',
      },
      {
        icon: 'lucide:credit-card',
        title: 'Split Payments',
        description: 'Accept cash, card, or a combination in a single transaction.',
      },
      {
        icon: 'lucide:rotate-ccw',
        title: 'Refunds',
        description: 'Full and partial refund processing with automatic inventory adjustment.',
      },
    ],
  },
  {
    id: 'inventory',
    title: 'Inventory Management',
    subtitle: 'Real-time stock visibility across all your outlets.',
    features: [
      {
        icon: 'lucide:package',
        title: 'Product CRUD',
        description: 'Create products with categories, variants, UoM, and barcode assignments.',
      },
      {
        icon: 'lucide:trending-down',
        title: 'Low-Stock Alerts',
        description: 'Get notified at dashboard open when items fall below reorder level.',
      },
      {
        icon: 'lucide:clipboard-list',
        title: 'Stock Adjustments',
        description: 'Log manual adjustments with reason codes and full audit trail.',
      },
      {
        icon: 'lucide:tag',
        title: 'Barcode Labels',
        description: 'Print ESC/POS barcode labels directly from the inventory screen.',
      },
    ],
  },
  {
    id: 'customers',
    title: 'Customers & Loyalty',
    subtitle: 'Build lasting relationships with your customers.',
    features: [
      {
        icon: 'lucide:contact',
        title: 'Customer Directory',
        description: 'Store contact info, purchase history, and loyalty balance.',
      },
      {
        icon: 'lucide:star',
        title: 'Loyalty Points',
        description: 'Award points on purchases; redeem at checkout automatically.',
      },
      {
        icon: 'lucide:download',
        title: 'GDPR Export',
        description: 'Export all customer data in JSON format on request.',
      },
    ],
  },
  {
    id: 'reports',
    title: 'Reports & Analytics',
    subtitle: 'Data-driven insights to grow your business.',
    features: [
      {
        icon: 'lucide:bar-chart-3',
        title: 'Sales Summary',
        description: 'Daily, weekly, and monthly revenue breakdowns with trend analysis.',
      },
      {
        icon: 'lucide:box',
        title: 'Product Performance',
        description: 'See which items drive the most revenue and identify slow movers.',
      },
      {
        icon: 'lucide:file-spreadsheet',
        title: 'CSV & PDF Export',
        description: 'Export any report to CSV or generate a formatted PDF.',
      },
      {
        icon: 'lucide:file-text',
        title: 'Z-Report',
        description: 'End-of-day Z-report for cash reconciliation and closing.',
      },
    ],
  },
  {
    id: 'security',
    title: 'Security',
    subtitle: 'Enterprise-grade protection for your business data.',
    features: [
      {
        icon: 'lucide:lock',
        title: 'AES-256-GCM Encryption',
        description: 'SQLCipher 4.5 encrypts every byte of the local database.',
      },
      {
        icon: 'lucide:key',
        title: 'Android Keystore',
        description: 'Encryption keys are hardware-backed — never stored in plaintext.',
      },
      {
        icon: 'lucide:file-clock',
        title: 'Audit Log',
        description: 'SHA-256 hash chain audit log captures 40+ event types with full tamper detection.',
      },
      {
        icon: 'lucide:fingerprint',
        title: 'Biometric Auth',
        description: 'Fingerprint / Face ID quick-switch for cashier sessions.',
      },
    ],
  },
  {
    id: 'multistore',
    title: 'Multi-Store',
    subtitle: 'Scale from one outlet to a national chain.',
    features: [
      {
        icon: 'lucide:layout-dashboard',
        title: 'Central Dashboard',
        description: 'See consolidated revenue, orders, and stock alerts across all outlets.',
      },
      {
        icon: 'lucide:arrow-left-right',
        title: 'Inter-Store Transfers',
        description: 'Move stock between outlets with a full transfer history.',
      },
      {
        icon: 'lucide:sliders',
        title: 'Per-Outlet Config',
        description: 'Different tax rates, printers, and user permissions per store.',
      },
    ],
  },
  {
    id: 'hardware',
    title: 'Hardware Support',
    subtitle: 'Works with the hardware you already own.',
    features: [
      {
        icon: 'lucide:printer',
        title: 'ESC/POS Printers',
        description: 'USB and network thermal printers — no driver installation needed.',
      },
      {
        icon: 'lucide:scan-barcode',
        title: 'Barcode Scanners',
        description: 'USB HID scanners work plug-and-play. Camera scanning via ML Kit.',
      },
      {
        icon: 'lucide:tablet',
        title: 'Android Tablets',
        description: 'Optimized for 10" Android tablets (minSdk 24 — Android 7+).',
      },
    ],
  },
  {
    id: 'offline',
    title: 'Offline-First Architecture',
    subtitle: 'Never lose a sale, no matter the connection.',
    features: [
      {
        icon: 'lucide:database',
        title: 'Local-First DB',
        description: 'All data lives in an encrypted SQLite DB on the device.',
      },
      {
        icon: 'lucide:refresh-cw',
        title: 'Background Sync',
        description: 'Operations queue locally and sync to the cloud when connected.',
      },
      {
        icon: 'lucide:cloud',
        title: 'Conflict Resolution',
        description: 'Smart merge logic ensures data consistency across devices.',
      },
    ],
  },
];
