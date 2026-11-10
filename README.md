<p align="center">
  <img src="assets/hibou-banner.svg" alt="Hibou — русская сборка Antigravity для Android" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/ovrrup/Hibou/releases/latest"><img src="https://img.shields.io/github/v/release/ovrrup/Hibou?style=flat-square&color=D58151&label=release" alt="Последний релиз" /></a>
  <img src="https://img.shields.io/badge/Android-arm64--v8a-373438?style=flat-square&logo=android&logoColor=F0DFCC" alt="Android · arm64-v8a" />
  <img src="https://img.shields.io/badge/Интерфейс-RU%20%2F%20EN-373438?style=flat-square" alt="Русский и английский интерфейс" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-373438?style=flat-square" alt="Apache 2.0" /></a>
</p>

<p align="center"><b>Среда разработки с ИИ-агентом прямо на Android.</b><br />
Русский интерфейс, собственные провайдеры моделей, инструменты разработки и управление работой агента.</p>

<p align="center">
  <a href="https://github.com/ovrrup/Hibou/releases/latest"><b>Скачать APK</b></a> ·
  <a href="docs/USAGE.md">Настройка</a> ·
  <a href="CHANGELOG.md">История версий</a> ·
  <a href="docs/BUILD.md">Сборка</a> ·
  <a href="https://github.com/ovrrup/Hibou/issues">Сообщить об ошибке</a>
</p>

## О проекте

