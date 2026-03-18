// ═══════════════════════════════════════════════════════════════
// CR Framework Type Definitions for Keyscript IDE
// ═══════════════════════════════════════════════════════════════

// ─── CR.XML ──────────────────────────────────────────────────

declare namespace CR {

  /** XML document builder for constructing Keystone transaction XML */
  class XML {
    constructor(config?: { xmlText?: string });
    /** Get the root <query> element */
    getRootElement(): XMLElement;
    /** Get root element name */
    getRootElementName(): string;
    /** Serialize the XML document to a string */
    getXMLDocument(): string;
    /** Create a namespaced element */
    createElement(name: string): XMLElement;
    /** Add a container (non-leaf) element */
    addContainer(parent: XMLElement, name: string): XMLElement;
    /** Remove a container element */
    removeContainer(parent: XMLElement, child: XMLElement): void;
    /** Set attribute on element */
    setAttribute(element: XMLElement, name: string, value: string): void;
    /** Remove attribute from element */
    removeAttribute(element: XMLElement, name: string): void;
    /** Add a text element with a value */
    addText(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add an option element (Y/N, operation codes, etc.) */
    addOption(parent: XMLElement, name: string, value: string): XMLElement;
    /** Add a count element */
    addCount(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a money element */
    addMoney(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a rate element */
    addRate(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a date element */
    addDate(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a time element */
    addTime(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a document element */
    addDocument(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    /** Add a binary element */
    addBinary(parent: XMLElement, name: string, value: any, forceAdd?: boolean): XMLElement;
    [key: string]: any;
  }

  interface XMLElement {}

  // ─── CR.JSON ─────────────────────────────────────────────────

  namespace JSON {
    /** Parse a JSON string (wrapper around native JSON.parse) */
    function parse(text: string): any;
    /** Stringify a value to JSON */
    function stringify(value: any): string;
  }

  // ─── CR.Core ─────────────────────────────────────────────────

  namespace Core {
    // -- AJAX & Network --
    /** Make an AJAX request to the Keystone server */
    function ajaxRequest(config: {
      /** Endpoint URL (relative to instance, e.g. 'DirectXMLPostJSON') */
      url: string;
      /** XML data for POST body */
      xmlData?: string;
      /** URL parameters */
      params?: Record<string, any>;
      /** Success callback */
      success?: (response: { responseText: string; [key: string]: any }) => void;
      /** Failure callback */
      failure?: (error: any) => void;
      /** Callback scope */
      scope?: any;
    }): void;

    /** AJAX timeout in milliseconds */
    let ajaxTimeout: number;
    /** AJAX proxy configuration */
    let AjaxProxy: any;
    /** Handle AJAX errors */
    function ajaxErrorHandler(conn: any, response: any, options: any): void;

    // -- Deferred Execution --
    /** Defer execution until Ext.onReady */
    function defer(fn: () => void): void;

    // -- UI Helpers --
    /** Display exceptions in a dialog */
    function displayExceptions(config: { items: string[] | any[] }): void;
    /** Display HTML content in a window */
    function displayHTML(config: any): void;
    /** Show a confirmation dialog */
    function confirm(title: string, msg: string, fn: (btn: string) => void, scope?: any): void;
    /** Add component to container and show it */
    function addToContainerAndShow(container: any, component: any): void;
    /** Clear all items from a container */
    function clearContainer(container: any): void;
    /** Refresh components in a container */
    function refreshComponents(container: any): void;

    // -- Data Utilities --
    /** Find items in an array matching criteria */
    function find(array: any[], property: string, value: any): any[];
    /** Find items using a callback */
    function findBy(array: any[], fn: (item: any) => boolean): any[];
    /** Find first matching item */
    function findFirst(array: any[], property: string, value: any): any;
    /** Find first matching item or return default */
    function findFirstOrDefault(array: any[], property: string, value: any, defaultValue: any): any;
    /** Find a property value in an object */
    function findProperty(obj: any, property: string): any;
    /** Find first property value or default */
    function findFirstPropertyOrDefault(obj: any, property: string, defaultValue: any): any;
    /** Find table by name in a query response */
    function findTableByName(query: any, tableName: string): any;
    /** Find a child component by CR ID */
    function findChildByCRID(container: any, crId: string): any;
    /** Remove a child component by CR ID */
    function removeChildByCRID(container: any, crId: string): void;
    /** Find all CR fields in a container */
    function findFields(container: any): any[];
    /** Safe property access */
    function safeGet(obj: any, path: string, defaultValue?: any): any;
    /** Copy properties from source to target */
    function copyTo(target: any, source: any, properties: string): void;

    // -- Money/Number Utilities --
    /** Add two money values (integer cents) */
    function addMoneyValues(a: number, b: number): number;
    /** Subtract money values */
    function subtractMoneyValues(a: number, b: number): number;
    /** Convert integer cents to display string */
    function intCentsToString(cents: number): string;
    /** Convert string to integer cents */
    function stringToIntCents(str: string): number;
    /** Reverse the sign of a money value */
    function reverseSign(value: number): number;

    // -- String/Format Utilities --
    /** Convert to HTML line breaks */
    function convertToHtmlLines(text: string): string;
    /** Unescape HTML entities */
    function unescapeHTML(text: string): string;
    /** Adjust string case */
    function adjustStringCase(value: string, caseType: string): string;
    /** Create a JavaScript literal string */
    function jsLiteral(value: any): string;
    /** Convert XML-based field to HTML display */
    function convertFieldToHtmlDisplayByType(field: any): string;

    // -- Record Operations --
    /** View a record */
    function recordView(config: { tableName: string; targetSerial: any; callback?: Function; [key: string]: any }): void;
    /** Update a record */
    function recordUpdate(config: { tableName: string; targetSerial: any; [key: string]: any }): void;
    /** Search for records */
    function recordSearch(config: { tableName: string; [key: string]: any }): void;
    /** Build a single record transaction */
    function record(config: { tableName: string; operation: string; targetSerial?: any; [key: string]: any }): void;
    /** View record tree */
    function recordTree(config: any): void;
    /** Get record tree detail */
    function getRecordTreeDetail(config: any): void;
    /** Get record tree metadata */
    function getRecordTreeMetaData(config: any): void;

    // -- Table/Tran Utilities --
    /** Retrieve table list from server */
    function retrieveTableList(callback: Function): void;
    /** Build transaction options */
    function buildTranOptions(config: any): any;
    /** Execute a transaction code */
    function tranCode(config: any): void;
    /** Set transaction code options */
    function setTranCodeOptions(config: any): void;
    /** Get row description text */
    function getRowDescription(record: any): string;

    // -- Security --
    /** Check security permissions */
    function securityCheck(config: { privilege: string; callback: (allowed: boolean) => void }): void;
    /** Search with prompt dialog */
    function searchPrompt(config: any): void;

    // -- Misc --
    /** Get the current posting date */
    function getPostingDate(): string;
    /** Get the current posting time */
    function getPostingTime(): string;
    /** Get JSESSIONID */
    function getJSessionID(): string;
    /** Check if this is a live database */
    function isLiveDatabase(): boolean;
    /** Get version info */
    function getVersionInfo(): any;
    /** Register a callback */
    function registerCallback(name: string, fn: Function): void;
    /** Execute a registered callback */
    function executeCallback(name: string, ...args: any[]): void;
    /** Get device interaction channel */
    function getDeviceInteractionChannel(): any;
    /** Get device printers */
    function getDevicePrinters(): any;
    /** Send email notification */
    function sendEmailNotification(config: any): void;
    /** Empty function placeholder */
    function emptyFunction(): void;
    /** Cascade a function through components */
    function cascade(container: any, fn: Function): void;

    /** Main application viewport */
    let viewPort: any;
    /** Keystone web app base URL */
    let keyStoneWebAppURL: string;
    /** ExtJS base URL */
    let extBaseURL: string;
    /** Table names cache */
    let tableNames: Record<string, string>;
    /** Table list cache */
    let tableList: any[];
    /** Batch server serial */
    let BatchServerSerial: string;

    // -- HTML Helpers (for grid renderers) --
    function htmlCell(value: string, cls?: string): string;
    function htmlText(value: string): string;
    function htmlOptionCell(value: string): string;
    function htmlFn(fn: string, value: string): string;
    function htmlFunctionVariableText(fn: string, variable: string, text: string): string;
    function isField(component: any): boolean;
    function applyXMLBasedOnFieldType(xml: XML, record: XMLElement, field: any): void;
    function highlightFieldChange(field: any): void;
  }

  // ─── CR.Login ────────────────────────────────────────────────

  namespace Login {
    let userName: string;
    let userSerial: string;
    let sessionID: string;
    let JSESSIONID: string;
    let postingDate: string;
    let locationName: string;
    let branchName: string;
    let branchSerial: string;
    let databaseName: string;
    let deviceName: string;
    let deviceSerial: string;
    let instance: string;
    let defaultTimeZone: string;
    let institutionLicense: string;
    let globalHTTPURL: string;
    let activeDirectoryLogon: boolean;
    let activeDirectoryLogonEnabled: boolean;
    let personVerificationOption: string;
    let personHouseholdCheckOnInsert: boolean;
    let personOFACCheckOnInsertUpdate: boolean;
    let accountListInPersonVerification: boolean;
    let profileAccessInteractionTypeSerial: string;
    let shareSavingsText: string;
    let sharesSavingsText: string;
    let draftCheckText: string;
    let draftCheckingText: string;
    let draftsChecksText: string;
    let dividendInterestText: string;
    let dividendsInterestText: string;
    let impoundText: string;
    let voidConfirmation: boolean;

    // Device printers
    let deviceReceiptPrinters: any[];
    let deviceCheckPrinters: any[];
    let deviceCardPrinters: any[];
    let deviceCamPrinters: any[];
    let deviceCheckScanners: any[];
    let deviceEndorsementPrinters: any[];

    /** Perform login (shows login dialog) */
    function performLogin(): void;
    /** Perform logoff */
    function performLogoff(): void;
    /** Refresh session ID */
    function refreshSessionID(): void;
    /** Clear all login values */
    function clearValues(): void;
    /** Reset login state */
    function resetLogin(): void;
    /** Process successful login response */
    function processSuccessResponse(data: any): void;
    /** Get user interface parameters */
    function getUserInterfaceParameters(): any;
    /** Get receipt config */
    function getReceiptConfig(): any;
    /** Set custom terminology */
    function setTerminology(config: any): void;
    /** Set window title */
    function setWindowTitle(title: string): void;
    /** Security check */
    function securityCheck(privilege: string, callback: Function): void;
    /** Kerberos login */
    function kerberosLogin(): void;
    /** Main page login */
    function mainPageLogin(): void;

    let receiptConfig: any;
    let receiptConfigEmailEnabled: boolean;
    let loginWindow: any;
    let logonHTML: string;
  }

  // ─── CR.Script ───────────────────────────────────────────────

  namespace Script {
    let personSerial: string;
    let accountSerial: string;
    let scriptDefaultPanelId: string;
    let scriptDescription: string;
    let scriptSerial: string;

    /** Run another Keyscript by name */
    function runScript(scriptName: string): void;
    /** Run a form-based script */
    function runForm(config: {
      scriptName?: string;
      panelId?: string;
      personSerial?: string;
      accountSerial?: string;
      [key: string]: any;
    }): void;
    /** Run a report */
    function runReport(config: any): void;
    /** Include external JavaScript/CSS files */
    function includeJSCSS(config: {
      js?: string[];
      css?: string[];
      callBackFunction?: (success: boolean) => void;
    }): void;
    /** Close the current script */
    function closeScript(): void;
    /** Show the work area */
    function showWorkArea(): void;
    /** Show a work task */
    function showWorkTask(config: any): void;
    /** Set script visibility */
    function setVisible(visible: boolean): void;
    /** Set script bounds */
    function setBounds(bounds: any): void;
    /** Refresh components */
    function refreshComponents(): void;
    /** Get core parameters */
    function getCoreParams(): any;
    /** Get core parameters as arguments */
    function getCoreParamsArgs(): any;
    /** Send message to Keystone */
    function messageKeyStone(msg: any): void;
    /** Validate an address */
    function validateAddress(config: any): void;
    /** Display signature pad */
    function sigPadDisplay(config: any): void;

    // FM (Financial Manager) integration
    function fmPost(config: any): void;
    function fmSetField(config: any): void;
    function fmSetViewGroup(config: any): void;
    function fmPopupError(config: any): void;
    function fmMessageHandler(msg: any): void;
    function sendFMResponse(response: any): void;
    function showFMPopup(config: any): void;

    // Workflow integration
    function workflowPost(config: any): void;
    function workflowSetField(config: any): void;
    function workflowAfterPost(config: any): void;
    function workflowPopupError(config: any): void;
    function workflowMessageHandler(msg: any): void;
    function sendWorkflowResponse(response: any): void;
    function refreshWorkflow(): void;
    function updateApplicationContinue(config: any): void;
  }

  // ─── CR.Settings ─────────────────────────────────────────────

  namespace Settings {
    function get(key: string): any;
    function set(key: string, value: any): void;
    function on(event: string, fn: Function): void;
    function un(event: string, fn: Function): void;
  }

  // ─── CR.Storage ──────────────────────────────────────────────

  namespace Storage {
    function getItem(key: string): string | null;
    function setItem(key: string, value: string): void;
    function removeItem(key: string): void;
    /** Get item and parse as JSON, with optional default */
    function getItemJSON(key: string, defaultValue?: any): any;
    /** Stringify and save as JSON */
    function setItemJSON(key: string, value: any): void;
    let keyBase: string;
  }

  namespace KeyStoneService {
    let SERVICE_BASE_URL: string;
  }

  // ─── CR UI Components (Panels) ──────────────────────────────

  interface CRPanelConfig {
    title?: string;
    html?: string;
    layout?: string;
    region?: string;
    items?: any[];
    width?: number;
    height?: number;
    border?: boolean;
    bodyStyle?: string;
    cls?: string;
    id?: string;
    crId?: string;
    listeners?: Record<string, Function>;
    tbar?: any[];
    bbar?: any[];
    [key: string]: any;
  }

  /** CR Panel — extends Ext.Panel with CR lifecycle management */
  class Panel {
    constructor(config?: CRPanelConfig);
    crBeforeDestroy(): void;
    crIsDestroyed(): boolean;
    crShow(): void;
    crHide(): void;
    show(): void;
    hide(): void;
    add(component: any): any;
    remove(component: any): void;
    doLayout(): void;
    setTitle(title: string): void;
    getEl(): any;
    body: any;
    items: any;
  }

  /** CR FormPanel — extends Ext.form.FormPanel with CR field support */
  class FormPanel extends Panel {
    constructor(config?: CRPanelConfig & { labelWidth?: number; labelAlign?: string; defaultType?: string; });
    /** Focus the first editable CR field */
    crFocusFirstField(): void;
    getForm(): any;
  }

  interface CRGridConfig extends CRPanelConfig {
    store?: any;
    columns?: any[];
    cm?: any;
    sm?: any;
    selModel?: any;
    view?: any;
    viewConfig?: any;
    crRowSingleSelect?: boolean;
    autoExpandColumn?: string;
    stripeRows?: boolean;
    enableColumnMove?: boolean;
  }

  /** CR GridPanel — extends Ext.grid.GridPanel with CR lifecycle */
  class GridPanel extends Panel {
    constructor(config?: CRGridConfig);
    getStore(): any;
    getSelectionModel(): any;
    getColumnModel(): any;
    getView(): any;
  }

  interface CREditorGridConfig extends CRGridConfig {
    crColumns?: any[];
    crColumnModelCfg?: any;
    crAutoCommit?: boolean;
    crStoreReader?: any;
    crStoreGroupField?: string;
    crRecordDefaultCfg?: any;
    crColumnConfigIdentifier?: string;
    clicksToEdit?: number;
  }

  /** CR EditorGridPanel — editable grid with CR column config */
  class EditorGridPanel extends GridPanel {
    constructor(config?: CREditorGridConfig);
    crAddRow(data?: any): void;
    crRemoveSelected(): void;
    crGetModifiedRecords(): any[];
    crGetAllRecords(): any[];
    crCommitChanges(): void;
    crRejectChanges(): void;
  }

  /** CR TabPanel — extends Ext.TabPanel */
  class TabPanel extends Panel {
    constructor(config?: CRPanelConfig & { activeTab?: number; deferredRender?: boolean; });
    setActiveTab(tab: any): void;
    getActiveTab(): any;
  }

  /** CR TreePanel — extends Ext.tree.TreePanel */
  class TreePanel extends Panel {
    constructor(config?: CRPanelConfig & { root?: any; rootVisible?: boolean; loader?: any; });
    getRootNode(): any;
    getSelectionModel(): any;
    expandAll(): void;
    collapseAll(): void;
  }

  /** CR Window — extends Ext.Window with CR lifecycle */
  class Window {
    constructor(config?: CRPanelConfig & { modal?: boolean; closable?: boolean; draggable?: boolean; resizable?: boolean; });
    show(): void;
    hide(): void;
    close(): void;
  }

  /** CR SearchPanel — search UI with result display */
  class SearchPanel extends Panel {
    constructor(config?: any);
  }

  /** CR TransactionPanel — transaction processing panel */
  class TransactionPanel extends Panel {
    constructor(config?: any);
  }

  /** CR FieldSet — collapsible group of fields */
  class FieldSet {
    constructor(config?: { title?: string; collapsible?: boolean; collapsed?: boolean; items?: any[]; [key: string]: any; });
  }

  // ─── CR Field Types ─────────────────────────────────────────

  interface CRFieldConfig {
    /** Database column name */
    crColumnName?: string;
    /** Display label */
    crColumnDescription?: string;
    /** Initial value */
    crContents?: string;
    /** Allow null values */
    crNullAllowed?: boolean;
    /** Width in pixels */
    width?: number;
    /** Read-only mode */
    readOnly?: boolean;
    /** Disabled state */
    disabled?: boolean;
    /** Hidden state */
    hidden?: boolean;
    /** Custom CSS class */
    cls?: string;
    /** Field ID */
    id?: string;
    /** CR ID for lookup */
    crId?: string;
    /** Contents change handler */
    crOnContentsChange?: () => void;
    /** Panel type context */
    crPanelType?: string;
    /** Additional listeners */
    listeners?: Record<string, Function>;
    [key: string]: any;
  }

  /** CR TextField — text input with CR data binding */
  class TextField {
    constructor(config?: CRFieldConfig & {
      crTextNumericOnly?: boolean;
      crTextBlankAllowed?: boolean;
      crMaximumLength?: number;
      crEnterKeyHandler?: () => void;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    getValue(): string;
    setValue(value: string): void;
    focus(selectText?: boolean, delay?: boolean): void;
    setReadOnly(readOnly: boolean): void;
    setDisabled(disabled: boolean): void;
    show(): void;
    hide(): void;
    crContents: string;
    crColumnName: string;
  }

  /** CR DateField — date picker with CR formatting */
  class DateField {
    constructor(config?: CRFieldConfig & {
      format?: string;
      crDateFormat?: string;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    getValue(): any;
    setValue(value: any): void;
    /** Convert date string (MM/DD/YYYY or YYYY-MM-DD) to JavaScript Date object */
    static convertToJavaScript(value: string): Date | null;
    /** Convert JavaScript Date to YYYY-MM-DD string */
    static convertFromJavaScript(date: Date): string;
    /** Convert date to display format MM/DD/YYYY */
    static convertToDisplay(value: string): string;
    /** Convert display format MM/DD/YYYY to storage format YYYY-MM-DD */
    static convertFromDisplay(value: string): string;
    [key: string]: any;
  }

  /** CR MoneyField — currency input with cents handling */
  class MoneyField {
    constructor(config?: CRFieldConfig & {
      crDecimalPrecision?: number;
      crAllowNegative?: boolean;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    getValue(): string;
    setValue(value: string): void;
    /** Format money value for display (e.g. "$1,234.56") */
    static convertToDisplay(value: string | number, allowNegative?: boolean, isKeyUp?: boolean): string;
    /** Remove display formatting, return numeric string */
    static convertFromDisplay(value: string): string;
    /** Convert number/string to money format with 2 decimal places */
    static convertFromValue(value: string | number): string;
    [key: string]: any;
  }

  /** CR SerialField — serial number input with search */
  class SerialField {
    constructor(config?: CRFieldConfig & {
      crTableName?: string;
      crSearchFunction?: Function;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    [key: string]: any;
  }

  /** CR OptionField — dropdown select with option values */
  class OptionField {
    constructor(config?: CRFieldConfig & {
      crOptions?: Array<{ value: string; description: string; }>;
      crOptionStore?: any;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    [key: string]: any;
  }

  /** CR CountField — numeric count input */
  class CountField {
    constructor(config?: CRFieldConfig);
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    /** Convert count to display format */
    static convertToDisplay(value: string | number): string;
    /** Remove display formatting from count */
    static convertFromDisplay(value: string): string;
    [key: string]: any;
  }

  /** CR RateField — rate/percentage input */
  class RateField {
    constructor(config?: CRFieldConfig & {
      crDecimalPrecision?: number;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    crGetDataType(): string;
    /** Format rate for display with % symbol (e.g. "12.500%") */
    static convertToDisplay(value: string | number, allowNegative?: boolean, isKeyUp?: boolean, minDecimals?: number): string;
    /** Remove % formatting, return numeric string */
    static convertFromDisplay(value: string): string;
    [key: string]: any;
  }

  /** CR Checkbox — checkbox with CR data binding */
  class Checkbox {
    constructor(config?: CRFieldConfig & {
      crCheckedValue?: string;
      crUncheckedValue?: string;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    getValue(): boolean;
    setValue(value: boolean): void;
    [key: string]: any;
  }

  /** CR TextAreaField — multiline text input */
  class TextAreaField {
    constructor(config?: CRFieldConfig & {
      crMaximumLength?: number;
      height?: number;
    });
    crGetContents(): string;
    crSetContents(value: string): void;
    [key: string]: any;
  }

  /** CR TimeField — time input */
  class TimeField {
    constructor(config?: CRFieldConfig);
    crGetContents(): string;
    crSetContents(value: string): void;
    /** Convert time to display format (MM/DD/YYYY HH:MM:SS) */
    static convertToDisplay(value: string): string;
    /** Convert display format to storage format (YYYY-MM-DD HH:MM:SS) */
    static convertFromDisplay(value: string): string;
    [key: string]: any;
  }

  /** CR BinaryField — file upload/display */
  class BinaryField {
    constructor(config?: CRFieldConfig);
    crGetContents(): string;
    crSetContents(value: string): void;
    [key: string]: any;
  }

  /** CR ColorPickerField — color selection */
  class ColorPickerField {
    constructor(config?: CRFieldConfig);
    crGetContents(): string;
    crSetContents(value: string): void;
    [key: string]: any;
  }

  /** CR DocumentField — document reference field */
  class DocumentField {
    constructor(config?: CRFieldConfig);
    crGetContents(): string;
    crSetContents(value: string): void;
    [key: string]: any;
  }

  // ─── CR Toolbar & Menu Components ───────────────────────────

  class Button {
    constructor(config?: { text?: string; iconCls?: string; handler?: Function; menu?: any; disabled?: boolean; [key: string]: any; });
  }

  class ToolbarButton extends Button {
    constructor(config?: any);
  }

  class Menu {
    constructor(config?: { items?: any[]; [key: string]: any; });
  }

  class MenuItem {
    constructor(config?: { text?: string; iconCls?: string; handler?: Function; [key: string]: any; });
  }

  class MenuCheckItem extends MenuItem {
    constructor(config?: any);
  }

  class MenuSeparator {
    constructor();
  }

  class MenuTextItem {
    constructor(config?: { text?: string; [key: string]: any; });
  }

  // ─── CR Specialty Components ────────────────────────────────

  class PersonSearch {
    constructor(config?: any);
  }

  class PDFDocument {
    constructor(config?: any);
  }

  class FileTransfer {
    constructor(config?: any);
  }

  class Tip {
    constructor(config?: any);
  }

  class ToolTip {
    constructor(config?: any);
  }

  class AddressValidation {
    constructor(config?: any);
  }

  namespace FMPanel {
    function create(config: any): any;
  }

  namespace FMScript {
    function run(config: any): any;
  }

  /** Template HTML for horizontal tab layout */
  let initialTabHtml: string;
  /** Template HTML for vertical tab layout */
  let initialTabHtmlVertical: string;

  let _scriptReady: boolean;
}


// ═══════════════════════════════════════════════════════════════
// ExtJS 3.2 Type Definitions (commonly used subset)
// ═══════════════════════════════════════════════════════════════

declare namespace Ext {

  // ─── Core Utilities ────────────────────────────────────────

  /** Iterate over an array */
  function each(array: any[], fn: (item: any, index?: number, allItems?: any[]) => boolean | void, scope?: any): void;
  /** Copy properties from config to obj */
  function apply(obj: any, config: any, defaults?: any): any;
  /** Apply properties only if they don't exist */
  function applyIf(obj: any, config: any): any;
  /** Get component by ID */
  function getCmp(id: string): any;
  /** Register a callback for DOM ready */
  function onReady(fn: () => void, scope?: any): void;
  /** Create a namespace */
  function namespace(...args: string[]): void;
  /** Extend a class */
  function extend(superclass: any, overrides: any): any;
  /** Register an xtype */
  function reg(xtype: string, cls: any): void;
  /** Check if value is empty */
  function isEmpty(value: any, allowBlank?: boolean): boolean;
  /** Check if value is an array */
  function isArray(value: any): boolean;
  /** Check if value is an object */
  function isObject(value: any): boolean;
  /** Check if value is a function */
  function isFunction(value: any): boolean;
  /** Check if value is a string */
  function isString(value: any): boolean;
  /** Check if value is a number */
  function isNumber(value: any): boolean;
  /** Check if value is defined */
  function isDefined(value: any): boolean;
  /** Encode HTML entities */
  function htmlEncode(value: string): string;
  /** Decode HTML entities */
  function htmlDecode(value: string): string;
  /** URL-encode a string */
  function urlEncode(obj: any): string;
  /** Decode URL parameters */
  function urlDecode(string: string, overwrite?: boolean): any;
  /** Create a delegate function */
  function createDelegate(fn: Function, scope: any, args?: any[]): Function;
  /** Defer function execution */
  function defer(fn: Function, millis: number, scope?: any): number;
  /** Format a string with arguments */
  function format(format: string, ...args: any[]): string;
  /** Unique ID generator */
  function id(el?: any, prefix?: string): string;

  // ─── Message Box ───────────────────────────────────────────

  namespace Msg {
    /** Show an alert dialog */
    function alert(title: string, msg: string, fn?: () => void, scope?: any): any;
    /** Show a confirmation dialog (Yes/No) */
    function confirm(title: string, msg: string, fn?: (btn: string) => void, scope?: any): any;
    /** Show a prompt dialog with text input */
    function prompt(title: string, msg: string, fn?: (btn: string, text: string) => void, scope?: any): any;
    /** Show a progress dialog */
    function progress(title: string, msg: string, progressText?: string): any;
    /** Show a wait dialog */
    function wait(msg: string, title?: string, config?: any): any;
    /** Show a custom message box */
    function show(config: {
      title?: string;
      msg?: string;
      buttons?: any;
      fn?: Function;
      icon?: string;
      width?: number;
      [key: string]: any;
    }): any;
    /** Hide the message box */
    function hide(): void;

    let OK: any;
    let CANCEL: any;
    let OKCANCEL: any;
    let YESNO: any;
    let YESNOCANCEL: any;
    let INFO: string;
    let WARNING: string;
    let QUESTION: string;
    let ERROR: string;
  }

  // ─── AJAX ──────────────────────────────────────────────────

  namespace Ajax {
    function request(config: {
      url: string;
      method?: string;
      params?: any;
      jsonData?: any;
      xmlData?: any;
      headers?: Record<string, string>;
      success?: (response: any, options: any) => void;
      failure?: (response: any, options: any) => void;
      callback?: (options: any, success: boolean, response: any) => void;
      scope?: any;
      timeout?: number;
    }): number;
  }

  // ─── UI Components ─────────────────────────────────────────

  class Component {
    constructor(config?: any);
    getId(): string;
    show(): void;
    hide(): void;
    enable(): void;
    disable(): void;
    destroy(): void;
    setVisible(visible: boolean): void;
    isVisible(): boolean;
    addListener(event: string, fn: Function, scope?: any): void;
    removeListener(event: string, fn: Function): void;
    on(event: string, fn: Function, scope?: any): void;
    un(event: string, fn: Function): void;
    fireEvent(event: string, ...args: any[]): boolean;
    rendered: boolean;
    hidden: boolean;
    disabled: boolean;
  }

  class Panel extends Component {
    constructor(config?: {
      title?: string;
      html?: string;
      layout?: string;
      region?: string;
      items?: any[];
      width?: number;
      height?: number;
      border?: boolean;
      frame?: boolean;
      bodyStyle?: string;
      style?: string;
      cls?: string;
      id?: string;
      autoScroll?: boolean;
      collapsible?: boolean;
      collapsed?: boolean;
      split?: boolean;
      tbar?: any[];
      bbar?: any[];
      tools?: any[];
      listeners?: Record<string, Function>;
      [key: string]: any;
    });
    add(component: any): any;
    insert(index: number, component: any): any;
    remove(component: any, autoDestroy?: boolean): void;
    removeAll(autoDestroy?: boolean): void;
    doLayout(): void;
    setTitle(title: string): void;
    getEl(): any;
    body: any;
    items: any;
    ownerCt: any;
  }

  class Viewport extends Panel {
    constructor(config?: { layout?: string; items?: any[]; [key: string]: any; });
  }

  class Window extends Panel {
    constructor(config?: {
      modal?: boolean;
      closable?: boolean;
      draggable?: boolean;
      resizable?: boolean;
      maximizable?: boolean;
      minimizable?: boolean;
      closeAction?: string;
      constrain?: boolean;
      [key: string]: any;
    });
    close(): void;
    maximize(): void;
    minimize(): void;
    restore(): void;
    center(): void;
  }

  class TabPanel extends Panel {
    constructor(config?: { activeTab?: number; deferredRender?: boolean; enableTabScroll?: boolean; [key: string]: any; });
    setActiveTab(tab: any): void;
    getActiveTab(): any;
    getItem(id: string): any;
  }

  // ─── Form Components ───────────────────────────────────────

  namespace form {
    class FormPanel extends Panel {
      constructor(config?: { labelWidth?: number; labelAlign?: string; defaultType?: string; [key: string]: any; });
      getForm(): BasicForm;
    }

    class BasicForm {
      isValid(): boolean;
      getValues(): any;
      setValues(values: any): void;
      findField(id: string): any;
      reset(): void;
      submit(config?: any): void;
      load(config?: any): void;
    }

    class TextField extends Component {
      constructor(config?: { fieldLabel?: string; name?: string; value?: string; allowBlank?: boolean; maxLength?: number; [key: string]: any; });
      getValue(): string;
      setValue(value: string): void;
      focus(selectText?: boolean): void;
    }

    class TextArea extends TextField {
      constructor(config?: any);
    }

    class NumberField extends TextField {
      constructor(config?: { minValue?: number; maxValue?: number; decimalPrecision?: number; allowNegative?: boolean; [key: string]: any; });
    }

    class DateField extends TextField {
      constructor(config?: { format?: string; minValue?: Date; maxValue?: Date; [key: string]: any; });
    }

    class ComboBox extends TextField {
      constructor(config?: {
        store?: any;
        displayField?: string;
        valueField?: string;
        mode?: string;
        triggerAction?: string;
        editable?: boolean;
        forceSelection?: boolean;
        [key: string]: any;
      });
    }

    class Checkbox extends Component {
      constructor(config?: { boxLabel?: string; checked?: boolean; [key: string]: any; });
      getValue(): boolean;
      setValue(value: boolean): void;
    }

    class Radio extends Checkbox {}

    class Hidden extends Component {
      constructor(config?: { name?: string; value?: string; });
      getValue(): string;
      setValue(value: string): void;
    }

    class FieldSet extends Component {
      constructor(config?: { title?: string; collapsible?: boolean; collapsed?: boolean; items?: any[]; [key: string]: any; });
    }
  }

  // ─── Grid Components ───────────────────────────────────────

  namespace grid {
    class GridPanel extends Panel {
      constructor(config?: {
        store?: any;
        columns?: any[];
        cm?: any;
        sm?: any;
        selModel?: any;
        view?: any;
        viewConfig?: any;
        autoExpandColumn?: string;
        stripeRows?: boolean;
        enableColumnMove?: boolean;
        loadMask?: boolean;
        trackMouseOver?: boolean;
        [key: string]: any;
      });
      getStore(): any;
      getSelectionModel(): any;
      getColumnModel(): any;
      getView(): any;
    }

    class EditorGridPanel extends GridPanel {
      constructor(config?: { clicksToEdit?: number; [key: string]: any; });
    }

    class ColumnModel {
      constructor(columns: any[]);
      getColumnCount(): number;
      getColumnId(index: number): string;
    }

    class GridView {
      constructor(config?: { forceFit?: boolean; [key: string]: any; });
      scrollOffset: number;
    }

    class RowSelectionModel {
      constructor(config?: { singleSelect?: boolean; });
      getSelected(): any;
      getSelections(): any[];
      selectRow(row: number): void;
      hasSelection(): boolean;
    }

    class CheckboxSelectionModel extends RowSelectionModel {}
  }

  // ─── Data Components ───────────────────────────────────────

  namespace data {
    class Store {
      constructor(config?: {
        data?: any;
        reader?: any;
        proxy?: any;
        autoLoad?: boolean;
        sortInfo?: any;
        groupField?: string;
        [key: string]: any;
      });
      load(config?: any): void;
      loadData(data: any): void;
      add(records: any[]): void;
      insert(index: number, records: any[]): void;
      remove(record: any): void;
      removeAll(): void;
      getAt(index: number): any;
      getById(id: string): any;
      getCount(): number;
      getTotalCount(): number;
      each(fn: (record: any, index: number) => void): void;
      find(property: string, value: any): number;
      findBy(fn: (record: any, id: string) => boolean): number;
      query(property: string, value: any): any;
      sort(field: string, dir?: string): void;
      filter(property: string, value: any): void;
      clearFilter(): void;
      getModifiedRecords(): any[];
      commitChanges(): void;
      rejectChanges(): void;
      collect(property: string): any[];
      sum(property: string, start?: number, end?: number): number;
      on(event: string, fn: Function): void;
      data: any;
    }

    class JsonStore extends Store {
      constructor(config?: {
        fields?: any[];
        data?: any[];
        root?: string;
        idProperty?: string;
        [key: string]: any;
      });
    }

    class ArrayStore extends Store {
      constructor(config?: { fields?: any[]; data?: any[][]; [key: string]: any; });
    }

    class SimpleStore extends ArrayStore {}

    class Record {
      static create(fields: any[]): any;
      get(name: string): any;
      set(name: string, value: any): void;
      data: any;
      id: string;
    }

    class JsonReader {
      constructor(config?: { root?: string; idProperty?: string; fields?: any[]; totalProperty?: string; });
    }

    class ArrayReader {
      constructor(config?: { fields?: any[]; });
    }

    class GroupingStore extends Store {
      constructor(config?: { groupField?: string; [key: string]: any; });
    }
  }

  // ─── Tree Components ───────────────────────────────────────

  namespace tree {
    class TreePanel extends Panel {
      constructor(config?: { root?: any; rootVisible?: boolean; loader?: any; [key: string]: any; });
      getRootNode(): TreeNode;
      getSelectionModel(): any;
      expandAll(): void;
      collapseAll(): void;
    }

    class TreeNode {
      constructor(config?: { text?: string; leaf?: boolean; children?: any[]; [key: string]: any; });
      appendChild(node: TreeNode): TreeNode;
      removeChild(node: TreeNode): TreeNode;
      expand(): void;
      collapse(): void;
      eachChild(fn: (node: TreeNode) => void): void;
      text: string;
      leaf: boolean;
      childNodes: TreeNode[];
    }

    class AsyncTreeNode extends TreeNode {}

    class TreeLoader {
      constructor(config?: { url?: string; [key: string]: any; });
    }
  }

  // ─── List Components ───────────────────────────────────────

  namespace list {
    class ListView extends Component {
      constructor(config?: { store?: any; columns?: any[]; [key: string]: any; });
    }
  }

  // ─── Layout ────────────────────────────────────────────────

  namespace layout {
    class BorderLayout {}
    class FitLayout {}
    class CardLayout {}
    class AccordionLayout {}
    class ColumnLayout {}
    class TableLayout {}
    class FormLayout {}
    class AnchorLayout {}
  }

  // ─── Toolbar ───────────────────────────────────────────────

  class Toolbar {
    constructor(config?: any);
    add(...items: any[]): void;
    addFill(): void;
    addSeparator(): void;
  }

  class Button extends Component {
    constructor(config?: {
      text?: string;
      iconCls?: string;
      handler?: Function;
      scope?: any;
      menu?: any;
      disabled?: boolean;
      tooltip?: string;
      toggleGroup?: string;
      enableToggle?: boolean;
      pressed?: boolean;
      [key: string]: any;
    });
    setText(text: string): void;
    setDisabled(disabled: boolean): void;
    toggle(state?: boolean): void;
  }

  class SplitButton extends Button {
    constructor(config?: any);
  }

  // ─── Menu ──────────────────────────────────────────────────

  namespace menu {
    class Menu {
      constructor(config?: { items?: any[]; [key: string]: any; });
      add(item: any): any;
      show(el: any): void;
      hide(): void;
    }

    class Item {
      constructor(config?: { text?: string; iconCls?: string; handler?: Function; [key: string]: any; });
    }

    class CheckItem extends Item {}
    class Separator {}
    class TextItem { constructor(config?: { text?: string; }); }
  }

  // ─── Utilities ─────────────────────────────────────────────

  namespace util {
    class Observable {
      addListener(event: string, fn: Function, scope?: any): void;
      removeListener(event: string, fn: Function): void;
      on(event: string, fn: Function, scope?: any): void;
      un(event: string, fn: Function): void;
      fireEvent(event: string, ...args: any[]): boolean;
    }

    class MixedCollection {
      add(key: string, obj: any): any;
      get(key: string): any;
      getCount(): number;
      each(fn: (item: any, index: number, length: number) => void): void;
      find(fn: (item: any) => boolean): any;
      filter(property: string, value: any): MixedCollection;
      getRange(start?: number, end?: number): any[];
      indexOf(obj: any): number;
      items: any[];
      length: number;
    }

    class Format {
      static date(value: Date | string, format?: string): string;
      static number(value: number, format: string): string;
      static htmlEncode(value: string): string;
      static htmlDecode(value: string): string;
    }

    class JSON {
      static encode(obj: any): string;
      static decode(json: string): any;
    }
  }

  // ─── DOM ───────────────────────────────────────────────────

  class Element {
    dom: HTMLElement;
    id: string;
    update(html: string): void;
    mask(msg?: string): void;
    unmask(): void;
    addClass(cls: string): void;
    removeClass(cls: string): void;
    setStyle(property: string, value: string): void;
    getWidth(): number;
    getHeight(): number;
    show(): void;
    hide(): void;
  }

  function get(el: string | HTMLElement): Element;
  function fly(el: string | HTMLElement): Element;
  function select(selector: string): any;
  function query(selector: string, root?: HTMLElement): HTMLElement[];
}
