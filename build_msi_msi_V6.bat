@echo off
rem ============ CONFIG ============
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PROJ=C:\Users\soporte\Desktop\feamassV3"
set "FXJMODS=C:\javafx-jmods-21.0.9"
set "OUTDIR=%PROJ%\dist"
set "TMPDIR=%PROJ%\jpkg_tmp"
set "APPNAME=Dictuc - Firma Masiva"
set "MAINCLASS=com.dictuc.feamass.MainFx"
set "ICON=assets\icon.ico"

echo === Entrando al proyecto ===
cd /d "%PROJ%" || (echo [ERROR] No existe %PROJ% & exit /b 1)

echo === Buscando uber-jar ===
set "JAR="
for %%F in ("target\feamass-*-jar-with-dependencies.jar") do set "JAR=%%~nxF"

if not defined JAR (
  echo === Compilando con Maven ===
  call mvn -DskipTests clean package || (echo [ERROR] Fallo mvn & exit /b 1)
  set "JAR="
  for %%F in ("target\feamass-*-jar-with-dependencies.jar") do set "JAR=%%~nxF"
)

if not defined JAR (
  echo [ERROR] No encontre target\feamass-*-jar-with-dependencies.jar
  echo Verifica la version en el pom.xml y que el assembly plugin genere el *-jar-with-dependencies.
  exit /b 1
)
echo [OK] JAR: %JAR%

dir /b "%JAVA_HOME%\jmods\jdk.crypto*.jmod"
dir /b "%FXJMODS%\javafx*.jmod"

"%JAVA_HOME%\bin\jlink.exe" --verbose ^
  --module-path "%JAVA_HOME%\jmods;%FXJMODS%" ^
  --add-modules java.base,java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing,jdk.crypto.mscapi,jdk.crypto.cryptoki ^
  --strip-debug --no-header-files --no-man-pages ^
  --output runtime
  
"%JAVA_HOME%\bin\jpackage.exe" ^
  --type msi ^
  --name "Dictuc - Firma Masiva" ^
  --app-version 1.0.4 ^
  --input target ^
  --main-jar feamass-1.0.1-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --runtime-image runtime ^
  --icon assets\icon.ico ^
  --dest dist ^
  --temp jpkg_tmp ^
  --win-menu --win-shortcut --win-dir-chooser --win-per-user-install ^
  --description "Software de firma electrónica avanzada masiva para Dictuc" ^
  --vendor "Francisco Moraga" ^
  --verbose