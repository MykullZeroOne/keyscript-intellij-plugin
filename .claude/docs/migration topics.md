he analyst did a deep dive into both keyscript-all.js (31K lines) and ide-all.js (49.5K lines). Here's what we found:

Already Migrated to Ktor (no work needed)

- SessionStore intercept + RunScript param injection
- JSESSIONID injection into all proxied requests
- Device info (GetDeviceInformation endpoint)
- Installed scripts CRUD (via InstalledScriptsService.kt)
- Table browser metadata (via TableBrowserToolWindowFactory.kt)
- Query builder XML construction (via QueryBuilderToolWindowFactory.kt)
- Login status check (via SessionService.keepAliveProxy())

High-Priority Migration Targets

┌───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬────────┬─────────────────────────────────────────────────────┐                                                                               
│                                                                  Target                                                                   │ Effort │                       Benefit                       │
├───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼────────┼─────────────────────────────────────────────────────┤                                                                               
│ Full crlogin param injection — extend injectSessionIntoParams() with userName, userSerial, deviceName, etc. from SessionService.loginData │ Medium │ Eliminates SessionStore pre-call per script run     │
├───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼────────┼─────────────────────────────────────────────────────┤                                                                               
│ Bundle query-language.json as a plugin resource — serveBundledStaticIfPresent already handles /KeyScript/ paths                           │ Easy   │ Removes a Keystone round-trip                       │                                                                               
├───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼────────┼─────────────────────────────────────────────────────┤                                                                               
│ Fetch VIEW_LOGON_VALUES at login — capture userInterfaceSettings in AuthenticationService and embed in scriptParameters                   │ Medium │ Eliminates CR.Settings.loadSettings call from JS    │                                                                               
├───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼────────┼─────────────────────────────────────────────────────┤                                                                               
│ Login info (DEVICE + BRANCH lookup) — add to AuthenticationService response capture                                                       │ Easy   │ Removes double round-trip from showLoginInformation │
└───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴────────┴─────────────────────────────────────────────────────┘

Must Stay in JS (browser-dependent)

- All ExtJS UI components — fields, panels, grids, windows, tree panels (~80% of both bundles)
- CR.Core.record* APIs — recordView, recordUpdate, recordSearch — these are the public CR framework API that user scripts call directly
- CR.XML class — DOM builder/parser used by scripts
- CR.Settings timers — dirty flags and save cadence tied to browser state
- CR.Core.searchPrompt — live-search UI with typing/pagination
- >>OVR<< / >>EXC<< / >>SNF<< response protocol — ~700 lines of override/exception dialog handling deeply coupled to UI. This is the hardest blocker and should be deferred

Key Insight

The ide-all.js IDE shell (mainPage, DevelopmentScriptsPanel, InstalledScriptsPanel, TableBrowser, QueryBuilder) is largely redundant for the IntelliJ plugin — those panels are already rebuilt in Kotlin. The IntelliJ plugin only uses the RunScript iframe path which loads             
keyscript-all.js (the CR framework runtime). The IDE shell JS is only needed if someone navigates to Keyscript_IDE/index.html directly.

Bottom line: The JS bundles are ~80% UI rendering that must stay in the browser. The remaining ~20% is data fetching and state management, of which roughly half is already migrated. The three medium-effort items above would close most of the remaining gap, leaving only the
>>OVR<</>>EXC<< protocol as the significant remaining coupling.

Want me to start implementing any of the migration targets?              