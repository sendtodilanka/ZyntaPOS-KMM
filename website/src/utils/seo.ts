import { company } from '@data/company';

export interface SeoProps {
  title: string;
  description: string;
  canonical: string;
  ogImage?: string;
  ogType?: string;
  noindex?: boolean;
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
    url: company.siteUrl,
    logo: `${company.siteUrl}/logo.svg`,
    sameAs: [company.linkedin, company.twitter ? `https://twitter.com/${company.twitter.replace('@', '')}` : ''].filter(Boolean),
    contactPoint: {
      '@type': 'ContactPoint',
      email: company.email,
      contactType: 'customer support',
    },
  };
}

export function buildWebSiteJsonLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: company.brand,
    url: company.siteUrl,
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
    operatingSystem: 'Android, Windows, macOS, Linux',
    applicationCategory: 'BusinessApplication',
    offers: [
      {
        '@type': 'Offer',
        price: '0',
        priceCurrency: 'USD',
        name: 'Starter',
      },
      {
        '@type': 'Offer',
        price: '29',
        priceCurrency: 'USD',
        name: 'Professional',
        priceSpecification: {
          '@type': 'UnitPriceSpecification',
          price: '29',
          priceCurrency: 'USD',
          unitCode: 'MON',
        },
      },
      {
        '@type': 'Offer',
        price: '79',
        priceCurrency: 'USD',
        name: 'Enterprise',
        priceSpecification: {
          '@type': 'UnitPriceSpecification',
          price: '79',
          priceCurrency: 'USD',
          unitCode: 'MON',
        },
      },
    ],
    url: company.siteUrl,
    downloadUrl: company.playStore,
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
