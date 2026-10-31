# Product and platform risk register

Likelihood and impact use **Low / Medium / High**. The register describes known risks for the diagnostic alpha; an emulator pass does not reduce a physical-device risk rating.

| ID | Risk | Likelihood | Impact | Mitigation / detection | Residual status |
|---|---|---:|---:|---|---|
| R-01 | Start produces no saved JPEG on Galaxy S21 (reported) | High | Critical | Keep STARTING until the first validated commit; log bind/request/callback/commit phases; run ten-frame S21 acceptance with hashes and EXIF | **Open P0; no physical S21 evidence** |
| R-02 | Android/OEM kills or restricts camera service after screen-off, idle, or process death | Medium | Critical | User-started camera FGS, bounded state transitions, clear recovery state; test display power, FGS and camera rebind on S21 | **Open; emulator cannot validate OEM policy** |
| R-03 | Low storage or filesystem errors interrupt a session | Medium | High | Check available reserve before capture; pause visibly; stage, validate, atomically publish, journal and reconcile originals | Automated policy/fault tests exist; full disk and removed-volume tests remain open |
| R-04 | Power loss occurs during staging or journal update | Medium | High | Sync staged bytes, publish atomically, recover valid staging, quarantine incomplete files, audit hashes | Synthetic journal failure and partial-file tests pass; actual power-cut test remains open |
| R-05 | Heat rises during long capture/export | Medium | High | Read platform thermal status, degrade/pause with hysteresis, avoid unbounded buffers, log temperature state | Unit/emulator tests only; S21 thermal/soak measurements remain open |
| R-06 | Export fails, is cancelled, or destination runs out of space | Medium | High | Preflight, bounded streaming, atomic pending output, retain completed day files, never touch source originals | JVM/emulator fault-injection tests; SAF and physical-storage failures still need device tests |
| R-07 | Codec/resolution behavior differs by device, driver or firmware | Medium | High | Probe capabilities, reject unsupported source/encoder combinations, independently decode MP4 output | Emulator AVC verified; S21 codec, 4K/8K quality and thermal behavior unverified |
| R-08 | Clock changes, timezone or DST create missing/duplicate scheduled captures | Medium | Medium | Use monotonic capture deadlines and local-calendar schedule policy with explicit DST tests | JVM tests cover known transitions; real alarm/Doze behavior remains unverified |
| R-09 | Project metadata or journal damage hides valid originals | Low | High | Recover from image files, hash/audit journal entries, keep files immutable | Synthetic damage and reconciliation tests; multi-thousand-file stress remains open |
| R-10 | Users mistake a diagnostic build or simulator result for S21 validation | Medium | Critical | Label artifacts UNVERIFIED, retain explicit scope in reports, block merge/release on CAPTURE-P0-001 | PR policy and docs in place; keep visible in every handoff |
| R-11 | Construction images or metadata are shared without consent | Low | High | Store locally; do not upload; anonymize logs and avoid committing user site imagery | No upload workflow; review any future network feature before implementation |
| R-12 | Dependencies or copied upstream code create licensing obligations | Low | High | Prefer official Apache/MIT dependencies, record versions/licenses, preserve upstream notices, exclude GPL source unless distribution terms are approved | Reuse review exists; complete generated transitive SBOM remains open |

## Escalation rule

Any valid-original loss, false RECORDING state, unbounded retry/queue, S21 zero-frame reproduction, or release-gate bypass is stop-the-line. Preserve logs and originals, block promotion, and link a reproducible result before downgrading severity.
