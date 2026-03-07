export interface PricingFeature {
  text: string;
  included: boolean;
  note?: string;
}

export interface PricingTier {
  id: string;
  name: string;
  price: string;
  period?: string;
  description: string;
  features: PricingFeature[];
  cta: string;
  ctaHref: string;
  popular?: boolean;
  badge?: string;
}

export const pricingTiers: PricingTier[] = [
  {
    id: 'starter',
    name: 'STARTER',
    price: 'Free',
    description: 'Perfect for small shops getting started with digital POS.',
    features: [
      { text: '1 terminal', included: true },
      { text: '1 store', included: true },
      { text: '2 user accounts', included: true },
      { text: 'Core POS & checkout', included: true },
      { text: 'Basic inventory', included: true },
      { text: 'Daily sales summary', included: true },
      { text: 'Receipt printing', included: true },
      { text: 'Barcode scanning', included: true },
      { text: 'Customer directory', included: false },
      { text: 'Coupons & promotions', included: false },
      { text: 'Advanced reports + export', included: false },
      { text: 'Multi-store', included: false },
      { text: 'Audit log', included: false },
      { text: 'E-Invoice (IRD)', included: false },
      { text: 'Priority support', included: false },
    ],
    cta: 'Start Free',
    ctaHref: '/pricing#download',
  },
  {
    id: 'professional',
    name: 'PROFESSIONAL',
    price: '$29',
    period: '/mo',
    description: 'For growing businesses that need advanced features and full reporting.',
    features: [
      { text: 'Up to 5 terminals', included: true },
      { text: '1 store', included: true },
      { text: '10 user accounts', included: true },
      { text: 'Core POS & checkout', included: true },
      { text: 'Advanced inventory', included: true },
      { text: 'Full reports + CSV/PDF export', included: true },
      { text: 'Receipt printing', included: true },
      { text: 'Barcode scanning', included: true },
      { text: 'Customer directory + loyalty', included: true },
      { text: 'Coupons & promotions', included: true },
      { text: 'Basic audit log', included: true },
      { text: 'Email support (48h SLA)', included: true },
      { text: 'Multi-store', included: false },
      { text: 'E-Invoice (IRD)', included: false },
      { text: 'Priority support (4h SLA)', included: false },
    ],
    cta: 'Start Free Trial',
    ctaHref: '/pricing#trial',
    popular: true,
    badge: 'Most Popular',
  },
  {
    id: 'enterprise',
    name: 'ENTERPRISE',
    price: '$79',
    period: '/mo',
    description: 'For chains and enterprises requiring multi-store management and compliance.',
    features: [
      { text: 'Unlimited terminals', included: true },
      { text: 'Unlimited stores', included: true },
      { text: 'Unlimited users', included: true },
      { text: 'Core POS & checkout', included: true },
      { text: 'Advanced inventory + inter-store', included: true },
      { text: 'Full reports + custom + API', included: true },
      { text: 'Receipt printing', included: true },
      { text: 'Barcode scanning', included: true },
      { text: 'Customer directory + loyalty', included: true },
      { text: 'Coupons & promotions', included: true },
      { text: 'Full audit log + integrity chain', included: true },
      { text: 'Multi-store central dashboard', included: true },
      { text: 'E-Invoice (IRD)', included: true },
      { text: 'Priority support (4h SLA) + dedicated', included: true },
    ],
    cta: 'Contact Sales',
    ctaHref: '/about#contact',
  },
];

export const pricingFaq = [
  {
    question: 'Is there a free trial for paid plans?',
    answer:
      'Yes — both Professional and Enterprise plans include a 14-day free trial. No credit card required to start.',
  },
  {
    question: 'Can I upgrade or downgrade my plan?',
    answer:
      'Absolutely. You can upgrade at any time; the price difference is prorated. Downgrades take effect at the next billing cycle.',
  },
  {
    question: 'What happens to my data if I cancel?',
    answer:
      'Your data remains on the device in the encrypted local database. You can export it at any time via the built-in export feature before cancelling.',
  },
  {
    question: 'Is pricing in USD or LKR?',
    answer:
      'Plans are listed in USD. LKR invoicing is available for Sri Lankan businesses — contact sales@zyntapos.com.',
  },
  {
    question: 'Do you offer discounts for annual billing?',
    answer:
      'Yes — annual billing gives you 2 months free (equivalent to ~17% off). Switch to annual in your license dashboard.',
  },
];
