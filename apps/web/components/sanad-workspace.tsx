"use client";

import { FormEvent, useMemo, useState } from "react";
import { sanadApi } from "@/lib/api";
import type { Membership, Organization, User, UserStatus } from "@/lib/types";

type Section = "overview" | "organizations" | "memberships" | "users";
type Mode = "demo" | "live";

const TENANT = "11111111-1111-1111-1111-111111111111";
const ORG_ID = "22222222-2222-2222-2222-222222222222";
const NOW = "2026-06-19T00:00:00Z";

const initialOrganizations: Organization[] = [
  { id: ORG_ID, tenantId: TENANT, name: "SANAD Technology", description: "21 API operations ready", status: "ACTIVE", createdAt: NOW, updatedAt: NOW },
  { id: "22222222-2222-2222-2222-222222222223", tenantId: TENANT, name: "Jeddah Operations", description: "Customer and commerce operations", status: "ACTIVE", createdAt: NOW, updatedAt: NOW },
];
const initialMemberships: Membership[] = [
  { id: "33333333-3333-3333-3333-333333333333", tenantId: TENANT, organizationId: ORG_ID, email: "owner@sanad.sa", displayName: "Project Owner", status: "ACTIVE", createdAt: NOW, updatedAt: NOW },
  { id: "33333333-3333-3333-3333-333333333334", tenantId: TENANT, organizationId: ORG_ID, email: "operations@sanad.sa", displayName: "Operations", status: "INVITED", createdAt: NOW, updatedAt: NOW },
];
const initialUsers: User[] = [
  { id: "44444444-4444-4444-4444-444444444444", tenantId: TENANT, email: "owner@sanad.sa", displayName: "Project Owner", status: "ACTIVE", createdAt: NOW, updatedAt: NOW },
  { id: "44444444-4444-4444-4444-444444444445", tenantId: TENANT, email: "finance@sanad.sa", displayName: "Finance", status: "INVITED", createdAt: NOW, updatedAt: NOW },
];

