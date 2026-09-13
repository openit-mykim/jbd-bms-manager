# JBD BMS Manager

Unofficial open-source Android manager for JBD / Jiabaida BMS.

JBD BMS Manager is planned as an extended derivative of [OpenJBD](https://github.com/gytxtx/OpenJBD), with a clear separation between everyday battery monitoring and technician-oriented maintenance functions.

## Goals

- Keep daily monitoring simple and safe.
- Add a separate Maintenance Mode for calibration and BMS configuration.
- Support modern Android versions, including proper Android 16 edge-to-edge / window inset handling.
- Operate locally over BLE without requiring a cloud account.
- Make potentially dangerous write operations explicit, reviewable, and difficult to trigger accidentally.

## Planned modes

### Monitor Mode

- State of charge (SOC)
- Pack voltage, current and power
- Remaining / learned capacity
- Cycle count
- Cell-group voltages
- Min / max / average cell voltage
- Cell delta (ΔV)
- Temperatures
- Charge / discharge MOS state
- Balance state
- Protection / alarm state
- Auto reconnect
- Landscape dashboard

### Maintenance Mode

- Pack voltage calibration
- Cell voltage calibration
- Idle current calibration
- Charge current calibration
- Discharge current calibration
- Nominal / learned / remaining capacity management
- Protection parameter read / write
- Balance parameter read / write
- Temperature parameter management
- Charge / discharge MOS control
- Configuration backup / restore
- Raw BMS information and diagnostics

Maintenance Mode should be protected by an explicit unlock flow and should use a **Read → Edit → Review changes → Apply** workflow rather than writing each value immediately.

## Initial development priorities

1. Import and preserve the useful OpenJBD monitoring foundation.
2. Fix Android 16 system-bar / edge-to-edge layout handling.
3. Refine Monitor Mode UI.
4. Add Korean localization.
5. Implement Maintenance Mode architecture.
6. Add JBD protocol write and calibration commands.
7. Add configuration backup / restore.
8. Add automated APK builds with GitHub Actions.

See [docs/roadmap.md](docs/roadmap.md) for the working roadmap.

## Project structure

```text
jbd-bms-manager/
├─ app/                  # Android application (to be imported)
├─ docs/
│  ├─ architecture.md
│  ├─ maintenance-mode.md
│  └─ roadmap.md
├─ README.md
└─ LICENSE
```

## Upstream

This project is intended to build on concepts and code from OpenJBD:

- Upstream: https://github.com/gytxtx/OpenJBD
- Upstream language: Kotlin
- Upstream license: MIT

When OpenJBD source code is imported, its original copyright and MIT license notice must be preserved as required by the MIT License.

## Disclaimer

This is an unofficial community project and is not affiliated with or endorsed by JBD / Jiabaida.

Changing BMS protection, calibration, balancing, temperature, capacity or MOS parameters can affect battery safety. Maintenance functions should only be used with verified settings and appropriate battery-service procedures.

## License

MIT License. See [LICENSE](LICENSE).
