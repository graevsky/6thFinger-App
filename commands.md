# Commands

[English](#english) | [Русский](#russian)

## English

### Files you edit before building

#### `app-config.properties`

Repository file with build-time app settings.

Typical keys:

```properties
OFFICIAL_BACKEND_BASE_URL=https://api.prothesis.ru/
COMMUNITY_BACKEND_BASE_URL=https://your-server.example/
LOCAL_BACKEND_BASE_URL=http://127.0.0.1:8000/
EMAIL_OFF=false
```

Use it when you want to change backend URLs or build-time app options.

#### `keystore.properties`

Local file for signing `official` and `officialLocal`.

Example:

```properties
OFFICIAL_STORE_FILE=C:\\keys\\my-release-key.p12
OFFICIAL_STORE_PASSWORD=your_keystore_password
OFFICIAL_KEY_ALIAS=mykey
OFFICIAL_KEY_PASSWORD=your_key_password
```

If the key was created without explicit `-alias`, `keytool` often uses `mykey`.

Key can be created using:

```powershell
keytool -genkeypair `
  -v `
  -storetype PKCS12 `
  -keystore C:\keys\keyname.p12 `
  -alias keyalias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

Check the alias with:

```powershell
keytool -list -v -storetype PKCS12 -keystore C:\keys\my-release-key.p12
```

### 1. Build all 4 release APKs

One command:

```powershell
.\gradlew.bat assembleAllReleaseApks
```

This builds:

- `communityRelease`
- `localRelease`
- `officialRelease`
- `officialLocalRelease`

Output APKs:

```text
app\build\outputs\apk\community\release\app-community-release.apk
app\build\outputs\apk\local\release\app-local-release.apk
app\build\outputs\apk\official\release\app-official-release.apk
app\build\outputs\apk\officialLocal\release\app-officialLocal-release.apk
```

### 2. Build each release APK separately

`communityRelease`:

```powershell
.\gradlew.bat :app:assembleCommunityRelease
```

`localRelease`:

```powershell
.\gradlew.bat :app:assembleLocalRelease
```

`officialRelease`:

```powershell
.\gradlew.bat :app:assembleOfficialRelease
```

`officialLocalRelease`:

```powershell
.\gradlew.bat :app:assembleOfficialLocalRelease
```

### 3. Install an APK on a phone over USB

Prerequisites:

1. Enable Developer options on the phone.
2. Enable USB debugging.
3. Connect the phone with a cable.
4. Approve the RSA fingerprint dialog on the phone if Android asks.

Check that `adb` sees the device:

```powershell
adb devices
```

Install an already built APK:

```powershell
adb install -r .\app\build\outputs\apk\official\release\app-official-release.apk
```

Examples:

```powershell
adb install -r .\app\build\outputs\apk\community\release\app-community-release.apk
adb install -r .\app\build\outputs\apk\local\release\app-local-release.apk
adb install -r .\app\build\outputs\apk\official\release\app-official-release.apk
adb install -r .\app\build\outputs\apk\officialLocal\release\app-officialLocal-release.apk
```

You can also let Gradle install the variant directly:

```powershell
.\gradlew.bat :app:installCommunityRelease
.\gradlew.bat :app:installLocalRelease
.\gradlew.bat :app:installOfficialRelease
.\gradlew.bat :app:installOfficialLocalRelease
```

If the variant is already installed and you want a clean reinstall:

```powershell
.\gradlew.bat :app:uninstallOfficialRelease
.\gradlew.bat :app:installOfficialRelease
```

### 4. If someone has their own server and wants an "official release" signed with their own key

App-side steps:

1. Create your own release key.
2. Put its path, passwords and alias into `keystore.properties`.
3. Edit `app-config.properties`.
4. Set `OFFICIAL_BACKEND_BASE_URL` to your own production server if you want `officialRelease`.
5. Set `LOCAL_BACKEND_BASE_URL` to your local server if you want `officialLocalRelease`.
6. Build:

```powershell
.\gradlew.bat :app:assembleOfficialRelease
```

or

```powershell
.\gradlew.bat :app:assembleOfficialLocalRelease
```

Important:

- this does not make your APK valid for `api.prothesis.ru`
- your backend must trust your own signing certificate fingerprint

If you do not need attestation on your own server, the simpler path is:

- `communityRelease` for a remote self-hosted server
- `localRelease` for a local server

### 5. Other useful commands

Build all 4 debug APKs:

```powershell
.\gradlew.bat assembleAllDebugApks
```

Build each debug APK separately:

```powershell
.\gradlew.bat :app:assembleCommunityDebug
.\gradlew.bat :app:assembleLocalDebug
.\gradlew.bat :app:assembleOfficialDebug
.\gradlew.bat :app:assembleOfficialLocalDebug
```

Install debug variants directly from Gradle:

```powershell
.\gradlew.bat :app:installCommunityDebug
.\gradlew.bat :app:installLocalDebug
.\gradlew.bat :app:installOfficialDebug
.\gradlew.bat :app:installOfficialLocalDebug
```

Uninstall debug variants:

```powershell
.\gradlew.bat :app:uninstallCommunityDebug
.\gradlew.bat :app:uninstallLocalDebug
.\gradlew.bat :app:uninstallOfficialDebug
.\gradlew.bat :app:uninstallOfficialLocalDebug
```

---

## Russian

### Какие файлы редактировать перед сборкой

#### `app-config.properties`

Файл в репозитории с build-time настройками приложения.

Типичные ключи:

```properties
OFFICIAL_BACKEND_BASE_URL=https://api.prothesis.ru/
COMMUNITY_BACKEND_BASE_URL=https://your-server.example/
LOCAL_BACKEND_BASE_URL=http://10.0.2.2:8000/
EMAIL_OFF=false
```

Его нужно менять, если вы хотите поменять URL backend или build-time опции приложения.

#### `keystore.properties`

Локальный файл для подписи `official` и `officialLocal`.

Пример:

```properties
OFFICIAL_STORE_FILE=C:\\keys\\my-release-key.p12
OFFICIAL_STORE_PASSWORD=твой_пароль_от_keystore
OFFICIAL_KEY_ALIAS=mykey
OFFICIAL_KEY_PASSWORD=твой_пароль_от_ключа
```

Если ключ создавался без явного `-alias`, `keytool` часто использует `mykey`.

Ключ можно создать с помощью:

```powershell
keytool -genkeypair `
  -v `
  -storetype PKCS12 `
  -keystore C:\keys\keyname.p12 `
  -alias keyalias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

Проверить alias можно так:

```powershell
keytool -list -v -storetype PKCS12 -keystore C:\keys\my-release-key.p12
```

### 1. Как собрать сразу 4 release APK

Одна команда:

```powershell
.\gradlew.bat assembleAllReleaseApks
```

Она собирает:

- `communityRelease`
- `localRelease`
- `officialRelease`
- `officialLocalRelease`

Готовые APK:

```text
app\build\outputs\apk\community\release\app-community-release.apk
app\build\outputs\apk\local\release\app-local-release.apk
app\build\outputs\apk\official\release\app-official-release.apk
app\build\outputs\apk\officialLocal\release\app-officialLocal-release.apk
```

### 2. Как собрать каждый release APK отдельно

`communityRelease`:

```powershell
.\gradlew.bat :app:assembleCommunityRelease
```

`localRelease`:

```powershell
.\gradlew.bat :app:assembleLocalRelease
```

`officialRelease`:

```powershell
.\gradlew.bat :app:assembleOfficialRelease
```

`officialLocalRelease`:

```powershell
.\gradlew.bat :app:assembleOfficialLocalRelease
```

### 3. Как установить APK на телефон по проводу

Что нужно сделать:

1. Включить Developer options на телефоне.
2. Включить USB debugging.
3. Подключить телефон по кабелю.
4. Подтвердить RSA-диалог на телефоне, если Android его покажет.

Проверить, что `adb` видит устройство:

```powershell
adb devices
```

Установить уже собранный APK:

```powershell
adb install -r .\app\build\outputs\apk\official\release\app-official-release.apk
```

Примеры:

```powershell
adb install -r .\app\build\outputs\apk\community\release\app-community-release.apk
adb install -r .\app\build\outputs\apk\local\release\app-local-release.apk
adb install -r .\app\build\outputs\apk\official\release\app-official-release.apk
adb install -r .\app\build\outputs\apk\officialLocal\release\app-officialLocal-release.apk
```

Либо можно попросить Gradle сразу установить нужный variant:

```powershell
.\gradlew.bat :app:installCommunityRelease
.\gradlew.bat :app:installLocalRelease
.\gradlew.bat :app:installOfficialRelease
.\gradlew.bat :app:installOfficialLocalRelease
```

Если нужна чистая переустановка:

```powershell
.\gradlew.bat :app:uninstallOfficialRelease
.\gradlew.bat :app:installOfficialRelease
```

### 4. Что делать, если у человека свой сервер и он хочет сделать "официальный релиз" со своей подписью

Со стороны приложения нужно:

1. Создать свой release key.
2. Заполнить `keystore.properties` своим путём, паролями и alias.
3. Отредактировать `app-config.properties`.
4. Если нужен удалённый production backend, выставить свой `OFFICIAL_BACKEND_BASE_URL`.
5. Если нужен локальный сервер, выставить свой `LOCAL_BACKEND_BASE_URL`.
6. Собрать:

```powershell
.\gradlew.bat :app:assembleOfficialRelease
```

или

```powershell
.\gradlew.bat :app:assembleOfficialLocalRelease
```

Важно:

- это не даст доступ к `api.prothesis.ru`
- backend должен доверять именно вашему signing certificate fingerprint

Если attestation на своём сервере не нужна, проще использовать:

- `communityRelease` для удалённого self-hosted backend
- `localRelease` для локального backend

### 5. Другие полезные команды

Собрать сразу 4 debug APK:

```powershell
.\gradlew.bat assembleAllDebugApks
```

Собрать каждый debug APK отдельно:

```powershell
.\gradlew.bat :app:assembleCommunityDebug
.\gradlew.bat :app:assembleLocalDebug
.\gradlew.bat :app:assembleOfficialDebug
.\gradlew.bat :app:assembleOfficialLocalDebug
```

Установить debug-варианты через Gradle:

```powershell
.\gradlew.bat :app:installCommunityDebug
.\gradlew.bat :app:installLocalDebug
.\gradlew.bat :app:installOfficialDebug
.\gradlew.bat :app:installOfficialLocalDebug
```

Удалить debug-варианты:

```powershell
.\gradlew.bat :app:uninstallCommunityDebug
.\gradlew.bat :app:uninstallLocalDebug
.\gradlew.bat :app:uninstallOfficialDebug
.\gradlew.bat :app:uninstallOfficialLocalDebug
```
