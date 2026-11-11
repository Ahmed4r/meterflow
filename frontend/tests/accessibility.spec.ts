import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { readFileSync } from 'node:fs';
const password = readFileSync('../.env', 'utf8')
  .split(/\r?\n/)
  .find((x) => x.startsWith('DEMO_PASSWORD='))!
  .slice('DEMO_PASSWORD='.length);
test('login and dashboard have no serious accessibility violations', async ({ page }) => {
  await page.goto('/login');
  const login = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(login.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')).toEqual(
    [],
  );
  await page.getByLabel('Email address').fill('demo@meterflow.test');
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByText('Usage over time')).toBeVisible();
  const dashboard = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    dashboard.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious'),
  ).toEqual([]);
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  const dark = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(dark.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')).toEqual(
    [],
  );
});
