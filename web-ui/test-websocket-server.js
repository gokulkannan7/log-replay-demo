// Sample WebSocket Test Server
// This is a simple Node.js WebSocket server for testing the UI
// Run with: node test-websocket-server.js

const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 8080 });

console.log('WebSocket server started on ws://localhost:8080');

const sampleMessages = [
    {
        orderId: "ORD-ONC-001",
        status: "MISMATCH",
        tagMismatches: {
            "447": ["P", "S"],
            "123": ["Q", "A"],
            "8019.Route": ["DIRECT", "SMART"],
            "8019.OInternal": ["true", "false"]
        },
        processType: "onc"
    },
    {
        orderId: "ORD-OMS-001",
        status: "MISMATCH",
        tagMismatches: {
            "55": ["AAPL", "GOOGL"],
            "-88": ["value1", "value2"],
            "100": ["BUY", "SELL"]
        },
        processType: "oms"
    },
    {
        orderId: "ORD-ONC-002",
        status: "MISMATCH",
        tagMismatches: {
            "38": ["1000", "1500"],
            "44": ["150.50", "151.00"],
            "8019.Profile": ["RETAIL", "INSTITUTIONAL"]
        },
        processType: "onc"
    },
    {
        orderId: "ORD-OMS-002",
        status: "MISMATCH",
        tagMismatches: {
            "54": ["1", "2"],
            "60": ["20260128-10:30:00", "20260128-10:31:00"]
        },
        processType: "oms"
    }
];

wss.on('connection', (ws) => {
    console.log('Client connected');

    // Send a message every 3 seconds
    let index = 0;
    const interval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
            const message = sampleMessages[index % sampleMessages.length];
            ws.send(JSON.stringify(message));
            console.log(`Sent message for order: ${message.orderId}`);
            index++;
        }
    }, 3000);

    ws.on('close', () => {
        console.log('Client disconnected');
        clearInterval(interval);
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
    });
});

console.log('Sample messages will be sent every 3 seconds to connected clients');
