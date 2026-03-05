import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getTransaction } from "../api/client";

export default function TransactionDetails() {
    const { reference } = useParams();
    const [transaction, setTransaction] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!reference) return;

        const loadTransaction = async () => {
            try {
                const res = await getTransaction(reference);
                setTransaction(res.data);
            } catch (err) {
                console.error("Failed to load transaction:", err);
            } finally {
                setLoading(false);
            }
        };

        loadTransaction();
    }, [reference]);

    if (loading) return <div>Loading...</div>;
    if (!transaction) return <div>Transaction not found</div>;

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
        <div style={{ maxWidth: "900px", margin: "0 auto", textAlign: "left" }}>
            <h2>Transaction Details</h2>

            <div style={{ background: "#f9f9f9", padding: "20px", borderRadius: "8px", marginBottom: "20px" }}>
                <h3>Overview</h3>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <tbody>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>Reference:</td>
                            <td style={{ padding: "8px" }}>{transaction.reference}</td>
                        </tr>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>Status:</td>
                            <td style={{ padding: "8px" }}>
                                <span
                                    style={{
                                        padding: "4px 8px",
                                        borderRadius: "4px",
                                        background: getStatusColor(transaction.status),
                                        color: "white",
                                        fontSize: "14px",
                                    }}
                                >
                                    {transaction.status}
                                </span>
                            </td>
                        </tr>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>Provider Ref:</td>
                            <td style={{ padding: "8px" }}>{transaction.providerReference || "N/A"}</td>
                        </tr>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>Amount:</td>
                            <td style={{ padding: "8px" }}>
                                {transaction.sourceAmount} {transaction.sourceCurrency} → {transaction.destinationAmount} {transaction.destinationCurrency}
                            </td>
                        </tr>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>FX Rate:</td>
                            <td style={{ padding: "8px" }}>{transaction.fxRate}</td>
                        </tr>
                        <tr>
                            <td style={{ padding: "8px", fontWeight: "bold" }}>Created:</td>
                            <td style={{ padding: "8px" }}>{new Date(transaction.createdAt).toLocaleString()}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            {transaction.statusHistory && transaction.statusHistory.length > 0 && (
                <div style={{ background: "#f9f9f9", padding: "20px", borderRadius: "8px", marginBottom: "20px" }}>
                    <h3>Status Timeline</h3>
                    <div>
                        {transaction.statusHistory.map((h, idx) => (
                            <div
                                key={idx}
                                style={{
                                    padding: "10px",
                                    borderLeft: "3px solid #2196f3",
                                    marginBottom: "10px",
                                    background: "white",
                                }}
                            >
                                <div style={{ fontWeight: "bold" }}>
                                    {h.fromStatus} → {h.toStatus}
                                </div>
                                <div style={{ fontSize: "12px", color: "#666" }}>
                                    {h.reason}
                                </div>
                                <div style={{ fontSize: "11px", color: "#999" }}>
                                    {new Date(h.atTime).toLocaleString()}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {transaction.ledgerEntries && transaction.ledgerEntries.length > 0 && (
                <div style={{ background: "#f9f9f9", padding: "20px", borderRadius: "8px" }}>
                    <h3>Ledger Entries</h3>
                    <table style={{ width: "100%", borderCollapse: "collapse", background: "white" }}>
                        <thead>
                            <tr style={{ background: "#e0e0e0" }}>
                                <th style={{ padding: "8px", border: "1px solid #ddd" }}>Account</th>
                                <th style={{ padding: "8px", border: "1px solid #ddd" }}>Direction</th>
                                <th style={{ padding: "8px", border: "1px solid #ddd" }}>Amount</th>
                                <th style={{ padding: "8px", border: "1px solid #ddd" }}>Currency</th>
                                <th style={{ padding: "8px", border: "1px solid #ddd" }}>Time</th>
                            </tr>
                        </thead>
                        <tbody>
                            {transaction.ledgerEntries.map((entry) => (
                                <tr key={entry.id}>
                                    <td style={{ padding: "8px", border: "1px solid #ddd" }}>
                                        {entry.ledgerAccountCode}
                                    </td>
                                    <td style={{ padding: "8px", border: "1px solid #ddd" }}>
                                        <span
                                            style={{
                                                color: entry.direction === "DEBIT" ? "#d32f2f" : "#388e3c",
                                                fontWeight: "bold",
                                            }}
                                        >
                                            {entry.direction}
                                        </span>
                                    </td>
                                    <td style={{ padding: "8px", border: "1px solid #ddd" }}>
                                        {entry.amount}
                                    </td>
                                    <td style={{ padding: "8px", border: "1px solid #ddd" }}>
                                        {entry.currency}
                                    </td>
                                    <td style={{ padding: "8px", border: "1px solid #ddd" }}>
                                        {new Date(entry.createdAt).toLocaleString()}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}