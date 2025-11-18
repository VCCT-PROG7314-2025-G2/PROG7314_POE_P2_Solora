# Solora

<p align="center">
  <img src="READMEAsset/SoloraLogo.jpg" alt="App Logo" width="300">
</p>

> Solora is a Kotlin-first Android platform that helps solar sales consultants size systems, manage leads, stay motivated, and deliver branded PDF proposals—even when they are fully offline.

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Badge">
  <img src="https://img.shields.io/badge/Android-API%2035-3DDC84?logo=android&logoColor=white" alt="Android Badge">
  <img src="https://img.shields.io/badge/Firebase-Firestore%20%7C%20Auth%20%7C%20Functions-FFCA28?logo=firebase&logoColor=black" alt="Firebase Badge">
  <img src="https://img.shields.io/badge/Node.js-22-43853D?logo=node.js&logoColor=white" alt="Node Badge">
  <img src="https://img.shields.io/badge/Room-Offline%20Cache-5C6BC0?logo=sqlite&logoColor=white" alt="Room Badge">
  <img src="https://img.shields.io/badge/FCM-Motivational%20Pushes-4285F4?logo=firebase&logoColor=white" alt="FCM Badge">
</p>

### Quick Links
- 🎬 [YouTube demo](https://youtu.be/wNVhsIj4Qn4?si=T80afQpQ41UrxHtV)
- ☁️ [`functions/`](functions/) – Firebase Cloud Functions source
- 📱 [`app/`](app/) – Android client (Kotlin, MVVM-ish)
- 🖼️ [`READMEAsset/`](READMEAsset/) – Logos, UI mockups, and database captures
- 📋 [Project repository](https://github.com/VCCT-PROG7314-2025-G2/PROG7314_POE_P2_Solora)

## Platform Snapshot
- Offline-first Android app with Room cache, Firestore sync, and NASA POWER-assisted quote calculations.
- Multi-language UI (English, Afrikaans, isiXhosa) plus onboarding, custom fonts (Poppins), and a guided notifications screen.
- Biometric login, “stay signed in” preferences, and motivational push notifications to keep consultants engaged.
- Branded PDF generation (iText 7) with HTML templates, company profile injection, and secure sharing via `FileProvider`.
- Firebase Auth + Google Sign-In + callable Functions powering a secure solar sales workflow end-to-end.

## Table of Contents
- [Features](#features)
  - [Sales & Quoting](#sales--quoting)
  - [Lead CRM & Pipeline](#lead-crm--pipeline)
  - [Authentication & Onboarding](#authentication--onboarding)
  - [Engagement & Notifications](#engagement--notifications)
  - [Offline & Sync](#offline--sync)
  - [PDF & Sharing](#pdf--sharing)
  - [Dashboard & Analytics](#dashboard--analytics)
  - [Localization & Accessibility](#localization--accessibility)
- [Feature Gallery](#feature-gallery)
- [Architecture Overview](#architecture-overview)
- [Offline Data Flow](#offline-data-flow)
- [Notifications & Motivation Engine](#notifications--motivation-engine)
- [Security Highlights](#security-highlights)
- [PDF Generation & Branding](#pdf-generation--branding)
- [Localization & UX Enhancements](#localization--ux-enhancements)
- [Tech Stack and Libraries](#tech-stack-and-libraries)
- [Project Modules](#project-modules)
- [Data Models (Firestore)](#data-models-firestore)
- [App Navigation and Screens](#app-navigation-and-screens)
- [Permissions](#permissions)
- [Setup and Installation](#setup-and-installation)
  - [Prerequisites](#prerequisites)
  - [Android App Setup and Run](#android-app-setup-and-run)
  - [Firebase Project Setup](#firebase-project-setup)
  - [Cloud Functions: Develop, Emulate, Deploy](#cloud-functions-develop-emulate-deploy)
  - [Testing and CI/CD](#testing-and-cicd)
- [Cloud Functions API (Callable Endpoints)](#cloud-functions-api-callable-endpoints)
- [Troubleshooting](#troubleshooting)
- [Project Structure (high-level)](#project-structure-high-level)
- [Comprehensive Project Report](#comprehensive-project-report)
  - [Purpose of the Application](#purpose-of-the-application)
  - [Design Considerations](#design-considerations)
  - [GitHub and GitHub Actions Utilization](#github-and-github-actions-utilization)
  - [Engagement & Adoption Strategy](#engagement--adoption-strategy)
- [References](#references)
- [License](#license)

## Features

### Sales & Quoting
- Guided quote creation wizard with client info, tariff presets, panel sizing, and reference tracking.
- NASA POWER-assisted irradiance lookup via callable `calculateQuote` function when GPS coordinates are available.
- Embedded validation, smart defaults (panel wattage, tariff, sun-hours), and quote history with search + pagination.

### Lead CRM & Pipeline
- Full CRUD for leads tied to the authenticated consultant with status filtering and free-text search.
- Lead detail dialog surfaces the latest quote attachments, notes, and quick actions.
- Dashboard shortcuts show lead totals, growth, and quick filters to keep the pipeline healthy.

### Authentication & Onboarding
- Email/password, Google Sign-In, and “stay logged in” preferences managed by `AuthRepository`.
- First-run onboarding screens explain the workflow; `AuthViewModel` tracks completion via DataStore.
- Optional biometric login (fingerprint/face) with encrypted tokens (`CryptographyManager`, `BiometricPromptUtils`).

### Engagement & Notifications
- Dedicated `NotificationsFragment` with runtime permission prompts for Android 13+.
- `MotivationalNotificationManager` syncs preferences to Firestore, stores them locally via DataStore, tracks milestones, and queues encouragement messages when users hit quote/lead goals.
- Firebase Cloud Messaging service (`SoloraFirebaseMessagingService`) handles remote pushes for reminders and motivational nudges.

### Offline & Sync
- True offline-first strategy: Room database (`LocalDatabase`, `LocalDao`, `LocalEntities`) mirrors quotes and leads per user.
- `OfflineRepository` records offline work; `SyncManager` pushes unsynced items once `NetworkMonitor` reports connectivity.
- Cloud `syncData` callable merges offline payloads with authoritative Firestore collections when needed.

### PDF & Sharing
- HTML-to-PDF generation (`PdfGenerator`, `PdfExporter`) builds branded proposals with company details from `CompanySettings`.
- Secure storage beneath `Documents/Quotes` and Android `FileProvider`-backed sharing via `FileShareUtils`.
- PDF preview flows with zooming, share sheet, and multi-language labels.

### Dashboard & Analytics
- `DashboardViewModel` aggregates KPIs (avg system size, revenue, payback) with time filters (7d/30d/6m).
- Custom `CircleChartView` in `ui/views` visualizes quote status distribution.
- Monthly performance cards highlight growth while offline caches keep widgets responsive.

### Localization & Accessibility
- Complete copy decks for English (default), Afrikaans (`values-af`), and isiXhosa (`values-xh`).
- Material 3 theming with proper contrast, scalable typography (Poppins family), landscape-specific layouts, and custom toasts/snackbars for accessibility feedback.

## Feature Gallery
<p align="center">
  <img src="READMEAsset/AppPic3.jpg" alt="Quotes" width="197">
  <img src="READMEAsset/AppPic4.jpg" alt="Quote Calculator" width="197">
  <img src="READMEAsset/AppPic5.jpg" alt="Quote Summary" width="197">
  <img src="READMEAsset/AppPic6.jpg" alt="Quote List" width="197">
  <img src="READMEAsset/AppPic7.jpg" alt="Quote Detail" width="197">
</p>

<p align="center">
  <img src="READMEAsset/AppPic9.jpg" alt="Leads" width="197">
  <img src="READMEAsset/AppPic10.jpg" alt="Lead Detail" width="197">
  <img src="READMEAsset/AppPic11.jpg" alt="Lead Filters" width="197">
</p>

<p align="center">
  <img src="READMEAsset/AppPic1.jpg" alt="Profile" width="197">
  <img src="READMEAsset/AppPic2.jpg" alt="Settings" width="197">
  <img src="READMEAsset/AppPic13.jpg" alt="Notifications" width="197">
  <img src="READMEAsset/AppPic14.jpg" alt="Biometric" width="197">
</p>

<p align="center">
  <img src="READMEAsset/AppPic8.jpg" alt="PDF Preview" width="197">
  <img src="READMEAsset/AppPic12.jpg" alt="PDF Export" width="197">
</p>

## Architecture Overview
- **Android app (Kotlin, MVVM-ish)**
  - Feature-based packages (`quotes`, `leads`, `dashboard`, `notifications`, `settings`, `pdf`, etc.).
  - `FirebaseRepository` handles Firestore reads/listeners + callable Functions, while `OfflineRepository`/`SyncManager` manage Room caching.
  - `AuthRepository` owns Firebase Auth, Google Sign-In, DataStore prefs (onboarding, biometrics, stay logged in), and encrypted tokens.
  - Navigation Component wires onboarding → auth → main tabs; `MainTabsFragment` hosts Home/Quotes/Leads/Profile flows.
- **Backend (Firebase Cloud Functions)**
  - Node.js 22 runtime with callable endpoints for quote math, leads/quotes CRUD, settings, sync, plus `healthCheck`.
  - NASA POWER API invoked server-side to avoid leaking API keys and to keep calculations deterministic.
  - FCM tokens stored alongside user documents to enable motivational pushes driven by backend rules or campaign scripts.

## Offline Data Flow
1. Room DB (`LocalDatabase`) stores per-user `LocalQuote`/`LocalLead` snapshots and an `isSynced` flag.
2. `OfflineRepository` exposes suspend functions for inserting, updating, deleting, and querying unsynced entities.
3. `NetworkMonitor` streams connectivity events; when a connection is restored, `SyncManager.syncAll()` uploads pending changes and marks them synced.
4. Initial data hydration uses `SyncManager.downloadFromFirebase()` to mirror server state into Room for instant UI rendering.
5. A callable `syncData` endpoint is available for explicit multi-collection merges during long offline sessions.

## Notifications & Motivation Engine
- `MotivationalNotificationManager` (Kotlin) stores user preferences in DataStore, mirrors them to `user_settings`, keeps track of milestone checkpoints, and fetches quote/lead counts to decide when to congratulate users.
- When toggled on, the manager retrieves an FCM token (`FirebaseMessaging`) and saves it to the `users` collection to enable remote pushes.
- `NotificationsFragment` handles runtime permission prompts (Android 13+), exposes test notification actions, and offers a back-to-home CTA so users never get stuck.
- `SoloraFirebaseMessagingService` receives remote push payloads, builds NotificationCompat cards, and launches `MainActivity` with proper back stack semantics.

## Security Highlights
- Firebase Auth with custom repositories for Google Sign-In, password resets, and session persistence.
- Android BiometricPrompt with AES encryption (`CryptographyManager`, `BiometricPromptUtils`) securely stores tokens in SharedPreferences via cipher wrappers.
- DataStore Preferences keep onboarding, notification, and stay-logged-in flags off the network until explicitly synced.
- `FileProvider` (configured in `xml/file_paths.xml`) guards PDF exports; only scoped directories are shared.
- Cloud Functions validate `context.auth` before any CRUD calls and double-check ownership (quotes/leads filtered by `userId`).

## PDF Generation & Branding
- `PdfGenerator` composes responsive HTML with company metadata, conversion metrics, VAT breakdowns, and 30-day validity statements.
- `CompanySettings` (within Settings tab) lets consultants define logos, contact info, websites, and optional legal footers that flow directly into the PDF template.
- `PdfExporter` + `FileShareUtils` give users preview, save, and share flows backed by scoped storage and share intents.

## Localization & UX Enhancements
- Trilingual copy resources (English, Afrikaans, isiXhosa) cover navigation, onboarding, analytics, PDF strings, and biometric prompts.
- Landscape-specific layouts (`layout-land/`) keep dashboards usable on tablets.
- Poppins font stack + Material typography scales across devices; contrast-friendly charts and toasts improve readability.
- Custom `ToastUtils` centralizes messaging; `home_header.xml` and other includes keep layouts consistent.

## Tech Stack and Libraries

### Android
- Kotlin 2.0.20, AGP 8.5.2, JVM target 17, compile/target SDK 35, min SDK 24
- Room (`androidx.room:room-runtime-ktx:2.6.1`) for offline caching
- Firebase
  - BOM `com.google.firebase:firebase-bom:33.3.0`
  - Auth (`firebase-auth-ktx`)
  - Firestore (`firebase-firestore-ktx`)
  - Analytics (`firebase-analytics-ktx`)
  - Callable Functions (`firebase-functions-ktx:20.4.0`)
  - Cloud Messaging (`firebase-messaging-ktx`)
- Google Sign-In: `com.google.android.gms:play-services-auth:20.7.0`
- AndroidX
  - Core KTX, Fragment KTX
  - Navigation: `androidx.navigation:navigation-fragment-ktx` / `navigation-ui-ktx` (2.8.0)
  - Lifecycle: ViewModel/Livedata/Runtime KTX (2.8.7)
  - DataStore Preferences: `androidx.datastore:datastore-preferences{,-core}:1.1.1`
  - Room / SQLite integration for offline data
  - Biometric: `androidx.biometric:biometric`
- Networking: Ktor Client (2.3.12) [core, android, logging, content-negotiation, kotlinx-json]
- Permissions helper: `com.google.accompanist:accompanist-permissions:0.36.0`
- PDF: iText 7 (note: both `8.0.5` and `7.2.5` are present)
- Material Components: `com.google.android.material:material:1.12.0`

### Cloud Functions
- Node.js 22 (see `functions/package.json`)
- `firebase-functions@^6`, `firebase-admin@^12`, `axios@^1.12`

## Project Modules
| Path | Purpose |
| --- | --- |
| `dev/solora/api` | `FirebaseFunctionsApi` wrapper around callable endpoints |
| `dev/solora/auth` | Auth flows, onboarding state, biometrics (`AuthRepository`, `AuthViewModel`) |
| `dev/solora/data` | Firestore/Room models, repositories, DAOs, sync + network monitoring |
| `dev/solora/dashboard` | KPIs, chart data, and dashboard ViewModel |
| `dev/solora/leads`, `dev/solora/quotes`, `dev/solora/quote` | Lead + quote ViewModels, calculators, UI glue |
| `dev/solora/navigation` | Fragment screens (home, onboarding, notifications, etc.) and NavGraph hosts |
| `dev/solora/notifications` | Motivational notification manager + FCM service |
| `dev/solora/pdf` | HTML template builder, exporter, and share utilities |
| `dev/solora/profile`, `dev/solora/settings` | Company/User settings, profile editing, DataStore |
| `dev/solora/ui/views` | Custom UI components (`CircleChartView`) |
| `functions/` | Node.js Cloud Functions for calculations, persistence, sync |

## Cloud Functions API (Callable Endpoints)
All callable endpoints require the user to be authenticated.

- `calculateQuote(data)`
  - Input: `{ address, usageKwh?, billRands?, tariff, panelWatt, latitude?, longitude? }`
  - Output: `{ success, calculation: { panels, systemKwp, inverterKw, monthlySavings, estimatedGeneration, paybackMonths, nasaData? } }`
  - Behavior: Optionally calls NASA POWER API to compute average sun-hours.

- `saveQuote(data)`
  - Input: quote fields (reference, client details, calculations...). Server enriches with company info from `user_settings` and timestamps.
  - Output: `{ success, quoteId }`

- `getQuoteById({ quoteId })`
  - Output: `{ success, quote? }` (only if owned by current user)

- `getQuotes({ search?, limit?=50 })`
  - Output: `{ success, quotes }` filtered to current user; simple search over `reference`, `clientName`, `address`.

- `saveLead(data)`
  - Input: `{ name, email, phone, status?, notes?, quoteId? }`
  - Output: `{ success, leadId }`

- `getLeads({ search?, status?, limit?=50 })`
  - Output: `{ success, leads }` filtered to current user; optional status filter and client-side search.

- `getSettings()` / `updateSettings({ settings })`
  - Output: `{ success, settings }` / `{ success, message }`

- `syncData({ offlineData })`
  - Merges local leads/quotes/settings into cloud; returns per-collection counts and errors.

- `healthCheck` (HTTP)
  - GET returns `{ status, timestamp, service, version, endpoints[] }`

Android app integrates these via `dev.solora.api.FirebaseFunctionsApi` and `dev.solora.data.FirebaseRepository` (which also provides Firestore fallbacks and real-time listeners).

## Data Models (Firestore)
- `FirebaseQuote`: id, reference, clientName, address, usageKwh, billRands, tariff, panelWatt, lat/lon, irradiance/sun-hours, calculation outputs, company/consultant snapshot, `userId`, `createdAt`, `updatedAt`.
- `FirebaseLead`: id, name, email, phone, status, notes, `quoteId`, `userId`, timestamps.
- `FirebaseUser`: id, name, surname, email, phone?, company?, role, timestamps.

## App Navigation and Screens
- Onboarding carousel (`fragment_onboarding`) stores completion state via `AuthViewModel`.
- Auth stack includes login/register, Google Sign-In, password recovery, and biometric opt-in dialogs.
- `MainTabsFragment` hosts Home, Quotes, Leads, and Profile tabs with BottomNavigation.
- `HomeFragment` surfaces KPIs, growth charts, quick create buttons, and deep links into quotes/leads.
- Quotes + Leads flows provide list/detail fragments, PDF preview dialogs, status pickers, and share options.
- `NotificationsFragment` centralizes motivational toggle, permission prompts, and “test notification” CTA.
- Profile/Settings manage localization, company branding, PDF metadata, and authentication options.

## Permissions
Declared in `AndroidManifest.xml`:
- `INTERNET` (network, Firebase, NASA API)
- `POST_NOTIFICATIONS` (runtime requested for Android 13+)
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (PDF export on older Androids)

`FileProvider` is configured via `xml/file_paths.xml` for secure sharing of generated files (e.g., PDFs).

## Setup and Installation

### Prerequisites
- **Android Studio** (Hedgehog / Jellyfish or newer)
- **Java 17** (bundled with recent Android Studio)
- **Android SDK 35** (compile and target SDK)
- **Gradle 8.9** (included in project)
- **Node.js 22** (for Cloud Functions)
- **Firebase CLI** (latest version)
- A Firebase project with:
  - Email/Password Auth enabled
  - Google Sign-In enabled (OAuth client configured)
  - Firestore in Native mode
  - Firebase Functions enabled
  - Firebase Cloud Messaging enabled (for motivational pushes)

### Android App Setup and Run

1) **Clone the repository:**
   ```bash
   git clone https://github.com/ST10359034/PROG7314_POE_P2_Solora.git
   cd PROG7314_POE_P2_Solora
   ```

2) **Open in Android Studio:**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository folder
   - Click "OK"

3) **Configure Firebase:**
   - Ensure `google-services.json` is present at `app/google-services.json`
   - If using your own Firebase project, download a new `google-services.json` from the Firebase Console and replace the existing file

4) **Sync Gradle:**
   - Click "Sync Now" when prompted, or
   - Go to File → Sync Project with Gradle Files
   - The app targets SDK 35 and uses Kotlin 2.0.20

5) **Run the application:**
   - **Option A (Android Studio):** Click the Run button (green play icon) or press Shift+F10
   - **Option B (Command Line):**
     ```bash
     ./gradlew :app:assembleDebug
     # Then install the generated APK from app/build/outputs/apk/debug/
     ```

6) **Sign in to the app:**
   - Use Email/Password authentication, or
   - Use Google Sign-In (if configured in your Firebase project)

**Note:** Release signing is configured in `app/build.gradle.kts` with a keystore at `app/keystore/solora-release-key.keystore`. Adjust or remove this configuration if you use your own signing setup.

### Firebase Project Setup
1) In Firebase Console, create a project (or reuse an existing one) and add an Android app with the correct `applicationId` (`dev.solora` by default).
2) Enable Auth providers (Email/Password, Google) in Authentication settings.
3) Enable Firestore (Native mode). For production queries with compound ordering/filters, you may need indexes. The app logs when an index is required; create the suggested index in the Firebase Console if you see those logs.
4) Download and place `google-services.json` into `app/`.
5) Enable Firebase Cloud Messaging and download the V1 server key if you plan to send pushes. The app automatically registers tokens via `MotivationalNotificationManager`.
6) (Optional) Configure `firebase use --add` locally so `npm run deploy` deploys to the correct project.
<p align="center">
  <img src="READMEAsset/DatabasePic4.png" alt="App Database" width="300">
  <img src="READMEAsset/DatabasePic5.png" alt="App Database" width="300">
  <img src="READMEAsset/DatabasePic6.png" alt="App Database" width="300">
  <img src="READMEAsset/DatabasePic7.png" alt="App Database" width="300">
  <img src="READMEAsset/DatabasePic8.png" alt="App Database" width="300">
  <img src="READMEAsset/DatabasePic9.png" alt="App Database" width="300">
</p>

### Cloud Functions: Develop, Emulate, Deploy
From the repo root:

Local develop with emulators:
```bash
cd functions
npm ci
npm run serve  # runs Firebase emulators for functions
```

Deploy to Firebase:
```bash
cd functions
npm ci
npm run deploy
```

Other scripts:
```bash
npm run logs   # tail function logs
npm run shell  # interactive function shell
```

Functions runtime: Node 22 (see `functions/package.json`). Ensure your Firebase CLI is up to date and that your project is selected (`firebase use`).

### Testing and CI/CD

The project includes automated testing and continuous integration:

**Unit Tests**
- Quote calculation tests (`QuoteCalculatorTest.kt`)
- Data model validation tests (`FirebaseModelsTest.kt`)
- Auth flows (`AuthViewModelTest.kt`), Dashboard aggregations (`DashboardDataTest.kt`)
- PDF template regression tests (`PdfGeneratorTest.kt`)
- Quotes list/VM behavior (`QuotesViewModelTest.kt`)
- Run tests: `./gradlew test`

**GitHub Actions CI/CD**
- Automated build and testing on pull requests
- APK generation and artifact upload
- Firebase deployment automation
- See `.github/workflows/android-ci.yml` for configuration

**Linting and Code Quality**
- Android Lint checks for code quality
- ESLint for Cloud Functions
- Automated code formatting

## Troubleshooting
- **Auth errors calling Functions**: Ensure you’re signed in before invoking callable functions. All callable endpoints require authentication.
- **Missing Firestore index**: Logs may show an index requirement for queries on `quotes` or `leads` (ordered by `createdAt` filtered by `userId`). Create the suggested index in the Firebase Console under Firestore Indexes.
- **Google Sign-In configuration**: Make sure your SHA-1/256 fingerprints and OAuth client are configured in Firebase Console for the app package `dev.solora`.
- **Biometric enrollment issues**: Android requires at least one fingerprint/face profile before enabling biometric login. Surface-level errors come from `AuthViewModel` → `BiometricState`.
- **Notifications on Android 13+**: Users must grant `POST_NOTIFICATIONS`. Guide them through `NotificationsFragment`; fallback messaging is shown if permission is denied.
- **Motivational pushes not firing**: Confirm `MotivationalNotificationManager` saved `notificationsEnabled` in `user_settings` and that FCM tokens exist in `users` collection.
- **Duplicate iText versions**: Dependencies include `com.itextpdf:itext7-core:8.0.5` and `7.2.5`. Prefer a single aligned version to avoid conflicts.
- **Old storage permissions**: On Android 10+, consider using `MediaStore`/`scoped storage`; WRITE permission may not be necessary depending on your PDF flow.
- **NASA API throttling**: Quote calculations gracefully degrade to default sun-hours if NASA API limits are reached; inform users if lat/lon accuracy matters.

## Project Structure (high-level)
```
app/
  src/main/java/dev/solora/
    api/               # FirebaseFunctionsApi wrapper
    auth/              # AuthRepository and auth flows
    data/              # FirebaseRepository, models
    dashboard/         # Dashboard data and ViewModel
    leads/             # Leads ViewModel
    navigation/        # Fragments and navigation glue
    notifications/     # MotivationalNotificationManager + FCM service
    pdf/               # PDF generation and file sharing
    profile/           # Profile ViewModel
    quote/             # Quote calculation and NASA API
    quotes/            # Quotes ViewModel
    settings/          # Settings repository and ViewModel
    ui/views/          # Custom UI components (CircleChartView)
    utils/             # Utility classes (ToastUtils)
    SoloraApp.kt       # App initialization (Firebase, Firestore offline)
    MainActivity.kt    # Activity host
  src/main/res/        # layouts, drawables, nav graphs, values
functions/             # Firebase Cloud Functions (Node.js)
```

## Comprehensive Project Report

### Purpose of the Application

**Solora** is a comprehensive Android application designed specifically for solar sales consultants to streamline their workflow and enhance their sales process. The application serves as a complete business solution that addresses the critical needs of solar energy professionals in the South African market.

**Primary Objectives:**
- **Quote Generation**: Provide accurate solar system calculations based on client requirements, location-specific solar irradiance data, and current electricity tariffs
- **Lead Management**: Enable consultants to efficiently track, manage, and follow up with potential clients throughout the sales pipeline
- **Professional Documentation**: Generate high-quality PDF quotes that can be shared with clients, containing detailed system specifications and cost breakdowns
- **Data Analytics**: Offer insights into sales performance through dashboard analytics and quote statistics
- **Offline Capability**: Ensure consultants can work effectively even in areas with poor internet connectivity
- **Consultant Motivation**: Deliver milestone-based motivational notifications (local + push) to keep sales teams engaged
- **Localization**: Provide an inclusive experience with multilingual UI support (English, Afrikaans, isiXhosa)

**Target Users:**
- Solar sales consultants and installers
- Solar energy companies and their sales teams
- Independent solar energy advisors
- Renewable energy professionals

### Design Considerations

#### **1. User Experience (UX) Design**
- **Intuitive Navigation**: Implemented bottom navigation with clear icons and labels for easy access to core features
- **Material Design**: Adopted Google's Material Design principles for consistent, modern UI components
- **Responsive Layout**: Designed to work seamlessly across different Android device sizes and orientations
- **Accessibility**: Ensured proper contrast ratios, touch targets, and screen reader compatibility

#### **2. Architecture Design**
- **MVVM Pattern**: Implemented Model-View-ViewModel architecture for clean separation of concerns
- **Repository Pattern**: Centralized data access through `FirebaseRepository` with API-first approach and Firestore fallback
- **Dependency Injection**: Used Android's built-in dependency management for loose coupling
- **Offline-First**: Designed with offline capabilities using Firestore's offline persistence
- **Engagement Layer**: Dedicated notifications stack (DataStore + Firestore + FCM) that syncs user preferences and milestone history
- **Localization Strategy**: Parallel resource sets (`values`, `values-af`, `values-xh`) keep UI inclusive without branching logic

#### **3. Performance Considerations**
- **Lazy Loading**: Implemented efficient data loading with pagination and lazy initialization
- **Caching Strategy**: Used Firebase's built-in caching and local DataStore for user preferences
- **Background Processing**: Leveraged WorkManager for data synchronization tasks
- **Memory Management**: Proper lifecycle management and memory leak prevention

#### **4. Security Design**
- **Authentication**: Multi-factor authentication with Firebase Auth (Email/Password + Google SSO)
- **Data Protection**: User data isolation with proper Firebase Security Rules
- **API Security**: All Cloud Functions require authentication and implement proper authorization
- **Local Storage**: Secure storage of sensitive data using Android's encrypted preferences
- **Biometric Encryption**: AES-backed biometric login ensures tokens never leave the device unencrypted

#### **5. Scalability Considerations**
- **Cloud Functions**: Serverless backend that automatically scales with demand
- **Firestore**: NoSQL database that scales horizontally
- **Modular Architecture**: Feature-based package structure for easy maintenance and expansion
- **API Design**: RESTful API design with proper versioning and error handling

### Engagement & Adoption Strategy
- **Motivational Milestones**: Local + remote notifications celebrate quote/lead milestones and prompt consultants to continue engaging with the app.
- **Notifications Center**: In-app screen explains benefits, requests runtime permissions, and allows quick opt-in/out backed by Firestore preferences.
- **Localization & Onboarding**: Multilingual resources and a guided onboarding carousel help new consultants ramp up quickly.
- **Biometric Convenience**: Optional biometric unlock removes friction while maintaining security, encouraging habitual daily logins.

### GitHub and GitHub Actions Utilization

#### **1. Version Control Strategy**
- **Branch Management**: Implemented feature branch workflow with main branch protection
- **Commit Standards**: Used conventional commit messages for clear change tracking
- **Code Review Process**: Required pull request reviews before merging to main branch
- **Release Management**: Tagged releases for version tracking and deployment

#### **2. GitHub Actions CI/CD Pipeline**

**Automated Build Process:**
```yaml
# .github/workflows/android-ci.yml
name: Android CI/CD
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
```

**Key Workflow Components:**
- **Environment Setup**: Automated Android SDK, Java 17, and Node.js 22 setup
- **Dependency Management**: Automatic Gradle dependency resolution and npm package installation
- **Code Quality Checks**: Automated linting, formatting, and static analysis
- **Testing**: Unit test execution with coverage reporting
- **Build Verification**: APK generation and artifact upload
- **Security Scanning**: Dependency vulnerability checks

**Benefits of GitHub Actions:**
- **Continuous Integration**: Every commit triggers automated testing and validation
- **Early Error Detection**: Issues are caught before they reach production
- **Consistent Builds**: Standardized build environment across all developers
- **Automated Deployment**: Streamlined deployment process to Firebase
- **Quality Assurance**: Automated code quality checks and security scanning

#### **3. Project Management Integration**
- **Issue Tracking**: Used GitHub Issues for bug reports and feature requests
- **Project Boards**: Organized development tasks using GitHub Projects
- **Milestone Tracking**: Set up milestones for major releases and feature completion
- **Documentation**: Maintained comprehensive documentation in the repository

#### **4. Collaboration Features**
- **Pull Request Reviews**: Mandatory code reviews for all changes
- **Discussion Forums**: Used GitHub Discussions for technical discussions
- **Wiki Documentation**: Maintained project wiki for detailed technical documentation
- **Release Notes**: Automated release note generation from commit messages

#### **5. Security and Compliance**
- **Secret Management**: Used GitHub Secrets for sensitive configuration data
- **Branch Protection**: Enforced branch protection rules for main branch
- **Dependency Updates**: Automated dependency update notifications
- **Security Alerts**: Integrated Dependabot for security vulnerability monitoring

### Technical Implementation Highlights

#### **1. Modern Android Development**
- **Kotlin 2.0.20**: Latest Kotlin features for improved developer experience
- **Android SDK 35**: Latest Android APIs and features
- **Jetpack Components**: ViewModel, LiveData, Navigation, and DataStore
- **Material Design 3**: Latest Material Design components and theming

#### **2. Cloud Integration**
- **Firebase Ecosystem**: Comprehensive use of Firebase services
- **REST API Design**: Well-structured API endpoints with proper error handling
- **Real-time Data**: Live updates using Firestore listeners
- **Offline Synchronization**: Automatic data sync when connectivity is restored

#### **3. Testing Strategy**
- **Unit Testing**: Comprehensive test coverage for business logic
- **Integration Testing**: API and database integration tests
- **UI Testing**: Automated UI testing with Espresso
- **Performance Testing**: Load testing for Cloud Functions

This comprehensive approach ensures that Solora is not just a functional application, but a robust, scalable, and maintainable solution that follows industry best practices and modern development standards.

## References

### AI Tools and Development Assistance
- **Claude AI** - Used for debugging, code suggestions, and development guidance throughout the project
- **ChatGPT** - Used for debugging, code suggestions, and development guidance throughout the project
- **Cursor IDE** - AI-powered code completion and suggestions during development
- **Quillbot AI** - AI-powered Grammar checker and paraphrasing tool
-

### Android Development
- **Android Developer Documentation** - https://developer.android.com/
- **Kotlin Programming Language** - https://kotlinlang.org/
- **Android Jetpack Components** - https://developer.android.com/jetpack
- **Material Design Guidelines** - https://material.io/design
- **Android Navigation Component** - https://developer.android.com/guide/navigation
- **Android ViewModel and LiveData** - https://developer.android.com/topic/libraries/architecture/viewmodel
- **Kotlin Coroutines** - https://kotlinlang.org/docs/coroutines-overview.html
- **Android DataStore** - https://developer.android.com/topic/libraries/architecture/datastore
- **Android Room Database** - https://developer.android.com/training/data-storage/room
- **Android Biometric Authentication** - https://developer.android.com/training/sign-in/biometric-auth
- **Android BiometricPrompt API** - https://developer.android.com/reference/androidx/biometric/BiometricPrompt
- **Android Keystore System** - https://developer.android.com/training/articles/keystore
- **Android Notifications** - https://developer.android.com/develop/ui/views/notifications
- **Android Runtime Permissions** - https://developer.android.com/training/permissions/requesting
- **Android ConnectivityManager** - https://developer.android.com/reference/android/net/ConnectivityManager
- **Android WorkManager** - https://developer.android.com/topic/libraries/architecture/workmanager
- **Android Localization** - https://developer.android.com/guide/topics/resources/localization

### Firebase and Backend Services
- **Firebase Documentation** - https://firebase.google.com/docs
- **Firebase Authentication** - https://firebase.google.com/docs/auth
- **Cloud Firestore** - https://firebase.google.com/docs/firestore
- **Firebase Cloud Functions** - https://firebase.google.com/docs/functions
- **Firebase Analytics** - https://firebase.google.com/docs/analytics
- **Firebase Cloud Messaging** - https://firebase.google.com/docs/cloud-messaging
- **Google Sign-In for Android** - https://developers.google.com/identity/sign-in/android

### Third-Party Libraries
- **iText PDF Library** - https://itextpdf.com/
- **Ktor HTTP Client** - https://ktor.io/docs/http-client.html
- **Material Components for Android** - https://github.com/material-components/material-components-android
- **Accompanist Permissions** - https://google.github.io/accompanist/permissions/
- **Gson JSON Library** - https://github.com/google/gson
- **AndroidX Room Persistence Library** - https://developer.android.com/jetpack/androidx/releases/room
- **AndroidX Biometric Library** - https://developer.android.com/jetpack/androidx/releases/biometric

### External APIs
- **NASA POWER API** - https://power.larc.nasa.gov/
- **Google Geocoding API** - https://developers.google.com/maps/documentation/geocoding

### Security and Cryptography
- **Android Keystore System** - https://source.android.com/docs/security/features/keystore
- **AES Encryption in Android** - https://developer.android.com/privacy-and-security/cryptography
- **Android Security Best Practices** - https://developer.android.com/privacy-and-security/security-tips
- **Cipher Class Documentation** - https://developer.android.com/reference/javax/crypto/Cipher
- **KeyGenerator Documentation** - https://developer.android.com/reference/javax/crypto/KeyGenerator

### Offline Data and Synchronization
- **Room Database Guide** - https://developer.android.com/training/data-storage/room
- **Room with Coroutines** - https://developer.android.com/training/data-storage/room/async-queries
- **Offline Data Sync Patterns** - https://developer.android.com/topic/architecture/data-layer/offline-first
- **Network Monitoring** - https://developer.android.com/training/monitoring-device-state/connectivity-status-type

### Push Notifications and Messaging
- **Firebase Cloud Messaging** - https://firebase.google.com/docs/cloud-messaging/android/client
- **FCM Token Management** - https://firebase.google.com/docs/cloud-messaging/android/client#sample-register
- **Android Notification Channels** - https://developer.android.com/develop/ui/views/notifications/channels
- **NotificationCompat Builder** - https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder

### Development Tools and Resources
- **Android Studio** - https://developer.android.com/studio
- **Git Version Control** - https://git-scm.com/
- **GitHub** - https://github.com/
- **Gradle Build System** - https://gradle.org/
- **JUnit Testing Framework** - https://junit.org/junit5/
- **Mockito Testing Framework** - https://site.mockito.org/

### Project Repository
- **Solora GitHub Repository** - https://github.com/VCCT-PROG7314-2025-G2/PROG7314_POE_P2_Solora

## License
This project is for educational purposes. Review any third-party libraries' licenses (e.g., iText) before production use.
