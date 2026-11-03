@echo off
rem Renders the demo videos of docs\EXPERIMENT.md into results\ (Windows). Needs only Java 17+.
cd /d "%~dp0\.."
call Ex0.bat data\phantom_spheres_500.nrrd data\Ex0.csv results\Ex0_phantom500.mp4 || exit /b 1
call Ex0.bat data\MRHead.nrrd data\Ex0.csv results\Ex0_MRHead.mp4 || exit /b 1
for %%M in (rotate sweep tilt triangle) do call Ex0.bat data\MRHead.nrrd data\planes_%%M.csv results\MRHead_%%M.mp4 || exit /b 1
echo Done - videos are in the results folder.
start "" "results\MRHead_rotate.mp4"
