# VoxSight OMR Fidelity, Staff Order, and Vocal-Color RCA

**Status:** Implementation changes are in place; backend, Android, Python, and JavaScript verification passed. The real Awit comparison remains pending because its scan and Audiveris export are not in the workspace.
**Investigation date:** October 2026
**Scope:** Audiveris to MusicXML, backend SATB analysis, Android import/review/playback, OSMD rendering, and vocal-part color resolution.

## Summary

The errors described are introduced downstream of Audiveris. The code review found several independent ways that valid MusicXML could be changed or its note identities and voice labels could be lost:

1. Runtime cleanup paths rewrote MusicXML after Audiveris, and the WebView cleanup routines could delete parts, reorder staves, strip score data, and apply a bass-pitch correction.
2. The WebView part sorter normalized “Baritone Solo” to `baritonesolo` but ranked only exact `baritone` as a baritone. The solo part therefore fell to the unknown rank after Bass. This explains the reported staff-order error.
3. Direct MusicXML import bypassed backend analysis. Review confirmation then rebuilt playback events locally, discarding the backend’s labels and note identity.
4. SATB score aggregation computed voice-signature evidence but omitted it from the final score. A note-density heuristic also inferred cross-staff unisons without evidence that both voices sang the same pitch.
5. Renderer/editor matching had permissive time, proximity, sequence, and arbitrary fallback joins. Playback synchronization separately matched by musical similarity. Repeated pitches and nearby notes could inherit another note's voice and color; identical duplicate notes could also be paired by list order.
6. Several layers used different color palettes and guessed voice identity when explicit assignment data was absent.

The implementation now removes destructive transforms from the runtime import path, analyzes direct MusicXML and edited XML through the shared backend, carries a stable note ID from MusicXML through analysis and playback, preserves explicitly user-marked voice edits, rejects ambiguous graphical joins, and applies the required family palette. Generic or unstamped parts now display as Unknown instead of receiving a guessed SATB color.

The original scan and its standalone Audiveris MusicXML for “Awit ng Paghahangad” are not present in this workspace. The code-level findings are confirmed; a byte/semantic comparison against that exact score is still required once both artifacts are available.

## Data flow and earliest confirmed deviations

```text
Image/PDF ──> OmrController ──> Audiveris MusicXML
                                  │
MusicXML/MXL import ──────────────┘
                                  ▼
                          SatbAnalysisService
                                  ▼
                            satb_analyzer.py
                    events + note assignment metadata
                                  ▼
                  review editor ──> shared backend reanalysis
                                  ▼
                score model + backend event JSON
                                  ▼
        renderer note mapping ──> exact data-vx-id sync
                                  ▼
             SATB colors / playback highlights
```

| Stage | Confirmed cause | Earliest effect | Implemented correction |
|---|---|---|---|
| Post-Audiveris | `OmrController` invoked `musicxml_cleaner.py`; Python analysis also invoked the cleaner. That script includes part reconciliation, ordering, and score mutations. | Audiveris XML could be changed before response. | Removed both runtime cleaner invocations and the controller helper. The standalone cleaner utility remains for its own tests, but is no longer on the import path. |
| WebView load | `cleanMusicXmlDom` contained part purging, part-list sorting, lyric cleanup, and a measure-specific bass pitch routine. | Review/render XML could differ from the backend XML. | Removed calls from renderer/editor load paths; MusicXML is passed through without cleanup. Credit blocks and note identity attributes are no longer stripped by renderer sanitization. |
| Baritone Solo ordering | Sort rank recognized exact `baritone`, but not normalized `baritonesolo`; unknown rank sorted after Bass. Both `score-part` and `part` nodes were reordered. | Original vertical part order was lost in the editor/renderer. | The destructive load-time sorting path is disabled; source part order remains authoritative. |
| MusicXML import | `UploadScoreScreen` sent XML directly to review instead of `/api/analyze`. | No shared event classification or stable note metadata on imported XML. | XML, MusicXML, and MXL now use the same upload/analyze flow; backend recognizes those extensions and skips Audiveris. |
| Review confirmation | `AppNavigation` used `regenerateEventsJsonFromScore`, applying a second local classifier after editing. | Backend SATB labels and source-note references were replaced in the playback timeline. | Edited XML is reanalyzed by `/api/analyze`; returned event and metadata JSON are stored with the analyzed XML. |
| SATB classification | Global score aggregation calculated `score_voice_signature()` but did not include that evidence. Cross-staff density could create a unison from one note on a staff when the other staff was denser. Explicit MusicXML stem direction was also too weak to resolve adjacent voice ranges. | Voice labels and colors could be wrong even when the note transcription was intact. | Included signature evidence in the weighted score; increased explicit stem/voice evidence for condensed scores; removed density-only cross-staff unison inference. The global score weights are pitch 0.31, staff 0.18, signature 0.13, voice/stem 0.25, and continuity 0.13. Unison requires same-staff pitch/voice evidence. |
| Assignment and identity | Renderer/editor used permissive matching fallbacks and greedily paired identical staff/time/pitch candidates by list order; `SyncManager` used pitch/time/staff proximity. Unknown labels were not consistently stampable, and any unmarked existing label could be mistaken for a user edit. | A correct event label could attach to the wrong displayed note or be silently guessed. | Added unique `data-vx-id`, `source_note_id`, and `source_part_id`; renderer/editor now require mutual one-to-one matches, prefer matching MusicXML voice numbers, and leave ambiguous duplicates unmapped. Sync joins only exact IDs. Only `data-vx-voice-source="user"` or `user_override` preserves a user assignment. |
| Color resolution | Web, editor, Compose, legends, and divisi used different colors; unmarked generic or condensed parts were inferred from staff/voice order. | The same voice could be shown in different colors, and generic notes could be miscolored. | Unified the required palette and made ambiguous fallback classification fail closed to Unknown. |

