import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { createOrganizationsApi } from "./organizations";

describe("organizationsApi", () => {
  function setup() {
    const client = new ApiClient({ baseUrl: "https://api.example.com" });
    return { client, api: createOrganizationsApi(client) };
  }

  it("lists organizations with explicit tenant scope", async () => {
    const { client, api } = setup();
    const response = [{ id: "org-1" }];
    const get = vi.spyOn(client, "get").mockResolvedValue(response);
    await expect(api.list(" tenant-1 ")).resolves.toBe(response);
    expect(get).toHaveBeenCalledWith("/api/v1/organizations", { query: { tenantId: "tenant-1" } });
  });

  it("creates an organization with the backend DTO", async () => {
    const { client, api } = setup();
    const post = vi.spyOn(client, "post").mockResolvedValue({ id: "org-1" });
    await api.create("tenant-1", { name: " SANAD ", description: " Main " });
    expect(post).toHaveBeenCalledWith("/api/v1/organizations", {
      tenantId: "tenant-1",
      name: "SANAD",
      description: "Main",
    });
  });

  it("updates with tenant query scope", async () => {
    const { client, api } = setup();
    const put = vi.spyOn(client, "put").mockResolvedValue({ id: "org-1" });
    await api.update("tenant-1", "org-1", { name: "Updated" });
    expect(put).toHaveBeenCalledWith("/api/v1/organizations/org-1", {
      name: "Updated",
      description: null,
    }, { query: { tenantId: "tenant-1" } });
  });

  it("uses only supported lifecycle actions", async () => {
    const { client, api } = setup();
    const patch = vi.spyOn(client, "patch").mockResolvedValue({ id: "org-1" });
    await api.transition("tenant-1", "org-1", "archive");
    expect(patch).toHaveBeenCalledWith("/api/v1/organizations/org-1/archive", undefined, {
      query: { tenantId: "tenant-1" },
    });
  });

  it("rejects blank names before transport", async () => {
    const { client, api } = setup();
    const post = vi.spyOn(client, "post");
    await expect(api.create("tenant-1", { name: " " })).rejects.toThrow(/name is required/i);
    expect(post).not.toHaveBeenCalled();
  });
});
