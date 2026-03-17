# Types Reference

## Contents
- cr-framework.d.ts Overview
- Adding New Type Declarations
- Namespace Conventions
- Extending Existing Namespaces
- Common Type Patterns

---

## cr-framework.d.ts Overview

`src/main/resources/cr-types/cr-framework.d.ts` is the single TypeScript declaration file providing IntelliJ code completion for all Keyscript user scripts. It declares the CR.* namespaces and the ExtJS `Ext` global that are loaded at runtime by `keyscript-all.js`.

**This file is not compiled.** It is a pure `.d.ts` ambient declaration consumed by IntelliJ's TypeScript language service for completions in `.keyscript.js` files. The runtime implementations live in `js-lib/keyscript-all.js`.

Key top-level namespaces:

| Namespace | Purpose |
|-----------|---------|
| `CR.XML` | XML document builder for Keystone transactions |
| `CR.Core` | Core utilities: ajaxRequest, defer, viewPort |
| `CR.JSON` | JSON parsing utilities |
| `CR.Login` | Session state: userName, JSESSIONID |
| `CR.Script` | Script context: scriptSerial, scriptDescription |
| `CR.Field` | Base field: focus, validate, getValue |
| `CR.GridPanel` | ExtJS table grid |
| `CR.EditorGridPanel` | Inline-editable grid |
| `CR.FormPanel` | Form UI builder |
| `CR.TabPanel` / `CR.Panel` | Layout containers |
| `Ext` | Full ExtJS 3.2.2 ambient declarations |

---

## Adding New Type Declarations

When the Keystone server exposes a new API or the CR framework gains new methods, add declarations here. Follow the existing namespace structure:

```typescript
// In cr-framework.d.ts

declare namespace CR {
    /**
     * File transfer utilities for Keystone document management.
     */
    namespace FileTransfer {
        /**
         * Upload a file to Keystone document store.
         * @param file - File object from input element
         * @param tableName - Target table name
         * @param callback - Called with upload result
         */
        function upload(
            file: File,
            tableName: string,
            callback: (result: { success: boolean; docId?: string; error?: string }) => void
        ): void;

        /** Download a document by ID */
        function download(docId: string): void;
    }
}
```

---

## Namespace Conventions

**DO**: Use nested `namespace` declarations matching the CR.* runtime structure

```typescript
// GOOD — mirrors runtime CR.Core.ajaxRequest() call
declare namespace CR {
    namespace Core {
        function ajaxRequest(config: AjaxConfig): void;
    }
}
```

**DON'T**: Flatten namespaces or use module syntax

```typescript
// BAD — breaks the CR.Core.* autocompletion path
declare module "cr-framework" {
    export function ajaxRequest(config: AjaxConfig): void;
}
```

**DON'T**: Add `export` keywords inside `declare namespace` blocks

```typescript
// BAD — ambient namespace members are implicitly exported
declare namespace CR {
    namespace Core {
        export function ajaxRequest(config: AjaxConfig): void;  // 'export' is redundant and wrong
    }
}
```

---

## Extending Existing Namespaces

TypeScript allows declaration merging — add new members to existing namespaces without touching the original declaration block. Put additions at the bottom of the file with a comment:

```typescript
// --- Extensions added for Keystone 4.2 API ---
declare namespace CR {
    namespace XML {
        /** New in Keystone 4.2: batch XML document support */
        function createBatch(): XMLBatch;

        interface XMLBatch {
            add(doc: XMLDocument): void;
            submit(config: AjaxConfig): void;
        }
    }
}
```

---

## Common Type Patterns

### AjaxRequest config type

The `CR.Core.ajaxRequest` config accepts callbacks with a response object. Type the response explicitly where you need strict checking:

```typescript
declare namespace CR {
    namespace Core {
        interface AjaxConfig {
            url: string;
            xmlData?: XMLDocument;
            params?: Record<string, string | number>;
            success?: (response: KeystoneResponse) => void;
            failure?: (response: KeystoneResponse) => void;
        }

        interface KeystoneResponse {
            data?: {
                records?: Record<string, unknown>[];
                results?: Record<string, unknown>[];
                errorCode?: string;
                errorDescription?: string;
            };
            status: number;
        }
    }
}
```

### Grid column definition type

```typescript
declare namespace CR {
    namespace GridPanel {
        interface ColumnConfig {
            header: string;
            dataIndex: string;
            width?: number;
            renderer?: (value: unknown, meta: unknown, record: Ext.data.Record) => string;
            editor?: Ext.form.Field;
        }
    }
}
```

### WARNING: Using `any` for CR response data

The Keystone response structure is well-defined. Using `any` defeats completions and masks bugs:

```typescript
// BAD — no completion, no type safety
success: function(response: any) {
    const val = response.data.records[0].someField;  // no error if field is wrong
}

// GOOD — declare the shape you expect
interface EmployeeRecord {
    empId: string;
    firstName: string;
    lastName: string;
}
success: function(response: CR.Core.KeystoneResponse) {
    const records = (response.data?.records ?? []) as EmployeeRecord[];
    records[0].firstName;  // completion works
}
```
