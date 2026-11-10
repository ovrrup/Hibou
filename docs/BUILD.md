# Сборка Leostryx 3.0.6

[Главная](../README.md) · [Исправления 3.0.6](REPAIR-3.0.6.md)

## Входные файлы

Репозиторий содержит рабочие файлы модификации APK, готовые исправленные DEX и исходники нового нативного обновлятора. Это не исходный Java/Kotlin/React-проект всех компонентов приложения.

`rebuild.py` использует базовые APK **3.0.5**, проверяя их закреплённые SHA-256. Затем он накладывает только исправленные файлы. Так сохраняются ресурсы оформления, четыре набора эмблем, библиотеки и нужный вариант ядра, даже если большие файлы отсутствуют в Git.

Повреждённый `classes.dex` базового APK заменяется пересобранным. Использовать базовый APK для самостоятельной установки не следует.

| Файл | Роль |
|---|---|
| `app/AndroidManifest.xml` | Скомпилированный манифест 3.0.6, code 22, разрешение установки пакетов |
| `app/classes.dex` | Пересобранный основной DEX, исправленный мост AgyTools |
| `app/classes4.dex` | Нативные метаданные и запуск системного установщика |
| `app/assets/web/agy_tools.js`, `agy_tools.sh` | UI инструментов и обновление |
| `app/assets/web/agy_providers.js` | Группировка выбранных моделей |
| `tools/patch_web_bundle.py` | Проверяемый патч рендера групп в базовом JS-бандле |
| `tools/update_backend.py` | Исходник логики обновления, встроенный в shell-скрипт |
| `native-src/` | Java нового моста, изменённый smali AgyTools, исходный XML манифеста |

Остальные файлы старого дерева `app/` не используются как неявные overrides. Список накладываемых файлов явно задан в `OVERRIDES`, чтобы не смешивать содержимое разных сборок.

## Требования

Python 3.9+, Node.js для проверки синтаксиса JS, Bash, Java и `tools/uber-apk-signer.jar`. Доступный ключ проекта — `dist/antigravity-debug.keystore`, alias `debug`. Для сборки не требуется отсутствующий `brand.py` или `dist/antigravity-signing.p12`.

## Сборка

```bash
python3 rebuild.py both --fetch-base
python3 rebuild.py standard --fetch-base
python3 rebuild.py bypass --fetch-base
```

Для уже скачанных входных файлов:

```bash
python3 rebuild.py both \
  --standard-apk /path/Leostryx-3.0.5-standard.apk \
  --bypass-apk /path/Leostryx-3.0.5-bypass.apk
```

Результат — `dist/signed/Leostryx-3.0.6-standard.apk`, `Leostryx-3.0.6-bypass.apk` и `SHA256SUMS.txt`. Входные APK кэшируются в `.cache/rebuild/`.

Сборщик проверяет точный SHA-256 базового APK, синтаксис изменённого JS и shell, DEX-заголовки, целостность ZIP и хеши runtime до подписи и после неё. Подпись и выравнивание проверяет signer. При несовпадении патча JS-бандла сборка останавливается.

## Подпись и переход со старых APK

Исправленные файлы используют сертификат `7360d9349c7d1e9af564d32c4d0392fcb4effe211567c8f3dadec640efbcfa53`. Опубликованные APK 3.0.3–3.0.5 подписаны другим сертификатом: `d72235a57bb27fe9755199e8b9daca1ae305adf8a0f0feac3ac98434f6f7e416`.

Чтобы поставить исправленную сборку поверх этих выпусков, необходимо восстановить именно их исходный ключ. Без него сохраните данные и переустановите приложение. Повышение versionCode не обходит проверку сертификата.

Для своего ключа задайте `LEOSTRYX_KEYSTORE`, `LEOSTRYX_KEY_ALIAS`, `LEOSTRYX_STORE_PASSWORD`, `LEOSTRYX_KEY_PASSWORD`. Этот сертификат нужно сохранять для следующих обновлений.

## Разработка и проверки

```bash
python3 -m unittest discover -s tests -v
```

При изменении `tools/update_backend.py` его содержимое должно оставаться синхронным с блоком `LEOSTRYX_UPDATE_PY` в `app/assets/web/agy_tools.sh`.

Новый Java-мост находится в `native-src/com/agy/ru/LeostryxUpdates.java`; он компилируется против Android API и преобразуется D8 в `classes4.dex`. Основной DEX пересобран из smali, а не исправлен подстановкой байтов. `native-src/smali/com/agy/ru/AgyTools.smali` содержит добавленные JavaScript-интерфейсы `getAppInfo()` и `installUpdate()`.

Статические проверки не заменяют чистый запуск, выдачу разрешения установки и обновление на физическом Android. Эти сценарии нужно проверить перед публикацией релиза.
