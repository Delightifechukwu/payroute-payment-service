import { useState, useEffect } from "react";
import { createPayment, getBalances } from "../api/client";

export default function NewPayment() {
    const [formData, setFormData] = useState({
        senderAccountId: "",
        recipientAccountId: "",
        sourceCurrency: "USD",
        destinationCurrency: "EUR",
        sourceAmount: 100,
    });

    const [result, setResult] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);
    const [fxPreview, setFxPreview] = useState(null);
    const [accounts, setAccounts] = useState([]);
    const [groupedAccounts, setGroupedAccounts] = useState({});

    useEffect(() => {
        loadAccounts();
    }, []);

    const loadAccounts = async () => {
        try {
            const res = await getBalances();
            setAccounts(res.data);

            // Group balances by account ID
            const grouped = res.data.reduce((acc, balance) => {
                if (!acc[balance.accountId]) {
                    acc[balance.accountId] = [];
                }
                acc[balance.accountId].push(balance);
                return acc;
            }, {});
            setGroupedAccounts(grouped);

            // Set default accounts
            const accountIds = Object.keys(grouped);
            if (accountIds.length >= 2) {
                setFormData(prev => ({
                    ...prev,
                    senderAccountId: accountIds[0],
                    recipientAccountId: accountIds[1]
                }));
            }
        } catch (err) {
            console.error("Failed to load accounts:", err);
        }
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({
            ...prev,
            [name]: name === "sourceAmount" ? parseFloat(value) || 0 : value,
        }));
        calculateFxPreview({ ...formData, [name]: value });
    };

    const calculateFxPreview = (data) => {
        let rate = 1.0;

        // Match backend FxService rates
        if (data.sourceCurrency === "USD") {
            if (data.destinationCurrency === "EUR") rate = 0.92;
            else if (data.destinationCurrency === "GBP") rate = 0.79;
            else if (data.destinationCurrency === "NGN") rate = 1580.0;
        } else if (data.sourceCurrency === "NGN") {
            if (data.destinationCurrency === "USD") rate = 0.00063;
            else if (data.destinationCurrency === "EUR") rate = 0.00058;
            else if (data.destinationCurrency === "GBP") rate = 0.00050;
        }

        const destAmount = (data.sourceAmount * rate).toFixed(2);
        setFxPreview({ rate, destAmount });
    };

    const submit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        setResult(null);

        try {
            const res = await createPayment(formData, crypto.randomUUID());
            setResult(res.data);
        } catch (err) {
            console.error("Payment error:", err);

            // Extract detailed error message
            let errorMsg = "Payment failed";

            if (err.response) {
                // Backend returned an error response
                const data = err.response.data;

                if (typeof data === 'string') {
                    errorMsg = data;
                } else if (data.message) {
                    errorMsg = data.message;
                } else if (data.error) {
                    errorMsg = data.error;
                } else {
                    errorMsg = `Error ${err.response.status}: ${err.response.statusText}`;
                }

                // Add more context
                if (err.response.status === 500) {
                    errorMsg = `Server Error: ${errorMsg}`;
                } else if (err.response.status === 400) {
                    errorMsg = `Validation Error: ${errorMsg}`;
                }
            } else if (err.request) {
                errorMsg = "Cannot connect to backend. Please ensure the backend is running on http://localhost:8080";
            } else {
                errorMsg = err.message || "Unknown error occurred";
            }

            setError(errorMsg);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: "600px", margin: "0 auto" }}>
            <h3>Create Payment</h3>

            <form onSubmit={submit} style={{ textAlign: "left" }}>
                <div style={{ marginBottom: "15px" }}>
                    <label style={{ display: "block", marginBottom: "5px" }}>
                        Sender Account:
                    </label>
                    <select
                        name="senderAccountId"
                        value={formData.senderAccountId}
                        onChange={handleChange}
                        style={{ width: "100%", padding: "8px" }}
                        required
                    >
                        <option value="">Select sender account</option>
                        {Object.entries(groupedAccounts).map(([accountId, balances]) => {
                            const currencies = balances.map(b => b.currency).join(", ");
                            const totalAvailable = balances.reduce((sum, b) => sum + b.available, 0);
                            return (
                                <option key={accountId} value={accountId}>
                                    {accountId.slice(0, 8)}... - Currencies: {currencies} - Available: {totalAvailable.toLocaleString()}
                                </option>
                            );
                        })}
                    </select>
                </div>

                <div style={{ marginBottom: "15px" }}>
                    <label style={{ display: "block", marginBottom: "5px" }}>
                        Recipient Account:
                    </label>
                    <select
                        name="recipientAccountId"
                        value={formData.recipientAccountId}
                        onChange={handleChange}
                        style={{ width: "100%", padding: "8px" }}
                        required
                    >
                        <option value="">Select recipient account</option>
                        {Object.entries(groupedAccounts).map(([accountId, balances]) => {
                            const currencies = balances.map(b => b.currency).join(", ");
                            const totalAvailable = balances.reduce((sum, b) => sum + b.available, 0);
                            return (
                                <option key={accountId} value={accountId}>
                                    {accountId.slice(0, 8)}... - Currencies: {currencies} - Available: {totalAvailable.toLocaleString()}
                                </option>
                            );
                        })}
                    </select>
                </div>

                <div style={{ marginBottom: "15px" }}>
                    <label style={{ display: "block", marginBottom: "5px" }}>
                        Source Currency:
                    </label>
                    <select
                        name="sourceCurrency"
                        value={formData.sourceCurrency}
                        onChange={handleChange}
                        style={{ width: "100%", padding: "8px" }}
                    >
                        <option value="NGN">NGN</option>
                        <option value="USD">USD</option>
                        <option value="EUR">EUR</option>
                        <option value="GBP">GBP</option>
                    </select>
                </div>

                <div style={{ marginBottom: "15px" }}>
                    <label style={{ display: "block", marginBottom: "5px" }}>
                        Destination Currency:
                    </label>
                    <select
                        name="destinationCurrency"
                        value={formData.destinationCurrency}
                        onChange={handleChange}
                        style={{ width: "100%", padding: "8px" }}
                    >
                        <option value="USD">USD</option>
                        <option value="EUR">EUR</option>
                        <option value="GBP">GBP</option>
                    </select>
                </div>

                <div style={{ marginBottom: "15px" }}>
                    <label style={{ display: "block", marginBottom: "5px" }}>
                        Amount:
                    </label>
                    <input
                        type="number"
                        name="sourceAmount"
                        value={formData.sourceAmount}
                        onChange={handleChange}
                        min="0"
                        step="0.01"
                        style={{ width: "100%", padding: "8px" }}
                        required
                    />
                </div>

                {fxPreview && (
                    <div
                        style={{
                            marginBottom: "15px",
                            padding: "10px",
                            background: "#f0f0f0",
                            borderRadius: "4px",
                        }}
                    >
                        <strong>FX Preview:</strong>
                        <div>Rate: {fxPreview.rate}</div>
                        <div>
                            You send: {formData.sourceAmount} {formData.sourceCurrency}
                        </div>
                        <div>
                            Recipient gets: {fxPreview.destAmount}{" "}
                            {formData.destinationCurrency}
                        </div>
                    </div>
                )}

                <button
                    type="submit"
                    disabled={loading}
                    style={{
                        width: "100%",
                        padding: "12px 20px",
                        background: loading ? "#ccc" : "#6a0dad",
                        color: "white",
                        border: "none",
                        borderRadius: "6px",
                        cursor: loading ? "not-allowed" : "pointer",
                        fontSize: "16px",
                        fontWeight: "600",
                        boxShadow: loading ? "none" : "0 2px 4px rgba(106,13,173,0.3)",
                        transition: "all 0.2s ease",
                    }}
                    onMouseEnter={(e) => {
                        if (!loading) {
                            e.target.style.background = "#5a0a9d";
                            e.target.style.transform = "translateY(-1px)";
                            e.target.style.boxShadow = "0 4px 8px rgba(106,13,173,0.4)";
                        }
                    }}
                    onMouseLeave={(e) => {
                        if (!loading) {
                            e.target.style.background = "#6a0dad";
                            e.target.style.transform = "translateY(0)";
                            e.target.style.boxShadow = "0 2px 4px rgba(106,13,173,0.3)";
                        }
                    }}
                >
                    {loading ? "Processing..." : "Create Payment"}
                </button>
            </form>

            {error && (
                <div
                    style={{
                        marginTop: "20px",
                        padding: "15px",
                        background: "#ffebee",
                        color: "#c62828",
                        borderRadius: "8px",
                        border: "2px solid #ef5350",
                        boxShadow: "0 2px 8px rgba(244,67,54,0.2)",
                    }}
                >
                    <div style={{
                        fontSize: "18px",
                        fontWeight: "bold",
                        marginBottom: "10px",
                        display: "flex",
                        alignItems: "center",
                        gap: "8px"
                    }}>
                        <span style={{ fontSize: "24px" }}>⚠️</span>
                        Payment Error
                    </div>
                    <div style={{
                        fontSize: "14px",
                        lineHeight: "1.6",
                        background: "white",
                        padding: "10px",
                        borderRadius: "4px",
                        fontFamily: "monospace",
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word"
                    }}>
                        {error}
                    </div>
                    <div style={{
                        marginTop: "10px",
                        fontSize: "12px",
                        fontStyle: "italic",
                        opacity: 0.8
                    }}>
                        💡 Tip: Check the browser console (F12) for more details
                    </div>
                </div>
            )}

            {result && (
                <div
                    style={{
                        marginTop: "20px",
                        padding: "10px",
                        background: "#e8f5e9",
                        borderRadius: "4px",
                    }}
                >
                    <h4>Payment Created Successfully!</h4>
                    <div>Reference: {result.reference}</div>
                    <div>Status: {result.status}</div>
                    <div>Provider Reference: {result.providerReference}</div>
                    <div>
                        Amount: {result.sourceAmount} → {result.destinationAmount}
                    </div>
                    <div>FX Rate: {result.fxRate}</div>

                    {result.status === "PROCESSING" && result.providerReference && (
                        <div style={{
                            marginTop: "15px",
                            padding: "15px",
                            background: "#fff3e0",
                            borderRadius: "6px",
                            border: "1px dashed #ff9800"
                        }}>
                            <p style={{ margin: "0 0 10px 0", fontSize: "14px", color: "#e65100" }}>
                                <strong>🔄 Payment waiting for confirmation</strong>
                            </p>
                            <p style={{ margin: "0 0 15px 0", fontSize: "12px", color: "#666" }}>
                                Simulate webhook (in production, provider sends automatically):
                            </p>
                            <button
                                onClick={async () => {
                                    try {
                                        await fetch(`http://localhost:8080/test/complete-payment/${result.providerReference}`, {method: 'POST'});
                                        alert("✅ Payment completed!");
                                        window.location.reload();
                                    } catch (err) {
                                        alert("Error: " + err.message);
                                    }
                                }}
                                style={{
                                    padding: "10px 20px",
                                    background: "#4caf50",
                                    color: "white",
                                    border: "none",
                                    borderRadius: "6px",
                                    cursor: "pointer",
                                    marginRight: "10px",
                                    fontWeight: "600"
                                }}
                            >
                                ✅ Complete Payment
                            </button>
                            <button
                                onClick={async () => {
                                    try {
                                        await fetch(`http://localhost:8080/test/fail-payment/${result.providerReference}`, {method: 'POST'});
                                        alert("❌ Payment failed! Money returned.");
                                        window.location.reload();
                                    } catch (err) {
                                        alert("Error: " + err.message);
                                    }
                                }}
                                style={{
                                    padding: "10px 20px",
                                    background: "#f44336",
                                    color: "white",
                                    border: "none",
                                    borderRadius: "6px",
                                    cursor: "pointer",
                                    fontWeight: "600"
                                }}
                            >
                                ❌ Fail Payment
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}