# Step 1: Solace Connection Test

## What This Does
This is a **simple connection test** that:
1. ✅ Connects to your Solace VPN
2. ✅ Subscribes to the topic
3. ✅ Prints every message received
4. ❌ Does NOT do any matching or comparison yet

## How to Run

### 1. First, download dependencies:
```bash
./gradlew build
```

### 2. Run the test:
```bash
./gradlew testSolace
```

## What You Should See

### If Successful:
```
=== Solace Connection Test ===

Configuration loaded:
  Host: tcps://solace-oq1-cti-uk-1u-virt.eur.nsroot.net:55443
  VPN: 174106_OCCOMET_UK_CTI_QA
  Topic (OMS): QA/UK/QC/OMS/TO/COMET

Connecting to Solace...
✓ Connected successfully!

Subscribing to topic: QA/UK/QC/OMS/TO/COMET
✓ Subscription active!

🎧 Listening for messages... (Press Ctrl+C to stop)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MESSAGE RECEIVED:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Type: TextMessage
Content:
8=FIX.4.2^A9=0288^A35=D^A49=BLPCGAP^A56=CGA6...

Metadata:
  Destination: QA/UK/QC/OMS/TO/COMET
  Timestamp: 1736671234567
  Message ID: abc123
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### If Connection Fails:
```
❌ Error: Unable to connect to host
```
**Solution**: Check network access and VPN credentials

## Troubleshooting

### Error: "com.solacesystems cannot be resolved"
**Solution**: Run `./gradlew build` first to download dependencies

### Error: "Authentication failed"
**Solution**: Verify credentials in `solace.properties` are correct

### Error: "Connection timeout"
**Solution**: 
1. Check if you're on the correct network
2. Verify firewall allows port 55443
3. Confirm VPN name is correct

## Next Steps

Once you see messages printing:
1. ✅ **Step 1 Complete** - Connection works!
2. ⏭️ **Step 2** - Parse the FIX messages
3. ⏭️ **Step 3** - Compare with original logs
4. ⏭️ **Step 4** - Send results to UI

## Stop the Test

Press `Ctrl+C` to stop listening and close the connection gracefully.
