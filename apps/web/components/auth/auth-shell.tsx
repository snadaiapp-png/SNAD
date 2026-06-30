import Image from "next/image";
import type { ReactNode } from "react";
import logoStyles from "./auth-logo.module.css";
import panelStyles from "./auth-panel.module.css";
import "./auth-hero-motion.css";

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

function DataFlow() {
  const nodes = [
    { x: 991, y: 108, tone: "gold" },
    { x: 1208, y: 121, tone: "gold" },
    { x: 1335, y: 250, tone: "cyan" },
    { x: 1376, y: 405, tone: "gold" },
    { x: 1322, y: 550, tone: "gold" },
    { x: 1190, y: 676, tone: "gold" },
    { x: 1019, y: 715, tone: "cyan" },
    { x: 827, y: 650, tone: "cyan" },
    { x: 708, y: 416, tone: "cyan" },
  ] as const;

  return (
    <svg className="snad-auth-data-flow" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <defs>
        <linearGradient id="snad-auth-flow-gradient" x1="0" x2="1">
          <stop offset="0%" stopColor="#38f8ff"/>
          <stop offset="42%" stopColor="#00a7a0"/>
          <stop offset="76%" stopColor="#ffd76a"/>
          <stop offset="100%" stopColor="#d4af37"/>
        </linearGradient>
        <filter id="snad-auth-soft-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="4" result="blur"/>
          <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
      </defs>

      <g className="snad-auth-em-grid">
        <path className="snad-auth-flow-track" d="M1030 410 C1010 280 1000 205 991 108"/>
        <path className="snad-auth-flow-pulse" d="M1030 410 C1010 280 1000 205 991 108"/>
        <path className="snad-auth-flow-track" d="M1050 410 C1130 300 1180 210 1208 121"/>
        <path className="snad-auth-flow-pulse" d="M1050 410 C1130 300 1180 210 1208 121"/>
        <path className="snad-auth-flow-track" d="M1080 430 C1200 370 1280 300 1335 250"/>
        <path className="snad-auth-flow-pulse" d="M1080 430 C1200 370 1280 300 1335 250"/>
        <path className="snad-auth-flow-track" d="M1090 455 C1225 450 1310 425 1376 405"/>
        <path className="snad-auth-flow-pulse" d="M1090 455 C1225 450 1310 425 1376 405"/>
        <path className="snad-auth-flow-track" d="M1080 485 C1210 520 1270 540 1322 550"/>
        <path className="snad-auth-flow-pulse" d="M1080 485 C1210 520 1270 540 1322 550"/>
        <path className="snad-auth-flow-track" d="M1060 510 C1120 585 1160 635 1190 676"/>
        <path className="snad-auth-flow-pulse" d="M1060 510 C1120 585 1160 635 1190 676"/>
        <path className="snad-auth-flow-track" d="M1030 520 C1025 590 1022 650 1019 715"/>
        <path className="snad-auth-flow-pulse" d="M1030 520 C1025 590 1022 650 1019 715"/>
        <path className="snad-auth-flow-track" d="M995 510 C930 580 875 625 827 650"/>
        <path className="snad-auth-flow-pulse" d="M995 510 C930 580 875 625 827 650"/>
        <path className="snad-auth-flow-track" d="M970 470 C875 455 790 435 708 416"/>
        <path className="snad-auth-flow-pulse" d="M970 470 C875 455 790 435 708 416"/>
      </g>

      <g className="snad-auth-node-status">
        {nodes.map((node) => (
          <g key={`${node.x}-${node.y}`} transform={`translate(${node.x} ${node.y})`}>
            <circle className={`snad-auth-status-ring snad-auth-status-ring--${node.tone}`} r="50"/>
            <circle className={`snad-auth-status-core snad-auth-status-core--${node.tone}`} r="3.4"/>
          </g>
        ))}
      </g>

      <circle className="snad-auth-particle snad-auth-particle-a" r="4"/>
      <circle className="snad-auth-particle snad-auth-particle-b" r="3.5"/>
      <circle className="snad-auth-particle snad-auth-particle-c" r="3"/>
    </svg>
  );
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
    <main className="snad-auth-scene snad-auth-scene-hero" dir="rtl">
      <div className="snad-auth-hero-overlay" aria-hidden="true"/>
      <Image
        src="/brand/snad-login-hero-ai.png"
        alt=""
        fill
        priority
        sizes="100vw"
        className="snad-auth-hero-bg"
      />
      <DataFlow/>
      <section className={`snad-auth-card ${panelStyles.panel} ${wide ? panelStyles.registrationPanel : ""}`}>{children}</section>
    </main>
  );
}
