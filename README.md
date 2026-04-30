# 6th Finger App

[English](#english) | [Русский](#russian)

Landing page: [prothesis.ru](https://prothesis.ru)

## English

### Overview

`6thFinger-App` is an Android application for the 6th Finger prosthesis ecosystem.

### Main Features

- Bluetooth Low Energy connection with the controller
- guest mode
- SRP-based registration and login
- recovery codes and optional email recovery
- local and cloud prosthesis settings
- avatar sync
- app language and theme sync

### Technologies

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit
- OkHttp
- Moshi
- Android DataStore
- Android Keystore
- Bluetooth Low Energy APIs

### Documentation

- Commands and build instructions: [commands.md](./commands.md)
- Product guide: [Open guide](https://google.com)

### Architecture

- `ui/` - Compose screens, dialogs and navigation
- `auth/` - SRP auth, sessions, password reset and account email flows
- `account/` - post-login sync, avatar sync and remote app settings sync
- `ble/` - BLE transport, telemetry, live control and device configuration
- `data/` - repositories, DataStore stores and offline cache
- `network/` - Retrofit APIs and DTOs
- `preferences/` - app language and theme preferences
- `security/` - Android Keystore, client attestation and request signing

### Build Flavors

- `official` - production build for the official backend, attestation enabled
- `community` - self-hosted build, attestation disabled
- `local` - local backend build, attestation disabled
- `officialLocal` - local end-to-end build with attestation enabled

**NB**:

If you have self-hosted server and want to protect it with key (so that other builds can't use your server), you will need to sign app with your key and place public key to server. See below and checkout guides.  

### Configuration Files

- `local.properties` - Android SDK path for local Gradle setup
- `app-config.properties` - tracked build-time app config
- `keystore.properties` - local signing config for `official` and `officialLocal`

Use [commands.md](./commands.md) for exact build, install and signing commands.

---

## Russian

### Обзор

`6thFinger-App` — Android-приложение для экосистемы протеза 6th Finger.

### Основные возможности

- подключение к контроллеру по Bluetooth Low Energy
- гостевой режим
- регистрация и вход через SRP
- recovery codes и опциональное восстановление через email
- локальные и облачные настройки протеза
- синхронизация аватара
- синхронизация языка и темы приложения

### Технологии

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit
- OkHttp
- Moshi
- Android DataStore
- Android Keystore
- Bluetooth Low Energy API

### Документация

- Команды и инструкции по сборке: [commands.md](./commands.md)
- Пользовательский гайд: [Открыть гайд](https://google.com)

### Архитектура

- `ui/` — Compose-экраны, диалоги и навигация
- `auth/` — SRP-аутентификация, сессии, password reset и email-сценарии аккаунта
- `account/` — постлогинная синхронизация, синхронизация аватара и удалённых настроек приложения
- `ble/` — BLE-транспорт, телеметрия, live control и конфигурация устройства
- `data/` — репозитории, DataStore-хранилища и offline cache
- `network/` — Retrofit API и DTO
- `preferences/` — настройки языка и темы
- `security/` — Android Keystore, client attestation и подпись запросов

### Flavor-сборки

- `official` — production-сборка для официального backend, attestation включён
- `community` — self-hosted сборка, attestation выключен
- `local` — сборка для локального backend, attestation выключен
- `officialLocal` — локальная end-to-end сборка с включённым attestation

**NB**:

Если вы используете self-hosted сервер и хотите защитить доступ приложений к нему используя ключ (чтобы билды других пользователей не могли использовать ваш сервер), вам необходимо подписать приложение вашим ключем и поместить публичную его часть на сервер. См. ниже и гайды.

### Файлы конфигурации

- `local.properties` — путь к Android SDK для локальной настройки Gradle
- `app-config.properties` — build-time конфиг приложения, который лежит в репозитории
- `keystore.properties` — локальная конфигурация подписи для `official` и `officialLocal`

Для точных команд сборки, установки и проверки подписи см. [commands.md](./commands.md).
