import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import NewPayment from "./pages/NewPayment";
import Transactions from "./pages/Transactions";
import TransactionDetails from "./pages/TransactionDetails";
import Balances from "./pages/Balances";

function App() {
    return (
        <BrowserRouter>
            <div
                style={{
                    maxWidth: "1000px",
                    margin: "0 auto",
                    padding: "20px",
                    textAlign: "center"
                }}
            >
                <h2 style={{ textAlign: "center" }}>Payroute APP</h2>

                <nav style={{ marginBottom: 20 }}>
                    <Link to="/">Create Payment</Link> |{" "}
                    <Link to="/transactions">Transactions</Link> |{" "}
                    <Link to="/balances">Balances</Link>
                </nav>

                <Routes>
                    <Route path="/" element={<NewPayment />} />
                    <Route path="/transactions" element={<Transactions />} />
                    <Route path="/transactions/:reference" element={<TransactionDetails />} />
                    <Route path="/balances" element={<Balances />} />
                </Routes>
            </div>
        </BrowserRouter>
    );
}

export default App;