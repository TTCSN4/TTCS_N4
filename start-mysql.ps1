$ErrorActionPreference='Stop'
$meetingSiteRoot=$PSScriptRoot
$meetingVars=@('DEMO_MODE','DATA_STORE','PORT','DB_PASSWORD')
$meetingPrevious=@{}
foreach($name in $meetingVars){$meetingPrevious[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$meetingSecret=Read-Host 'Nhap mat khau MySQL (khong luu vao file)' -AsSecureString
try {
 $env:DEMO_MODE='1'
 $env:DATA_STORE='mysql'
 $env:PORT='8080'
 $env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $meetingSecret).Password
 & (Get-Command node -ErrorAction Stop).Source (Join-Path $meetingSiteRoot 'server.cjs')
} finally {
 foreach($name in $meetingVars){[Environment]::SetEnvironmentVariable($name,$meetingPrevious[$name],'Process')}
 $meetingSecret.Dispose()
}
