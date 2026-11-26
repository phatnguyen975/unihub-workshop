import type { MockIdentityService } from "@/features/identity/data/mock/index";
import type { AccessController } from "@/features/identity";
import { useState } from "react";

const workshopScenarios = [
  "normal",
  "empty",
  "no-featured",
  "slow",
  "list-error",
  "offline",
  "detail-error",
];
export default function ReviewTools({
  service,
  controller,
}: {
  service: MockIdentityService;
  controller: AccessController;
}) {
  const [workshopScenario, setWorkshopScenario] = useState("normal");
  const setWorkshopScenarioAndNotify = (value: string) => {
    setWorkshopScenario(value);
    window.dispatchEvent(
      new CustomEvent("unihub:workshop-scenario", { detail: value }),
    );
  };

  return (
    <aside className="reviewbar" aria-label="Workshop review scenarios">
      <strong>MOCK review</strong>
      <label>
        Service scenario{" "}
        <select
          aria-label="Service scenario"
          onChange={(e) => service.scenario(e.target.value)}
        >
          {service.scenarios.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Workshop scenario{" "}
        <select
          aria-label="Workshop scenario"
          value={workshopScenario}
          onChange={(event) => setWorkshopScenarioAndNotify(event.target.value)}
        >
          {workshopScenarios.map((scenario) => (
            <option value={scenario} key={scenario}>
              {scenario}
            </option>
          ))}
        </select>
      </label>
      <button onClick={() => setWorkshopScenarioAndNotify("normal")}>
        Reset workshop scenario
      </button>
      <button
        onClick={() => {
          service.advance();
          void controller.restore();
        }}
      >
        Advance clock +61s
      </button>
      <button
        onClick={() => {
          service.revoke();
          void controller.restore();
        }}
      >
        Revoke session
      </button>
      <button
        onClick={() => {
          service.reset();
          location.assign("/login");
        }}
      >
        Reset
      </button>
      <p>
        Public synthetic examples: linh@example.test / minh@example.test /
        an@example.test / new.staff@example.test / new.organizer@example.test —
        UniHubDemo1!
      </p>
    </aside>
  );
}
