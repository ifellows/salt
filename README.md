# SALT (System Assisted Link Tracing)

A comprehensive survey system implementing the SALT methodology for monitoring key populations in HIV response programs. This system replaces traditional Bio-Behavioral Surveys (BBS) with continuous monitoring capabilities.

**📚 Documentation Hub**

| Component | Setup Guide | Technical Details |
|-----------|------------|------------------|
| **Management Server** | [Server README](salt_management/README.md) | |
| **Android Tablets** | [Android README](salt_android/README.md) |  |
| **SALT Methodology** | [SALT.pdf](SALT.pdf) | |

---

## Table of Contents

- [What is SALT?](#what-is-salt)
- [Quick Start](#quick-start)
- [System Architecture](#system-architecture)
- [Recent Updates](#recent-updates-september-2025)
- [Documentation](#documentation)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Status](#project-status)

---

## What is SALT?

**SALT (System Assisted Link Tracing)** is a modern approach to monitoring key populations in HIV response programs. It addresses critical limitations of traditional Bio-Behavioral Surveys (BBS):

### The Problem with Traditional BBS
- **Expensive and Slow**: Traditional surveys are costly and time-consuming to implement
- **Outdated Data**: Results are often obsolete by the time they're published
- **Periodic Only**: Data collection happens infrequently (every few years)
- **Inconsistent Sampling**: Manual RDS processes can vary between interviewers

### The SALT Solution
- **Continuous Monitoring**: Ongoing data collection instead of periodic surveys
- **Real-Time Insights**: Data available immediately for policy decisions
- **Cost-Effective**: Facility-based implementation reduces overhead
- **Software-Guided**: Consistent sampling process with built-in quality controls
- **Accessible**: Audio support (ACASI) for low-literacy populations

### How It Works
1. **Facilities**: Healthcare facilities deploy tablets with the SALT app
2. **Recruitment**: Initial participants (seeds) recruit peers using coupons
3. **Data Collection**: Staff conduct surveys with automated workflows
4. **Analysis**: Data feeds into RDS statistical models for population estimates

For complete methodology details, see [SALT.pdf](SALT.pdf).

---

## Quick Start

### I'm an Administrator Setting Up the Server

**⏱️ Time: 5 minutes**

```bash
cd salt_management
npm install
npm start
```

Visit http://localhost:3000 and login with `admin` / `admin123`

**Next steps**: [Complete Server Setup Guide →](salt_management/README.md)

### I'm Setting Up a Tablet for Field Use

**⏱️ Time: 10 minutes**

**Recommended Tablet**: Samsung Galaxy Tab A7 Lite (8.7", Android 11+)
This is the only tablet that has been directly tested with SALT. Other Android tablets meeting the minimum requirements may also work.

**Prerequisites**:
- Android tablet (7"+ screen, Android 8.0+)
- SecuGen HU20-A fingerprint scanner
- USB OTG cable
- The APK file (see below)

**Getting the APK**:

The pre-built APK is located at:
```
salt_android/app/build/outputs/apk/debug/app-debug.apk
```

To build the APK yourself:
```bash
cd salt_android
./gradlew assembleDebug

# APK will be created at:
# app/build/outputs/apk/debug/app-debug.apk
```

**Setup Steps**:
1. Copy `app-debug.apk` to your tablet (via USB, email, or cloud storage)
2. Enable "Install from Unknown Sources" in tablet Settings → Security
3. Install the APK by tapping on it
4. Connect SecuGen HU20-A fingerprint scanner via USB OTG cable
5. Launch SALT app and enter server URL and setup code from administrator
6. Complete initial sync to download surveys
7. Set up Administrator and Survey Staff users
8. Start surveying

**Next steps**: [Complete Tablet Setup Guide →](salt_android/README.md)

### I'm a Developer

**Server Development**:
```bash
cd salt_management
npm install
npm run dev  # Auto-restart on changes
```

**Android Development**:
```bash
cd salt_android
./gradlew assembleDebug
```


---

## System Architecture

SALT consists of three main components working in a coordinated ecosystem:

### 1. Android Application ([Setup Guide →](salt_android/README.md))
- **Purpose**: Facility-based tablet application for data collection
- **Features**:
  - Multi-language survey support with audio playback (ACASI)
  - Biometric authentication and fingerprint duplicate detection
  - Offline-first architecture with background sync
  - SQLCipher database encryption
  - Staff validation workflows
  - Coupon generation and tracking
  - Payment processing integration
- **Technology**: Kotlin, Jetpack Compose, Room Database
- **Current Version**: Database v59

### 2. Management Server ([Setup Guide →](salt_management/README.md))
- **Purpose**: Web-based survey configuration and monitoring interface
- **Features**:
  - Survey builder with multi-language support
  - Audio recording for questions and system messages
  - Skip logic and validation rules using JEXL
  - Facility and user management
  - Real-time data sync with tablets
  - Upload monitoring and analytics
  - Lab test result management
  - Data export in multiple formats (wide, long, RDS)
- **Technology**: Node.js, Express, SQLite, EJS
- **API**: RESTful endpoints for tablet synchronization
- **Deployment**: Native or Docker with R/Quarto support

### 3. Analytics Platform (Future Development)
- Statistical analysis with RDS estimators
- Rolling 6, 12, and 24-month estimates
- Policy stakeholder dashboards
- Real-time monitoring and alerts

## Documentation

### 📖 User Guides

**Start here if you're setting up SALT for the first time:**

- **[Management Server Setup](salt_management/README.md)** - Complete guide to installing and configuring the server
  - Native installation (Node.js)
  - Docker deployment
  - Survey configuration
  - Facility management
  - User management
  - Data export

- **[Android Tablet Setup](salt_android/README.md)** - Complete guide to deploying tablets
  - Hardware requirements
  - APK installation
  - Initial configuration
  - Fingerprint scanner setup
  - Language configuration
  - Troubleshooting

- **[SALT Methodology (PDF)](SALT.pdf)** - Research design and sampling theory
  - Why SALT replaces traditional BBS surveys
  - Sampling methodology
  - Statistical analysis approach

## Key Features

### Continuous Monitoring
Unlike traditional RDS surveys, SALT enables ongoing data collection with real-time analysis capabilities.

### Multi-Language Support
- Survey questions and options in multiple languages
- Audio support for low-literacy populations
- System messages customizable per language

### Security & Privacy
- End-to-end encryption for sensitive data
- Biometric authentication for staff
- Fingerprint-based duplicate detection
- Role-based access control

### Offline Capability
- Android tablets work offline with automatic sync
- Background upload with retry logic
- Conflict resolution for concurrent edits

## Technology Stack

### Backend
- Node.js with Express framework
- SQLite database (PostgreSQL ready)
- Session-based authentication
- RESTful API design

### Android
- Kotlin with Jetpack Compose
- Room persistence with SQLCipher
- Coroutines for async operations
- Material Design 3 UI

### Audio Processing
- Client-side MP3 encoding (lamejs)
- Base64 audio storage and transmission
- MediaPlayer integration on Android

## Prerequisites

Before setting up SALT, ensure you have the following:

### For Server Setup
- **Node.js**: Version 14 or higher ([Download](https://nodejs.org/))
- **SQLite3**: Database engine (usually pre-installed on macOS/Linux)
- **R**: Version 4.0+ for RDS analysis ([Download](https://cran.r-project.org/))
- **Quarto**: For generating reports ([Download](https://quarto.org/))

### For Tablet Deployment
- **Android Tablet**: Samsung Galaxy Tab A7 Lite (8.7", Android 11+) **recommended**
  - This is the only tablet directly tested with SALT
  - Other tablets with 7"+ screen and Android 8.0+ may also work
- **Fingerprint Scanner**: SecuGen Hamster Pro 20 (SecuGen HU20-A)
- **USB OTG Cable**: To connect scanner to tablet (must support data transfer, not just charging)
- **Network**: WiFi connectivity for synchronization

