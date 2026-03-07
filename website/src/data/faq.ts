export interface FaqItem {
  question: string;
  answer: string;
  category: string;
}

export const faqItems: FaqItem[] = [
  // Getting Started
  {
    category: 'Getting Started',
    question: 'How do I download ZyntaPOS?',
    answer:
      'ZyntaPOS is available on the Google Play Store for Android tablets (Android 7+, 10" recommended). Search for "ZyntaPOS" or click the "Download" button on our homepage. Desktop (Windows/macOS/Linux) versions are available from our releases page.',
  },
  {
    category: 'Getting Started',
    question: 'Do I need internet to set up ZyntaPOS?',
    answer:
      'No. You can complete the initial setup — business name, admin account, products, and pricing — entirely offline. An internet connection is only required to activate your license and to sync data to the cloud.',
  },
  {
    category: 'Getting Started',
    question: 'How long does it take to make the first sale?',
    answer:
      'Most new users complete setup and process their first test transaction in under 15 minutes. The onboarding wizard guides you through: business name → admin PIN → add your first product → open the register → checkout.',
  },
  // Billing & Licensing
  {
    category: 'Billing & Licensing',
    question: 'How does licensing work?',
    answer:
      'Each terminal (device) requires one license slot. A Starter license covers 1 terminal for free. Professional covers up to 5, and Enterprise is unlimited. Licenses are managed in your online dashboard at panel.zyntapos.com.',
  },
  {
    category: 'Billing & Licensing',
    question: 'Can I use ZyntaPOS on multiple devices?',
    answer:
      'Yes. Professional supports up to 5 devices and Enterprise is unlimited. Each device runs independently offline and syncs to your shared account when connected.',
  },
  {
    category: 'Billing & Licensing',
    question: 'What payment methods do you accept?',
    answer:
      'We accept major credit/debit cards (Visa, Mastercard) and bank transfer for annual plans. LKR invoicing is available for Sri Lankan businesses.',
  },
  // Technical
  {
    category: 'Technical',
    question: 'What happens if I lose internet connection during a sale?',
    answer:
      'Nothing — ZyntaPOS continues working normally. All transactions are saved to the local encrypted database immediately. When your connection is restored, all transactions sync to the cloud automatically in the background.',
  },
  {
    category: 'Technical',
    question: 'Which Android tablets are supported?',
    answer:
      'ZyntaPOS supports Android 7.0 (API 24) and above. We recommend 10" tablets with at least 4GB RAM for the best experience. Tested on Samsung Tab A series, Lenovo M10, and Huawei MatePad.',
  },
  {
    category: 'Technical',
    question: 'Which printers are compatible?',
    answer:
      'Any ESC/POS-compatible thermal receipt printer works via USB or network (TCP/IP). Popular models include Epson TM-T20, Star TSP100, and BIXOLON SRP-350. Network printers connect over Wi-Fi.',
  },
  {
    category: 'Technical',
    question: 'Can I use a barcode scanner with ZyntaPOS?',
    answer:
      'Yes. USB HID barcode scanners work plug-and-play — no setup required. ZyntaPOS also has built-in camera barcode scanning using ML Kit for scanning with the device camera.',
  },
  // Security & Privacy
  {
    category: 'Security & Privacy',
    question: 'How is my business data protected?',
    answer:
      'All data is encrypted using AES-256-GCM via SQLCipher 4.5. Encryption keys are stored in Android Keystore hardware — they never leave the device in plaintext. Even if a device is stolen, the data cannot be read without the key.',
  },
  {
    category: 'Security & Privacy',
    question: 'Can Zynta Solutions access my business data?',
    answer:
      'No. Data on your device is encrypted with keys only your device holds. Cloud backups are encrypted before upload. We have no technical means to read your transaction data, customer information, or business records.',
  },
  {
    category: 'Security & Privacy',
    question: 'How do I export my data if I decide to leave?',
    answer:
      'ZyntaPOS supports full data export from the Settings → Data Export screen. You can export all sales, customers, inventory, and audit logs in CSV format at any time. GDPR requests are handled within 30 days.',
  },
  {
    category: 'Security & Privacy',
    question: 'Is ZyntaPOS GDPR compliant?',
    answer:
      'Yes. Customer data is stored only on your device and your cloud account. You control deletion and export. We provide a data processing agreement (DPA) for Professional and Enterprise customers on request.',
  },
];
