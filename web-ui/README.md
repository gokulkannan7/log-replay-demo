# Log Replay Monitor UI

A professional React-based WebSocket UI for monitoring log replay mismatches across ONC and OMS engines.

## Features

- **Dual Engine Support**: Separate tabs for ONC and OMS engines
- **Real-time WebSocket Connection**: Live updates with connection status indicator
- **Profile Tag Detection**: Special highlighting for 8019.* profile tags
- **Tag Normalization**: Automatically removes leading "-" from tag names
- **Detailed Analysis**: Click any order to see comprehensive mismatch details
- **Connection Logs**: Real-time logging of WebSocket events
- **Auto-reconnect**: Automatic reconnection with exponential backoff
- **Modern UI**: Dark theme with smooth animations and professional design

## Installation

```bash
cd web-ui
npm install
```

## Running the Application

```bash
npm run dev
```

The application will start on `http://localhost:3000`

## WebSocket Configuration

The WebSocket URL is configured in `src/App.jsx`:

```javascript
const WS_URL = 'ws://localhost:8080/ws';
```

Update this URL to match your backend WebSocket endpoint.

## Expected JSON Format

The backend should send JSON messages in the following format:

```json
{
  "orderId": "ORDER123",
  "status": "MISMATCH",
  "tagMismatches": {
    "447": ["P", "S"],
    "123": ["Q", "A"],
    "8019.Route": ["value1", "value2"],
    "-88": ["asd", "dfgf"]
  },
  "processType": "onc"
}
```

### Field Descriptions

- **orderId**: Unique identifier for the order
- **status**: Status of the order (e.g., "MISMATCH")
- **tagMismatches**: Object containing tag numbers/names as keys and arrays of mismatched values
- **processType**: Either "onc" or "oms" to route to the appropriate tab

### Special Tag Handling

1. **Profile Tags**: Tags starting with "8019." (e.g., "8019.Route", "8019.OInternal") are highlighted as profile tags
2. **Negative Tags**: Tags with leading "-" (e.g., "-88") are normalized to remove the minus sign

## Project Structure

```
web-ui/
├── src/
│   ├── components/
│   │   ├── ConnectionStatus.jsx  # WebSocket connection indicator
│   │   ├── OrderCard.jsx         # Order summary card
│   │   ├── OrderDetailsModal.jsx # Detailed mismatch view
│   │   └── Logs.jsx              # Connection logs viewer
│   ├── hooks/
│   │   └── useWebSocket.js       # WebSocket connection hook
│   ├── App.jsx                   # Main application component
│   ├── main.jsx                  # Application entry point
│   └── index.css                 # Global styles
├── index.html
├── package.json
└── vite.config.js
```

## Building for Production

```bash
npm run build
```

The production build will be in the `dist` folder.

## Browser Support

- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## Notes

- The UI automatically separates orders based on the `processType` field
- Connection logs show all WebSocket events for debugging
- The application will attempt to reconnect automatically if the connection is lost
- All timestamps in logs use local time format
