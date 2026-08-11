@rem Wrapper standard di Gradle per Windows.
@rem Richiede gradle\wrapper\gradle-wrapper.jar, non incluso in questo scheletro.
@rem Apri il progetto in Android Studio per generarlo automaticamente,
@rem oppure esegui: gradle wrapper --gradle-version 9.7.0

@echo off
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo gradle-wrapper.jar mancante. Apri il progetto in Android Studio per rigenerarlo,
  echo oppure esegui: gradle wrapper --gradle-version 9.7.0
  exit /b 1
)
"%JAVA_HOME%\bin\java" -Dorg.gradle.appname=%~n0 -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
