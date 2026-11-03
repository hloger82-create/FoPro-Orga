@rem Gradle startup script for Windows (minimal)
@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto init
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto init
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
exit /b 1

:init
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%~n0" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
