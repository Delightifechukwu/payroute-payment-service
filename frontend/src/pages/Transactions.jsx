import { useEffect, useState } from "react";
import axios from "axios";
import { Link } from "react-router-dom";

export default function Transactions() {
    const [transactions, setTransactions] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [statusFilter, setStatusFilter] = useState("");
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadTransactions();
    }, [page, statusFilter]);

    const loadTransactions = async () => {
        setLoading(true);
        try {
            const params = { page, size: 20 };
            if (statusFilter) params.status = statusFilter;

            const res = await axios.get("http://localhost:8080/payments", { params });
            setTransactions(res.data.content || res.data);
            setTotalPages(res.data.totalPages || 1);
        } catch (err) {
            console.error("Failed to load transactions:", err);
        } finally {
            setLoading(false);
        }
    };

    const getStatusColor = (status) => {
        switch (status) {
            case "COMPLETED":
                return "#4caf50";
            case "PROCESSING":
                return "#ff9800";
            case "FAILED":
                return "#f44336";
            case "INITIATED":
                return "#2196f3";
            default:
                return "#999";
        }
    };

    return (
        <div>
            <h2>Transactions</h2>

            <div style={{ marginBottom: "20px" }}>
                <label style={{ marginRight: "10px" }}>Filter by status: </label>
                <select
                    value={statusFilter}
                    onChange={(e) => {
                        setStatusFilter(e.target.value);
                        setPage(0);
                    }}
                    style={{ padding: "5px" }}
                >
                    <option value="">All</option>
                    <option value="INITIATED">Initiated</option>
                    <option value="PROCESSING">Processing</option>
                    <option value="COMPLETED">Completed</option>
                    <option value="FAILED">Failed</option>
                </select>
            </div>

            {loading && <div>Loading...</div>}

            <table
                style={{
                    margin: "0 auto",
                    borderCollapse: "collapse",
                    width: "100%",
                    border: "1px solid #ddd",
                }}
            >
                <thead>
                    <tr style={{ background: "#f5f5f5" }}>
                        <th style={{ padding: "10px", border: "1px solid #ddd" }}>
                            Reference
                        </th>
                        <th style={{ padding: "10px", border: "1px solid #ddd" }}>
                            Status
                        </th>
                        <th style={{ padding: "10px", border: "1px solid #ddd" }}>
                            Source
                        </th>
                        <th style={{ padding: "10px", border: "1px solid #ddd" }}>
                            Destination
                        </th>
                        <th style={{ padding: "10px", border: "1px solid #ddd" }}>
                            Created
                        </th>
                    </tr>
                </thead>

                <tbody>
                    {transactions.map((t) => (
                        <tr key={t.reference}>
                            <td
                                style={{
                                    padding: "10px",
                                    border: "1px solid #ddd",
                                    textAlign: "center",
                                }}
                            >
                                <Link to={`/transactions/${t.reference}`}>
                                    {t.reference}
                                </Link>
                            </td>
                            <td
                                style={{
                                    padding: "10px",
                                    border: "1px solid #ddd",
                                    textAlign: "center",
                                }}
                            >
                                <span
                                    style={{
                                        padding: "4px 8px",
                                        borderRadius: "4px",
                                        background: getStatusColor(t.status),
                                        color: "white",
                                        fontSize: "12px",
                                    }}
                                >
                                    {t.status}
                                </span>
                            </td>
                            <td
                                style={{
                                    padding: "10px",
                                    border: "1px solid #ddd",
                                    textAlign: "center",
                                }}
                            >
                                {t.sourceAmount} {t.sourceCurrency}
                            </td>
                            <td
                                style={{
                                    padding: "10px",
                                    border: "1px solid #ddd",
                                    textAlign: "center",
                                }}
                            >
                                {t.destinationAmount} {t.destinationCurrency}
                            </td>
                            <td
                                style={{
                                    padding: "10px",
                                    border: "1px solid #ddd",
                                    textAlign: "center",
                                }}
                            >
                                {new Date(t.createdAt).toLocaleString()}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>

            {totalPages > 1 && (
                <div style={{ marginTop: "20px", textAlign: "center" }}>
                    <button
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                        style={{ margin: "0 5px", padding: "5px 10px" }}
                    >
                        Previous
                    </button>
                    <span style={{ margin: "0 10px" }}>
                        Page {page + 1} of {totalPages}
                    </span>
                    <button
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        style={{ margin: "0 5px", padding: "5px 10px" }}
                    >
                        Next
                    </button>
                </div>
            )}
        </div>
    );
}