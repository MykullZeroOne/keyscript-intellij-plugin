# Product Analytics Reference

## Contents
- What to Instrument
- Where State Transitions Happen
- Notification as Lightweight Analytics
- WARNING: No Formal Analytics Layer
- Instrumentation Checklist

---

## WARNING: No Formal Analytics Layer

This plugin has no structured analytics or telemetry system. There is no event bus, no tracking
service, no funnel metrics. All "analytics" today are indirect: IDE logs, user-reported issues, and
support conversations.

Before adding instrumentation, decide on the collection mechanism:

| Option | Tradeoff |
|--------|----------|
| IntelliJ Logger (existing) | Free, local only, not aggregatable |
| JetBrains Marketplace analytics | Install counts only, no in-app events |
| Custom telemetry endpoint | Requires user consent, GDPR compliance, server infra |
| IDE Usage Statistics API | Requires JetBrains approval for plugin stats |

The pragmatic path for now: **use Logger at INFO level for key activation events**, so logs captured
in support sessions reveal where users get stuck.

---

## What to Instrument

These are the highest-value events for understanding activation and empty state impact:

```kotlin
private val log = Logger.getInstance(SessionService::class.java)

// Login funnel
log.info("[keyscript.activation] login_attempted instance=$instance")
log.info("[keyscript.activation] login_succeeded username=$username instance=$instance")
log.info("[keyscript.activation] login_failed reason=${result.error}")

// Script execution
log.info("[keyscript.activation] script_run_started file=${file.name} instance=$instance")
log.info("[keyscript.activation] script_run_completed file=${file.name}")

// Empty state interactions
log.info("[keyscript.ux] empty_state_shown panel=TableBrowser reason=not_logged_in")
log.info("[keyscript.ux] settings_opened_from_empty_state panel=SessionPanel")
```

Using a consistent prefix (`[keyscript.activation]`, `[keyscript.ux]`) makes log grep trivial
during support sessions.

---

## Where State Transitions Happen

These are the exact code locations where meaningful state changes occur. Instrument here:

| Event | Location | Method |
|-------|----------|--------|
| Login success | `AuthenticationService` | after `session.setSession()` call |
| Login failure | `AuthenticationService` | in catch block / `LoginResult(false)` branch |
| Session expired | `SessionService` | `handleSessionExpired()` |
| Session restored | `SessionService` | `setSession()` called from auto-relogin |
| Script run initiated | `RunKeyscriptService` | `runScript()` entry |
| Deploy initiated | `DeploymentService` | `deploy()` entry |
| Table list loaded | `TableBrowserPanel` | `loadTableList()` success branch |

---

## Notification as Lightweight Analytics

`AuthenticationService` already uses `NotificationGroupManager` for login events. Notifications
are user-visible and also appear in the IDE Event Log — a discoverable record of plugin activity.

```kotlin
// AuthenticationService — existing notification on login success
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript")
    .createNotification(
        "Logged in to Keystone",
        "Authenticated as $username on $instance",
        NotificationType.INFORMATION
    )
    .notify(project)
```

Consistent use of notifications for success/failure events gives users (and support) a timeline
in the Event Log without needing a custom analytics backend.

---

## Empty State Interaction Instrumentation

When a user clicks a button inside an empty state (Login, Open Settings), log it. This tells you
which empty states are actually encountered and whether users recover from them.

```kotlin
// Augment SessionPanel's login button action
panel.add(createActionButton("Login") {
    log.info("[keyscript.ux] login_button_clicked_from_empty_state panel=SessionPanel")
    triggerLoginAction()
}, gbc)

// Augment settings link action
panel.add(ActionLink("Open Settings") {
    log.info("[keyscript.ux] settings_link_clicked_from_empty_state panel=SessionPanel")
    openSettings()
}, gbc)
```

---

## Instrumentation Checklist

Copy this checklist when adding a new feature or panel:

```
- [ ] Login success/failure logged with username + instance
- [ ] Primary empty state shown logged with panel name + reason
- [ ] CTA clicks within empty states logged
- [ ] Script run start/end logged
- [ ] Error cases logged at WARN level with specific message
- [ ] Notification shown for user-visible success/failure events
- [ ] All log entries use [keyscript.*] prefix for grepping
```

---

See the **instrumenting-product-metrics** skill for deeper telemetry design patterns.
See the **improving-activation-flow** skill for which funnel steps most need instrumentation.
