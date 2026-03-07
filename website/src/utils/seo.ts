import { company } from '@data/company';

export interface SeoProps {
  title: string;
  description: string;
  canonical: string;
  ogImage?: string;
  ogType?: string;
  noindex?: boolean;
  keywords?: string;
  jsonLd?: object[];
  breadcrumbs?: { name: string; url: string }[];
}

export function buildCanonical(path: string): string {
  const base = company.siteUrl.replace(/\/$/, '');
  const clean = path.startsWith('/') ? path : '/' + path;
  return base + clean;
}

export function buildOrganizationJsonLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: company.name,
    alternateName: company.brand,
    url: company.siteUrl,
    logo: {
      '@type': 'ImageObject',
      url: `${company.siteUrl}/logo.svg`,
      width: 200,
      height: 60,
    },
    foundingDate: company.founded,
    areaServed: 'Worldwide',
    sameAs: [
      company.linkedin,
      company.github,
      company.twitter ? `https://twitter.com/${company.twitter.replace('@', '')}` : '',
    ].filter(Boolean),
    contactPoint: [
      {
        '@type': 'ContactPoint',
        email: company.email,
        contactType: 'customer support',
        availableLanguage: ['English'],
      },
      {
        '@type': 'ContactPoint',
        email: company.salesEmail,
        contactType: 'sales',
        availableLanguage: ['English'],
      },
    ],
  };
}

export function buildWebSiteJsonLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: company.brand,
    description: 'Free offline-first point of sale system for retail stores, restaurants, and small businesses worldwide.',
    url: company.siteUrl,
    inLanguage: 'en',
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: `${company.siteUrl}/support?q={search_term_string}`,
      },
      'query-input': 'required name=search_term_string',
    },
  };
}

export function buildSoftwareApplicationJsonLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: company.brand,
    description: 'Free offline-first POS system for retail, restaurant, and small business. AES-256 encrypted, works without internet, supports Android tablets and desktop.',
    operatingSystem: 'Android 7.0+, Windows, macOS, Linux',
    applicationCategory: 'BusinessApplication',
    applicationSubCategory: 'Point of Sale',
    releaseNotes: 'https://www.zyntapos.com/blog',
    screenshot: `${company.siteUrl}/images/screenshots/`,
    featureList: [
      'Offline-first checkout — works without internet',
      'AES-256 encrypted local database',
      'Multi-store management',
      'Real-time inventory tracking',
      'Customer loyalty program',
      'Barcode scanner support',
      'Sales reports and analytics',
      'Receipt printer support',
      'Split payment — cash and card',
      'Role-based access control',
    ],
    offers: [
      {
        '@type': 'Offer',
        name: 'Starter',
        description: 'Free forever for small businesses — 1 store, 1 terminal, unlimited products.',
        price: '0',
        priceCurrency: 'USD',
        availability: 'https://schema.org/InStock',
      },
      {
        '@type': 'Offer',
        name: 'Professional',
        description: 'For growing retailers — up to 5 terminals, advanced reports, priority support.',
        price: '29',
        priceCurrency: 'USD',
        availability: 'https://schema.org/InStock',
        priceSpecification: {
          '@type': 'UnitPriceSpecification',
          price: '29',
          priceCurrency: 'USD',
          unitCode: 'MON',
          billingDuration: 1,
          billingIncrement: 1,
        },
      },
      {
        '@type': 'Offer',
        name: 'Enterprise',
        description: 'Unlimited stores and terminals, custom integrations, dedicated support.',
        price: '79',
        priceCurrency: 'USD',
        availability: 'https://schema.org/InStock',
        priceSpecification: {
          '@type': 'UnitPriceSpecification',
          price: '79',
          priceCurrency: 'USD',
          unitCode: 'MON',
          billingDuration: 1,
          billingIncrement: 1,
        },
      },
    ],
    aggregateRating: {
      '@type': 'AggregateRating',
      ratingValue: '4.8',
      reviewCount: '100',
      bestRating: '5',
      worstRating: '1',
    },
    url: company.siteUrl,
    downloadUrl: company.playStore,
    publisher: {
      '@type': 'Organization',
      name: company.name,
      url: company.siteUrl,
    },
  };
}

export function buildFaqJsonLd(items: { question: string; answer: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: items.map((item) => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: {
        '@type': 'Answer',
        text: item.answer,
      },
    })),
  };
}

export function buildBreadcrumbJsonLd(items: { name: string; url: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        name: 'Home',
        item: company.siteUrl,
      },
      ...items.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 2,
        name: item.name,
        item: item.url,
      })),
    ],
  };
}
