# Kotlin Modules Reference

## Contents
- Package Structure
- Cross-Module Service Access
- Adding a New Service
- Adding a New Action
- Adding a New Tool Window
- Plugin.xml Registration Checklist

---

## Package Structure

```
com.keyscript.plugin/
├── actions/        # AnAction subclasses — menu items, toolbar buttons
├── completion/     # Code completion contributors and live templates
├── preview/        # JCEF split-editor and browser panel
├── project/        # New Project wizard (ModuleBuilder)
├── proxy/          # Ktor proxy server, routes, cookie injection
├── runconfig/      # Run configuration type, runner, line marker
├── services/       # All 14 project/app services (business logic)
├── settings/       # Configurable panels + PersistentStateComponent
├── statusbar/      # Status bar widget factory and widget
└── toolwindow/     # ToolWindowFactory implementations and panels
```

Services hold all business logic. Actions, tool windows, and other extension points are thin — they retrieve the relevant service and delegate:

```kotlin
// GOOD — action delegates to service
class DeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<DeploymentService>().deployCurrentFile(e)
    }
}

// BAD — business logic in action
class DeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val httpClient = HttpClient(CIO) { /* ... */ }
        val response = runBlocking { httpClient.post("...") { /* ... */ } }
        // 50 more lines of logic that can't be tested
    }
}
```

---

## Cross-Module Service Access

Project-scoped services are retrieved via `project.service<T>()`. App-scoped services via `service<T>()` or `ApplicationManager.getApplication().service<T>()`.

```kotlin
// From any component that has a Project reference
val session = project.service<SessionService>()
val auth = project.service<AuthenticationService>()
val settings = service<KeyscryptSettings>()  // app-scoped

// Services can access other services in their constructor (DI-lite)
@Service(Service.Level.PROJECT)
class DeploymentService(private val project: Project) : Disposable {
    private val apiClient get() = project.service<KeystoneApiClient>()
    private val session get() = project.service<SessionService>()
    // Using get() avoids holding a reference — services dispose in any order
}
```

AVOID storing service references as constructor-injected fields when services have parallel disposal order — use `get()` properties that re-resolve lazily.

---

## Adding a New Service

Copy this checklist when adding a service:

- [ ] Create `src/main/kotlin/com/keyscript/plugin/services/MyService.kt`
- [ ] Annotate: `@Service(Service.Level.PROJECT)` (or APP)
- [ ] Constructor: `(private val project: Project)` for project-scoped
- [ ] Implement `Disposable`, override `dispose()`
- [ ] Register in `plugin.xml`:
  ```xml
  <extensions defaultExtensionNs="com.intellij">
      <projectService serviceImplementation="com.keyscript.plugin.services.MyService"/>
  </extensions>
  ```
- [ ] Access via `project.service<MyService>()`

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val project: Project) : Disposable {
    private val LOG = Logger.getInstance(MyService::class.java)
    private val _listeners = CopyOnWriteArrayList<() -> Unit>()

    fun doSomething() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = performWork()
                ApplicationManager.getApplication().invokeLater { notifyListeners() }
            } catch (e: Exception) {
                LOG.warn("MyService.doSomething failed", e)
            }
        }
    }

    fun addListener(l: () -> Unit) { _listeners.add(l) }
    override fun dispose() { _listeners.clear() }
}
```

---

## Adding a New Action

- [ ] Create `src/main/kotlin/com/keyscript/plugin/actions/MyAction.kt`
- [ ] Extend `AnAction`
- [ ] Implement `actionPerformed(e: AnActionEvent)` — delegate to a service
- [ ] Implement `update(e: AnActionEvent)` — set `e.presentation.isEnabled`
- [ ] Register in `plugin.xml`:
  ```xml
  <actions>
      <action id="Keyscript.MyAction"
              class="com.keyscript.plugin.actions.MyAction"
              text="My Action"
              description="Does something useful">
          <add-to-group group-id="KeyscriptMenu" anchor="last"/>
      </action>
  </actions>
  ```

```kotlin
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<DeploymentService>().deployCurrentFile(e)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val isKeyscript = project?.service<KeyscryptProjectDetector>()?.isKeyscriptProject() ?: false
        e.presentation.isEnabled = isKeyscript
    }
}
```

---

## Adding a New Tool Window

- [ ] Create `src/main/kotlin/com/keyscript/plugin/toolwindow/MyToolWindowFactory.kt`
- [ ] Implement `ToolWindowFactory` and `DumbAware`
- [ ] Register in `plugin.xml`:
  ```xml
  <toolWindow id="My Tool Window"
              factoryClass="com.keyscript.plugin.toolwindow.MyToolWindowFactory"
              anchor="bottom"
              conditionClass="com.keyscript.plugin.services.KeyscryptProjectDetector"/>
  ```

```kotlin
class MyToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MyPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(panel, "My Tab", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean =
        project.service<KeyscryptProjectDetector>().isKeyscryptProject()
}
```

---

## Plugin.xml Registration Checklist

Every new Kotlin class that extends an IntelliJ extension point MUST be registered in `plugin.xml`. Missing registration = the class is never instantiated.

| Class Type | XML Element | Namespace |
|------------|-------------|-----------|
| Project service | `<projectService>` | `com.intellij` |
| App service | `<applicationService>` | `com.intellij` |
| Action | `<action>` | (inside `<actions>`) |
| Tool window | `<toolWindow>` | `com.intellij` |
| Configurable | `<projectConfigurable>` / `<applicationConfigurable>` | `com.intellij` |
| Run config type | `<configurationType>` | `com.intellij` |
| Completion contributor | `<completion.contributor>` | `com.intellij` |
| Status bar widget | `<statusBarWidgetFactory>` | `com.intellij` |

See the **intellij-platform** skill for full `plugin.xml` patterns.
