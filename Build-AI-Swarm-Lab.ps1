$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$project=$PSScriptRoot
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$java='C:\Program Files\Android\Android Studio\jbr'
if(!(Test-Path "$sdk\platforms\android-35\android.jar")){throw 'Android SDK 35 missing'}
if(!(Test-Path "$java\bin\java.exe")){throw 'Android Studio JBR missing'}
$env:JAVA_HOME=$java
$env:ANDROID_HOME=$sdk
$env:PATH="$java\bin;$env:PATH"
$gradle=Join-Path $project '.tools\gradle-8.10.2\bin\gradle.bat'
if(!(Test-Path $gradle)){
  New-Item -ItemType Directory -Force "$project\.tools" | Out-Null
  $zip=Join-Path $project '.tools\gradle.zip'
  [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
  Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -OutFile $zip -UseBasicParsing
  Expand-Archive $zip -DestinationPath "$project\.tools" -Force
}
("sdk.dir="+$sdk.Replace('\','/')) | Set-Content "$project\local.properties" -Encoding ASCII
Push-Location $project
try {& $gradle --no-daemon --console plain :app:assembleDebug; if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}}finally{Pop-Location}
$apk=Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
if(!(Test-Path $apk)){throw 'No APK produced'}
Copy-Item $apk (Join-Path $project 'AI-Swarm-Lab-v0.1-debug.apk') -Force
Write-Output "APK_READY: $project\AI-Swarm-Lab-v0.1-debug.apk"
Get-FileHash (Join-Path $project 'AI-Swarm-Lab-v0.1-debug.apk') -Algorithm SHA256
