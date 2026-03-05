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
            <h2>Balances</h2>

            <table border="1" cellPadding="8">
                <thead>
                <tr>
                    <th>Currency</th>
                    <th>Available</th>
                    <th>Pending</th>
                </tr>
                </thead>

                <tbody>
                {balances.map((b, idx) => (
                    <tr key={idx}>
                        <td>{b.currency}</td>
                        <td>{b.available}</td>
                        <td>{b.locked}</td>
                    </tr>
                ))}
                </tbody>
            </table>

        </div>
    );
}