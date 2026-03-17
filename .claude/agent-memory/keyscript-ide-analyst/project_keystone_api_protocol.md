---
name: Keystone API Protocol
description: Exact HTTP protocol details for all Keystone endpoints — request format, body encoding, response structure, and JSESSIONID session mechanics
type: project
---

## Authentication / UserLogin

- Endpoint: `POST /{instance}/UserLogin`
- Content-Type: `application/x-www-form-urlencoded`
- Login body params: `loginUsername`, `loginPassword`, `loginDeviceIdentifier`, `loginDeviceInsertOption=Y`
- Status check (no-creds probe): body is just `loginStatus=Y`
- Kerberos SSO: `GET /{instance}/UserLogin?loginDeviceIdentifier=...&loginDeviceInsertOption=N` — must hit Keystone directly (not via proxy) for Kerberos tokens to flow
- Response: JSON with `{ success, loggedOn, userName, JSESSIONID, postingDate, deviceName, branchName, databaseName, exception[], warning[] }`
- Errors returned as `exception: ["message"]` — not HTTP 4xx
- Post-login call: empty POST to `LoginUserInterface` (non-critical)

## Device Identifier

- Electron: GET `/GetDeviceInformation` → Go service at port 51763 → XML `<identifier>` element
- Web/Docker: user-supplied, stored in localStorage `keyscript-device-id`, sent to proxy via `POST /api/device-id`
- Proxy rewrites body: `loginDeviceInsertOption=N` → `Y` for login requests

## SearchJSON

- `POST /SearchJSON`, `Content-Type: text/xml`
- XML namespace: `http://www.corelationinc.com/queryLanguage/v1.0`, prefix `v1`
- Structure: `<v1:query><v1:sequence><v1:transaction><v1:step><v1:search>..`
- Key search elements: `<v1:tableName>`, `<v1:filterName>`, `<v1:includeSelectColumns option="Y"/>`, `<v1:includeTotalHitCount option="Y"/>`, `<v1:returnLimit>`, `<v1:parameter><v1:columnName>...<v1:contents>...`
- Response: JSON `{ resultRows: [...], totalHitCount: N }`
- Error responses: prefixed `>>EXC<<code>>message` or `>>SNF<<code>>` (NOT JSON)

## DirectXMLPostJSON

- `POST /DirectXMLPostJSON`, `Content-Type: text/xml`
- Same XML namespace/prefix as SearchJSON
- Used for record view (`<v1:record><v1:operation option="V"/>...`) and record update/post
- Response: JSON representation of XML tree; results at `data.query.sequence[n].transaction[n].$attr.result`
- Success value: `"posted"`, verify-mode: `"verified"`, failure: `"failed"`
- Exceptions at `data.query.sequence[n].transaction[n].exception[n].message`

## TableBrowser

All `POST /TableBrowser`, `Content-Type: application/x-www-form-urlencoded`
- Full list: empty body → JSON array of table objects (tableName, tableDescription, field[], childTable[], reference[])
- Search filters: `step=searchList&tableName=X` → nested `{ query.sequence[0].transaction[0].step[] }` where each step has a `search` object with filterName, filterDescription, parameter[]
- Record tree: `step=recordTreeList&tableName=X`

## SessionStore (script parameter passing)

- `POST /SessionStore`, `Content-Type: application/x-www-form-urlencoded`
- Body: `value=<JSON-string>` where JSON = `{ crlogin: {...}, crscript: {...} }`
- Response: `{ success: true, id: "some-id" }`
- The `id` is then passed as `scriptParametersId` query param to RunScript
- Proxy intercepts and stores params locally; RunScript handler injects them into the page

## Script Listing (KeyscriptServlet/List)

- `POST */KeyscriptServlet/List`, `Content-Type: application/x-www-form-urlencoded`
- Body: `node=KeyScripts\FolderPath`
- Handled LOCALLY by proxy (not forwarded to Keystone) — reads filesystem
- Response: ExtJS tree node array `[{ text, id, cls, leaf?, scriptPath? }]`

## Filter Auto-Detection

Both IDEs use same logic to auto-pick filter from free-text:
- All digits ≤10 chars → `BY_ACCOUNT_NUMBER`
- `999-99-9999` or `99-9999999` → `BY_TIN`
- `999.999.9999` or `999-999-9999` → `BY_PHONE_NUMBER`
- Has `@` with `.` after → `BY_EMAIL_ADDRESS`
- Default → `BY_LAST_FIRST_MIDDLE_NAME`

## crXMLData= Unwrapping

CR framework sometimes sends XML as a URL-encoded form field `crXMLData=<encoded-xml>`.
Proxy detects `raw.startsWith('crXMLData=')` and decodes before forwarding.

## Session Management

- No heartbeat timer in the IDE's own code — CR framework AJAX calls in the iframe keep Keystone session alive naturally
- JSESSIONID lives as a module-level string in the proxy process (`ssoSessionId`)
- Injected via `Cookie: JSESSIONID=...` header on every outgoing proxied request
- No automatic re-login after timeout — user must manually log in again
- Keystone Set-Cookie headers have `Path=/{instance}/...` — proxy rewrites to `Path=/` so browser accepts for all localhost requests
- On HTTP, proxy also strips `Secure` flag from Set-Cookie
