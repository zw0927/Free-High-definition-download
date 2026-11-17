$ErrorActionPreference = 'Stop'
$skillDirectory = Join-Path $env:USERPROFILE '.codex\skills\opus-5-5-86-styles-in-motion'
$destination = Join-Path $PSScriptRoot 'styles-gallery'
$catalog = Get-Content -LiteralPath (Join-Path $skillDirectory 'assets\atlas\catalog.json') -Raw | ConvertFrom-Json
$labels = Get-Content -LiteralPath (Join-Path $skillDirectory 'references\gallery-zh.json') -Raw | ConvertFrom-Json
$template = Get-Content -LiteralPath (Join-Path $skillDirectory 'assets\gallery.template.html') -Raw
$records = @()
foreach ($style in $catalog.styles) {
    $records += @{
        id = $style.id
        name = $style.name
        nameZh = $labels.names[$style.id - 1]
        family = $style.family
        familyZh = $labels.families.($style.family)
        description = $style.description
        image = $style.media.poster.url
        explainer = $style.media.explainer.url
        styleClip = $style.media.styleClip.url
    }
}
if ($records.Count -ne 86) {
    throw 'Expected all 86 original style entries.'
}
$payload = ConvertTo-Json -InputObject $records -Depth 6 -Compress
$payload = $payload.Replace('<', '\u003c')
$html = $template.Replace('__STYLE_DATA__', $payload)
$html = $html.Replace('点击主图播放案例，复制名称发给我。', '点击主图播放案例，复制名称发给我。此图库的原始图片和视频需要联网加载。')
New-Item -ItemType Directory -Path $destination -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $destination 'index.html'), $html, [Text.UTF8Encoding]::new($false))
Write-Output ('Gallery: ' + (Join-Path $destination 'index.html'))
