@echo off
rem Double-click: opens the Ex1 window app.  Drag a .nrrd file onto this icon: opens that file.
rem Uses the Java runtime in runtime\ (shipped with the app); otherwise finds Java by itself
rem (IntelliJ .jdks, Android Studio, JAVA_HOME, Program Files, PATH). Ex1.exe does the same.
setlocal EnableDelayedExpansion
set "JAR=%~dp0build\Ex0.jar"
set "JW="
rem The Java runtime that ships with the app (runtime\) comes first: nothing to install.
if exist "%~dp0runtime\bin\javaw.exe" set "JW=%~dp0runtime\bin\javaw.exe"
if not defined JW for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%D\bin\javaw.exe" set "JW=%%D\bin\javaw.exe"
if not defined JW if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javaw.exe" set "JW=%ProgramFiles%\Android\Android Studio\jbr\bin\javaw.exe"
if not defined JW if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JW=%JAVA_HOME%\bin\javaw.exe"
if not defined JW for /d %%D in ("%ProgramFiles%\Java\*" "%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Microsoft\*") do if exist "%%D\bin\javaw.exe" set "JW=%%D\bin\javaw.exe"
if not defined JW for %%X in (javaw.exe) do set "JW=%%~$PATH:X"
if not defined JW (
    echo Java 17 or newer was not found on this computer.
    echo Install it from https://adoptium.net and double-click this file again.
    pause
    exit /b 1
)
if not exist "%JAR%" (
    echo build\Ex0.jar is missing - building it with javac ...
    for %%J in ("%JW%") do set "PATH=%%~dpJ;!PATH!"
    call "%~dp0scripts\build.bat"
    if not exist "%JAR%" (
        echo Build failed. Run scripts\build.bat with a JDK and look at the error.
        pause
        exit /b 1
    )
)
start "" "%JW%" -jar "%JAR%" %1
