import type { MockIdentityService } from '@/features/identity/data/mock/index';
import type { AccessController } from '@/features/identity';
export default function ReviewTools({ service, controller }: {
    service: MockIdentityService;
    controller: AccessController;
}) {
    return <aside className="reviewbar" aria-label="Review scenarios">
    <strong>MOCK review</strong>
    <label>Service scenario <select aria-label="Service scenario" onChange={e => service.scenario(e.target.value)}>{service.scenarios.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}</select>
    </label>
    <button onClick={() => { service.advance(); void controller.restore(); }}>Advance clock +61s</button>
    <button onClick={() => { service.revoke(); void controller.restore(); }}>Revoke session</button>
    <button onClick={() => { service.reset(); location.assign('/login'); }}>Reset</button>
    <p>Public synthetic examples: linh@example.test / minh@example.test / an@example.test / new.staff@example.test / new.organizer@example.test — UniHubDemo1!</p>
    </aside>;
}
