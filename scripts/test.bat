@echo off
rem Builds the project and runs all JUnit 4 tests (jars in lib\, no internet needed).
cd /d "%~dp0\.."
call scripts\build.bat || exit /b 1
set CP=build\classes;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar
if exist build\test-classes rmdir /s /q build\test-classes
mkdir build\test-classes
dir /s /b core\src\test\java\*.java app\src\test\java\*.java > build\test-sources.txt
javac --release 17 -encoding UTF-8 -cp %CP% -d build\test-classes @build\test-sources.txt || exit /b 1
del build\test-sources.txt
java -cp %CP%;build\test-classes org.junit.runner.JUnitCore ex1.core.PlaneTest ex1.core.PlaneResolverTest ex1.core.ProjectorTest ex1.core.ParallelProjectorTest ex1.core.IntensityWindowTest ex1.io.NrrdReaderTest ex1.io.PlaneCsvReaderTest ex1.io.Mp4WriterTest ex1.app.Ex0Test
