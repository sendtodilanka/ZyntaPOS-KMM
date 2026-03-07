import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import icon from 'astro-icon';

export default defineConfig({
  site: 'https://www.zyntapos.com',
  integrations: [
    sitemap(),
    icon({
      iconDir: 'src/icons',
    }),
  ],
  vite: {
    plugins: [tailwindcss()],
  },
});
