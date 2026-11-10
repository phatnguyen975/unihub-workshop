# Mobile Rules — Expo on Physical Devices

Use client rules, `docs/client/adrs/README.md` and the existing mobile persistence design when relevant. The approved `docs/client/mobile/ui-design.md` owns mobile design, including Android/iOS differences; HTML prototypes approve flow/design, not native behavior. Use `docs/client/mobile/source-structure.md` for feature-oriented source organization. App dependencies, Expo SDK/client and runtime tooling are selected during app setup.

## Development Feedback and Device Control

Use physical Android/iOS devices; do not boot or silently substitute an emulator/simulator. Metro/Expo Fast Refresh transfers JS/TS/style changes to a connected Expo Go/development client. This connection does not give the agent device automation access. Native dependency/config changes may require rebuilding; normal JS/style edits do not require a native build each time.

Use `agent-device` on an explicitly selected, proven physical target. A screenshot lets the agent inspect visual UI; accessibility snapshots/refs help interaction. If AX is sparse, a current screenshot plus coordinate interaction and verified outcome can still be sufficient. Creating an image without viewing it is not visual inspection.

## Android Setup Checklist

1. Install ADB separately from official Android SDK Platform-Tools; `agent-device` does not bundle it. Add the folder containing adb.exe to PATH on Windows and open a fresh terminal. Android Studio/emulators are not required merely to obtain ADB.
2. Enable Developer options/USB debugging, use a data-capable USB cable and authorize the computer on the device.
3. Check adb version and adb devices; bind the correct serial in device state. Resolve unauthorized/offline/cable/OEM-driver issues as needed. Wireless debugging is optional.
4. Install the selected `agent-device` runtime version; verify host/device prerequisites during initial setup or when they change.
5. Open the correct project in a compatible Expo Go/development build, verify SDK/client/account and Metro LAN/tunnel connectivity. Capturing an Expo launcher instead of the project is not evidence.
6. Prove screenshot/view, interaction/assertion, a visible code update via Fast Refresh, fixture reset and journey replay before relying on autonomous UI review.

## IPhone

The local `agent-device` iPhone route requires a Mac/Xcode, a paired/trusted device, Developer Mode and a correctly signed runner. Windows plus Expo Go does not itself supply native iPhone automation. A cloud-built binary does not grant control of a phone next to the user. Configure/verify an appropriate host route when available; otherwise use the human UI fallback below. Android results never substitute for iOS results.

## Expo Go and Native Verification

Expo Go is usable within its bundled native modules and compatible SDK/client/account setup. Evaluate development builds when the selected native libraries/config or required checks need them; they still use Metro/Fast Refresh for most edits. Do not claim camera/QR, SQLite persistence, permissions, keyboard, back navigation, background/kill/relaunch or offline lifecycle verified from browser/component tests.

Prepare app/fixtures before deliberately disconnecting networking. Metro/Fast Refresh may stop while the phone cannot reach the dev server; keep device control over USB when appropriate and restore the development connection to continue coding. Offline cold-start/relaunch checks need an appropriate packaged build/runtime; an already-open Expo Go session does not prove them all.

## UI Review Fallback and Acceptance

If current native access is proven, inspect and operate the target app, compare to the approved prototype/ui-design rules, correct in-scope defects and save screenshot/action/assertion evidence. If access is unavailable, unreliable or lost, finish applicable lint/type/unit/component checks, prepare the prototype, fixture, screen/state scenarios and expected results for human review, and leave native UI verification pending. Do not claim a pass from stale images, another platform or HTML emulation.

Human reviews the unavailable native UI/hardware portion and supplies findings or acceptance; the agent fixes authorized defects and the user can see updates through Fast Refresh. The capability does not become UI-accepted without the required evidence/confirmation. Even when automation works, human design and final UX acceptance remain.

Use RNTL references matching actual versions for component tests. Apply native/composition/performance skills to selected dependencies and measured concerns; do not force @expo/ui, NativeWind, FlashList, navigation or animation libraries. Select the persistent native E2E runner during app tooling setup; an agent-driven smoke flow is not the entire CI test suite.
