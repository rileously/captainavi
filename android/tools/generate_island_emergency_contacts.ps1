param(
    [string]$SnapshotDate = (Get-Date -Format 'yyyy-MM-dd'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\app\src\main\assets\island_emergency_contacts_v1.json')
)

$ErrorActionPreference = 'Stop'

$lgaUrl = 'https://www.lga.gov.mv/en/councils?state=4'
$healthUrl = 'https://health.gov.mv/en/health-facilities'
$hanimaadhooCouncilUrl = 'https://hanimaadhoo.gov.mv/contact'
$kulhudhuffushiHospitalUrl = 'https://krh.gov.mv/'
$gazetteerPath = Join-Path $PSScriptRoot '..\app\src\main\assets\island_gazetteer_v1.json'

function Normalize-Key([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    $decomposed = $Value.Normalize([Text.NormalizationForm]::FormD)
    return (($decomposed -replace '\p{Mn}', '').ToLowerInvariant() -replace '[^a-z0-9]', '')
}

function Get-PhoneNumbers([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return @() }
    return @(
        [regex]::Matches($Value, '(?<!\d)(?:\d[\s-]?){4,7}(?!\d)') |
            ForEach-Object { $_.Value -replace '\D', '' } |
            Where-Object { $_.Length -in 4..7 } |
            Select-Object -Unique
    )
}

function Get-Island([string]$Name, [string]$Atoll) {
    $nameKey = Normalize-Key $Name
    $atollKey = Normalize-Key $Atoll
    return $script:islands | Where-Object {
        (Normalize-Key $_.englishName) -eq $nameKey -and
            (Normalize-Key $_.atoll) -eq $atollKey
    } | Select-Object -First 1
}

function Get-OrCreateEntry($Island) {
    $key = [string]$Island.id
    if (-not $script:entries.Contains($key)) {
        $script:entries[$key] = [ordered]@{
            islandId = [int]$Island.id
            islandName = [string]$Island.englishName
            atoll = [string]$Island.atoll
            council = $null
            health = $null
        }
    }
    return $script:entries[$key]
}

function New-Contact(
    [string]$ServiceLabel,
    [string]$Organization,
    [string[]]$Phones,
    [string]$SourceLabel,
    [string]$SourceUrl
) {
    return [ordered]@{
        serviceLabel = $ServiceLabel
        organization = $Organization
        phones = @($Phones)
        sourceLabel = $SourceLabel
        sourceUrl = $SourceUrl
    }
}

$gazetteer = Get-Content -Raw $gazetteerPath | ConvertFrom-Json
$script:islands = @($gazetteer.islands | Where-Object { $_.category -eq 'Residential Island' })
$script:entries = [ordered]@{}

$atollCodes = @{
    'Haa Alifu' = 'HA'; 'Haa Dhaalu' = 'HDH'; 'Shaviyani' = 'SH'; 'Noonu' = 'N'
    'Raa' = 'R'; 'Baa' = 'B'; 'Lhaviyani' = 'LH'; 'Kaafu' = 'K'
    'Alifu Alifu' = 'AA'; 'Alifu Dhaalu' = 'ADH'; 'Vaavu' = 'V'; 'Meemu' = 'M'
    'Faafu' = 'F'; 'Dhaalu' = 'DH'; 'Thaa' = 'TH'; 'Laamu' = 'L'
    'Gaafu Alifu' = 'GA'; 'Gaafu Dhaalu' = 'GDH'; 'Gnaviyani' = 'GN'
    'Seenu' = 'S'; 'MLE' = 'MLE'
}
$councilAliases = @{
    'kurinbee' = 'kurinbi'
    'kanduhulhuhdoo' = 'kanduhulhudhoo'
    'hoadedhdhoo' = 'hoandehdhoo'
    'fuvammulah' = 'fuvahmulah'
    'dhonfanu' = 'dhonfan'
    'himmafushi' = 'hinmafushi'
    'maaenboodoo' = 'maaenboodhoo'
    'nadella' = 'nadellaa'
}

$lgaHtml = (Invoke-WebRequest -UseBasicParsing $lgaUrl).Content
$lgaMatch = [regex]::Match(
    $lgaHtml,
    "(?s)window\.councilorsPage = JSON\.parse\('(?<data>.*?)'\);"
)
if (-not $lgaMatch.Success) { throw 'Could not locate the LGA council directory data.' }
$lgaOuterJson = '"' + $lgaMatch.Groups['data'].Value + '"'
$lgaPage = (($lgaOuterJson | ConvertFrom-Json) | ConvertFrom-Json -Depth 20)

foreach ($group in @($lgaPage.councilors | Group-Object { $_.council.id })) {
    $members = @($group.Group)
    $council = $members[0].council
    $officialName = [string]$council.name.en
    $prefix = $null
    $targetName = $officialName
    if ($officialName -match '^([A-Za-z]+)\.\s*(.+?)\s+(?:City\s+)?Council\s*$') {
        $prefix = Normalize-Key $Matches[1]
        $targetName = $Matches[2]
    } elseif ($officialName -match '^(.+?)\s+(?:City\s+)?Council\s*$') {
        $targetName = $Matches[1]
    }

    $targetKey = Normalize-Key $targetName
    if ($councilAliases.ContainsKey($targetKey)) { $targetKey = $councilAliases[$targetKey] }
    $matches = @($script:islands | Where-Object {
        (Normalize-Key $_.englishName) -eq $targetKey -and
            (-not $prefix -or (Normalize-Key $atollCodes[$_.atoll]) -eq $prefix)
    })

    if ($officialName -eq 'Thinadhoo City Council') {
        $matches = @(Get-Island 'Thinadhoo' 'Gaafu Dhaalu')
    } elseif ($officialName -eq "Male' City Council") {
        $matches = @($script:islands | Where-Object { $_.atoll -eq 'MLE' })
    } elseif ($officialName -eq 'Addu City Council') {
        $matches = @($script:islands | Where-Object {
            $_.atoll -eq 'Seenu' -and
                $_.englishName -in @('Hithadhoo', 'Feydhoo', 'Maradhoo', 'Maradhoofeydhoo')
        })
    }

    $leader = $members | Where-Object { $_.phone } | Sort-Object @{
        Expression = {
            $designation = [string]$_.designation.en
            if ($designation -match '(?i)^Council President|Mayor') { 0 }
            elseif ($designation -match '(?i)Vice') { 1 }
            else { 2 }
        }
    } | Select-Object -First 1
    $phones = if ($council.phone) {
        @(Get-PhoneNumbers ([string]$council.phone))
    } elseif ($leader.phone) {
        @(Get-PhoneNumbers ([string]$leader.phone))
    } else {
        @()
    }
    if ($matches.Count -eq 0 -or $phones.Count -eq 0) { continue }

    $serviceLabel = if ($council.phone) {
        'Council office'
    } else {
        ([string]$leader.designation.en).Trim()
    }
    foreach ($island in $matches) {
        $entry = Get-OrCreateEntry $island
        $entry.council = New-Contact `
            $serviceLabel `
            $officialName `
            $phones `
            'Local Government Authority' `
            $lgaUrl
    }
}

$healthHtml = (Invoke-WebRequest -UseBasicParsing $healthUrl).Content
$healthRows = foreach ($row in [regex]::Matches($healthHtml, '(?s)<tr>(.*?)</tr>')) {
    $cells = @(
        [regex]::Matches($row.Groups[1].Value, '(?s)<td[^>]*>(.*?)</td>') |
            ForEach-Object {
                $plain = [regex]::Replace($_.Groups[1].Value, '<[^>]+>', ' ')
                [Net.WebUtility]::HtmlDecode($plain).Trim() -replace '\s+', ' '
            }
    )
    if ($cells.Count -ge 3 -and $cells[0] -match 'Health Centre') {
        [pscustomobject]@{ name = $cells[0]; phone = $cells[2] }
    }
}

$healthAliases = @{
    'aminadhiyo' = @('Haa Alifu|Ihavandhoo')
    'aminarehendhi' = @('Haa Alifu|Baarah')
    'gaafudhaaluhoandedhdhoo' = @("Gaafu Dhaalu|Hoan'dehdhoo")
    'seenuhulhumeedhoo' = @('Seenu|Addu Hulhudhoo', 'Seenu|Addu Meedhoo')
}

foreach ($facility in $healthRows) {
    $coreKey = Normalize-Key ([string]$facility.name -replace '(?i)\s+Health Centre\s*$', '')
    if ($healthAliases.ContainsKey($coreKey)) {
        $targets = @($healthAliases[$coreKey])
        $matches = @($script:islands | Where-Object { ($_.atoll + '|' + $_.englishName) -in $targets })
    } else {
        $matches = @($script:islands | Where-Object {
            (Normalize-Key ($_.atoll + $_.englishName)) -eq $coreKey
        })
    }
    $phones = @(Get-PhoneNumbers ([string]$facility.phone))
    if ($matches.Count -eq 0 -or $phones.Count -eq 0) { continue }

    foreach ($island in $matches) {
        $entry = Get-OrCreateEntry $island
        $entry.health = New-Contact `
            'Health centre' `
            ([string]$facility.name) `
            $phones `
            'Ministry of Health' `
            $healthUrl
    }
}

# The LGA directory currently omits Hanimaadhoo's office number. Its council site
# publishes the office line directly, so prefer that over a councillor's mobile.
$hanimaadhoo = Get-Island 'Hanimaadhoo' 'Haa Dhaalu'
$hanimaadhooEntry = Get-OrCreateEntry $hanimaadhoo
$hanimaadhooEntry.council = New-Contact `
    'Council office' `
    'HDh. Hanimaadhoo Council' `
    @('4002064') `
    'Hanimaadhoo Council' `
    $hanimaadhooCouncilUrl

# Kulhudhuffushi has a regional hospital instead of an island health centre.
$kulhudhuffushi = Get-Island 'Kulhudhuffushi' 'Haa Dhaalu'
$kulhudhuffushiEntry = Get-OrCreateEntry $kulhudhuffushi
$kulhudhuffushiEntry.health = New-Contact `
    'Regional hospital' `
    'Kulhudhuffushi Regional Hospital' `
    @('1401', '6528864') `
    'Kulhudhuffushi Regional Hospital' `
    $kulhudhuffushiHospitalUrl

$contacts = @($script:entries.Values | Sort-Object atoll, islandName)
$councilCount = @($contacts | Where-Object { $_.council }).Count
$healthCount = @($contacts | Where-Object { $_.health }).Count
if ($councilCount -lt 175) { throw "Only $councilCount council contacts were mapped." }
if ($healthCount -lt 160) { throw "Only $healthCount health contacts were mapped." }

$asset = [ordered]@{
    version = 1
    snapshotDate = $SnapshotDate
    sources = @(
        [ordered]@{ label = 'Local Government Authority'; url = $lgaUrl }
        [ordered]@{ label = 'Ministry of Health'; url = $healthUrl }
        [ordered]@{ label = 'Hanimaadhoo Council'; url = $hanimaadhooCouncilUrl }
        [ordered]@{ label = 'Kulhudhuffushi Regional Hospital'; url = $kulhudhuffushiHospitalUrl }
    )
    count = $contacts.Count
    councilCount = $councilCount
    healthCount = $healthCount
    contacts = $contacts
}

$json = $asset | ConvertTo-Json -Depth 10
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
[IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Host "Wrote $($contacts.Count) island entries ($councilCount council, $healthCount health) to $resolvedOutput"
