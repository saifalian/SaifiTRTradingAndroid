# Saifi TR Trading Android

Saifi TR Trading Android is an Android trading research app built with Kotlin.

In simple words, this app is made to explore trading screens, market data, strategy ideas, backtesting, bot management, charts, and order book views. It includes support-style code for crypto, forex, and stock market features.

The app package is `com.godzilla.app`, and the app name shown to users is `Saifi TR`.

## What This App Can Do

- Show trading screens for crypto, forex, and stocks.
- Manage trading bot information.
- Include a manual trading mode.
- Run backtesting logic and show backtest screens.
- Use trading calculators and strategy classes.
- Display candlestick charts and interactive chart components.
- Show order book information.
- Include exchange repository code for Binance, Bybit, OKX, KuCoin, Gate.io, MEXC, Bitget, BitMart, Coinbase, BingX, Toobit, and Yahoo Finance.
- Store local user preferences.
- Include unit tests for trading calculator logic.

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

## Safety Notes

This project is for trading research and app development.

Before connecting anything to real trading, carefully review the strategy, exchange, and risk logic. Trading is risky, and this project is not financial advice.
