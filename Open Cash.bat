@echo off
cd /d "%~dp0"

start "" /b "C:\Program Files\Java\jdk-21\bin\javaw.exe" ^
--module-path "C:\openjfx-17.0.10_windows-x64_bin-sdk\javafx-sdk-17.0.10\lib" ^
--add-modules javafx.controls,javafx.fxml,javafx.media ^
-jar "C:\Users\juang\Documents\ULTIMARQUET_SAC\proyecto_dosar\catalogo_dosar\abridor_caja\open_cash\target\utilsac-1.0-SNAPSHOT.jar"

exit