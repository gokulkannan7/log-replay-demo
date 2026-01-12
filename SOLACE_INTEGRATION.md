# Solace Integration Guide

## Overview
This document explains how to use the Solace consumer layer to stream replay logs from your production Solace broker.

## Configuration

### 1. Update `solace.properties`
The configuration file has been set up to match your production YAML format:

```properties
# Connection Details
host=tcps://solace-oq1-cti-uk-1u-virt.eur.nsroot.net:55443
vpnName=174106_OCCOMET_UK_CTI_QA
username=ov+1QPRSaX84wW1HsDWkIYu6Aot8V2Bt1FhuS6Q9CLio=
password=vp+Hd1k1xY5RiGCwy/UIzQ==

# SSL Settings
SSL_VALIDATE_CERTIFICATE=false

# Resilience
reconnectTries=10
destinationCreationRetryCount=6000

# Topics
topic_oms=QA/UK/QC/OMS/TO/COMET
topic_onc=QA/UK/QC/OMS/TO/COMET/TR
```

### 2. Key Differences from Generic Config

**Production Features Enabled:**
- ✅ **TCPS Protocol** - Secure TLS connection (`tcps://` instead of `tcp://`)
- ✅ **SSL Certificate Validation** - Disabled for internal certs
- ✅ **Auto-Reconnect** - 10 retry attempts with exponential backoff
- ✅ **High Retry Count** - 6000 retries for destination creation
- ✅ **Encrypted Credentials** - Base64 encoded username/password

## Usage

### Option 1: Standalone Testing
Test the Solace consumer independently:

```bash
# Build the project
./gradlew build

# Run the integration test
./gradlew runSolaceTest
```

### Option 2: Integrated with Matching Engine
The consumer automatically feeds messages to your matching engine:

```java
SolaceReplayIntegration integration = new SolaceReplayIntegration();

integration.startReplayStreams(result -> {
    // This callback receives comparison results in real-time
    System.out.println("Order: " + result.orderId + " Status: " + result.status);
});
```

### Option 3: Connected to UI Gateway
Modify `UIRestGateway.java` to use Solace instead of files:

```java
// In UIRestGateway.onOpen()
SolaceReplayIntegration solace = new SolaceReplayIntegration();
solace.startReplayStreams(result -> {
    String json = gson.toJson(result);
    conn.send(json); // Send to WebSocket client
});
```

## Topic Structure

Based on your config, you have two replay streams:

| Stream | Topic | Purpose |
|--------|-------|---------|
| **OMS** | `QA/UK/QC/OMS/TO/COMET` | Order Management System logs |
| **ONC** | `QA/UK/QC/OMS/TO/COMET/TR` | Trade Reports / Confirmations |

## Security Notes

⚠️ **Important**: The credentials in `solace.properties` appear to be encrypted/encoded. 

- If these are Base64 encoded, the consumer will use them as-is
- If they require decryption, you'll need to add a decryption step in `loadConfig()`
- For production, consider using environment variables or a secrets manager

## Performance Tuning

The consumer is configured with:
- **Window Size**: 255 messages (max unacknowledged)
- **Reconnect Retries**: 10 attempts
- **Backoff Strategy**: Exponential (1s, 2s, 4s, 8s, 16s...)

To adjust these, modify the constants in `SolaceLogConsumer.java`:
```java
private static final int CONSUMER_WINDOW_SIZE = 255;
private static final int RECONNECT_RETRIES = 10;
```

## Troubleshooting

### Connection Issues
```
Error: Unable to connect to Solace broker
```
**Solution**: Verify network access to `solace-oq1-cti-uk-1u-virt.eur.nsroot.net:55443`

### SSL Certificate Errors
```
Error: SSL validation failed
```
**Solution**: Ensure `SSL_VALIDATE_CERTIFICATE=false` in properties file

### Authentication Failures
```
Error: Authentication failed
```
**Solution**: Verify credentials are correct and not expired

## Next Steps

1. **Test Connection**: Run standalone test to verify Solace connectivity
2. **Integrate Cache**: Connect to your MMFLogCache for original log lookup
3. **Connect to UI**: Wire up to UIRestGateway for live dashboard updates
4. **Monitor Performance**: Track message throughput and latency

## Architecture Flow

```
Solace Broker (Production)
    ↓
    ↓ (TCPS/SSL)
    ↓
SolaceLogConsumer
    ↓
    ↓ (Callback)
    ↓
SolaceReplayIntegration
    ↓
    ↓ (Extract Order ID)
    ↓
MMFLogCache ← Fetch Original
    ↓
    ↓ (Compare)
    ↓
MatchingService
    ↓
    ↓ (WebSocket)
    ↓
UIRestGateway → React Dashboard
```
