# Vibe Player bridge: reads what is playing on Windows (System Media Transport Controls)
# and controls it. Output: one JSON object per line on stdout. Input: one command per line on stdin.
# Exits when stdin is closed (the game quit).
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
[Console]::InputEncoding = $utf8

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await($op, [Type]$type) {
    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    $t.Result
}

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress -Depth 4))
    [Console]::Out.Flush()
}

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
$TManager = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$TProps = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$TStream = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
$TInput = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$AsStreamForRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@($TInput))

try {
    $mgr = Await ($TManager::RequestAsync()) $TManager
} catch {
    Emit @{ type = 'error'; msg = "media sessions unavailable: $($_.Exception.Message)" }
    exit 2
}
Emit @{ type = 'hello'; version = 1 }

# [Console]::In.ReadLineAsync() blocks; a StreamReader over the raw stdin stream reads in the background
$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), $utf8)
$pending = $stdin.ReadLineAsync()
$pinned = ''
$props = $null
$propsAt = [DateTime]::MinValue
$propsApp = ''
$thumbKey = ''
$thumbTries = 0
$lastJson = ''
$lastEmit = [DateTime]::MinValue

function Pick {
    $all = @($mgr.GetSessions())
    if ($pinned -ne '') {
        foreach ($s in $all) { if ($s.SourceAppUserModelId -eq $pinned) { return $s } }
    }
    $cur = $mgr.GetCurrentSession()
    if ($cur -ne $null) { return $cur }
    foreach ($s in $all) { if ($s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { return $s } }
    if ($all.Count -gt 0) { return $all[0] }
    return $null
}

function Run($cmd) {
    $parts = $cmd.Trim().Split(' ', 2)
    $name = $parts[0]
    $arg = if ($parts.Count -gt 1) { $parts[1] } else { '' }
    if ($name -eq 'pin') { $script:pinned = if ($arg -eq 'auto') { '' } else { $arg }; $script:propsAt = [DateTime]::MinValue; return }
    if ($name -eq 'thumb') { $script:thumbKey = ''; return }
    $s = Pick
    if ($s -eq $null) { return }
    switch ($name) {
        'toggle' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
        'play' { Await ($s.TryPlayAsync()) ([bool]) | Out-Null }
        'pause' { Await ($s.TryPauseAsync()) ([bool]) | Out-Null }
        'next' { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null; $script:propsAt = [DateTime]::MinValue }
        'prev' { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null; $script:propsAt = [DateTime]::MinValue }
        'seek' {
            $ticks = [long]([double]::Parse($arg, [Globalization.CultureInfo]::InvariantCulture) * 10000000)
            Await ($s.TryChangePlaybackPositionAsync($ticks)) ([bool]) | Out-Null
        }
    }
}

function SendThumb($p, $key) {
    $stream = Await ($p.Thumbnail.OpenReadAsync()) $TStream
    # PowerShell cannot bind the WinRT stream to the overload by itself: call it through reflection
    $net = $AsStreamForRead.Invoke($null, @($stream))
    $ms = New-Object System.IO.MemoryStream
    $net.CopyTo($ms)
    $net.Dispose()
    $ms.Position = 0
    $img = [System.Drawing.Image]::FromStream($ms)
    $side = [Math]::Min($img.Width, $img.Height)
    $sx = [int](($img.Width - $side) / 2)
    $sy = [int](($img.Height - $side) / 2)
    $bmp = New-Object System.Drawing.Bitmap 128, 128
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle 0, 0, 128, 128),
        (New-Object System.Drawing.Rectangle $sx, $sy, $side, $side), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $out = New-Object System.IO.MemoryStream
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $img.Dispose()
    Emit @{ type = 'thumb'; key = $key; png = [Convert]::ToBase64String($out.ToArray()) }
}

while ($true) {
    try {
        while ($pending.IsCompleted) {
            $line = $pending.Result
            if ($line -eq $null) { exit 0 }
            $pending = $stdin.ReadLineAsync()
            if ($line.Trim() -ne '') { Run $line }
        }

        $apps = @()
        foreach ($s in @($mgr.GetSessions())) { $apps += $s.SourceAppUserModelId }
        $s = Pick
        if ($s -eq $null) {
            $state = @{ type = 'state'; app = ''; sessions = $apps; pinned = $pinned }
        } else {
            $app = $s.SourceAppUserModelId
            $now = [DateTime]::UtcNow
            if ($props -eq $null -or $propsApp -ne $app -or ($now - $propsAt).TotalMilliseconds -ge 700) {
                $props = Await ($s.TryGetMediaPropertiesAsync()) $TProps
                $propsAt = $now
                $propsApp = $app
            }
            $tl = $s.GetTimelineProperties()
            $pi = $s.GetPlaybackInfo()
            $key = "$app|$($props.Title)|$($props.Artist)"
            $state = @{
                type     = 'state'
                app      = $app
                title    = "$($props.Title)"
                artist   = "$($props.Artist)"
                album    = "$($props.AlbumTitle)"
                status   = "$($pi.PlaybackStatus)"
                pos      = [Math]::Round($tl.Position.TotalSeconds, 2)
                dur      = [Math]::Round(($tl.EndTime - $tl.StartTime).TotalSeconds, 2)
                upd      = $tl.LastUpdatedTime.ToUnixTimeMilliseconds()
                next     = [bool]$pi.Controls.IsNextEnabled
                prev     = [bool]$pi.Controls.IsPreviousEnabled
                seek     = [bool]$pi.Controls.IsPlaybackPositionEnabled
                toggle   = [bool]($pi.Controls.IsPlayPauseToggleEnabled -or $pi.Controls.IsPlayEnabled -or $pi.Controls.IsPauseEnabled)
                key      = $key
                sessions = $apps
                pinned   = $pinned
            }
            if ($key -ne $thumbKey) {
                if ($props.Thumbnail -ne $null) {
                    try { SendThumb $props $key; $thumbKey = $key; $thumbTries = 0 }
                    catch { $thumbTries++; if ($thumbTries -gt 12) { $thumbKey = $key; $thumbTries = 0 } }
                } else {
                    # the app often sets the cover a moment after the title
                    $thumbTries++
                    if ($thumbTries -gt 12) { Emit @{ type = 'thumb'; key = $key; png = '' }; $thumbKey = $key; $thumbTries = 0 }
                }
            }
        }
        $json = $state | ConvertTo-Json -Compress -Depth 4
        $nowEmit = [DateTime]::UtcNow
        if ($json -ne $lastJson -or ($nowEmit - $lastEmit).TotalMilliseconds -ge 2000) {
            [Console]::Out.WriteLine($json)
            [Console]::Out.Flush()
            $lastJson = $json
            $lastEmit = $nowEmit
        }
    } catch {
        Emit @{ type = 'error'; msg = "$($_.Exception.Message)" }
        $props = $null
        Start-Sleep -Milliseconds 800
    }
    Start-Sleep -Milliseconds 250
}
