import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { describe, expect, it, vi } from "vitest";
import { AppProviders } from "./AppProviders";
import { createQueryClient } from "./query-client";

const schema = z.object({
  value: z.string().trim().min(3, "Enter at least three characters"),
});

function FormProbe({
  submit,
}: {
  submit: (data: z.output<typeof schema>) => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema) });
  return (
    <form onSubmit={handleSubmit(submit)}>
      <label htmlFor="value">Value</label>
      <input id="value" {...register("value")} />
      {errors.value && <p role="alert">{errors.value.message}</p>}
      <button type="submit">Submit</button>
    </form>
  );
}

function ServiceProbe({ load }: { load: () => Promise<string> }) {
  const state = useQuery({ queryKey: ["setup-probe"], queryFn: load });
  if (state.isPending) return <p role="status">Loading</p>;
  if (state.isError) return <p role="alert">Service unavailable</p>;
  return <p>{state.data}</p>;
}

describe("tooling integration using test-only probes", () => {
  it("blocks invalid input and submits schema-normalized values", async () => {
    const submit = vi.fn();
    const user = userEvent.setup();
    render(<FormProbe submit={submit} />);
    await user.type(screen.getByLabelText("Value"), "x");
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter at least three characters",
    );
    expect(submit).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText("Value"));
    await user.type(screen.getByLabelText("Value"), "  UniHub  ");
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(submit.mock.calls[0]?.[0]).toEqual({ value: "UniHub" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("uses a service fake without invoking a live network provider", async () => {
    const network = vi.fn(() => {
      throw new Error("Unexpected network");
    });
    vi.stubGlobal("fetch", network);
    let resolve!: (value: string) => void;
    const result = new Promise<string>((done) => {
      resolve = done;
    });
    const load = vi.fn(() => result);
    render(
      <AppProviders>
        <ServiceProbe load={load} />
      </AppProviders>,
    );
    expect(screen.getByRole("status")).toHaveTextContent("Loading");
    resolve("Fake service result");
    expect(await screen.findByText("Fake service result")).toBeVisible();
    expect(load).toHaveBeenCalledTimes(1);
    expect(network).not.toHaveBeenCalled();
  });

  it("surfaces failed service outcomes without silently retrying mutations/queries", async () => {
    const load = vi.fn(async () => {
      throw new Error("Fixture failure");
    });
    render(
      <AppProviders>
        <ServiceProbe load={load} />
      </AppProviders>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Service unavailable",
    );
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("does not leak one application cache into a fresh application", () => {
    const first = createQueryClient();
    const second = createQueryClient();
    first.setQueryData(["account-probe"], "first-account");
    expect(second.getQueryData(["account-probe"])).toBeUndefined();
    first.clear();
    second.clear();
  });
});
