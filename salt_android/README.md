# SALT Android Tablet Application

The SALT Android app is a facility-based survey data collection tool that runs on tablets, enabling healthcare staff to conduct participant interviews with fingerprint-based duplicate detection and audio-assisted surveys.

**[← Back to Main Documentation](../README.md)**

## Quick Setup

### For Field Staff (10 minutes)

**Before you start**, get from your administrator:
- Server URL (e.g., `http://192.168.1.100:3000`)
- Facility API Key (format: `salt_<uuid>`)
- Facility Short Code (6-digit code, valid for 24 hours)

**Setup steps**:

1. **Install the App**
   - Download `SALT.apk` from your administrator
   - Enable "Install from Unknown Sources" in Settings
   - Tap the APK file to install
   - Grant requested permissions

2. **Connect Fingerprint Scanner**
   - Connect SecuGen scanner to tablet via USB OTG cable
   - Wait for "Scanner connected" notification

3. **Configure the App**
   - Launch SALT app
   - On first run, you'll see the Initial Setup Wizard
   - Enter Server URL
   - Enter Facility API Key
   - Enter Short Code (from administrator)
   - Tap "Complete Setup"

4. **Sync Surveys**
   - App will automatically download surveys
   - Wait for "Sync complete" message
   - You'll see available surveys on the welcome screen

5. **Test the Setup**
   - Tap "Start Survey"
   - Enter a test coupon code
   - Try scanning a fingerprint
   - Select language and answer a few questions
   - Verify audio playback works

**You're ready to start!**

---

## Hardware Requirements

### Required Equipment

