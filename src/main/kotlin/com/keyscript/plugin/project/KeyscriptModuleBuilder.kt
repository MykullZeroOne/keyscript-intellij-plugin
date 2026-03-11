package com.keyscript.plugin.project

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.icons.AllIcons
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Module builder for new Keyscript projects.
 * Appears in File > New > Project as "Keyscript".
 */
class KeyscriptModuleBuilder : ModuleBuilder() {

    var selectedTemplate: Template = Template.VANILLA_KEYSTONE

    enum class Template(val label: String, val description: String) {
        VANILLA_KEYSTONE(
            "Vanilla JS + Keystone",
            "Plain JavaScript with CR framework integration — no build step needed."
        ),
        REACT_KEYSTONE(
            "React + Keystone",
            "React app with CR framework integration and esbuild bundling."
        ),
        REACT_STANDALONE(
            "React Standalone",
            "Pure React app for prototyping — no Keystone dependency."
        ),
        BLANK(
            "Blank Script",
            "A single Keyscript file — the simplest starting point."
        )
    }

    override fun getModuleType(): ModuleType<*> = StdModuleTypes.JAVA
    override fun getName(): String = "Keyscript"
    override fun getPresentableName(): String = "Keyscript"
    override fun getDescription(): String = "Create a new Keyscript project for Keystone script development"
    override fun getNodeIcon(): javax.swing.Icon = AllIcons.General.Web
    override fun getBuilderId(): String = "keyscript.project.builder"

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val contentEntry = doAddContentEntry(modifiableRootModel) ?: return
        val root = contentEntry.file?.path ?: return
        val rootDir = File(root)

        // Create .keyscript marker
        File(rootDir, ".keyscript").writeText("# Keyscript IDE project marker\n")

        when (selectedTemplate) {
            Template.BLANK -> scaffoldBlank(rootDir)
            Template.VANILLA_KEYSTONE -> scaffoldVanillaKeystone(rootDir)
            Template.REACT_KEYSTONE -> scaffoldReactKeystone(rootDir)
            Template.REACT_STANDALONE -> scaffoldReactStandalone(rootDir)
        }

