import { useState } from "react";
import { createPayment } from "../api/client";

export default function NewPayment() {
    const [formData, setFormData] = useState({
        senderAccountId: "11111111-1111-1111-1111-111111111111",
        recipientAccountId: "22222222-2222-2222-2222-222222222222",
        sourceCurrency: "USD",
        destinationCurrency: "EUR",
        sourceAmount: 100,
    });

    const [result, setResult] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);
    const [fxPreview, setFxPreview] = useState(null);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({
            ...prev,
            [name]: name === "sourceAmount" ? parseFloat(value) || 0 : value,
        }));
        calculateFxPreview({ ...formData, [name]: value });
    };

    const calculateFxPreview = (data) => {
        const rates = {
            USD: 0.00065,
            EUR: 0.00060,
            GBP: 0.00052,
        };

        if (data.sourceCurrency === "NGN") {
            const rate = rates[data.destinationCurrency] || 0.0005;
            const destAmount = (data.sourceAmount * rate).toFixed(2);
            setFxPreview({ rate, destAmount });
        } else if (data.sourceCurrency === "USD" && data.destinationCurrency === "EUR") {
            setFxPreview({ rate: 0.92, destAmount: (data.sourceAmount * 0.92).toFixed(2) });
        } else {
            setFxPreview({ rate: 1, destAmount: data.sourceAmount });
        }
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
            setError(err.response?.data?.message || err.message || "Payment failed");
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
                    >
                        <option value="11111111-1111-1111-1111-111111111111">
                            Sender Account (USD/NGN)
                        </option>
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
                    >
                        <option value="22222222-2222-2222-2222-222222222222">
                            Recipient Account (EUR/USD)
                        </option>
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
                        padding: "10px",
                        background: loading ? "#ccc" : "#007bff",
                        color: "white",
                        border: "none",
                        borderRadius: "4px",
                        cursor: loading ? "not-allowed" : "pointer",
                    }}
                >
                    {loading ? "Processing..." : "Create Payment"}
                </button>
            </form>

            {error && (
                <div
                    style={{
                        marginTop: "20px",
                        padding: "10px",
                        background: "#ffebee",
                        color: "#c62828",
                        borderRadius: "4px",
                    }}
                >
                    Error: {error}
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
                    <div>
                        Amount: {result.sourceAmount} → {result.destinationAmount}
                    </div>
                    <div>FX Rate: {result.fxRate}</div>
                </div>
            )}
        </div>
    );
}