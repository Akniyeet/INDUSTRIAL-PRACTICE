import type { Config } from 'tailwindcss'

/**
 * Webizon design tokens.
 *
 * The palette is anchored on a single `brand` scale (indigo-leaning blue) plus
 * semantic accents (`success`, `warning`, `danger`) so that components can be
 * written against intent rather than raw colour values. Keep this file as the
 * single source of truth — ad-hoc hex values in components are a review smell.
 */
export default <Partial<Config>>{
  darkMode: 'class',
  content: [
    './app/**/*.{vue,ts}',
    './shared/**/*.ts',
    './nuxt.config.ts',
  ],
  theme: {
    extend: {
      colors: {
        // Primary brand: derived from logo #215CFF
        brand: {
          50:  '#eff3ff',
          100: '#dbe4ff',
          200: '#bfcfff',
          300: '#93adff',
          400: '#6082ff',
          500: '#3d63ff',
          600: '#215CFF', // logo primary
          700: '#1a4adb',
          800: '#1c3db2',
          900: '#1d378c',
          950: '#142255',
        },
        // Accent: derived from logo #FF428D
        accent: {
          50:  '#fff1f6',
          100: '#ffe4ed',
          200: '#ffcadc',
          300: '#ff9ebc',
          400: '#ff6a98',
          500: '#FF428D', // logo secondary
          600: '#ed1166',
          700: '#c80a53',
          800: '#a80c49',
          900: '#8f0f42',
          950: '#520121',
        },
        success: {
          50:  '#ecfdf5',
          500: '#10b981',
          600: '#059669',
          700: '#047857',
        },
        warning: {
          50:  '#fffbeb',
          500: '#f59e0b',
          600: '#d97706',
          700: '#b45309',
        },
        danger: {
          50:  '#fef2f2',
          500: '#ef4444',
          600: '#dc2626',
          700: '#b91c1c',
        },
      },
      fontFamily: {
        sans: ['"DM Sans"', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      boxShadow: {
        // Soft elevation for cards and popovers — tuned to look crisp on the
        // slate-50 page background without adding a visible halo.
        soft:   '0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.06)',
        pop:    '0 4px 6px rgba(15, 23, 42, 0.05), 0 10px 20px rgba(15, 23, 42, 0.08)',
        overlay:'0 10px 30px rgba(15, 23, 42, 0.12), 0 30px 60px rgba(15, 23, 42, 0.16)',
      },
      borderRadius: {
        xl:  '0.875rem',
        '2xl': '1.125rem',
      },
      transitionTimingFunction: {
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
    },
  },
  plugins: [],
}
