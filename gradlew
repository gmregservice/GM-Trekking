#!/bin/sh
#
# Wrapper standard di Gradle. Richiede gradle/wrapper/gradle-wrapper.jar,
# NON incluso in questo scheletro (file binario, non generabile in questo ambiente).
#
# Per generarlo:
#   1) Apri il progetto in Android Studio: lo rigenera automaticamente al primo sync, oppure
#   2) Se hai Gradle installato in locale: esegui `gradle wrapper --gradle-version 9.7.0`
#      nella cartella del progetto.
#
# La pipeline GitHub Actions di questo repo NON usa questo script: installa Gradle
# direttamente (vedi .github/workflows/android-build.yml), quindi funziona anche
# prima che tu generi questo wrapper in locale.

DIR="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "gradle-wrapper.jar mancante. Apri il progetto in Android Studio per rigenerarlo,"
  echo "oppure esegui: gradle wrapper --gradle-version 9.7.0"
  exit 1
fi

exec "${JAVA_HOME:-.}/bin/java" -Dorg.gradle.appname="$(basename "$0")" \
  -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
