export interface IndustryVertical {
  id: string;
  title: string;
  headline: string;
  description: string;
  icon: string;
  features: string[];
  keywords: string[];
}

export const industries: IndustryVertical[] = [
  {
    id: 'retail',
    title: 'Retail / Fashion',
    headline: 'Built for Modern Retail',
    description:
      'From corner boutiques to multi-outlet fashion chains — ZyntaPOS handles your full retail operation with barcode scanning, variant management, and loyalty programs.',
    icon: 'lucide:shopping-bag',
    features: [
      'Barcode scanning for fast checkout',
      'Size, color & variant tracking',
      'Low-stock alerts & reorder reminders',
      'Customer loyalty points',
      'Multi-outlet stock visibility',
      'CSV/PDF sales reports',
    ],
    keywords: ['retail POS', 'fashion POS', 'clothing store POS Sri Lanka'],
  },
  {
    id: 'restaurant',
    title: 'Restaurant / Café',
    headline: 'Speed Up Your Service',
    description:
      'Quick-add menu items, split bills, hold orders for table service, and print kitchen receipts — ZyntaPOS keeps your café running smoothly even during the busiest hours.',
    icon: 'lucide:utensils',
    features: [
      'Fast product grid for quick orders',
      'Hold & resume orders for tables',
      'Split payment (cash + card)',
      'ESC/POS kitchen receipt printing',
      'End-of-day Z-report for reconciliation',
      'Offline operation — no Wi-Fi needed',
    ],
    keywords: ['restaurant POS', 'café POS', 'food & beverage POS Sri Lanka'],
  },
  {
    id: 'grocery',
    title: 'Grocery / Supermarket',
    headline: 'Handle High Volume with Ease',
    description:
      'Full-text search finds products in milliseconds, barcode scanning handles high throughput, and daily Z-reports keep your cashiers accountable at every shift.',
    icon: 'lucide:shopping-basket',
    features: [
      'Full-text search across 10,000+ products',
      'Multi-barcode per product',
      'Weight-based item support',
      'Daily Z-report + cash reconciliation',
      'Supplier management',
      'Stock adjustment with audit trail',
    ],
    keywords: ['grocery POS', 'supermarket POS', 'convenience store POS Sri Lanka'],
  },
  {
    id: 'pharmacy',
    title: 'Pharmacy',
    headline: 'Accurate Dispensing, Every Time',
    description:
      'Track batch numbers, expiry dates, and regulated product flags. Full audit logging ensures every dispensing event is recorded and tamper-proof.',
    icon: 'lucide:pill',
    features: [
      'Batch & expiry date tracking',
      'Regulated product flags',
      'Full audit log for compliance',
      'Customer prescription history',
      'Low-stock alerts for critical medicines',
      'AES-256 encrypted data at rest',
    ],
    keywords: ['pharmacy POS', 'dispensary POS', 'medical store POS Sri Lanka'],
  },
];
