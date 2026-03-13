package com.keyscript.plugin.toolwindow

object KeyscriptToolWindowIds {
    const val WORKSPACE = "Keyscript Workspace"
    const val DATA_TOOLS = "Keyscript Data Tools"
    const val DIAGNOSTICS = "Keyscript Diagnostics"
}

enum class WorkspaceTab(val title: String) {
    RUN_OPTIONS("Run Options"),
    SESSION("Session")
}

enum class DataToolsTab(val title: String) {
    SEARCH("Search"),
    TABLE_BROWSER("Table Browser"),
    QUERY_BUILDER("Query Builder"),
    INSTALLED_SCRIPTS("Installed Scripts")
}

enum class DiagnosticsTab(val title: String) {
    CONSOLE("Console"),
    NETWORK("Network")
}