**Hibou** — русская сборка [Antigravity для Android](https://github.com/werdio325-png/antigravity-android) с доработанным интерфейсом, настройками провайдеров и встроенным управлением инструментами. Агент может читать и изменять файлы проекта, выполнять команды и запускать сборки на устройстве. Окружение работает в userspace Android на ARM64/Bionic, без PRoot, QEMU и chroot.

## Возможности

| Раздел | Что доступно |
|---|---|
| **Чат и модели** | Стандартные и собственные провайдеры, группировка моделей, режимы мышления, до 10 вложений |
| **Управление агентом** | Щит безопасности: доступ к файлам и сети, выполнение команд, настройки JavaScript |
| **Инструменты разработки** | Установка компонентов из настроек, Android SDK/NDK, Java, Node.js LTS, Python и Flutter/Dart |
| **Интеграции** | MCP-серверы, UTCP/OpenAPI, навыки, инструкции и плагины |
| **Диагностика и данные** | Консоль и история ошибок в «Инструментах», копирование логов, экспорт сессий и резервные копии |
| **Оформление** | Русский и английский интерфейс, светлая и тёмная темы, четыре набора брендинга: Сова (маскот Hibou), Рысь, LSX и Angular X |

## Скриншоты

<table>
  <tr>
    <td align="center"><img src="assets/screenshots/home.jpg" alt="Главный экран" width="230" /><br /><sub>Главный экран</sub></td>
    <td align="center"><img src="assets/screenshots/models.jpg" alt="Выбор модели" width="230" /><br /><sub>Модели</sub></td>
    <td align="center"><img src="assets/screenshots/shield.jpg" alt="Настройки щита" width="230" /><br /><sub>Щит безопасности</sub></td>
  </tr>
</table>

<details>
<summary>Темы и диктовка</summary>

<table>
  <tr>
    <td align="center"><img src="assets/screenshots/light-theme.jpg" alt="Светлая тема" width="230" /><br /><sub>Светлая тема</sub></td>
    <td align="center"><img src="assets/screenshots/dark-theme.jpg" alt="Тёмная тема" width="230" /><br /><sub>Тёмная тема</sub></td>
    <td align="center"><img src="assets/screenshots/dictation.jpg" alt="Диктовка" width="230" /><br /><sub>Диктовка</sub></td>
  </tr>
</table>

</details>

## Скачать и установить · исправленные выпуски

| Вариант · 3.0.5 | Назначение | Скачать |
|---|---|---|
| **Standard** | Базовая сборка без патча региональных проверок ядра | [Standard APK](https://github.com/ovrrup/Hibou/releases/download/v3.0.5/Hibou-3.0.5-standard.apk) |
| **Bypass** | Сборка с патчем региональных проверок ядра | [Bypass APK](https://github.com/ovrrup/Hibou/releases/download/v3.0.5/Hibou-3.0.5-bypass.apk) |

1. Скачайте выбранный APK из [релиза](https://github.com/ovrrup/Hibou/releases/latest).
2. Разрешите Android установку из используемого источника и установите приложение.
3. При первом запуске пройдите настройку разрешений и языка, затем войдите в аккаунт или настройте своего провайдера.

Исправленные APK подписаны исходным сертификатом выпусков 3.0.3–3.0.5. Выбирайте ту же или более новую версию: Android может запретить понижение versionCode. Если на устройстве осталась прежняя скачанная копия APK, скачайте файл заново.

## Различия выпусков

Все шесть APK 3.0.3–3.0.5 повторно собраны с исходной подписью. Причина стартового сбоя DEX устранена; прежние файлы сохранены под именами `.before-repair` и не предназначены для установки.

| Выпуск | versionCode | Изменения |
|---|---|---|
| [3.0.3](https://github.com/ovrrup/Hibou/releases/tag/v3.0.3) | 19 | Брендинг, четыре набора эмблем, исправленный запуск и раздел инструментов |
| [3.0.4](https://github.com/ovrrup/Hibou/releases/tag/v3.0.4) | 20 | Дополнительно: заголовки провайдеров в селекторе моделей и согласованные пути резервных копий |
| [3.0.5](https://github.com/ovrrup/Hibou/releases/tag/v3.0.5) | 21 | Дополнительно: встроенная проверка, загрузка и установка обновлений с сохранением варианта сборки |

Для установки поверх 3.0.3/3.0.4/3.0.5 выбирайте исправленную **3.0.5**. Удалять приложение и данные не требуется: сертификат совпадает. Старый APK, уже скачанный на телефон, нужно скачать заново.

[Отчёт об исправлении](docs/REPAIRED-RELEASES.md) · [История изменений](CHANGELOG.md).

## Что учитывать

- Стандартные и пользовательские провайдеры используют разные режимы ядра; порядок подключения описан в [руководстве](docs/USAGE.md).
- Для диктовки с пользовательскими провайдерами используется системное распознавание речи Android.
- Доступность моделей, регионов и внешних API зависит от провайдера. Bypass меняет проверки ядра, но не гарантирует доступность каждого сервиса.
- Каталог инструментов не означает, что все компоненты уже установлены: нужные пакеты выбираются в настройках.

## Документация

| Документ | Содержание |
|---|---|
| [Настройка и диагностика](docs/USAGE.md) | Установка, провайдеры, инструменты, MCP, журнал ошибок и резервное копирование |
| [Сборка и состав репозитория](docs/BUILD.md) | Подготовка дерева APK, зависимости и ограничения воспроизводимости |
| [История версий](CHANGELOG.md) | Изменения по релизам, начиная с новых |
| [Подробный журнал разработки](docs/CHANGELOG-ARCHIVE.md) | Все 65 пунктов исходной истории с техническими подробностями |
| [Flutter/Dart](docs/flutter-toolchain.md) · [JVM и jadx](docs/jadx-fixes.md) | Дополнительные инструкции для инструментов |

## Авторы и лицензия

Основа проекта — [werdio325-png/antigravity-android](https://github.com/werdio325-png/antigravity-android). Предыдущие наработки — [Leostryx (@Leostrange)](https://github.com/Leostrange/Leostryx). Развитие и сборки Hibou — [@ovrrup](https://github.com/ovrrup).

Лицензия репозитория — [Apache 2.0](LICENSE). Сторонние компоненты распространяются на условиях их собственных лицензий.
