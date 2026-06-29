import Image from "next/image";
import type { ReactNode } from "react";
import logoStyles from "./auth-logo.module.css";
import panelStyles from "./auth-panel.module.css";

export type IconProps = { className?: string };

export function IconMail({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16v12H4z" stroke="currentColor" strokeWidth="1.7"/><path d="m5 7 7 6 7-6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}

export function IconLock({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="1.7"/><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" strokeWidth="1.7"/><path d="M12 14v2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"/></svg>;
}

export function IconEye({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M2.5 12s3.5-5 9.5-5 9.5 5 9.5 5-3.5 5-9.5 5-9.5-5-9.5-5Z" stroke="currentColor" strokeWidth="1.6"/><circle cx="12" cy="12" r="2.5" stroke="currentColor" strokeWidth="1.6"/></svg>;
}

export function IconPerson({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="7" r="3.5" stroke="currentColor" strokeWidth="1.6"/><path d="M5 20c.6-4 3-6 7-6s6.4 2 7 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>;
}

export function IconBuilding({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 21V5l8-3 8 3v16M2 21h20" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/><path d="M8 7h2M14 7h2M8 11h2M14 11h2M8 15h2M14 15h2M10 21v-3h4v3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>;
}

export function IconGlobe({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6"/><path d="M3.5 12h17M12 3c2.4 2.5 3.6 5.5 3.6 9S14.4 18.5 12 21M12 3C9.6 5.5 8.4 8.5 8.4 12S9.6 18.5 12 21" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round"/></svg>;
}

export function BrandHeader({ title, description }: { title: string; description: string }) {
  return (
    <header className="snad-auth-brand">
      <div className={logoStyles.frame}>
        <span className={logoStyles.glow} aria-hidden="true"/>
        <Image src="/brand/snad-logo-original.png" alt="شعار منصة سند" width={1448} height={1086} sizes="(max-width: 760px) 168px, 190px" priority className={logoStyles.image}/>
      </div>
      <p className="snad-auth-subtitle">منظومة الأعمال الرقمية</p>
      {title && <h1 className="snad-auth-title">{title}</h1>}
      <p className="snad-auth-description">{description}</p>
    </header>
  );
}

export function AuthShell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <main className="snad-auth-scene" dir="rtl">
      <section className={`snad-auth-card ${panelStyles.panel} ${wide ? panelStyles.registrationPanel : ""}`}>{children}</section>
    </main>
  );
}
