/**
 * HR API client — typed client for /api/v1/hr endpoints.
 */

export interface HrEmployeeResponse {
  id: string;
  tenantId: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  positionId: string | null;
  managerId: string | null;
  employmentType: string;
  status: string;
  hireDate: string | null;
  terminationDate: string | null;
}

const API_BASE = "/api/platform/api/v1/hr";

export const hrApi = {
  async listEmployees(limit = 50, search?: string): Promise<HrEmployeeResponse[]> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (search) params.set("search", search);
    const res = await fetch(`${API_BASE}/employees?${params}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`HR employees: ${res.status}`);
    return res.json();
  },

  async getEmployee(id: string): Promise<HrEmployeeResponse> {
    const res = await fetch(`${API_BASE}/employees/${id}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`HR employee: ${res.status}`);
    return res.json();
  },

  async createEmployee(data: Partial<HrEmployeeResponse>): Promise<HrEmployeeResponse> {
    const res = await fetch(`${API_BASE}/employees`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(`HR create: ${res.status}`);
    return res.json();
  },

  async updateEmployee(id: string, data: Partial<HrEmployeeResponse>): Promise<HrEmployeeResponse> {
    const res = await fetch(`${API_BASE}/employees/${id}`, {
      method: "PATCH",
      credentials: "include",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(`HR update: ${res.status}`);
    return res.json();
  },
};
