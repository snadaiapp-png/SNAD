"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type { CrmTeam, CrmTeamMembership } from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  Employee status helpers
 * ============================================================================ */

const ROLE_LABELS: Record<string, string> = {
  OWNER: "Owner",
  MANAGER: "Manager",
  MEMBER: "Member",
  VIEWER: "Viewer",
};

/* ============================================================================
 *  EmployeesTab — main component
 * ============================================================================ */

export function EmployeesTab() {
  const { t } = useCrmI18n();
  const [teams, setTeams] = useState<CrmTeam[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<CrmTeam | null>(null);
  const [memberships, setMemberships] = useState<CrmTeamMembership[]>([]);
  const [loading, setLoading] = useState(true);
  const [membersLoading, setMembersLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /* ---------- data fetching ---------- */

  const loadTeams = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await crmApi.teams();
      setTeams(result.filter((t) => !t.archived));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load teams");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMemberships = useCallback(async (teamId: string) => {
    setMembersLoading(true);
    try {
      const result = await crmApi.teamMemberships(teamId);
      setMemberships(result.filter((m) => m.active));
    } catch {
      setMemberships([]);
    } finally {
      setMembersLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTeams();
  }, [loadTeams]);

  useEffect(() => {
    if (selectedTeam) {
      loadMemberships(selectedTeam.id);
    } else {
      setMemberships([]);
    }
  }, [selectedTeam, loadMemberships]);

  /* ---------- render ---------- */

  return (
    <div className={styles.tabContainer}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.employees")}</h2>
      </div>

      {/* Content */}
      {loading ? (
        <div className={styles.loadingState}>Loading teams…</div>
      ) : error ? (
        <div className={styles.errorState}>
          {error}
          <button onClick={loadTeams} className={styles.retryButton}>
            Retry
          </button>
        </div>
      ) : teams.length === 0 ? (
        <div className={styles.emptyState}>No CRM teams found.</div>
      ) : (
        <div style={{ display: "flex", gap: "1rem" }}>
          {/* Teams list */}
          <div className={styles.tableContainer} style={{ flex: "0 0 300px" }}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Team</th>
                  <th>Code</th>
                </tr>
              </thead>
              <tbody>
                {teams.map((team) => (
                  <tr
                    key={team.id}
                    className={`${styles.tableRow} ${selectedTeam?.id === team.id ? styles.tableRowActive : ""}`}
                    onClick={() => setSelectedTeam(team)}
                  >
                    <td className={styles.tableCellTitle}>
                      {team.nameEn ?? team.nameAr ?? team.code}
                    </td>
                    <td>{team.code}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Members list */}
          <div className={styles.tableContainer} style={{ flex: 1 }}>
            {selectedTeam ? (
              membersLoading ? (
                <div className={styles.loadingState}>Loading members…</div>
              ) : memberships.length === 0 ? (
                <div className={styles.emptyState}>No active members in this team.</div>
              ) : (
                <>
                  <div style={{ padding: "0.75rem", borderBottom: "1px solid var(--snad-border, #e5e7eb)" }}>
                    <strong>{selectedTeam.nameEn ?? selectedTeam.nameAr ?? selectedTeam.code}</strong>
                    <span style={{ marginLeft: "0.5rem", color: "var(--snad-muted, #6b7280)" }}>
                      ({memberships.length} member{memberships.length !== 1 ? "s" : ""})
                    </span>
                  </div>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>User ID</th>
                        <th>Role</th>
                        <th>Primary</th>
                        <th>Joined</th>
                      </tr>
                    </thead>
                    <tbody>
                      {memberships.map((membership) => (
                        <tr key={membership.id} className={styles.tableRow}>
                          <td className={styles.tableCellTitle}>{membership.userId}</td>
                          <td>
                            <span className={styles.statusBadge}>
                              {ROLE_LABELS[membership.role] ?? membership.role}
                            </span>
                          </td>
                          <td>{membership.primary ? "✓" : "—"}</td>
                          <td>{new Date(membership.joinedAt).toLocaleDateString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )
            ) : (
              <div className={styles.emptyState}>Select a team to view members.</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
