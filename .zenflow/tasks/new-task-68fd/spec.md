# Technical Specification - Keystone Keyscript JetBrains Plugin

## Technical Context
- **Target Platform**: JetBrains IDEs (IntelliJ IDEA, WebStorm, etc.)
- **Language**: Kotlin
- **Build System**: Gradle with IntelliJ Plugin Verifier
- **Dependencies**:
    - `com.squareup.okhttp3:okhttp`: For API calls to Keybridge and Keystone.
    - `com.google.code.gson:gson`: For JSON serialization/deserialization.
    - JetBrains SDK: For UI components (Tool Windows, Dialogs, Project View integration).

## Implementation Approach

### 1. Project Window Integration
Instead of a custom script explorer, the plugin will utilize the standard JetBrains **Project Window**. 
- Scripts are identified as `.js` files within the project structure.
- Action to "Run as Keyscript" will be added to the context menu for these files.

### 2. Authentication (Keystone Logon)
A dedicated `AuthenticationManager` service will handle session lifecycle.
- **Keybridge Logon**: Utilize the `KeybridgeAPI` to obtain a `sessionId`.
    - **Endpoint**: `http://keystonedev.revfcu.com:52310/Development` (Configurable).
    - **Request**: JSON-RPC/GraphQL style `logon` query.
    - **Session Persistence**: The `sessionId` will be stored in memory and reused for script executions until it expires or is explicitly cleared.
- **Logon UI**: A login dialog will be provided to capture credentials and server URL.

### 3. Script Options (Execution Context)
A `ScriptOptionsManager` will maintain the execution context parameters.
- **Parameters**: `personSerial`, `accountSerial`, `applicationSerial`, `workTaskSerial`, `collectionItemSerial`.
- **UI**: A Tool Window (similar to "Script Options" in the original IDE) will allow users to view and edit these serials.
- **Persistence**: These options will be saved at the project level (`.idea/keyscript-options.xml`).

### 4. Script Execution
When a user runs a script:
1. The plugin retrieves the current `sessionId` from `AuthenticationManager`.
2. It retrieves the current `scriptOptions` from `ScriptOptionsManager`.
3. It constructs the execution payload (similar to the original IDE's `SessionStore` + `RunScript` flow).
4. It triggers the execution. Since this is an IDE plugin, execution might involve:
    - Opening a browser/webview with the `RunScript` URL.
    - Or executing via a headless API if supported by Keystone.

## Source Code Structure Changes (New Plugin Structure)
```
src/main/kotlin/com/revfcu/keyscript/
├── api/
│   ├── KeybridgeClient.kt       // OkHttp wrapper for KeybridgeAPI
│   └── KeystoneClient.kt        // Wrapper for RunScript and other Keystone endpoints
├── auth/
│   ├── AuthenticationManager.kt // Handles session and login
│   └── LoginDialog.kt           // UI for credentials
├── options/
│   ├── ScriptOptionsManager.kt  // Manages execution context serials
│   ├── ScriptOptionsService.kt  // Persistent state for options
│   └── ScriptOptionsToolWindow.kt // UI for editing options
├── actions/
│   └── RunKeyscriptAction.kt    // Context menu action to run a script
└── settings/
    └── KeyscriptSettingsConfigurable.kt // Plugin settings UI
```

## Data Model / Interface Changes
- **LoginResult**: Will now primarily focus on the `sessionId` returned by `KeybridgeAPI`.
- **Execution Payload**:
    ```json
    {
      "crlogin": {
        "sessionID": "...",
        "userName": "...",
        "postingDate": "...",
        "deviceName": "...",
        "branchName": "..."
      },
      "crscript": {
        "personSerial": "...",
        "accountSerial": "...",
        "applicationSerial": "...",
        "workTaskSerial": "...",
        "collectionItemSerial": "..."
      }
    }
    ```

## Delivery Phases
1. **Phase 1: Setup & Authentication**: Implement `KeybridgeClient` and `AuthenticationManager` with basic Login Dialog.
2. **Phase 2: Script Options**: Implement `ScriptOptionsManager` and the Tool Window.
3. **Phase 3: Execution Engine**: Implement `RunKeyscriptAction` and integration with Keystone's `RunScript` endpoint.
4. **Phase 4: Settings & Refinement**: Add configuration options and polish UI.

## Verification Approach
- **Unit Tests**: Test `KeybridgeClient` with mocked API responses. Test `ScriptOptionsManager` persistence.
- **Manual Testing**: 
    - Verify "Logon" successfully retrieves a session ID.
    - Verify "Script Options" values are correctly persisted and retrieved.
    - Verify "Run as Keyscript" correctly opens the execution environment with the right parameters.
