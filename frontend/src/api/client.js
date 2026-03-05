import axios from "axios";

const API = axios.create({
    baseURL: "http://localhost:8080",
    headers: {
        "Content-Type": "application/json",
    },
});

export const createPayment = (data, idempotencyKey) =>
    API.post("/payments", data, {
        headers: { "Idempotency-Key": idempotencyKey },
    });

export const getTransactions = () => API.get("/transactions");

export const getTransaction = (reference) =>
    API.get(`/payments/${reference}`);

export const getBalances = () => API.get("/balances");

export const sendWebhook = (body) =>
    API.post("/webhooks", body, {
        headers: {
            "X-Webhook-Signature": "demo",
        },
    });

export const getWebhook = (id) => API.get(`/webhooks/${id}`);