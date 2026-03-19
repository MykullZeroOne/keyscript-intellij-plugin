import React, { useState } from 'react';
import { searchTable, viewRecord } from './keystone';

export default function App() {
    const [searchTerm, setSearchTerm] = useState('');
    const [results, setResults] = useState([]);
    const [selectedRecord, setSelectedRecord] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const handleSearch = async () => {
        if (!searchTerm.trim()) return;
        setLoading(true);
        setError(null);
        setSelectedRecord(null);

        try {
            const rows = await searchTable('PERSON', 'BY_LAST_FIRST_MIDDLE_NAME', searchTerm);
            setResults(rows);
            if (rows.length === 0) {
                setError('No results found');
            }
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleViewRecord = async (serial) => {
        setLoading(true);
        setError(null);

        try {
            const record = await viewRecord('PERSON', serial);
            setSelectedRecord(record);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={styles.container}>
            <h2 style={styles.title}>Person Record Lookup</h2>

            <div style={styles.searchBar}>
                <input
                    type="text"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                    placeholder="Search by name..."
                    style={styles.input}
                />
                <button onClick={handleSearch} disabled={loading} style={styles.button}>
                    {loading ? 'Searching...' : 'Search'}
                </button>
            </div>

            {error && <div style={styles.error}>{error}</div>}

            <div style={styles.content}>
                <div style={styles.resultsList}>
                    <h3 style={styles.sectionTitle}>Results ({results.length})</h3>
                    {results.map((row) => (
                        <div
                            key={row.serial}
                            onClick={() => handleViewRecord(row.serial)}
                            style={{
                                ...styles.resultRow,
                                ...(selectedRecord?.serial === row.serial ? styles.selectedRow : {})
                            }}
                        >
                            <span style={styles.serial}>#{row.serial}</span>
                            <span style={styles.description}>{row.description}</span>
                        </div>
                    ))}
                </div>

                {selectedRecord && (
                    <div style={styles.recordDetail}>
                        <h3 style={styles.sectionTitle}>
                            Record #{selectedRecord.serial}
                        </h3>
                        <table style={styles.table}>
                            <tbody>
                                {Object.entries(selectedRecord.fields).map(([key, value]) => (
                                    <tr key={key}>
                                        <td style={styles.fieldName}>{key}</td>
                                        <td style={styles.fieldValue}>{value}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}

const styles = {
    container: {
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        padding: '16px',
        maxWidth: '900px',
        margin: '0 auto',
    },
    title: {
        margin: '0 0 16px',
        color: '#333',
    },
    searchBar: {
        display: 'flex',
        gap: '8px',
        marginBottom: '16px',
    },
    input: {
        flex: 1,
        padding: '8px 12px',
        border: '1px solid #ccc',
        borderRadius: '4px',
        fontSize: '14px',
    },
    button: {
        padding: '8px 20px',
        background: '#4A90D9',
        color: '#fff',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer',
        fontSize: '14px',
    },
    error: {
        padding: '8px 12px',
        background: '#FEE',
        color: '#C33',
        borderRadius: '4px',
        marginBottom: '12px',
    },
    content: {
        display: 'flex',
        gap: '16px',
    },
    resultsList: {
        flex: '0 0 300px',
        border: '1px solid #ddd',
        borderRadius: '4px',
        overflow: 'auto',
        maxHeight: '500px',
    },
    sectionTitle: {
        margin: 0,
        padding: '8px 12px',
        background: '#f5f5f5',
        borderBottom: '1px solid #ddd',
        fontSize: '13px',
        color: '#666',
    },
    resultRow: {
        padding: '8px 12px',
        cursor: 'pointer',
        borderBottom: '1px solid #eee',
        display: 'flex',
        gap: '8px',
        alignItems: 'center',
    },
    selectedRow: {
        background: '#E8F0FE',
    },
    serial: {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: '#888',
        flexShrink: 0,
    },
    description: {
        fontSize: '13px',
        color: '#888',
    },
    recordDetail: {
        flex: 1,
        border: '1px solid #ddd',
        borderRadius: '4px',
        overflow: 'auto',
        maxHeight: '500px',
        color: '#9e9d9d',
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
        color: '#9e9d9d',

    },

    fieldName: {
        padding: '4px 12px',
        fontFamily: 'monospace',
        fontSize: '12px',
        width: '50%',
        color: '#737171',
        borderBottom: '1px solid #eee',
        whiteSpace: 'nowrap',
        verticalAlign: 'top',
    },
    fieldValue: {
        padding: '4px 12px',
        width: '50%',
        fontSize: '13px',
        borderBottom: '1px solid #eee',
        wordBreak: 'break-word',
        color: '#fff',
    },
};
