# TypeSafe Kotlin App

Native Android application written in Kotlin + Jetpack Compose.

## What it does

- Connects to the TypeSafe mobile bridge at:
  https://typesafe-mcp-key-hyouka1.vercel.app
- Checks the bridge health.
- Sends typed System One questions.
- Supports noul, score, and choice.
- Does not embed the TypeSafe API key in the APK.

The existing typesafe-mcp-key server keeps the TypeSafe credential server-side. Its MCP endpoint remains available for Key, while the mobile JSON bridge is used by this native app.

## Build

GitHub Actions builds an installable debug APK on every push to main and supports manual workflow dispatch.

The workflow uses JDK 17, Gradle 9.6.0, AGP 9.4.0, Kotlin 2.4.10, and the September 2026 Compose BOM.