function uuid() { return crypto.randomUUID(); }
function replace<T extends { id: string }>(items: T[], item: T) { return items.map((x) => x.id === item.id ? item : x); }
function validUuid(value: string) { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function Status({ value }: { value: string }) { return <span className={`badge badge-${value.toLowerCase()}`}>{value}</span>; }

export default function SanadWorkspace() {
  const [section, setSection] = useState<Section>("overview");
  const [mode, setMode] = useState<Mode>("demo");
  const [tenantInput, setTenantInput] = useState(TENANT);
  const [tenantId, setTenantId] = useState(TENANT);
  const [organizations, setOrganizations] = useState(initialOrganizations);
  const [memberships, setMemberships] = useState(initialMemberships);
  const [users, setUsers] = useState(initialUsers);
  const [selectedOrgId, setSelectedOrgId] = useState(ORG_ID);
  const [orgName, setOrgName] = useState("");
  const [orgDescription, setOrgDescription] = useState("");
  const [memberEmail, setMemberEmail] = useState("");
  const [memberName, setMemberName] = useState("");
  const [userEmail, setUserEmail] = useState("");
  const [userName, setUserName] = useState("");
  const [userStatus, setUserStatus] = useState<UserStatus>("INVITED");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("DEMO");

  const selectedOrg = organizations.find((x) => x.id === selectedOrgId) ?? null;
  const selectedMemberships = memberships.filter((x) => x.organizationId === selectedOrgId);
  const stats = useMemo(() => [organizations.length, memberships.length, users.length], [organizations, memberships, users]);

  function resetDemo() {
    setMode("demo"); setTenantId(TENANT); setTenantInput(TENANT);
    setOrganizations(initialOrganizations); setMemberships(initialMemberships); setUsers(initialUsers);
    setSelectedOrgId(ORG_ID); setNotice("DEMO");
  }

  async function connectLive() {
    if (!validUuid(tenantInput.trim())) { setNotice("INVALID TENANT"); return; }
    setBusy("connect");
    try {
      const id = tenantInput.trim();
      const [orgs, userList] = await Promise.all([sanadApi.listOrganizations(id), sanadApi.listUsers(id)]);
      const first = orgs[0]?.id ?? "";
      const memberList = first ? await sanadApi.listMemberships(id, first) : [];
      setTenantId(id); setOrganizations(orgs); setUsers(userList); setMemberships(memberList);
      setSelectedOrgId(first); setMode("live"); setNotice("LIVE");
    } catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
    finally { setBusy(""); }
  }

  async function chooseOrg(id: string) {
    setSelectedOrgId(id);
    if (mode === "live" && id) {
      setBusy("members");
      try { setMemberships(await sanadApi.listMemberships(tenantId, id)); }
      catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
      finally { setBusy(""); }
    }
  }

  async function addOrganization(event: FormEvent) {
    event.preventDefault(); if (!orgName.trim()) return;
    setBusy("org");
    try {
      const saved = mode === "live" ? await sanadApi.createOrganization(tenantId, { name: orgName, description: orgDescription }) :
        { id: uuid(), tenantId, name: orgName.trim(), description: orgDescription.trim() || null, status: "ACTIVE", createdAt: NOW, updatedAt: new Date().toISOString() } as Organization;
      setOrganizations((items) => [saved, ...items]); setSelectedOrgId(saved.id); setOrgName(""); setOrgDescription(""); setNotice("SAVED");
    } catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
    finally { setBusy(""); }
  }

  async function orgAction(item: Organization, action: string) {
    setBusy(item.id);
    try {
      const status = action === "activate" ? "ACTIVE" : action === "archive" ? "ARCHIVED" : "INACTIVE";
      const saved = mode === "live" ? await sanadApi.transitionOrganization(tenantId, item.id, action) : { ...item, status } as Organization;
      setOrganizations((items) => replace(items, saved)); setNotice("UPDATED");
    } catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
    finally { setBusy(""); }
  }

  async function addMembership(event: FormEvent) {
    event.preventDefault(); if (!selectedOrgId || !memberEmail.trim()) return;
    setBusy("member");
    try {
      const saved = mode === "live" ? await sanadApi.inviteMembership(tenantId, selectedOrgId, { email: memberEmail, displayName: memberName }) :
        { id: uuid(), tenantId, organizationId: selectedOrgId, email: memberEmail.trim().toLowerCase(), displayName: memberName.trim() || null, status: "INVITED", createdAt: NOW, updatedAt: NOW } as Membership;
      setMemberships((items) => [saved, ...items]); setMemberEmail(""); setMemberName(""); setNotice("SAVED");
    } catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
    finally { setBusy(""); }
  }

  async function memberAction(item: Membership, action: string) {
    const status = action === "activate" ? "ACTIVE" : action === "remove" ? "REMOVED" : "INACTIVE";
    const saved = mode === "live" ? await sanadApi.transitionMembership(tenantId, item.organizationId, item.id, action) : { ...item, status } as Membership;
    setMemberships((items) => replace(items, saved)); setNotice("UPDATED");
  }

  async function addUser(event: FormEvent) {
    event.preventDefault(); if (!userEmail.trim()) return;
    setBusy("user");
    try {
      const saved = mode === "live" ? await sanadApi.createUser(tenantId, { email: userEmail, displayName: userName, status: userStatus }) :
        { id: uuid(), tenantId, email: userEmail.trim().toLowerCase(), displayName: userName.trim() || null, status: userStatus, createdAt: NOW, updatedAt: NOW } as User;
      setUsers((items) => [saved, ...items]); setUserEmail(""); setUserName(""); setNotice("SAVED");
    } catch (error) { setNotice(error instanceof Error ? error.message : "ERROR"); }
    finally { setBusy(""); }
  }

  async function userAction(item: User, action: string) {
    const statuses: Record<string, UserStatus> = { activate: "ACTIVE", deactivate: "INACTIVE", suspend: "SUSPENDED", archive: "ARCHIVED" };
    const saved = mode === "live" ? await sanadApi.transitionUser(tenantId, item.id, action) : { ...item, status: statuses[action] };
    setUsers((items) => replace(items, saved)); setNotice("UPDATED");
  }

  const nav: Array<[Section, string, string]> = [
    ["overview", "&#1606;&#1592;&#1585;&#1577; &#1593;&#1575;&#1605;&#1577;", "⌂"],
    ["organizations", "&#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1575;&#1578;", "▦"],
    ["memberships", "&#1575;&#1604;&#1593;&#1590;&#1608;&#1610;&#1575;&#1578;", "◎"],
    ["users", "&#1575;&#1604;&#1605;&#1587;&#1578;&#1582;&#1583;&#1605;&#1608;&#1606;", "♙"],
  ];

  return <div className="workspace-shell">
    <aside className="sidebar"><div className="brand"><span className="brand-mark">S</span><div><strong>SANAD</strong><small>Business OS</small></div></div><nav>{nav.map(([key, label, icon]) => <button key={key} className={section === key ? "nav-item active" : "nav-item"} onClick={() => setSection(key)}><span>{icon}</span><i dangerouslySetInnerHTML={{ __html: label }} /></button>)}</nav><div className="sidebar-foot"><span className="mode-dot" />{mode.toUpperCase()}</div></aside>
    <main className="workspace-main">
      <header className="topbar"><div><small>&#1605;&#1587;&#1575;&#1581;&#1577; &#1575;&#1604;&#1593;&#1605;&#1604;</small><h1 dangerouslySetInnerHTML={{ __html: nav.find(([key]) => key === section)?.[1] ?? "" }} /><p>&#1573;&#1583;&#1575;&#1585;&#1577; &#1575;&#1604;&#1603;&#1610;&#1575;&#1606;&#1575;&#1578; &#1575;&#1604;&#1571;&#1587;&#1575;&#1587;&#1610;&#1577; &#1604;&#1605;&#1606;&#1589;&#1577; SANAD</p></div><div className="avatar">S</div></header>
      <section className="tenant-console"><div><b>Tenant Context</b><p>&#1610;&#1605;&#1585;&#1585; Tenant ID &#1589;&#1585;&#1575;&#1581;&#1577; &#1581;&#1578;&#1609; &#1573;&#1590;&#1575;&#1601;&#1577; &#1575;&#1604;&#1605;&#1589;&#1575;&#1583;&#1602;&#1577;.</p></div><div className="tenant-controls"><input dir="ltr" value={tenantInput} onChange={(e) => setTenantInput(e.target.value)} /><button className="primary" onClick={connectLive} disabled={busy === "connect"}>LIVE</button><button className="secondary" onClick={resetDemo}>DEMO</button></div></section>
      <div className="feedback success">{notice}</div>
      {section === "overview" && <section className="stack"><div className="stats"><article><span>&#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1575;&#1578;</span><strong>{stats[0]}</strong></article><article><span>&#1575;&#1604;&#1593;&#1590;&#1608;&#1610;&#1575;&#1578;</span><strong>{stats[1]}</strong></article><article><span>&#1575;&#1604;&#1605;&#1587;&#1578;&#1582;&#1583;&#1605;&#1608;&#1606;</span><strong>{stats[2]}</strong></article><article><span>&#1575;&#1604;&#1575;&#1578;&#1589;&#1575;&#1604;</span><strong>{mode.toUpperCase()}</strong></article></div><article className="panel"><h2>&#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1577; &#1575;&#1604;&#1605;&#1581;&#1583;&#1583;&#1577;</h2>{selectedOrg ? <div className="summary"><Status value={selectedOrg.status} /><h3>{selectedOrg.name}</h3><p>{selectedOrg.description}</p><button className="link" onClick={() => setSection("memberships")}>&#1601;&#1578;&#1581; &#1575;&#1604;&#1593;&#1590;&#1608;&#1610;&#1575;&#1578;</button></div> : <p>EMPTY</p>}</article></section>}
      {section === "organizations" && <section className="split"><form className="panel form" onSubmit={addOrganization}><h2>&#1605;&#1572;&#1587;&#1587;&#1577; &#1580;&#1583;&#1610;&#1583;&#1577;</h2><label>&#1575;&#1587;&#1605; &#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1577;<input value={orgName} onChange={(e) => setOrgName(e.target.value)} required /></label><label>&#1575;&#1604;&#1608;&#1589;&#1601;<textarea rows={4} value={orgDescription} onChange={(e) => setOrgDescription(e.target.value)} /></label><button className="primary" disabled={busy === "org"}>&#1573;&#1606;&#1588;&#1575;&#1569;</button></form><article className="panel"><h2>&#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1575;&#1578;</h2><div className="cards">{organizations.map((item) => <div className={selectedOrgId === item.id ? "card selected" : "card"} key={item.id}><button className="card-main" onClick={() => chooseOrg(item.id)}><span className="entity-logo">{item.name[0]}</span><span><b>{item.name}</b><small>{item.description}</small></span></button><Status value={item.status} /><div className="actions"><button onClick={() => orgAction(item, item.status === "ACTIVE" ? "deactivate" : "activate")}>{item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"}</button><button className="danger" onClick={() => orgAction(item, "archive")}>ARCHIVE</button></div></div>)}</div></article></section>}
      {section === "memberships" && <section className="split"><form className="panel form" onSubmit={addMembership}><h2>&#1583;&#1593;&#1608;&#1577; &#1593;&#1590;&#1608;</h2><label>&#1575;&#1604;&#1605;&#1572;&#1587;&#1587;&#1577;<select value={selectedOrgId} onChange={(e) => chooseOrg(e.target.value)}>{organizations.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Email<input type="email" dir="ltr" value={memberEmail} onChange={(e) => setMemberEmail(e.target.value)} required /></label><label>&#1575;&#1604;&#1575;&#1587;&#1605;<input value={memberName} onChange={(e) => setMemberName(e.target.value)} /></label><button className="primary">&#1573;&#1585;&#1587;&#1575;&#1604; &#1575;&#1604;&#1583;&#1593;&#1608;&#1577;</button></form><article className="panel"><h2>&#1575;&#1604;&#1593;&#1590;&#1608;&#1610;&#1575;&#1578;</h2><div className="cards">{selectedMemberships.map((item) => <div className="card" key={item.id}><div className="card-main static"><span className="entity-avatar">{(item.displayName || item.email)[0]}</span><span><b>{item.displayName || item.email}</b><small dir="ltr">{item.email}</small></span></div><Status value={item.status} /><div className="actions"><button onClick={() => memberAction(item, item.status === "ACTIVE" ? "deactivate" : "activate")}>TOGGLE</button><button className="danger" onClick={() => memberAction(item, "remove")}>REMOVE</button></div></div>)}</div></article></section>}
      {section === "users" && <section className="split"><form className="panel form" onSubmit={addUser}><h2>&#1605;&#1587;&#1578;&#1582;&#1583;&#1605; &#1580;&#1583;&#1610;&#1583;</h2><label>Email<input type="email" dir="ltr" value={userEmail} onChange={(e) => setUserEmail(e.target.value)} required /></label><label>&#1575;&#1604;&#1575;&#1587;&#1605;<input value={userName} onChange={(e) => setUserName(e.target.value)} /></label><label>Status<select value={userStatus} onChange={(e) => setUserStatus(e.target.value as UserStatus)}><option>INVITED</option><option>ACTIVE</option><option>INACTIVE</option></select></label><button className="primary">&#1573;&#1606;&#1588;&#1575;&#1569;</button></form><article className="panel"><h2>&#1575;&#1604;&#1605;&#1587;&#1578;&#1582;&#1583;&#1605;&#1608;&#1606;</h2><div className="cards">{users.map((item) => <div className="card" key={item.id}><div className="card-main static"><span className="entity-avatar">{(item.displayName || item.email)[0]}</span><span><b>{item.displayName || item.email}</b><small dir="ltr">{item.email}</small></span></div><Status value={item.status} /><div className="actions"><button onClick={() => userAction(item, item.status === "ACTIVE" ? "deactivate" : "activate")}>TOGGLE</button><button onClick={() => userAction(item, "suspend")}>SUSPEND</button><button className="danger" onClick={() => userAction(item, "archive")}>ARCHIVE</button></div></div>)}</div></article></section>}
    </main>
    <nav className="mobile-nav">{nav.map(([key, label, icon]) => <button key={key} className={section === key ? "active" : ""} onClick={() => setSection(key)}><span>{icon}</span><i dangerouslySetInnerHTML={{ __html: label }} /></button>)}</nav>
  </div>;
}
