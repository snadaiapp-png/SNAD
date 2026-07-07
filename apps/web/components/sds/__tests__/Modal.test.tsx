// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Modal } from "../Modal";

describe("SDS Modal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders nothing when isOpen=false", () => {
    render(
      <Modal isOpen={false} onClose={() => {}} title="My Modal">
        <p>Body</p>
      </Modal>,
    );
    expect(screen.queryByText("Body")).toBeNull();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("renders the dialog with role=dialog and aria-modal=true when open", () => {
    render(
      <Modal isOpen onClose={() => {}} title="My Modal">
        <p>Body</p>
      </Modal>,
    );
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby");
  });

  it("renders the title and links it via aria-labelledby", () => {
    render(
      <Modal isOpen onClose={() => {}} title="Confirm deletion">
        <p>Are you sure?</p>
      </Modal>,
    );
    const dialog = screen.getByRole("dialog");
    const labelledBy = dialog.getAttribute("aria-labelledby");
    expect(labelledBy).toBeTruthy();
    const titleEl = document.getElementById(labelledBy ?? "");
    expect(titleEl).not.toBeNull();
    expect(titleEl?.textContent).toBe("Confirm deletion");
  });

  it("renders the subtitle when provided", () => {
    render(
      <Modal
        isOpen
        onClose={() => {}}
        title="Title"
        subtitle="Optional subtitle"
      >
        <p>Body</p>
      </Modal>,
    );
    expect(screen.getByText("Optional subtitle")).toBeInTheDocument();
  });

  it("renders footer content when provided", () => {
    render(
      <Modal
        isOpen
        onClose={() => {}}
        title="Title"
        footer={<button type="button">Save</button>}
      >
        <p>Body</p>
      </Modal>,
    );
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("invokes onClose when the close button is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Title">
        <p>Body</p>
      </Modal>,
    );
    await user.click(screen.getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("invokes onClose when ESC is pressed", () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Title">
        <p>Body</p>
      </Modal>,
    );
    act(() => {
      const event = new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
      });
      document.dispatchEvent(event);
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does NOT invoke onClose on ESC when closeOnEsc=false", () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Title" closeOnEsc={false}>
        <p>Body</p>
      </Modal>,
    );
    act(() => {
      const event = new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
      });
      document.dispatchEvent(event);
    });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("invokes onClose when the backdrop is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Title">
        <p>Body</p>
      </Modal>,
    );
    // The backdrop is the parent overlay div. Click on it directly.
    const dialog = screen.getByRole("dialog");
    const overlay = dialog.parentElement;
    expect(overlay).not.toBeNull();
    await user.click(overlay as HTMLElement);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does NOT invoke onClose when content inside the modal is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Title">
        <p>Body content</p>
      </Modal>,
    );
    await user.click(screen.getByText("Body content"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("does NOT invoke onClose on backdrop click when closeOnBackdropClick=false", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal
        isOpen
        onClose={onClose}
        title="Title"
        closeOnBackdropClick={false}
      >
        <p>Body</p>
      </Modal>,
    );
    const dialog = screen.getByRole("dialog");
    const overlay = dialog.parentElement;
    await user.click(overlay as HTMLElement);
    expect(onClose).not.toHaveBeenCalled();
  });

  it.each(["sm", "md", "lg", "full"] as const)(
    "renders size=%s without error",
    (size) => {
      render(
        <Modal isOpen onClose={() => {}} title="Title" size={size}>
          <p>Body</p>
        </Modal>,
      );
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    },
  );
});
