# Saifi TR Trading Android

Saifi TR Trading Android is a Kotlin Android trading research app. It includes market screens for crypto, forex, and stocks, trading strategy components, backtesting, bot management, exchange repositories, and interactive chart/order-book UI components.

The app package is `com.godzilla.app`, and the visible app name is `Saifi TR`.

## Features

- Multi-market trading UI for crypto, forex, and stocks
- Bot management screens and bot instance models
- Manual trading mode
- Backtesting engine and backtest UI
- Trading calculators and strategy classes
- Candlestick and interactive chart components
- Order book visualization
- Exchange repository layer for Binance, Bybit, OKX, KuCoin, Gate.io, MEXC, Bitget, BitMart, Coinbase, BingX, Toobit, and Yahoo Finance
- Hilt dependency injection modules
- Local user preferences
- Unit test coverage for trading calculator logic

## Tech Stack

- Kotlin
- Android Gradle Plugin with Kotlin DSL
- Jetpack Compose
- Hilt
- Retrofit-style repository/API structure
- Kotlin Coroutines
- AndroidX lifecycle and navigation components

## Project Structure

```text
app/src/main/java/com/godzilla/app
├── data/
│   ├── local/
│   ├── remote/
│   └── repository/
├── di/
├── domain/
├── ui/
└── MainActivity.kt
```

## Requirements

- Android Studio
- JDK 17 or Android Studio bundled JDK
- Android SDK installed

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Notes

This project is for trading research and application development. Review strategy, exchange, and risk logic carefully before connecting to any real trading workflow.

