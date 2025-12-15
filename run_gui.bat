@echo off
setlocal
set JFX_VER=21.0.3
set M2=%USERPROFILE%\.m2\repository

set MP=%M2%\org\openjfx\javafx-base\%JFX_VER%\javafx-base-%JFX_VER%-win.jar;^
%M2%\org\openjfx\javafx-graphics\%JFX_VER%\javafx-graphics-%JFX_VER%-win.jar;^
%M2%\org\openjfx\javafx-controls\%JFX_VER%\javafx-controls-%JFX_VER%-win.jar;^
%M2%\org\openjfx\javafx-fxml\%JFX_VER%\javafx-fxml-%JFX_VER%-win.jar

java --module-path "%MP%" --add-modules=javafx.controls,javafx.graphics,javafx.fxml -jar target\feamass-1.0.1-jar-with-dependencies.jar
endlocal