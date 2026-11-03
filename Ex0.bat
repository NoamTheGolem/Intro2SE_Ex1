@echo off
rem Demo "main" of assignment item 7 (Java version of: python Ex0.py file.nrrd file.csv file.mp4)
rem Usage:  Ex0 file.nrrd file.csv file.mp4 [--interp trilinear] [--fps N] [--strict] ...
rem With no arguments it opens the window app instead.
setlocal
set "JAVA="
for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%D\bin\java.exe" set "JAVA=%%D\bin\java.exe"
if not defined JAVA if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for %%X in (java.exe) do set "JAVA=%%~$PATH:X"
if not defined JAVA (
    echo Java 17+ not found. Install it from https://adoptium.net
    exit /b 1
)
"%JAVA%" -Xmx2g -jar "%~dp0build\Ex0.jar" %*
