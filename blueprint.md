# Project Blueprint: TEPRA SR5500P Android Print Helper

## 1. Overview

This document outlines the plan for developing an Android native helper application in Kotlin. The app's primary function is to receive print data from a Next.js web application via a custom URL scheme (`tepra-print://`), and then print labels directly to a KING JIM TEPRA SR5500P thermal printer via Bluetooth.

This approach separates the web application from the native printing mechanism, providing a streamlined and high-quality printing solution for thermal printers where browser-based printing is insufficient.

## 2. Style, Design, and Features (Current State)

The project is based on a default Flutter template. However, all development for this feature will be focused exclusively on the native Android (Kotlin) part of the project within the `/android` directory. The Flutter part of the application will be ignored.

**Core Android Native Features to be Implemented:**

*   **URL Scheme Handling:** The app will register and respond to the `tepra-print://` custom URL scheme.
*   **Data Parsing:** It will parse JSON data passed as a parameter in the URL.
*   **TEPRA SDK Integration:** The KING JIM TEPRA Android SDK will be integrated for printer communication.
*   **Bluetooth Printing:** The app will manage Bluetooth connectivity and send print commands to a paired TEPRA SR5500P.
*   **Minimal UI:** The user interface will be simple, likely showing only a "Printing..." status indicator and automatically closing upon completion.

## 3. Current Task: Initial Setup for URL Scheme Handling and SDK Integration

This phase focuses on laying the foundational framework for the application.

**Plan & Steps:**

1.  **`AndroidManifest.xml` Configuration:**
    *   Modify the `AndroidManifest.xml` to add an `<intent-filter>`.
    *   This filter will allow the `MainActivity` to be launched when a URL with the `tepra-print` scheme is triggered.

2.  **Gradle Configuration for TEPRA SDK:**
    *   Create a `libs` directory within the `android/app/` folder.
    *   Update the app-level `build.gradle.kts` file to include any `.aar` or `.jar` files placed in the `libs` directory as dependencies. This will allow the project to use the TEPRA Android SDK.

3.  **`MainActivity.kt` Implementation:**
    *   Update `MainActivity.kt` to handle the incoming `Intent`.
    *   Add logic to the `onCreate` method to check if the app was launched by the custom URL scheme.
    *   Implement code to extract the `data` query parameter (containing the JSON string) from the Intent's URI.
    *   Add placeholder code to parse the JSON and log the extracted data. This will serve as the entry point for the actual printing logic.