#### Android Tablet
- **Screen Size**: 7 inches or larger (10" recommended for better usability)
- **Operating System**: Android 8.0 (API 26) or higher
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 4GB available space minimum
- **USB Port**: USB-C or Micro-USB with **OTG (On-The-Go) support**
- **Network**: WiFi capable for synchronization

**Recommended Tablets**:
- Samsung Galaxy Tab A7 Lite (8.7", Android 11+)
- Samsung Galaxy Tab A8 (10.5", Android 11+)
- Lenovo Tab M10 Plus (10.3", Android 10+)

#### Fingerprint Scanner
- **Model**: SecuGen Hamster Pro 20 (or compatible SecuGen HU20-A)
- **Certification**: FBI-certified for quality
- **Connection**: USB interface
- **Driver**: Android compatible (supported natively in app)

#### Accessories
- **USB OTG Cable**: USB-C to USB-A or Micro-USB to USB-A (depending on tablet)
  - **Critical**: Must be an OTG (On-The-Go) cable, not just a regular USB cable
  - Verify OTG support: Install "USB OTG Checker" app from Play Store
- **Protective Case**: Drop-resistant case for healthcare environment
- **Screen Protector**: Tempered glass for durability
- **Charging Station**: Multi-port USB charger for multiple tablets

### Optional Equipment
- Portable WiFi hotspot (if facility WiFi is unreliable)
- External battery pack for field use
- Stylus for easier data entry
- Desktop stand for ergonomic positioning

### Hardware Verification

Before deployment, verify:

```bash
# Check if tablet supports USB OTG
# Install "USB OTG Checker" from Play Store
# Connect OTG cable → should see "OTG supported" message

# Check Android version
# Settings → About tablet → Android version
# Must be 8.0 or higher

# Check available storage
# Settings → Storage
# Should have at least 4GB free
```

---

## Installation

### Method 1: Install from APK (Field Deployment)

This is the standard method for deploying to facility tablets.

**Step 1: Download APK**
- Get the latest `SALT.apk` file from your administrator
- Or download from project releases page
- Transfer to tablet via:
  - USB cable
  - Email attachment
  - Cloud storage (Google Drive, Dropbox)
  - Direct download from server

**Step 2: Enable Unknown Sources**
1. Open **Settings** on tablet
2. Navigate to **Security** or **Privacy**
3. Find **Install Unknown Apps** or **Unknown Sources**
4. Allow installation from your file manager or browser
5. This setting may need to be enabled per-app on Android 8+

**Step 3: Install APK**
1. Open file manager and locate `SALT.apk`
2. Tap the APK file
3. Review permissions:
   - **Camera**: For future features
   - **Storage**: For audio files and database
   - **Network**: For server synchronization
4. Tap **Install**
5. Wait for installation to complete
6. Tap **Open** or find **SALT** icon in app drawer

**Step 4: Grant Runtime Permissions**

When you first launch the app, grant these permissions:
- **Storage**: Required for local database and audio files
- **Network**: Required for syncing with server
- **Camera**: For future barcode scanning features

### Method 2: Build from Source (Developers)

See [Developer Setup](#developer-setup) section below.

---

## Initial Configuration

### First Launch: Setup Wizard

On first run, the app will guide you through initial setup.

**Screen 1: Welcome**
- Tap "Get Started" to begin setup

**Screen 2: Server Configuration**
1. Enter **Server URL**:
   - Format: `http://SERVER_IP:PORT` or `https://domain.com`
   - Example: `http://192.168.1.100:3000`
   - Do NOT include trailing slash
   - Use HTTPS if server has SSL certificate

2. Enter **Facility API Key**:
   - Format: `salt_<uuid>`
   - Example: `salt_18dcd398-8c91-4e1e-af2c-542a32eeff3d`
   - Get this from administrator via Facilities page on server
   - Copy-paste carefully to avoid typos

3. Enter **Short Code**:
   - 6-digit code (e.g., `A1B2C3`)
   - Generated by administrator, valid for 24 hours only
   - One-time use for initial registration
   - Case-sensitive

4. Tap **"Test Connection"** to verify settings
   - Should show "Connection successful"
   - If fails, verify server URL and network connection

5. Tap **"Complete Setup"**

**Screen 3: Initial Sync**
- App automatically downloads surveys and configuration
- Wait for "Sync complete" message
- May take 1-2 minutes depending on survey size and audio files

**Screen 4: Login**
- Default credentials (provided by administrator):
  - Username: `staff`
  - Password: `123`
- Change password after first login via User Management

### Manual Configuration (Advanced)

If you need to change settings later:

1. Login as Administrator
2. Navigate to **Admin Dashboard**
3. Select **Server Settings**
4. Update:
   - Server URL
   - API Key
   - Sync interval
5. Tap **Save** and **Sync Now**

---

## Using the App

### Login

1. Launch SALT app
2. Enter username and password
3. Tap **Sign In**

**User Roles**:
- **Survey Staff**: Conduct surveys, view facility data only
- **Administrator**: Full access including user management and settings

### Starting a New Survey

**Step 1: Enter Coupon Code**
1. From welcome screen, tap **"Start Survey"**
2. Enter participant's coupon code
3. Or select "New Enrollment" for seed participants
4. Tap **Continue**

**Step 2: Fingerprint Screening** (if enabled)
1. Prompt participant to place finger on scanner
2. Wait for scanner to capture fingerprint
3. System checks for duplicate enrollment:
   - **Green checkmark**: New participant, proceed
   - **Red X**: Already enrolled within re-enrollment period
   - Show "days until re-enrollment" if duplicate detected

**Step 3: Language Selection**
1. Participant selects preferred language
2. Tap language name to hear pronunciation
3. Tap **Continue**

**Step 4: Conduct Survey**
1. Read question to participant (or let them read/listen)
2. Audio automatically plays for each question (ACASI mode)
3. Participant selects answer
4. Tap **Next** to continue
5. Some questions may be skipped based on previous answers (skip logic)

**Step 5: Eligibility Check**
1. After eligibility questions, system evaluates participation criteria
2. If **Eligible**:
   - Continue to full survey
3. If **Ineligible**:
   - Show ineligibility message
   - Generate coupon for participant if configured
   - End survey

**Step 6: Payment Confirmation**
1. After survey completion, proceed to payment screen
2. **Option A: Participant Fingerprint**
   - Prompt participant to scan fingerprint
   - Confirms identity and payment receipt
3. **Option B: Admin Override**
   - If participant unavailable or fingerprint fails
   - Tap "Admin Override"
   - Administrator scans their fingerprint
   - Payment confirmed with admin name logged
4. Enter payment details:
   - Phone number (for mobile money)
   - Or mark as cash payment
   - Indicate if sample collected
5. Tap **Confirm Payment**

**Step 7: Generate New Coupons**
1. System generates coupons for participant to distribute
2. Number of coupons based on facility configuration (default: 3)
3. Print or write down coupon codes for participant

**Step 8: Survey Upload**
- Survey automatically uploads to server
- Green checkmark indicates successful upload
- If offline, survey queued for automatic upload when online

### Audio Playback (ACASI)

**ACASI** = Audio Computer-Assisted Self-Interview

- Audio automatically plays when question is displayed
- Participant can listen privately with headphones
- Tap **Replay** button to hear question again
- Each answer option also has audio
- Supports multilingual audio

**Benefits**:
- Accommodates low literacy
- Ensures consistent question delivery
- Provides privacy for sensitive questions
- Reduces interviewer bias

### Navigating Surveys

- **Next**: Move to next question (enabled when current question answered)
- **Back**: Return to previous question (answers preserved)
- **Replay**: Replay audio for current question
- **Skip**: Some questions auto-skip based on logic

**Answer Validation**:
- Cannot proceed without answering current question
- Numeric questions validate range
- Text questions validate format
- Multi-select questions validate min/max selections

### Offline Mode

The app works offline:
- Surveys can be completed without internet
- Answers saved to encrypted local database
- Uploads queued for when connection restored
- Automatic sync when network available

**Sync Indicators**:
- **Green cloud**: All surveys uploaded
- **Orange cloud**: Uploads pending
- **Red cloud**: Upload failures (retry needed)

### Viewing Survey Status

1. Login as Administrator
2. Navigate to **Upload Status** screen
3. View:
   - Pending uploads (yellow)
   - Completed uploads (green)
   - Failed uploads (red)
4. Tap failed upload to retry manually

### Logging Out

1. From menu screen, tap **Logout**
2. Confirm logout
3. Cannot logout during active survey (prevents data loss)

## Fingerprint Scanner Setup

### Connecting the Scanner

**Hardware Setup**:
1. Connect scanner to USB OTG cable
2. Connect OTG cable to tablet USB port
3. Scanner LED should light up (indicates power)
4. Tablet may show "USB device connected" notification

**Verification**:
1. Open SALT app
2. Scanner icon should show "Connected" (green)
3. If not connected:
   - Check OTG cable is properly inserted
   - Verify OTG cable supports data transfer (not just charging)
   - Try different USB port if tablet has multiple
   - Restart tablet

### Using the Scanner

**For Participant Enrollment**:
1. Instruct participant: "Please place your right index finger on the scanner"
2. Wait for green light or beep (scanner-dependent)
3. System captures fingerprint template
4. Template saved locally (never uploaded to server)
5. Used for duplicate detection

**For Payment Confirmation**:
1. Prompt participant to scan fingerprint again
2. System matches against saved template
3. Confirms identity and payment receipt

**For Admin Override**:
1. Administrator places finger on scanner
2. System checks against all admin fingerprints
3. If match found, payment confirmed with admin's name

### Scanner Troubleshooting

**Problem**: "Scanner not detected"
- **Check**: OTG cable properly connected
- **Check**: Cable supports data (not just power)
- **Solution**: Try different OTG cable
- **Solution**: Restart tablet with scanner connected

**Problem**: "Fingerprint capture failed"
- **Cause**: Finger too dry, wet, or dirty
- **Solution**: Clean finger, dry thoroughly, try again
- **Solution**: Use different finger
- **Solution**: Apply slight pressure without smudging

**Problem**: "Template extraction failed"
- **Cause**: Poor quality scan
- **Solution**: Re-position finger on scanner
- **Solution**: Ensure finger covers sensor area
- **Solution**: Clean scanner surface with soft cloth

**Problem**: Scanner works initially but stops
- **Cause**: USB power management
- **Solution**: Disable USB power saving:
  - Settings → Developer Options → USB debugging settings
  - Disable "USB suspend" or "USB power saving"

### Scanner Maintenance

- **Clean regularly**: Wipe scanner surface with microfiber cloth
- **Avoid liquids**: Don't spray cleaner directly on scanner
- **Handle carefully**: Scanner is sensitive equipment
- **Cable management**: Secure cable to prevent tripping or pulling
- **Test daily**: Verify scanner function at start of shift

### Fingerprint Privacy

**Important**: Fingerprints are handled securely:
- Templates stored locally on tablet only
- **Never uploaded to server**
- Encrypted in local database (SQLCipher)
- Used only for duplicate detection
- Cannot be reverse-engineered to original fingerprint
- Automatically deleted per retention policy

---

## Offline Mode

### How Offline Mode Works

The SALT app is designed for **offline-first** operation:

1. **Surveys work offline**: Complete surveys without internet
2. **Local storage**: All data saved to encrypted database
3. **Auto-sync**: Uploads when connection restored
4. **Smart queueing**: Failed uploads retry automatically

### Working Offline

**What works offline**:
- Starting new surveys
- Answering all questions
- Audio playback (if previously downloaded)
- Fingerprint scanning
- Payment confirmation
- Coupon generation
- Viewing local survey history

**What requires internet**:
- Initial setup and registration
- Downloading new surveys
- Uploading completed surveys
- Syncing configuration changes
- Downloading new audio files
- User authentication (first time)

### Sync Behavior

**Automatic Sync**:
- Triggers when network connection detected
- Runs every 30 minutes when online
- Uploads pending surveys first
- Downloads new surveys if available
- Updates configuration

**Manual Sync**:
1. Open app menu
2. Tap **Sync Now**
3. Wait for "Sync complete" message
4. Check upload status for failures

**Sync Indicators**:
- **Green cloud icon**: Fully synced, no pending uploads
- **Orange cloud icon**: Uploads pending
- **Red cloud icon**: Upload failures (attention needed)
- **Sync in progress**: Animated spinner

### Handling Sync Failures

If surveys fail to upload:

1. **Check Network**: Verify tablet has internet access
2. **Check Server**: Verify server is running and accessible
3. **View Details**:
   - Admin menu → Upload Status
   - Tap failed survey to see error message
4. **Retry**:
   - Tap "Retry Upload" button
   - Or wait for automatic retry (exponential backoff)
5. **Max Retries**: After 5 failed attempts, manual intervention required

### Offline Best Practices

**Preparation**:
- Sync before going to field or remote locations
- Verify all audio files downloaded
- Test offline functionality before deployment
- Charge tablet fully

**During Offline Use**:
- Monitor storage space
- Complete surveys normally
- Don't worry about upload errors
- Document any issues for later review

**After Coming Online**:
- Connect to WiFi (not mobile data if possible)
- Trigger manual sync
- Verify all surveys uploaded (green checkmarks)
- Review upload status screen for failures

### Data Persistence

**Local Database**:
- SQLite database with SQLCipher encryption
- Stores all survey data
- Survives app restarts
- Automatic backup recommended

**Storage Requirements**:
- Each survey: ~50-200 KB
- Audio files: ~500 KB - 2 MB per survey
- Plan for 500-1000 surveys before clearing
- Monitor storage via Settings

---

## Troubleshooting

### Common Issues

#### App Won't Install

**Error**: "App not installed"
- **Cause**: Insufficient storage
- **Solution**: Delete unused apps, clear cache
  - Settings → Storage → Free up space

**Error**: "Unknown sources blocked"
- **Cause**: Security setting
- **Solution**: Enable "Unknown Sources" in Settings → Security

**Error**: "Package conflicts with existing app"
- **Cause**: Different signature
- **Solution**: Uninstall old version first, then reinstall

#### App Crashes on Startup

**Check**:
1. Android version (must be 8.0+)
2. Available storage (need 500MB minimum)
3. Permissions granted (Storage, Network)

**Solution**:
```
Settings → Apps → SALT → Storage → Clear Cache
Settings → Apps → SALT → Permissions → Verify all granted
If persists: Clear Data (warning: loses local data)
```

#### Cannot Connect to Server

**Error**: "Connection failed" or "Server unreachable"

**Checks**:
1. Server URL correct (no typos, trailing slash)
2. Server is running (check with administrator)
3. Tablet on same network as server
4. Firewall not blocking connection
5. API key correct (check for copy-paste errors)

**Test Connection**:
```bash
# On tablet, open browser and visit:
http://SERVER_IP:PORT

# Should show login page
# If not, server is unreachable from tablet
```

**Solutions**:
- Verify WiFi connected and has internet
- Try pinging server from another device
- Check server firewall rules
- Verify port number (default 3000)
- Use IP address instead of hostname

#### Audio Not Playing

**Problem**: Questions have no audio or audio doesn't play

**Causes**:
1. Audio files not downloaded
2. Storage permission denied
3. Volume muted or too low
4. Audio file corrupted

**Solutions**:
1. Tap Sync Now to download audio files
2. Check tablet volume and unmute
3. Verify Storage permission granted
4. Test with different question
5. Check if audio files exist on server

#### Fingerprint Scanner Not Working

See [Fingerprint Scanner Setup](#fingerprint-scanner-setup) section above.

#### Survey Won't Upload

**Error**: "Upload failed" with red indicator

**Checks**:
1. Network connection active
2. Server reachable
3. Survey data valid
4. Storage space available

**Solution**:
1. Admin menu → Upload Status
2. Tap failed survey to see error details
3. Tap "Retry Upload"
4. If retries fail, contact administrator
5. Check server logs for error message

#### Login Fails

**Error**: "Invalid username or password"

**Solutions**:
- Verify credentials with administrator
- Check for typos (usernames are case-sensitive)
- Ensure user account is active
- Try default credentials if first login: `staff` / `123`
- Admin can reset password via server interface

#### Logout Button Not Showing

**Cause**: Survey in progress
- **Design**: Prevents accidental data loss
- **Solution**: Complete or abandon current survey first

#### App is Slow or Freezing

**Causes**:
1. Too many surveys in local database
2. Large audio files
3. Low storage space
4. Background sync running

**Solutions**:
1. Clear old surveys after upload
2. Archive data via admin panel
3. Free up storage space
4. Wait for sync to complete
5. Restart tablet

### Advanced Troubleshooting

#### View App Logs

For developers or advanced troubleshooting:

```bash
# Connect tablet to computer via USB
# Enable USB Debugging in Developer Options

# View real-time logs
adb logcat | grep SALT

# View crash logs
adb logcat | grep AndroidRuntime
```

#### Database Issues

If database becomes corrupted:

```bash
# WARNING: This deletes all local data

Settings → Apps → SALT → Storage → Clear Data

# Then re-setup app from scratch
# Surveys will need to be re-downloaded
```

## Developer Setup

### Prerequisites

- **Android Studio**: Latest stable version (Flamingo or newer)
  - Download: https://developer.android.com/studio
- **JDK**: Version 11 or higher (bundled with Android Studio)
- **Git**: For cloning repository
- **Android SDK**: API Level 26+ (Android 8.0+)

### Building from Source

**Step 1: Clone Repository**

```bash
git clone <repository-url>
cd salt/salt_android
```

**Step 2: Open in Android Studio**

1. Launch Android Studio
2. File → Open
3. Select `salt_android` directory
4. Wait for Gradle sync to complete

**Step 3: Configure Build**

No special configuration needed. The project uses:
- Gradle 8.0+
- Kotlin 1.9+
- Compose BOM for UI dependencies

**Step 4: Build APK**

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing key)
./gradlew assembleRelease

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

**Step 5: Install on Device**

```bash
# Via Android Studio: Run → Run 'app'

# Or via command line:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Project Structure

```
salt_android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/dev/salt/
│   │   │   │   ├── ui/              # Compose UI screens
│   │   │   │   ├── data/            # Room database
│   │   │   │   ├── viewmodel/       # ViewModels
│   │   │   │   ├── sync/            # Server sync logic
│   │   │   │   └── utils/           # Utilities
│   │   │   ├── res/                 # Resources
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                    # Unit tests
│   │   └── androidTest/             # Instrumentation tests
│   ├── schemas/                     # Room schema exports
│   └── build.gradle.kts             # Build configuration
├── gradle/                          # Gradle wrapper
└── build.gradle.kts                 # Project build config
```

### Security Considerations

**Database Encryption**:
- Uses SQLCipher for full database encryption
- Keys stored in Android Keystore
- Never hardcode encryption keys

**API Keys**:
- Store in encrypted preferences
- Never log or display in plain text
- Rotate regularly

**Fingerprint Data**:
- Store templates locally only
- Never upload to server
- Use SHA-256 for template hashing
- Implement secure deletion

**Network Security**:
- Use HTTPS in production
- Pin SSL certificates if possible
- Implement certificate validation
- Handle token refresh securely

### Building for Production

**Release Build Configuration**:

1. **Create signing key**:
   ```bash
   keytool -genkey -v -keystore salt-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias salt
   ```

2. **Configure in `gradle.properties`**:
   ```
   SALT_RELEASE_STORE_FILE=../salt-release-key.jks
   SALT_RELEASE_STORE_PASSWORD=your_password
   SALT_RELEASE_KEY_ALIAS=salt
   SALT_RELEASE_KEY_PASSWORD=your_password
   ```

3. **Build release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

4. **Verify signing**:
   ```bash
   jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
   ```

### Documentation for Developers

- **Technical Architecture**: [CLAUDE.md](CLAUDE.md)
- **Code Summaries**: [../android_code_summary.md](../android_code_summary.md)
- **Language Setup**: [ADDING_LANGUAGES.md](ADDING_LANGUAGES.md)
- **Fingerprint Integration**: [FINGERPRINT_INTEGRATION_SUCCESS.md](FINGERPRINT_INTEGRATION_SUCCESS.md)

---

## Next Steps

After completing tablet setup:

1. **Test Survey Flow**
   - Complete test survey end-to-end
   - Verify audio playback
   - Test fingerprint scanner
   - Confirm upload to server

2. **Train Staff**
   - Survey interview process
   - Fingerprint scanner usage
   - Handling ineligible participants
   - Payment confirmation workflow
   - Troubleshooting common issues

3. **Deploy to Facilities**
   - Install on all tablets
   - Configure facility-specific settings
   - Test network connectivity
   - Establish support procedures

4. **Monitor and Maintain**
   - Check upload status daily
   - Clear old surveys periodically
   - Update app when new versions released
   - Respond to user feedback
