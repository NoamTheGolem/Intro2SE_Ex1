@echo off
rem Renders the demo videos of docs\EXPERIMENT.md into results\ (Windows). Needs only Java 17+.
cd /d "%~dp0\.."
call Ex0.bat data\phantom_spheres_500.nrrd data\Ex0.csv results\Ex0_phantom500.mp4 || exit /b 1
call Ex0.bat data\MRHead.nrrd data\Ex0.csv results\Ex0_MRHead.mp4 || exit /b 1
for %%M in (rotate sweep tilt triangle) do call Ex0.bat data\MRHead.nrrd data\planes_%%M.csv results\MRHead_%%M.mp4 || exit /b 1
call Ex0.bat data\dice.nrrd data\course\edge_cases.csv results\edge_cases_dice.mp4 || exit /b 1
rem The lecturer's test subjects are not stored here (data\README.md): set LECTURER=<folder of the Drive download>
if defined LECTURER (
    call Ex0.bat "%LECTURER%\MRHead_1.nrrd" data\course\head.csv results\lecturer_head.mp4 || exit /b 1
    for %%S in (onion croissant swiss_roll biscuit_cake) do call Ex0.bat "%LECTURER%\%%S.nrrd" data\course\%%S.csv results\lecturer_%%S.mp4 || exit /b 1
)
echo Done - videos are in the results folder.
start "" "results\MRHead_rotate.mp4"
