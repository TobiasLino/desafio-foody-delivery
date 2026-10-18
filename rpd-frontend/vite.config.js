import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Dentro do Docker (principalmente no Windows e no macOS) o Vite nem sempre percebe que um
    // arquivo mudou; o polling resolve isso.
    watch: { usePolling: true },
  },
})
