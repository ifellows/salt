# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SALT (System Assisted Link Tracing) is an Android survey application implementing a new sampling methodology for monitoring key populations in HIV response programs. This app is part of a comprehensive system designed to replace traditional Bio-Behavioral Surveys (BBS) using Respondent Driven Sampling (RDS) with a more efficient, continuous monitoring approach.

### SALT Methodology Context

SALT addresses key limitations of traditional BBS surveys:
- **Cost and Speed**: Traditional BBS surveys are expensive and slow to implement
- **Outdated Data**: Results are often outdated by the time they're published
- **Continuous Monitoring**: SALT enables ongoing data collection rather than periodic surveys
- **Software-Guided Sampling**: Uses technology to guide the sampling process for better consistency

The application is designed as a facility-based tablet system where program staff conduct surveys with participants using guided workflows.

## Build Commands

### Development Commands
- `./gradlew build` - Build the project
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleRelease` - Build release APK
- `./gradlew test` - Run unit tests
- `./gradlew connectedAndroidTest` - Run instrumentation tests

### Database Management
- Room database with fallback to destructive migration enabled
- Database schemas are stored in `app/schemas/com.dev.salt.data.SurveyDatabase/`
- Current database version: 37
- SQLCipher encryption with Android Keystore for secure key management

## Architecture Overview

### Three-Tier System Design

The SALT application is part of a larger three-tier architecture as illustrated in `salt_structure.png`:

1. **SALT Management Software** (Left tier)
   - Administrator interaction through Configuration & Monitoring Web UI
   - Application layer for business logic and sampling control
   - Manages recruitment pool criteria, data collection rates, and coupon distribution
   - Controls the sampling process to ensure study goals are met

2. **SALT Analytics Platform** (Right tier)
   - Policy Stakeholder interaction through Dashboard Web UI
   - Statistical Analytics layer with real-time processing
   - Displays rolling 6, 12, and 24-month estimates (prevalence, viral suppression, etc.)
   - Uses RDS estimators (Salganik-Heckathorn, Homophily Configuration Graph) transparently

3. **Facility Tablets** (Bottom tier - this application)
   - Staff and Participant interaction through Android application
   - Provides strict guard rails for survey staff, limiting training needs
   - Implements Audio-Computer Assisted Self-Interview (ACASI) functionality
   - Handles PII encryption and secure data collection at facility level

**Central Database**: All tiers connect through a central database hub that manages data flow while maintaining security protocols.

**SMS Payment Provider**: Integrated payment system for participant compensation, supporting both automatic phone payments and in-person cash payments.

### Core Components

**Database Layer (`com.dev.salt.data`)**
- `SurveyDatabase.kt` - Room database with entities: Question, Option, Survey, Answer, User, SurveyUploadState
- Uses Room with version 16, allows main thread queries, fallback to destructive migration
- Key DAOs: `SurveyDao`, `UserDao`, `UploadStateDao`
- Supports survey data persistence and participant tracking
- Enhanced User entity with biometric authentication, session management, and upload server configuration
- SurveyUploadState entity for tracking upload status with retry logic and error handling

**Authentication System**
- `LoginViewModel.kt` - Handles user authentication with role-based access and session management
- `LoginScreen.kt` - Compose UI for login with biometric authentication support
- `BiometricAuthManager.kt` - Mock biometric authentication system (hardware-ready)
- Roles: SURVEY_STAFF (facility staff), ADMINISTRATOR (management)
- Uses secure password hashing with salt via `PasswordUtils`
- Supports both password and biometric authentication methods

**Survey System**
- `SurveyViewModel.kt` - Core survey logic with skip logic using JEXL expressions
- `SurveyScreen.kt` - Main survey interface with audio playback and state tracking
- `SurveyStateManager.kt` - Tracks active survey sessions and prevents data loss
- Supports multiple question types: multiple_choice, numeric, text
- Questions can have validation scripts and pre-scripts for conditional logic
- Implements software-guided sampling as per SALT methodology
- Survey state protection prevents logout during active data collection

**Navigation**
- Uses Jetpack Navigation Compose
- Main destinations: welcome → login → menu/admin → survey
- `AppDestinations` object defines route constants
- Designed for tablet-based workflow in healthcare facilities

### Key Features

**Audio Integration**
- Audio files stored in `res/raw/` and copied to local storage
- Sequential audio playback for questions and options
- MediaPlayer integration with proper lifecycle management
- Supports accessibility and multilingual capabilities

**Skip Logic & Conditional Surveys**
- Uses Apache Commons JEXL3 for expression evaluation
- Questions can have `preScript` for conditional display
- Validation scripts for answer validation
- Context built from previous answers using question short names
- Enables complex survey flows based on participant responses

**User Management**
- `UserManagementViewModel.kt` - Complete CRUD operations for user accounts
- `UserManagementScreen.kt` - Admin interface for managing staff and admin accounts
- Role-based authentication (SURVEY_STAFF, ADMINISTRATOR)
- Sample users populated on first run: admin/123, staff/123
- Password hashing with salt for security
- Biometric enrollment and management per user
- Session timeout configuration per user
- Designed for facility-based staff workflows
- Safety checks prevent deletion of last administrator

**Security & PII Protection**
- Implements comprehensive security measures as outlined in SALT methodology
- Secure password storage with salt hashing
- Biometric authentication with SHA-256 key hashing
- Session management with automatic timeout and warnings
- Logout protection during active surveys to prevent data loss
- User activity tracking and session extension
- Data protection for personally identifiable information
- Facility-based data collection with appropriate access controls

## Recently Implemented Features

### ✅ Completed in Recent Sessions

**System Messages Support** (v36 → v37)
- **Database Changes**: Added `SystemMessage` entity for storing multilingual system messages with audio support
- **SystemMessageDao**: Full CRUD operations for system messages with language fallback support
- **Survey Sync Integration**: Downloads and stores system messages during survey synchronization
- **Staff Validation Screen**:
  - Displays custom staff validation messages from database
  - Automatic audio playback when screen loads
  - Replay button for re-listening to audio messages
  - Language fallback (en → English → any available)
  - Proper MediaPlayer resource management with DisposableEffect
- **Server Integration**: Messages downloaded as part of survey sync with text and audio in multiple languages

**Multi-Select Question Support** (v27 → v34)
- **Database Changes**: Added `minSelections` and `maxSelections` to Question entity for validation constraints
- **Answer Storage**: Enhanced Answer entity with `isMultiSelect` and `multiSelectIndices` fields for comma-separated selections
- **UI Implementation**: 
  - Checkbox-based interface for multi-select questions with reactive state management
  - Visual highlighting during audio playback (yellow background at 30% opacity)
  - Display of min/max selection requirements to users
- **Validation**: Enforces minimum and maximum selection constraints with clear error messages
- **Data Serialization**: Updated SurveySerializer to handle multi-select answers as comma-separated indices
- **Sync Support**: SurveySyncManager parses min/max selections from server JSON
- **Key Fix**: Resolved checkbox reactivity by creating new Answer objects instead of mutating in place

**Fingerprint Screening for Duplicate Enrollment Prevention** (v25 → v27)
- `FingerprintScreeningScreen.kt` - UI for fingerprint capture with visual instructions
- `FingerprintManager.kt` - Placeholder methods for SecuGen Hamster Pro 20 integration
- Database entities for storing fingerprint hashes locally (not sent to server)
- `SurveyConfig` entity for survey-level configuration from server
- Duplicate enrollment detection within configurable time window (default 90 days)
- Fingerprinting enabled/disabled based on survey configuration (not local setting)
- Re-enrollment period configured per survey from server
- Navigation flow updated to include optional fingerprint screening before language selection
- Visual diagram showing proper finger placement on scanner
- Enrollment rejection screen with days until re-enrollment allowed
- SurveySyncManager parses `survey_config` from server JSON

**Survey Completion Flow with Coupon Generation** (v24 → v25)
- Fixed coupon generation at survey completion
- Enhanced navigation to always show contact consent screen
- Proper waiting for asynchronous coupon generation
- Survey answers now saved correctly on completion
- Coupons generated based on facility configuration (default 3)
- Existing coupons loaded if survey is re-accessed

**Language Selection Feature** (v21 → v24)
- `LanguageSelectionScreen.kt` - New screen for language selection after coupon entry
- Dynamic language detection from available survey questions
- Proper survey creation flow with language selection
- Support for multiple languages with native display names
- Navigation updates to route through language selection
- SurveyViewModel enhanced to accept existing survey IDs
- Fixed multilingual question loading with proper ID mapping

**Survey Upload and Data Export System** (v15 → v16)
- `SurveyUploadManager.kt` - Core upload logic with HTTP POST and retry mechanism
- `SurveySerializer.kt` - Complete JSON serialization of survey data including device info
- `SurveyUploadWorker.kt` - WorkManager background processing for failed uploads
- `UploadStateDao` and `SurveyUploadState` entity for tracking upload attempts
- `ServerSettingsScreen.kt` - Admin interface for configuring upload servers
- `UploadStatusScreen.kt` - Dashboard for monitoring upload status and manual retries
- Enhanced User entity with server URL and API key configuration
- Automatic upload trigger on survey completion with background retry
- Exponential backoff strategy for failed uploads (up to 5 attempts)
- Network error handling and offline capability
- Survey completion navigation fix - automatic return to menu after completion

### ✅ Previously Completed

**User Management System** (v14 → v15)
- Complete admin interface for adding/removing user accounts
- Role management with safety checks (prevents deletion of last admin)
- User creation with validation and duplicate prevention
- Enhanced user entity with new fields for biometric and session management

**Biometric Authentication System**
- Mock biometric authentication ready for hardware integration
- BiometricAuthManager with enrollment, authentication, and disable functionality
- SHA-256 secure key storage and comparison
- Per-user biometric enable/disable controls in user management interface
- Biometric login option automatically shown when available for user

**Session Timeout and Management**
- SessionManager with configurable timeout periods (default 30 minutes)
- Automatic session warnings (5 minutes before expiration)
- Session extension on user activity
- Database tracking of login times and activity
- Per-user session timeout configuration
- Session state management with reactive UI updates

**Logout Functionality with Survey Protection**
- Smart logout button that hides during active surveys
- SurveyStateManager tracks survey sessions and unsaved changes
- Logout confirmation dialogs to prevent accidental logout
- Complete session and survey state cleanup
- Integrated across all main screens (Menu, Admin Dashboard, User Management)

**Enhanced Security Features**
- Activity detection with automatic session extension
- User activity tracking in database
- Session expiration handling with automatic navigation to login
- Biometric key management with secure hashing
- Session timeout dialogs with countdown timers

**Survey Data Upload System** (v15 → v16)
- Complete survey upload system with JSON serialization
- HTTP POST integration with configurable server endpoints
- Offline-first architecture with local storage and retry logic
- Admin server configuration interface with connection testing
- Upload status dashboard showing pending, completed, and failed uploads
- Background upload processing with WorkManager
- Exponential backoff retry strategy for failed uploads
- Bearer token authentication support

**Enhanced Survey Completion Flow**
- Automatic survey completion detection and navigation
- Upload trigger on survey completion with background processing
- User feedback with completion message and automatic return to menu
- Survey completion state management and cleanup

### 🔧 Key Infrastructure Updates

- **Database Schema**: Version 37 with upload state tracking, server configuration, coupon management, fingerprint storage, survey configuration, multi-select support, and system messages
- **SQLCipher Database Encryption**: Full database encryption using SQLCipher with Android Keystore for secure key management
- **Enhanced Security**: DatabaseKeyManager for secure key generation and storage using Android Keystore
- **Session Management**: Complete lifecycle management with StateFlow
- **Survey State Tracking**: Active survey detection and protection
- **Authentication Flow**: Dual password/biometric support
- **User Interface**: Material3 components with logout integration
- **Upload System**: Complete offline-first upload architecture with retry logic

## Development Notes

### Dependencies
- Jetpack Compose with Material3
- Room for database persistence
- Navigation Compose
- Apache Commons JEXL3 for expression evaluation
- Coroutines for async operations
- WorkManager for background upload processing
- JSON handling for survey serialization

### Testing
- Unit tests in `src/test/java/`
- Instrumentation tests in `src/androidTest/java/`
- Uses JUnit and Espresso

### Survey Data Model
- Questions support multiple languages (currently English)
- Answers can be multiple choice (stored as option index) or numeric/text
- Survey progress tracked by question index with skip logic evaluation
- Designed for continuous monitoring rather than one-time surveys

### Audio Files
Raw audio files are automatically copied to app's internal storage on first run for playback during surveys. This supports accessibility and ensures consistent question delivery.

### SALT-Specific Implementation Notes

**Continuous Monitoring Approach**
- Unlike traditional RDS surveys, SALT enables ongoing data collection
- Survey data is designed to be analyzed using existing RDS estimators with time windows
- System supports recruitment pool management for continuous sampling

**Facility-Based Workflow**
- Application designed for use in healthcare facilities
- Staff-guided process with tablet-based interface
- Supports both staff and participant interaction modes

**Integration with Broader SALT System**
- This tablet application represents the "Facility Tablet" component in the three-tier SALT architecture
- Designed to integrate with management software and analytics platform through central database
- Data collection structured to support policy stakeholder analysis via statistical analytics layer

**System Architecture Context**
- The Android app is positioned as the facility-based data collection tool within the broader SALT methodology framework
- Role-based authentication supports both Staff and Participant interaction modes as shown in system structure
- Security measures for PII protection align with the document's emphasis on encrypted storage and tablet-based decryption
- Audio support (ACASI) enables self-administered surveys with behavioral counseling capabilities
- The app provides "strict guard rails" for survey staff, reducing training requirements and workload

## Next Priority Features

Based on current architecture and SALT methodology requirements:

### ✅ **Recently Completed: Survey Upload System**
- JSON survey export with comprehensive data serialization
- HTTP upload to configurable server endpoints with authentication
- Offline capability with retry logic and background processing
- Admin interface for server configuration and upload monitoring
- Database integration for upload state tracking
- Essential foundation for three-tier architecture data flow

### 🔄 **Recommended Next: Enhanced Analytics and Monitoring**
- Real-time upload statistics and monitoring dashboard
- Survey completion analytics and reporting
- Data quality checks and validation reporting
- Enhanced retry strategies and upload optimization
- Integration preparation for SALT Management Software

### 📋 **Other High-Priority Features**
- **Survey Form Validation Enhancement**: Improved JEXL validation with better error handling
- **Offline Capability**: Enhanced offline survey completion and sync status
- **Advanced User Management**: Audit trails, password policies, session history
- **Survey Analytics Dashboard**: Real-time completion statistics and data visualization

## Target Hardware Configuration

### Recommended Hardware Setup (per facility station)
- **Tablet**: Samsung Galaxy Tab A7 Lite (8.7" display, Android 11+)
- **Fingerprint Scanner**: SecuGen Hamster Pro 20 (FBI certified, USB)
- **Required Adapter**: USB-C to USB-A OTG adapter (for connecting scanner to tablet)
- **Optional**: Protective tablet case for healthcare environment

### Hardware Requirements
- Android tablet with USB Host Mode (OTG) support
- Minimum Android 8.0 (API 26) or higher
- USB-C or Micro-USB port with OTG capability
- Sufficient storage for survey data and audio files

### Fingerprint Integration
- Current implementation uses mock BiometricAuthManager
- Ready for SecuGen SDK integration when hardware is deployed
- Supports enrollment, authentication, and secure key storage

## Missing System Components (for full SALT implementation)
- Management software for study administrators (Configuration & Monitoring UI)
- Analytics platform for policy stakeholders (Dashboard with real-time statistical analysis)
- Central database infrastructure with proper security protocols
- SMS payment integration for participant compensation
- Coupon printing and management system
- Recruitment pool management functionality