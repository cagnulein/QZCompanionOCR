:: QZ-Companion App Installer
:: Author: Al Udell
:: Date: June 28, 2022
:: QZ on Facebook - https://www.facebook.com/groups/149984563348738

@echo off

::set current directory to the location of this script
@pushd %~dp0

echo *** Make sure your treadmill is powered-up and USB Debugging is turned on ***

set /p TMIP="Enter treadmill IP address: "

ping %TMIP%
timeout 5

adb disconnect
adb kill-server
adb connect %TMIP%
adb devices -l
timeout 5

adb uninstall org.cagnulein.qzcompanionpeloton
timeout 5

adb install QZCompanionPeloton.apk
timeout 5

adb shell monkey -p org.cagnulein.qzcompanionpeloton 1
timeout 5

adb shell monkey -p com.ifit.standalone 1
timeout 5

pause > nul | set/p = QZ Companion is installed. Press any key to reboot treadmill . . .

adb reboot

echo Installation complete.

pause


