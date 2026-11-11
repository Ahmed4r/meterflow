import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
const env = Object.fromEntries(
  readFileSync('../.env', 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
test('seeded dashboard, navigation, filters, theme and mobile layout', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@meterflow.test');
  await page.getByLabel('Password', { exact: true }).fill(env.DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  await expect(page.getByText('Usage over time')).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(5);
  await page.screenshot({ path: 'test-results/overview-light.png', fullPage: true });
  await page.getByRole('button', { name: 'Bars', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Bars', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.screenshot({ path: 'test-results/overview-dark.png', fullPage: true });
  await page.getByRole('link', { name: 'Usage analytics', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Usage analytics', exact: true })).toBeVisible();
  await page.getByLabel('Filter service').selectOption({ index: 1 });
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export daily CSV' }).click();
  expect((await downloadPromise).suggestedFilename()).toMatch(/usage-.*csv/);
  await page.getByRole('link', { name: 'Invoices', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Invoices', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /INV-/ }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Monthly platform fee')).toBeVisible();
  await page.getByRole('button', { name: 'Close dialog' }).click();
  await page.getByRole('link', { name: 'Billing analyst', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Analyze billing' })).toBeVisible();
  await page.getByRole('link', { name: 'Overview', exact: true }).click();
  await page.getByRole('button', { name: 'Switch to light mode' }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/overview-mobile.png', fullPage: true });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  expect(errors).toEqual([]);
});
test('shared refresh cookie remains valid across simultaneous tabs', async ({ page, context }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@meterflow.test');
  await page.getByLabel('Password', { exact: true }).fill(env.DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  const second = await context.newPage();
  await Promise.all([page.reload(), second.goto('/')]);
  for (const tab of [page, second]) {
    await expect(tab.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
    await tab.getByRole('link', { name: 'Usage analytics', exact: true }).click();
    await expect(tab.getByRole('heading', { name: 'Usage analytics', exact: true })).toBeVisible();
  }
});

test('registration, customer, plan, subscription, key, export and deletion', async ({ page }) => {
  const unique = Date.now().toString(),
    password = 'Synthetic-Test-Password-123!';
  await page.goto('/register');
  await page.getByLabel('Your name').fill('Workflow Test');
  await page.getByLabel('Organization name').fill('Test Workspace ' + unique);
  await page.getByLabel('Email address').fill(`workflow-${unique}@example.test`);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  // A new organization is opted out, so this check never invokes a live AI provider.
  await page.getByRole('link', { name: 'Billing analyst', exact: true }).click();
  await page.getByRole('button', { name: 'Analyze billing' }).click();
  await expect(page.getByText('Verified analytics', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Customers', exact: true }).click();
  await page.getByRole('button', { name: 'Add customer', exact: true }).click();
  await page.getByLabel('Customer name').fill('Test Customer');
  await page.getByLabel('Reference', { exact: true }).fill('TEST-1');
  await page.getByRole('dialog').getByRole('button', { name: 'Add customer' }).click();
  await expect(page.getByText('Test Customer', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Subscriptions', exact: true }).click();
  await page.getByRole('button', { name: 'Create plan', exact: true }).click();
  await page.getByLabel('Plan name').fill('Test Plan');
  await page.getByRole('dialog').getByRole('button', { name: 'Create plan', exact: true }).click();
  await expect(page.getByText('Test Plan', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Assign plan', exact: true }).click();
  await page.getByRole('button', { name: 'Start subscription', exact: true }).click();
  await expect(page.getByText('Active', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'History', exact: true }).click();
  await expect(page.getByText('Created', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Close dialog' }).click();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await page.getByRole('button', { name: 'Cancel subscription', exact: true }).click();
  await expect(page.getByText('Cancelled', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Settings', exact: true }).click();
  await page.getByLabel('Key name').fill('Test key');
  await page.getByRole('button', { name: 'Create API key' }).click();
  await expect(page.getByLabel('New API key')).toHaveValue(/^mf_/);
  await page.getByRole('button', { name: 'Dismiss', exact: true }).click();
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Revoke', exact: true }).click();
  await expect(page.getByText('Revoked', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: /Workflow Test/ }).click();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export JSON' }).click();
  expect((await downloadPromise).suggestedFilename()).toBe('meterflow-personal-data.json');
  await page.getByRole('button', { name: 'Delete account', exact: true }).click();
  await page.getByLabel('Confirm your password').fill(password);
  await page.getByRole('button', { name: 'Permanently delete account' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
});
