param([switch]$Check)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$formatterPath = Join-Path $projectRoot '.tooling/google-java-format.jar'
$formatterVersion = '1.28.0' # Compatible with the project's JDK 17.
$expectedHash = '32342E7C1B4600F80DF3471DA46AEE8012D3E1445D5EA1BE1FB71289B07CC735'

if (-not (Test-Path -LiteralPath $formatterPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $formatterPath) | Out-Null
    $formatterUrl = "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/$formatterVersion/google-java-format-$formatterVersion-all-deps.jar"
    Invoke-WebRequest -Uri $formatterUrl -OutFile $formatterPath
}

if ((Get-FileHash -LiteralPath $formatterPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Formatter checksum mismatch. Remove the cached jar and download it again.'
}

$javaFiles = Get-ChildItem (Join-Path $projectRoot 'app/src') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }
$formatterArguments = @('--aosp')
if ($Check) {
    $formatterArguments += @('--dry-run', '--set-exit-if-changed')
} else {
    $formatterArguments += '--replace'
}

& java -jar $formatterPath @formatterArguments @javaFiles
if ($LASTEXITCODE -ne 0) {
    throw "Java formatter failed with exit code $LASTEXITCODE."
}
