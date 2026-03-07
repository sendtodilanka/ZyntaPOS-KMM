export interface NavLink {
  label: string;
  href: string;
  external?: boolean;
}

export const mainNav: NavLink[] = [
  { label: 'Features', href: '/features' },
  { label: 'Pricing', href: '/pricing' },
  { label: 'Industries', href: '/industries' },
  { label: 'Download', href: '/download' },
  { label: 'About', href: '/about' },
  { label: 'Support', href: '/support' },
];

export const footerNav: Record<string, NavLink[]> = {
  Product: [
    { label: 'Features', href: '/features' },
    { label: 'Pricing', href: '/pricing' },
    { label: 'Download', href: '/download' },
    { label: 'Industries', href: '/industries' },
    { label: 'Changelog', href: '/blog' },
  ],
  Company: [
    { label: 'About Us', href: '/about' },
    { label: 'Blog', href: '/blog' },
    { label: 'Careers', href: '/about#careers' },
    { label: 'Contact', href: '/about#contact' },
  ],
  Support: [
    { label: 'FAQ', href: '/support' },
    { label: 'Documentation', href: 'https://docs.zyntapos.com', external: true },
    { label: 'System Status', href: 'https://status.zyntapos.com', external: true },
    { label: 'Community', href: '/support#community' },
  ],
  Legal: [
    { label: 'Privacy Policy', href: '/privacy' },
    { label: 'Terms of Service', href: '/terms' },
    { label: 'Cookie Policy', href: '/privacy#cookies' },
    { label: 'GDPR', href: '/privacy#gdpr' },
  ],
};