The Android parser still normalizes local score structures for the UI and retains fallback event generation for older local scores with no backend event payload. New imports and review confirmations now use backend events as their playback source. Local fallback behavior remains a regression target for legacy saved files.

## Files changed by the implementation

- `voxsight/src/main/java/edu/cit/capstone/voxsight/controller/OmrController.java`: removed cleaner calls; direct MusicXML/MXL analysis route; analysis cache version bump.
- `voxsight/src/main/java/edu/cit/capstone/voxsight/service/SatbAnalysisService.java`: corrected pipeline contract and cleaner description.
- `voxsight/scripts/satb_analyzer.py`: stable note IDs and source part IDs, explicit assignment-source handling, unknown stamping, score-signature weighting, and removal of density-only unisons.
- `mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/upload/UploadScoreScreen.kt`: direct MusicXML import uses shared upload/analyze path.
- `mobile/app/src/main/java/com/cit/kaido/voxsight/ui/navigation/AppNavigation.kt`: review confirmation reanalyzes edited MusicXML and saves backend events/metadata.
- `mobile/app/src/main/java/com/cit/kaido/voxsight/model/MusicalEvent.kt`: event source identity fields.
- `mobile/app/src/main/java/com/cit/kaido/voxsight/playback/SyncManager.kt`: exact-ID-only coordinate joins.
- `mobile/app/src/main/assets/renderer.html` and `interactive_editor.html`: preserve imported XML, require mutual one-to-one note joins, fail closed for ambiguous voice classification, preserve IDs and editor override source.
- `mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/practice/VoicePart.kt`, `PracticeScreen.kt`, `NoteEditorBottomSheet.kt`, `ScoreReviewScreen.kt`, `PracticeActivity.kt`, and `MidiPlaybackEngine.kt`: consistent voice palette and visual indicators.
- Regression tests updated under `voxsight/scripts/tests/` and `mobile/app/src/test/`.

## Prioritized implementation and verification plan

### Phase 1 — Preserve the upstream score (implemented)

- Bypass cleanup/reconciliation code in production import flow.
- Keep original part/staff order through review and renderer loading.
- Do not remove lyrics, notes, key signatures, dynamics, or ornaments as a side effect of loading.
- Compare the original Audiveris and VoxSight-returned XML semantically, ignoring only VoxSight `data-vx-*` metadata.

### Phase 2 — Use one analysis contract (implemented)

- Route PDF/image imports through Audiveris and then the shared SATB analyzer.
- Route XML/MusicXML/MXL imports straight to the shared analyzer, without running Audiveris.
- On review confirmation, analyze edited XML through the same endpoint and use its event stream and metadata.
- Keep user assignments distinguishable from backend assignments so reanalysis preserves only explicit user overrides.

### Phase 3 — Make note identity and vocal assignment deterministic (implemented; sample coverage pending)

- Maintain unique note IDs in MusicXML and attach them to analysis events.
- Map renderer coordinates and playback events by the same note ID; do not use pitch or proximity as an identity substitute.
- Leave unmatched graphical notes uncolored and unmapped; surface mismatch counts in logs.
- Use evidence-based SATB scoring and do not infer unison from staff note density alone.

### Phase 4 — Keep color resolution consistent (implemented)

| Part | Required color |
|---|---|
| Soprano, including S1/S2 | `#FF69B4` |
| Alto, including A1/A2 | `#9B59B6` |
| Tenor, including T1/T2 | `#3498DB` |
| Bass, including B1/B2 | `#2ECC71` |
| Solo, Baritone Solo, and Tutti | `#F1C40F` |
| Others | `#546E7A` |
| Unknown/unmatched | `#808080` |

Unison notes use a participating family’s base color (S/A uses Soprano pink; T/B uses Tenor blue). Pitch-match feedback remains a separate green/red feedback state.

### Phase 5 — Golden-score regression and acceptance (pending the score artifacts)

Use the same original scan/PDF in Audiveris and VoxSight. Preserve the Audiveris MusicXML as the baseline and compare the returned VoxSight MusicXML and rendered score. Include the “Awit ng Paghahangad” case with Baritone Solo above Soprano.

Acceptance criteria:

1. Note count, part IDs/order, staff numbers, pitch/alteration, duration, onset, voice number, ties, lyrics, rests, clefs, key/time signatures, and measure order match the Audiveris baseline. Differences are limited to VoxSight metadata attributes.
2. Baritone Solo remains above Soprano in the rendered system and does not move below Bass.
3. Every pitched event in the supported fixture has a unique source note ID and maps to exactly one source note and rendered coordinate; no fallback coordinate or part assignment is used.
4. Event pitch, onset, duration, measure, staff, and part match the source note for every mapped event. Divisi and chord members retain their original starts/durations.
5. Backend-assigned or explicitly user-assigned vocal labels agree across MusicXML attributes, event JSON, editor display, playback filtering, and renderer color. User overrides survive confirm/reanalysis.
6. Exact palette values above appear in editor options, review legend, notehead fills, and playback highlights. Unknown and unmatched notes are gray, and rests receive no vocal notehead color.
7. Automated tests pass: Python analyzer/stamping tests, JS renderer/editor tests, Android unit tests, Android Kotlin compilation, and backend Maven tests (`mvnw.cmd test`).

The paired Awit scan and Audiveris export should be added as a versioned regression fixture when available. Until then, no claim of score-specific parity is made.
