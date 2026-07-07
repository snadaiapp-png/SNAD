'use client';

import { forwardRef, type ReactNode } from 'react';
import { SnadLogo } from '@/components/sds';
import { useI18n, type Locale } from '@/app/providers';
import { useTheme, type ThemePreference } from '@/lib/hooks/useTheme';
import styles from './ExecutiveShell.module.css';

export interface ExecutiveShellProps {
  children: ReactNode;
  menuButton?: ReactNode;
  organizationName?: ReactNode;
  search?: ReactNode;
  notifications?: ReactNode;
  aiAssistant?: ReactNode;
  userProfile?: ReactNode;
  logoHref?: string;
  logoAriaLabel?: string;
  className?: string;
}

export const ExecutiveShell = forwardRef<HTMLElement, ExecutiveShellProps>(
  function ExecutiveShell(
    {
      children,
      menuButton,
      organizationName,
      search,
      notifications,
      aiAssistant,
      userProfile,
      logoHref = '/workspace',
      logoAriaLabel,
      className,
    },
    ref,
  ) {
    const { locale, setLocale, t } = useI18n();
    const { preference, setTheme } = useTheme();
    const accessibleLogoLabel = logoAriaLabel ?? t('workspaceLink');
    const headerClass = [styles.shell, className ?? ''].filter(Boolean).join(' ');

    return (
      <div className={styles.layout}>
        <header ref={ref} className={headerClass} role="banner">
          <div className={styles.bar}>
            <div className={styles.startCluster}>
              {menuButton ? <div className={styles.menuSlot}>{menuButton}</div> : null}
              <a href={logoHref} className={styles.logoLink} aria-label={accessibleLogoLabel}>
                <SnadLogo variant="compact" size="md" alt={accessibleLogoLabel} />
              </a>
            </div>

            {organizationName ? (
              <div className={styles.orgSlot}>
                <span className={styles.orgName}>{organizationName}</span>
              </div>
            ) : null}

            <div className={styles.endCluster}>
              <div className={styles.searchSlot} role="group" aria-label={`${t('language')} · ${t('appearance')}`}>
                <select
                  className={styles.profileSlot}
                  value={locale}
                  onChange={(event) => setLocale(event.target.value as Locale)}
                  aria-label={t('language')}
                >
                  <option value="ar">{t('arabic')}</option>
                  <option value="en">{t('english')}</option>
                </select>
                <select
                  className={styles.profileSlot}
                  value={preference}
                  onChange={(event) => setTheme(event.target.value as ThemePreference)}
                  aria-label={t('appearance')}
                >
                  <option value="light">{t('light')}</option>
                  <option value="dark">{t('dark')}</option>
                  <option value="system">{t('system')}</option>
                </select>
              </div>
              {search ? <div className={styles.searchSlot}>{search}</div> : null}
              {notifications ? <div className={styles.iconSlot}>{notifications}</div> : null}
              {aiAssistant ? <div className={styles.iconSlot}>{aiAssistant}</div> : null}
              {userProfile ? <div className={styles.profileSlot}>{userProfile}</div> : null}
            </div>
          </div>
        </header>

        <main id="main-content" className={styles.main} role="main">
          {children}
        </main>
      </div>
    );
  },
);

ExecutiveShell.displayName = 'ExecutiveShell';
