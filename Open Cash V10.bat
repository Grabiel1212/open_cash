@echo off
cd /d "%~dp0"

start "" /b "C:\Program Files\Java\jdk-21\bin\javaw.exe" ^
--module-path "openfx\javafx-sdk-21.0.9\lib" ^
--add-modules javafx.controls,javafx.fxml,javafx.media ^
-jar "C:\Users\juang\Documents\ULTIMARQUET_SAC\proyecto_dosar\catalogo_dosar\abridor_caja\open_cash\target\utilsac-1.0-SNAPSHOT.jar"

exit