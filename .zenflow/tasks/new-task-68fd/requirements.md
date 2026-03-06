# Product Requirements Document (PRD) - KeyScript Jetbrains Plugin Improvements

## Overview
The goal is to improve the KeyScript Jetbrains Plugin by aligning its session management and script execution with the KeybridgeAPI and utilizing built-in IDE features.

## Requirements

### 1. Integration with Built-in Project Window
- **Requirement**: Remove the custom "Script Explorer" tool window.
- **Goal**: Allow users to manage and navigate KeyScript files (`.js`) directly through the standard IntelliJ Project View.
- **Action**: Deleted `ScriptExplorerToolWindowFactory.kt` and removed its registration from `plugin.xml`.

### 2. Keystone Logon via KeybridgeAPI
- **Requirement**: Implement a new logon method using the `KeybridgeAPI` to obtain a `sessionId`.
- **Goal**: Replace the traditional login mechanism with the JSON-based Keybridge logon.
- **Implementation**:
    - Added `keybridgeLogon(username, password, deviceName)` to `KeyScriptApiService.kt`.
    - Updated `LoginAction.kt` to call `keybridgeLogon`.
    - Renamed `jsessionId` to `sessionId` across `AuthService.kt`, `KeyScriptApiService.kt`, and `LoginResult` for consistency.

### 3. Execution Context & Session Management
- **Requirement**: Maintain and pass the `sessionId` and `scriptOptions` during script execution.
- **Goal**: Ensure the Keystone execution environment has the correct context (person, account, instance) and a valid session.
- **Implementation**:
    - Updated `KeyScriptRunState.kt` to include `crlogin` (with `sessionID`, `userName`, etc.) and `crscript` (with `personSerial`, `accountSerial`, etc.) in the session parameters stored via `SessionStore`.
    - Integrated `AuthService` and `ScriptOptionsState` into the run configuration state.

## Success Criteria
- [x] Scripts can be run from the IDE with session parameters correctly populated.
- [x] Authentication uses the KeybridgeAPI.
- [x] No redundant "Script Explorer" window.
