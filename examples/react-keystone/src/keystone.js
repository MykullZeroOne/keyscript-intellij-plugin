/**
 * Keystone API helpers — wraps CR.XML + CR.Core.ajaxRequest in Promises.
 */

/**
 * Search a Keystone table by filter name.
 * @param {string} tableName - e.g. 'PERSON', 'ACCOUNT', 'SCRIPT'
 * @param {string} filterName - e.g. 'BY_LAST_FIRST_MIDDLE_NAME'
 * @param {string} searchValue - the search term
 * @param {number} limit - max results (default 20)
 * @returns {Promise<Array<{serial: string, description: string}>>}
 */
export function searchTable(tableName, filterName, searchValue, limit = 20) {
    return new Promise(function(resolve, reject) {
        var xml = new CR.XML();
        var sequence = xml.addContainer(xml.getRootElement(), 'sequence');
        var transaction = xml.addContainer(sequence, 'transaction');
        var step = xml.addContainer(transaction, 'step');
        var search = xml.addContainer(step, 'search');

        xml.addText(search, 'tableName', tableName);
        xml.addText(search, 'filterName', filterName);
        xml.addOption(search, 'includeSelectColumns', 'Y');
        xml.addOption(search, 'includeRowDescriptions', 'Y');
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
                    resolve(extractSearchResults(data));
                } catch (e) {
                    reject(e);
                }
            },
            failure: function(response) {
                reject(new Error(response.statusText || 'Search request failed'));
            }
        });
    });
}

/**
 * View a record by serial.
 * @param {string} tableName
 * @param {string} serial
 * @returns {Promise<{serial: string, description: string, fields: Object}>}
 */
export function viewRecord(tableName, serial) {
    return new Promise(function(resolve, reject) {
        var xml = new CR.XML();
        var sequence = xml.addContainer(xml.getRootElement(), 'sequence');
        var transaction = xml.addContainer(sequence, 'transaction');
        var step = xml.addContainer(transaction, 'step');
        var record = xml.addContainer(step, 'record');

        xml.addText(record, 'tableName', tableName);
        xml.addOption(record, 'operation', 'V');
        xml.addText(record, 'targetSerial', serial);
        xml.addOption(record, 'includeAllColumns', 'Y');
        xml.addOption(record, 'includeRowDescriptions', 'Y');

        CR.Core.ajaxRequest({
            url: 'DirectXMLPostJSON',
            xmlData: xml.getXMLDocument(),
            success: function(response) {
                try {
                    var data = CR.JSON.parse(response.responseText);
                    resolve(extractRecordFields(data, serial));
                } catch (e) {
                    reject(e);
                }
            },
            failure: function(response) {
                reject(new Error(response.statusText || 'View request failed'));
            }
        });
    });
}

// ── Response parsers ──────────────────────────────

function extractSearchResults(data) {
    var rows = [];
    var query = data.query;
    if (!query) return rows;

    eachNested(query, 'search', function(search) {
        var resultRows = search.resultRow;
        if (!resultRows) return;
        if (!Array.isArray(resultRows)) resultRows = [resultRows];

        // Build column name mapping from search-level selectColumn
        var colNames = [];
        var selectCols = search.selectColumn;
        if (selectCols) {
            if (!Array.isArray(selectCols)) selectCols = [selectCols];
            selectCols.forEach(function(sc) {
                colNames.push(sc.columnName || '');
            });
        }

        resultRows.forEach(function(row) {
            var desc = row.rowDescription || '';

            // Fall back to positional selectColumn contents
            if (!desc && row.selectColumn) {
                var rsc = row.selectColumn;
                if (!Array.isArray(rsc)) rsc = [rsc];
                for (var i = 0; i < rsc.length; i++) {
                    var cn = (i < colNames.length) ? colNames[i] : '';
                    if (rsc[i].contents && (cn === 'ROW_DESCRIPTION' || cn === 'DESCRIPTION' || i === 0)) {
                        desc = rsc[i].contents;
                        break;
                    }
                }
            }

            rows.push({
                serial: row.serial || '',
                description: desc
            });
        });
    });

    return rows;
}

function extractRecordFields(data, serial) {
    var result = { serial: serial, description: '', fields: {} };
    var query = data.query;
    if (!query) return result;

    eachNested(query, 'record', function(record) {
        result.description = record.rowDescription || '';

        var fields = record.field;
        if (!fields) return;
        if (!Array.isArray(fields)) fields = [fields];

        fields.forEach(function(f) {
            var name = f.columnName;
            if (!name) return;
            var value = f.contents || f.newContents || '';
            result.fields[name] = value;
        });
    });

    return result;
}

/**
 * Walk nested sequence/transaction/step arrays to find a named node.
 */
function eachNested(query, targetKey, callback) {
    var sequences = query.sequence;
    if (!sequences) return;
    if (!Array.isArray(sequences)) sequences = [sequences];

    sequences.forEach(function(seq) {
        var transactions = seq.transaction;
        if (!transactions) return;
        if (!Array.isArray(transactions)) transactions = [transactions];

        transactions.forEach(function(txn) {
            var steps = txn.step;
            if (!steps) return;
            if (!Array.isArray(steps)) steps = [steps];

            steps.forEach(function(step) {
                if (step[targetKey]) {
                    callback(step[targetKey]);
                }
            });
        });
    });
}
