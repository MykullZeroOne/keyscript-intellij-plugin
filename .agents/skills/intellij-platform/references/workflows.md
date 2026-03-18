# IntelliJ Platform Workflows Reference

## Contents
- Build & Run
- Add a New Service
- Add a New Action
- Add a New Tool Window
- Add a Settings Configurable
- Validate Build Errors

---

## Build & Run

```bash
# Compile and verify plugin descriptor
./gradlew build

# Launch sandbox IDE (test instance with plugin loaded)
./gradlew runIde

# Package for distribution
./gradlew buildPlugin
# → build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

Use `mcp__jetbrains__build_project` to trigger a build from within the IDE and `mcp__jetbrains__get_file_problems` to surface compilation errors without leaving the conversation.

**Feedback loop for compile errors:**
1. Edit source file
2. Run `mcp__jetbrains__build_project`
3. If errors, run `mcp__jetbrains__get_file_problems` on the failing file
4. Fix and repeat until build is clean

---

## Add a New Service

Copy this checklist and track progress:

- [ ] Step 1: Create `src/main/kotlin/com/keyscript/plugin/services/MyService.kt`
- [ ] Step 2: Annotate with `@Service(Service.Level.PROJECT)` (or `APP`)
- [ ] Step 3: Implement `Disposable` if the service holds resources (timers, threads, coroutines)
- [ ] Step 4: Add `companion object { fun getInstance(project: Project) = project.getService(...) }`
- [ ] Step 5: Register in `plugin.xml` under `<projectService serviceImplementation="..."/>`
- [ ] Step 6: Build to verify registration

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(MyService::class.java)

    fun doWork() {
        log.info("Doing work for ${project.name}")
    }

    override fun dispose() {
        // cancel timers, coroutine scopes, etc.
    }

    companion object {
        fun getInstance(project: Project): MyService =
            project.getService(MyService::class.java)
    }
}
```

```xml
<!-- plugin.xml -->
<projectService serviceImplementation="com.keyscript.plugin.services.MyService"/>
```

---

## Add a New Action

- [ ] Step 1: Create `src/main/kotlin/com/keyscript/plugin/actions/MyAction.kt`
- [ ] Step 2: Extend `AnAction`, override `getActionUpdateThread()` → `BGT`
- [ ] Step 3: Implement `update()` (visibility/enabled state) and `actionPerformed()`
- [ ] Step 4: Register in `plugin.xml` `<actions>` block with group membership
- [ ] Step 5: Build and test in sandbox

```kotlin
class MyAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabledAndVisible = false; return }
        // Only show in Keyscript projects
        e.presentation.isEnabledAndVisible = KeyscriptProjectDetector.isKeyscriptProject(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Never block EDT — use a named thread
        Thread({
            val result = doHeavyWork(project)
            SwingUtilities.invokeLater { showResult(project, result) }
        }, "keyscript-my-action").start()
    }
}
```

```xml
<action id="Keyscript.MyAction"
        class="com.keyscript.plugin.actions.MyAction"
        text="My Action"
        description="Does something useful">
    <add-to-group group-id="KeyscriptMenu" anchor="last"/>
</action>
```

---

## Add a New Tool Window

Tool windows in this plugin are project-gated via `isApplicable` and use `secondary="true"` to avoid opening in non-Keyscript projects.

- [ ] Step 1: Create factory `src/main/kotlin/com/keyscript/plugin/toolwindow/MyToolWindowFactory.kt`
- [ ] Step 2: Implement `ToolWindowFactory`, gate with `isApplicable`
- [ ] Step 3: Create panel class with Swing components
- [ ] Step 4: Register in `plugin.xml`
- [ ] Step 5: Build and verify the tool window appears only in Keyscript projects

```kotlin
class MyToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MyPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class MyPanel(private val project: Project) {
    val component: JComponent = JPanel(BorderLayout()).apply {
        add(JBLabel("Hello from My Panel"), BorderLayout.CENTER)
    }
}
```

```xml
<toolWindow id="My Tool Window" anchor="bottom" secondary="true"
            factoryClass="com.keyscript.plugin.toolwindow.MyToolWindowFactory"
            icon="AllIcons.Nodes.DataTables"/>
```

---

## Add a Settings Configurable

Application-level settings (shown under **Settings > Keyscript IDE**) use `applicationConfigurable`. Per-project settings go under `projectConfigurable` with `parentId="language"`.

```kotlin
class MySettingsConfigurable : Configurable {
    private val settings = KeyscryptSettings.getInstance()
    private val portField = JBTextField()

    override fun getDisplayName(): String = "My Settings"

    override fun createComponent(): JComponent {
        portField.text = settings.proxyPort.toString()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Proxy Port:"), portField, 1, false)
            .panel
    }

    override fun isModified(): Boolean =
        portField.text.toIntOrNull() != settings.proxyPort

    override fun apply() {
        settings.proxyPort = portField.text.toIntOrNull() ?: settings.proxyPort
    }

    override fun reset() {
        portField.text = settings.proxyPort.toString()
    }
}
```

```xml
<applicationConfigurable instance="com.keyscript.plugin.settings.MySettingsConfigurable"
                         id="com.keyscript.plugin.settings.my"
                         displayName="My Settings"/>
```

---

## Validate Build Errors

When the build fails after a plugin.xml change, the error is usually one of:
1. Missing service registration → `ServiceNotRegisteredException` at runtime
2. Wrong class name in XML → build warning; plugin fails to load
3. Missing `<depends>` for a bundled plugin → `PluginException` on load

**Validation steps:**
1. `./gradlew build` — look for `plugin.xml` parse warnings
2. `./gradlew runIde` — check IDE log (`Help > Show Log`) for startup errors
3. Use `mcp__jetbrains__get_file_problems` on `plugin.xml` for IDE-level validation

For Kotlin compilation errors after platform API changes, check IntelliJ Platform changelog — method signatures change between builds 251–253. The SDK version is pinned at `intellijIdeaUltimate("2025.1.3")` in `build.gradle.kts`.

See the **gradle** skill for dependency management and the **kotlin** skill for Kotlin-specific patterns used in services.
