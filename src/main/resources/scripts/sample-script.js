console.clear();
console.log('-- Sample Script --');

function decodeExceptions(error) {
  const items = [];
  if (!error) {
    items.push('Undefined error');
  } else {
    if (Array.isArray(error)) {
      items.push(...error);
    } else if (error instanceof Error) {
      items.push(error.message);
    } else if (typeof error === 'object') {
      items.push(error.statusText ?? error.message ?? 'Unspecified error');
    } else {
      items.push(error.toString());
    }
  }
  return items;
}

function getEnv() {
  const xml = new CR.XML();
  const sequence = xml.addContainer(xml.getRootElement(), 'sequence');
  const transaction = xml.addContainer(sequence, 'transaction');
  const step = xml.addContainer(transaction, 'step');
  const record = xml.addContainer(step, 'record');
  xml.addText(record, 'tableName', 'ENV');
  xml.addOption(record, 'operation', 'V');
  xml.addText(record, 'targetSerial', 1);
  xml.addOption(record, 'includeAllColumns', 'Y');
  return new Promise((resolve, reject) => {
    CR.Core.ajaxRequest({
      url: 'DirectXMLPostJSON',
      xmlData: xml.getXMLDocument(),
      failure: (e) => reject(e),
      success: (response) => {
        var records = [];
        var tranResult = [];
        var errorArray = [];
        var responseJson = CR.JSON.parse(response.responseText);
        var query = responseJson.query;
        if (query) {
          Ext.each(query.sequence, (sequence) => {
            Ext.each(sequence.transaction, (transaction) => {
              tranResult.push(transaction.$attr.result);
              Ext.each(transaction.exception, (exception) => {
                errorArray.push(exception.message);
              });
              Ext.each(transaction.step, (step) => {
                if (step.tranResult?.category?.option === 'E') {
                  errorArray.push(step.tranResult.description);
                } else if (step.record) {
                  records.push(step.record);
                }
              });
            });
          });
        }
        if (errorArray.length > 0) {
          reject(errorArray);
        } else if (!tranResult.every(tr => tr == 'posted')) {
          reject('transaction failed');
        } else if (!records.length) {
          reject('env record not found');
        } else {
          resolve(records[0]);
        }
      }
    });
  });
}


async function main() {
  try {
    const record = await getEnv();
    const postingDate = record.field.find(f => f.columnName == 'POSTING_DATE')?.newContents;
    if (!postingDate)
      Ext.Msg.alert('Status', 'Posting date not found.');
    else
      Ext.Msg.alert('Status', `Posting date is: ${postingDate}`);
  } catch (errors) {
    CR.Core.displayExceptions({ items: decodeExceptions(errors) });
  }
}

main();