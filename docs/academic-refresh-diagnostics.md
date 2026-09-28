# Academic refresh diagnostic archive

When the academic snapshot assembler rejects a refresh, the app replaces one file in its private files directory:
`diagnostics/academic_refresh/academic-refresh-diagnostic.zip`.

The ZIP contains one `diagnostic.json` with an allowlisted schema: app version, Android API level, a fixed failure stage/code, source row counts, and aggregate credit reconciliation totals. It does not contain raw Logcat output, endpoint URLs or query parameters, request/response bodies, course or person identifiers, names, teachers, grades, cookies, tokens, or other authentication material. The app does not upload the archive. The user can open the Android share sheet from the academic page and choose whether to share it.

Only the latest archive is retained; the next validation failure atomically replaces it. The existing fail-closed import behavior remains in place, so a rejected refresh does not replace the prior local snapshot.
