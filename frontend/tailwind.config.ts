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
  content: [
    './app/**/*.{vue,ts}',
    './components/**/*.{vue,ts}',
    './layouts/**/*.vue',
    './pages/**/*.vue',
    './composables/**/*.ts',
    './stores/**/*.ts',
    './nuxt.config.ts',
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#eef4ff',
          100: '#dbe6ff',
          200: '#bdd0ff',
          300: '#8cadff',
          400: '#5981ff',
          500: '#305aff',
          600: '#1b3ef5',
          700: '#162fcc',
          800: '#152aa1',
          900: '#152580',
          950: '#0d174d',
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
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
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
      keyframes: {
        'fade-in': {
          from: { opacity: '0' },
          to:   { opacity: '1' },
        },
        'slide-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
        'pulse-ring': {
          '0%':   { transform: 'scale(0.75)', opacity: '0.8' },
          '100%': { transform: 'scale(1.75)', opacity: '0' },
        },
      },
      animation: {
        'fade-in':  'fade-in 200ms ease-out',
        'slide-up': 'slide-up 220ms cubic-bezier(0.16, 1, 0.3, 1)',
        'pulse-ring': 'pulse-ring 1.6s ease-out infinite',
      },
    },
  },
  plugins: [],
}