        // Refresh VFS so IntelliJ sees the new files
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootDir)
    }

    override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: com.intellij.openapi.Disposable?): ModuleWizardStep {
        return TemplateSelectionStep()
    }

    // ─── Template scaffolding ────────────────────────

    private fun scaffoldBlank(root: File) {
        File(root, "script.keyscript.js").writeText(BLANK_SCRIPT)
    }

    private fun scaffoldVanillaKeystone(root: File) {
        File(root, "script.keyscript.js").writeText(VANILLA_SCRIPT)
    }

    private fun scaffoldReactKeystone(root: File) {
        val src = File(root, "src").apply { mkdirs() }
        File(root, "keyscript.bundle.json").writeText(BUNDLE_CONFIG_REACT)
        File(src, "index.jsx").writeText(REACT_INDEX)
        File(src, "App.jsx").writeText(REACT_APP_KEYSTONE)
        File(src, "keystone.js").writeText(KEYSTONE_HELPERS)
    }

    private fun scaffoldReactStandalone(root: File) {
        val src = File(root, "src").apply { mkdirs() }
        File(root, "keyscript.bundle.json").writeText(BUNDLE_CONFIG_REACT)
        File(src, "index.jsx").writeText(REACT_INDEX)
        File(src, "App.jsx").writeText(REACT_APP_STANDALONE)
    }

    // ─── Wizard step ─────────────────────────────────

    inner class TemplateSelectionStep : ModuleWizardStep() {
        private val group = ButtonGroup()
        private val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(12, 12, 12, 12)
        }

        init {
            panel.add(JLabel("Select a project template:"))
            panel.add(Box.createVerticalStrut(12))

            for (template in Template.entries) {
                val radio = JRadioButton(template.label).apply {
                    isSelected = template == selectedTemplate
                    addActionListener { selectedTemplate = template }
                }
                group.add(radio)
                panel.add(radio)

                val desc = JLabel("    ${template.description}").apply {
                    foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
                    font = font.deriveFont(font.size2D - 1f)
                }
                panel.add(desc)
                panel.add(Box.createVerticalStrut(8))
            }
        }

        override fun getComponent(): JComponent = panel
        override fun updateDataModel() { /* selectedTemplate already set via listener */ }
    }

    companion object {
        private val BLANK_SCRIPT = """
// @keyscript
// Keyscript — edit and run with Ctrl+Shift+F10

Ext.onReady(function() {
    var panel = new Ext.Panel({
        title: 'My Script',
        html: '<h2>Hello from Keyscript!</h2>',
        renderTo: Ext.getBody()
    });
});
""".trimIndent() + "\n"

        private val VANILLA_SCRIPT = """
// @keyscript
// Keyscript with Keystone integration

Ext.onReady(function() {
    var xml = new CR.XML();
    var sequence = xml.addContainer(xml.getRootElement(), 'sequence');
    var transaction = xml.addContainer(sequence, 'transaction');
    var step = xml.addContainer(transaction, 'step');
    var search = xml.addContainer(step, 'search');

    xml.addText(search, 'tableName', 'PERSON');
    xml.addText(search, 'filterName', 'BY_LAST_FIRST_MIDDLE_NAME');
    xml.addOption(search, 'includeSelectColumns', 'Y');
    xml.addOption(search, 'includeTotalHitCount', 'Y');
    xml.addCount(search, 'returnLimit', 10);

    var param = xml.addContainer(search, 'parameter');
    xml.addText(param, 'contents', 'Smith');

    CR.Core.ajaxRequest({
        url: 'DirectXMLPostJSON',
        xmlData: xml.getXMLDocument(),
        success: function(response) {
            var data = CR.JSON.parse(response.responseText);
            console.log('Search result:', data);
        }
    });
});
""".trimIndent() + "\n"

        private val BUNDLE_CONFIG_REACT = """
{
  "entry": "src/index.jsx",
  "outfile": "dist/bundle.js",
  "format": "iife",
  "target": "es2020",
  "minify": false,
  "jsx": "automatic",
  "external": [],
  "define": {}
}
""".trimIndent() + "\n"

        private val REACT_INDEX = """
// @keyscript
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

Ext.onReady(function() {
    var container = document.createElement('div');
    container.id = 'root';
    document.body.appendChild(container);
    ReactDOM.createRoot(container).render(<App />);
});
""".trimIndent() + "\n"

        private val REACT_APP_KEYSTONE = """
import React, { useState, useEffect } from 'react';
import { query } from './keystone';

export default function App() {
    const [results, setResults] = useState([]);
    const [error, setError] = useState(null);

    useEffect(() => {
        query('PERSON', 'BY_LAST_FIRST_MIDDLE_NAME', 'Smith')
            .then(setResults)
            .catch(err => setError(err.message));
    }, []);

    if (error) return <div style={{ color: 'red' }}>Error: {error}</div>;

    return (
        <div style={{ padding: 16 }}>
            <h2>Keyscript React App</h2>
            <p>Found {results.length} results</p>
            <ul>
                {results.map((r, i) => (
                    <li key={i}>{r.serial} — {r.description}</li>
                ))}
            </ul>
        </div>
    );
}
""".trimIndent() + "\n"

        private val REACT_APP_STANDALONE = """
import React, { useState } from 'react';

export default function App() {
    const [count, setCount] = useState(0);

    return (
        <div style={{ padding: 16, fontFamily: 'sans-serif' }}>
            <h2>Keyscript React App</h2>
            <p>Count: {count}</p>
            <button onClick={() => setCount(c => c + 1)}>Increment</button>
            <button onClick={() => setCount(0)} style={{ marginLeft: 8 }}>Reset</button>
        </div>
    );
}
""".trimIndent() + "\n"

        private val KEYSTONE_HELPERS = """
/**
 * Keystone query helper — wraps CR.XML + CR.Core.ajaxRequest in a Promise.
 */
export function query(tableName, filterName, searchValue, limit = 20) {
    return new Promise(function(resolve, reject) {
        var xml = new CR.XML();
        var sequence = xml.addContainer(xml.getRootElement(), 'sequence');
        var transaction = xml.addContainer(sequence, 'transaction');
        var step = xml.addContainer(transaction, 'step');
        var search = xml.addContainer(step, 'search');

        xml.addText(search, 'tableName', tableName);
        xml.addText(search, 'filterName', filterName);
        xml.addOption(search, 'includeSelectColumns', 'Y');
        xml.addOption(search, 'includeTotalHitCount', 'Y');
        xml.addCount(search, 'returnLimit', limit);

        var param = xml.addContainer(search, 'parameter');
        xml.addText(param, 'contents', searchValue);

        CR.Core.ajaxRequest({
            url: 'DirectXMLPostJSON',
            xmlData: xml.getXMLDocument(),
            success: function(response) {
                try {
                    var data = CR.JSON.parse(response.responseText);
                    var rows = [];
                    var query = data.query;
                    if (query) {
                        Ext.each(query.sequence, function(seq) {
                            Ext.each(seq.transaction, function(txn) {
                                Ext.each(txn.step, function(step) {
                                    if (step.search && step.search.resultRow) {
                                        var resultRows = step.search.resultRow;
                                        if (!Array.isArray(resultRows)) resultRows = [resultRows];
                                        resultRows.forEach(function(row) {
                                            rows.push({
                                                serial: row.serial,
                                                description: row.rowDescription || ''
                                            });
                                        });
                                    }
                                });
                            });
                        });
                    }
                    resolve(rows);
                } catch (e) {
                    reject(e);
                }
            },
            failure: function(response) {
                reject(new Error(response.statusText || 'Request failed'));
            }
        });
    });
}
""".trimIndent() + "\n"
    }
}
