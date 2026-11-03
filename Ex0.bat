@echo off
rem Demo "main" of assignment item 7 (Java form of: python Ex0.py file.nrrd file.csv file.mp4)
rem Usage:  Ex0 file.nrrd file.csv file.mp4 [--interp trilinear] [--fps N] [--threads auto] [--strict] ...
rem With no arguments it opens the window app. Builds build\Ex0.jar first when it is missing (needs a JDK).
setlocal
set "JAVA="
for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%D\bin\java.exe" set "JAVA=%%D\bin\java.exe"
if not defined JAVA if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for %%X in (java.exe) do set "JAVA=%%~$PATH:X"
if not defined JAVA (
    echo Java 17+ not found. Install it from https://adoptium.net or set JAVA_HOME.
    exit /b 4
)
if not exist "%~dp0build\Ex0.jar" (
    echo build\Ex0.jar is missing - building it with javac ...
    for %%J in ("%JAVA%") do set "PATH=%%~dpJ;%PATH%"
    call "%~dp0scripts\build.bat" || exit /b 4
)
"%JAVA%" -Xmx2g -jar "%~dp0build\Ex0.jar" %*
