$ErrorActionPreference = 'Stop'

$cfgPath = Join-Path $PSScriptRoot '..\src\main\resources\config.yml'
$petPath = Join-Path $PSScriptRoot '..\pet.txt'

$lines = Get-Content -Path $cfgPath -Encoding utf8

# GUI defaults
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '^\s*material:\s*GRAY_STAINED_GLASS_PANE\s*$') {
    $lines[$i] = '      material: LIME_STAINED_GLASS_PANE'
  }
  if ($lines[$i] -match '^\s*tab-selected:\s*') {
    $lines[$i] = '      tab-selected: EMERALD_BLOCK'
  }
  if ($lines[$i] -match '^\s*tab-unselected:\s*') {
    $lines[$i] = '      tab-unselected: CYAN_STAINED_GLASS_PANE'
  }
}

# Insert filter slots (if missing)
if (-not ($lines | Select-String -SimpleMatch 'filter-all:' -Quiet)) {
  $tabMineIdx1 = ($lines | Select-String -SimpleMatch 'tab-mine:' | Select-Object -First 1).LineNumber
  if ($tabMineIdx1) {
    $insertAt = $tabMineIdx1 # 1-based, insert after tab-mine line
    $before = $lines[0..($insertAt - 1)]
    $after = $lines[$insertAt..($lines.Count - 1)]
    $ins = @(
      '      filter-all: 3',
      '      filter-with-effects: 4',
      '      filter-without-effects: 5'
    )
    $lines = @($before + $ins + $after)
  }
}

# Locate pets section
$petsIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '^pets:\s*$') { $petsIdx = $i; break }
}
if ($petsIdx -lt 0) { throw 'Could not find pets: section in config.yml' }

$prefix = $lines[0..$petsIdx]

# Parse pet.txt
$petLines = Get-Content -Path $petPath -Encoding utf8 | Where-Object { $_ -match '^/give' }
$pets = @()
foreach ($l in $petLines) {
  $name = [regex]::Match($l, '"text":"([^"]+)"').Groups[1].Value
  $id = [regex]::Match($l, 'ID[^0-9]*(\d+)').Groups[1].Value
  $b64 = [regex]::Match($l, 'value:"([A-Za-z0-9+/=]+)"').Groups[1].Value
  if ($name -and $id -and $b64) {
    $pets += [pscustomobject]@{ id = [int]$id; name = $name; b64 = $b64 }
  }
}

$pets = $pets | Sort-Object id

$outPets = @()
foreach ($p in $pets) {
  $n = $p.name.Replace("'", "''")
  $b = $p.b64.Replace("'", "''")
  $outPets += "  - id: head_$($p.id)"
  $outPets += "    permission: pats.pets.head.$($p.id)"
  $outPets += "    display:"
  $outPets += "      ru: '$n'"
  $outPets += "      en: '$n'"
  $outPets += "    head:"
  $outPets += "      profile-name: 'mcheads-$($p.id)'"
  $outPets += "      textures: '$b'"
}

Set-Content -Path $cfgPath -Value @($prefix + $outPets) -Encoding utf8
Write-Host ("Updated config.yml pets count: {0}" -f $pets.Count)

