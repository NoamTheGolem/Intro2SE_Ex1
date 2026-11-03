@echo off
rem Builds build\Ex0.jar with plain javac (JDK 17+). No internet, no Gradle needed.
cd /d "%~dp0\.."
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
dir /s /b core\src\main\java\*.java app\src\main\java\*.java > build\sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -Werror -d build\classes @build\sources.txt || exit /b 1
jar --create --file build\Ex0.jar --main-class ex1.app.Ex0 -C build\classes . || exit /b 1
del build\sources.txt
echo Built build\Ex0.jar
