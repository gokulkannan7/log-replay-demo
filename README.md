# Log Replay & Verification Tool

A high-performance **Log Replay** and **Real-Time Verification** system designed to validate FIX message fidelity between legacy and modern trading systems. 

This tool parses massive log files, indexes them for memory efficiency, and streams mismatches in real-time to a React Dashboard via WebSockets.

---

## 🏛 Architecture

The system is composed of two main components:
1.  **Backend (Java 17)**: A high-throughput matching engine using `RandomAccessFile` for low-memory indexing.
2.  **Frontend (React + Vite)**: A WebSocket-enabled dashboard for live audit monitoring.

### 🧠 Core Algorithm: Offset Indexing
Instead of loading 10GB logs into RAM, we use a **Two-Pass Approach**:

1.  **Phase 1 (Indexing)**: Scan `Original.log` -> Store `Map<OrderID, ByteOffset>` (Key + Pointer only).
2.  **Phase 2 (Streaming)**: Read `Replayed.log` -> Lookup ID -> **Seek** to Offset in Original -> Compare.

![Architecture Diagram](https://i.imgur.com/your-diagram-placeholder.png) 
*(Note: Replace with the diagram generated earlier if hosted)*

---

## 🚀 Getting Started

### Prerequisites
*   **Java 17+**
*   **Node.js 18+**
*   **Gradle**

### 1. Setup Data
Place your log files in the `logs/` directory:
*   `logs/original.log` (The "Truth" source)
*   `logs/replayed.log` (The output from your new system)

### 2. Start the Backend (Gateway)
The Gateway starts the WebSocket server on **Port 8080** and waits for a UI connection to trigger the replay.

```bash
# In the root 'simple-aeron-demo' directory
./gradlew runGateway
```
*Expected Output:* `WebSocket Gateway Server started on port: 8080`

### 3. Start the Frontend (Dashboard)
The UI runs on **Port 3000**.

```bash
# In the 'web-ui' directory
cd web-ui
npm install
npm run dev
```

### 4. Run the Audit
Open your browser to **http://localhost:3000**.
*   The connection status will turn **GREEN**.
*   Mismatches will stream in instantly.
*   Use the **Insights Panel** to see which tags are breaking most frequently.

---

## 🛠 Feature Highlights

### ⚡️ WebSocket Streaming
Unlike traditional batch scripts that take minutes to report, this tool pushes the *first* error to the UI in milliseconds.

### 📉 Smart Diffing
*   **Ignored Tags**: Automatically ignores transient tags like `9` (BodyLength), `10` (Checksum), and `52` (SendingTime).
*   **Strict Filtering**: Mismatches are highlighted. Perfect matches are discarded to reduce noise.

### 📊 Analytics Dashboard
*   **Top Offenders**: Automatically calculates which FIX Tags are causing the most failures (e.g., "Tag 49 is wrong 50 times").
*   **Missing Order Detection**: Identifies orders present in Original but dropped in Replay (and vice versa).

---

## 📂 Project Structure

```
simple-aeron-demo/
├── src/main/java/com/logreplay/
│   ├── matching/
│   │   └── MatchingService.java    # Core Logic (The Brain)
│   └── gateway/
│       └── UIRestGateway.java      # WebSocket Server (The Mouth)
├── web-ui/                         # React Application
│   ├── src/App.jsx                 # Dashboard UI
│   └── src/App.css                 # Dark Mode Styles
└── build.gradle                    # Dependencies (Gson, Java-WebSocket)
```

---

## 📝 Troubleshooting

**Q: I see "DISCONNECTED" in the UI.**
*   A: Ensure `gradle runGateway` is running in a separate terminal.

**Q: The logs matches perfectly but I see no data?**
*   A: The UI hides perfect matches by default. Check the "Scanned" count in the Insights Panel. If it's increasing, the tool is working!

