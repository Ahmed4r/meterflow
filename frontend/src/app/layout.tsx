import type { Metadata } from 'next';
import './globals.css';
import { Providers } from '@/components/providers';
export const metadata: Metadata = {
  title: 'MeterFlow · Usage & Billing',
  description: 'Usage metering, clear billing, informed decisions.',
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
