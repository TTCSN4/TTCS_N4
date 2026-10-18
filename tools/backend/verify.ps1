param(
    [string]$Repository = '',
    [string]$Tests = 'MeetingmanagementApplicationTests,EquipmentReservationTest,EquipmentConcurrencyTest,US02MeetingServiceTest,US02EditIntegrationTest,US18UserServiceTest,US18AccountHttpTest'
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$validationPom = Join-Path $project '.backend-validation-pom.xml'
if (Test-Path -LiteralPath $validationPom) { throw 'Validation POM already exists; inspect it before retrying.' }
# Two pre-existing US03 tests refer to an API absent from develop 633dea6.
# Do not alter those tests or silently exclude them from the regular Maven build.
$xml = [IO.File]::ReadAllText((Join-Path $project 'pom.xml'))
$plugin = '<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><testExcludes><testExclude>**/RecurringScheduleTest.java</testExclude><testExclude>**/RecurringPersistenceTest.java</testExclude></testExcludes></configuration></plugin>'
$xml = $xml.Replace('<plugins>', '<plugins>' + $plugin)
try {
    [IO.File]::WriteAllText($validationPom, $xml)
    $mavenArgs = @('-f', $validationPom, "-Dtest=$Tests", 'clean', 'test')
    if ($Repository) { $mavenArgs = @("-Dmaven.repo.local=$Repository") + $mavenArgs }
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Backend verification failed ($LASTEXITCODE)" }
} finally {
    if (Test-Path -LiteralPath $validationPom) { Remove-Item -LiteralPath $validationPom }
}
