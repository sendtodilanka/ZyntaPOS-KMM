import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import icon from 'astro-icon';

export default defineConfig({
  site: 'https://www.zyntapos.com',
  integrations: [
    sitemap({
      filter: (page) => !page.includes('/404'),
      changefreq: 'weekly',
      priority: 0.7,
      serialize(item) {
        // Home page — highest priority
        if (item.url === 'https://www.zyntapos.com/') {
          return { ...item, changefreq: 'daily', priority: 1.0 };
        }
        // Core conversion pages
        if (/\/(features|pricing|industries|download)\/?$/.test(item.url)) {
          return { ...item, changefreq: 'weekly', priority: 0.9 };
        }
        // About / support
        if (/\/(about|support)\/?$/.test(item.url)) {
          return { ...item, changefreq: 'monthly', priority: 0.6 };
        }
        // Blog index
        if (/\/blog\/?$/.test(item.url)) {
          return { ...item, changefreq: 'daily', priority: 0.8 };
        }
        // Individual blog posts
        if (/\/blog\/.+/.test(item.url)) {
          return { ...item, changefreq: 'monthly', priority: 0.6 };
        }
        // Legal pages (privacy, terms)
        if (/\/(privacy|terms)\/?$/.test(item.url)) {
          return { ...item, changefreq: 'yearly', priority: 0.3 };
        }
        return item;
      },
    }),
    icon({
      iconDir: 'src/icons',
    }),
  ],
  vite: {
    plugins: [tailwindcss()],
  },
});
