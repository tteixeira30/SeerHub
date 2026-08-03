/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Escala neutra da aplicação: fundo quase preto com um toque de azul,
        // para o verde da marca destacar sem cansar a vista.
        ink: {
          50: "#F6F8FB",
          100: "#E8EDF5",
          200: "#CBD5E4",
          300: "#9CA9BD",
          400: "#71809A",
          500: "#4F5D75",
          600: "#3A4657",
          700: "#2A3444",
          750: "#212A38",
          800: "#19212C",
          850: "#131A24",
          900: "#0D131B",
          950: "#070B10",
        },
        // Verde de relvado: a única cor de ação da interface.
        brand: {
          200: "#A7F3D0",
          300: "#6EE7B7",
          400: "#34D399",
          500: "#10B981",
          600: "#059669",
          700: "#047857",
        },
      },
      fontFamily: {
        sans: [
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "monospace"],
      },
      boxShadow: {
        card: "0 1px 2px rgba(0,0,0,0.35), 0 12px 32px -18px rgba(0,0,0,0.9)",
        lifted: "0 1px 2px rgba(0,0,0,0.35), 0 24px 48px -24px rgba(0,0,0,0.95)",
        brand: "0 10px 30px -12px rgba(16,185,129,0.75)",
      },
      keyframes: {
        "fade-up": {
          from: { opacity: "0", transform: "translateY(6px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        shimmer: {
          "100%": { transform: "translateX(100%)" },
        },
      },
      animation: {
        "fade-up": "fade-up 300ms cubic-bezier(0.22, 1, 0.36, 1) both",
        shimmer: "shimmer 1.6s infinite",
      },
    },
  },
  plugins: [],
};
