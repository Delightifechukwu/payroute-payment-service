import { useEffect, useState } from "react";
import { getBalances } from "../api/client";

export default function Balances() {

    const [balances, setBalances] = useState([]);

    useEffect(() => {
        loadBalances();
    }, []);

    const loadBalances = async () => {
        const res = await getBalances();
        setBalances(res.data);
    };

    return (
        <div>
            <h2 style={{ textAlign: "center" }}>Account Balances</h2>

            <table
                style={{
                    margin: "20px auto",
                    borderCollapse: "collapse",
                    width: "100%",
                    maxWidth: "800px",
                    border: "1px solid #ddd",
                }}
            >
                <thead>
                    <tr style={{ background: "#f5f5f5" }}>
                        <th style={{ padding: "12px", border: "1px solid #ddd", textAlign: "center" }}>
                            Account ID
                        </th>
                        <th style={{ padding: "12px", border: "1px solid #ddd", textAlign: "center" }}>
                            Currency
                        </th>
                        <th style={{ padding: "12px", border: "1px solid #ddd", textAlign: "center" }}>
                            Available
                        </th>
                        <th style={{ padding: "12px", border: "1px solid #ddd", textAlign: "center" }}>
                            Locked
                        </th>
                    </tr>
                </thead>

                <tbody>
                    {balances.map((b, idx) => (
                        <tr key={idx} style={{ background: idx % 2 === 0 ? "#fff" : "#f9f9f9" }}>
                            <td style={{ padding: "10px", border: "1px solid #ddd", textAlign: "center", fontSize: "11px" }}>
                                {b.accountId}
                            </td>
                            <td style={{ padding: "10px", border: "1px solid #ddd", textAlign: "center", fontWeight: "bold" }}>
                                {b.currency}
                            </td>
                            <td style={{ padding: "10px", border: "1px solid #ddd", textAlign: "center", color: "#4caf50" }}>
                                {b.available.toLocaleString()}
                            </td>
                            <td style={{ padding: "10px", border: "1px solid #ddd", textAlign: "center", color: "#ff9800" }}>
                                {b.locked.toLocaleString()}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}