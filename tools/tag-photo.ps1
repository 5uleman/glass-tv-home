# Measures a wallpaper URL the same way as the catalog audit.
# Usage: powershell -File tools/tag-photo.ps1 "https://images.example/photo.jpg?w=2560&h=1440"
param([Parameter(Mandatory = $true)][string]$Url)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$source = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class TagPhotoLuma
{
    public static double[] Measure(string path)
    {
        using (var bmp = new Bitmap(path))
        {
            int w = bmp.Width;
            int h = bmp.Height;
            var rect = new Rectangle(0, 0, w, h);
            var data = bmp.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
            try
            {
                int stride = data.Stride;
                byte[] bytes = new byte[stride * h];
                Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);
                double sum = 0;
                long n = 0;
                for (int y = 0; y < h; y++)
                {
                    int row = y * stride;
                    for (int x = 0; x < w; x++)
                    {
                        int i = row + x * 3;
                        sum += 0.299 * bytes[i + 2] + 0.587 * bytes[i + 1] + 0.114 * bytes[i];
                        n++;
                    }
                }
                return new double[] { sum / n, w, h };
            }
            finally
            {
                bmp.UnlockBits(data);
            }
        }
    }
}
'@

Add-Type -TypeDefinition $source -ReferencedAssemblies System.Drawing

$ua = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GlassTV/2.4 (https://github.com/aqmangge/glass-tv-home)"
$temp = Join-Path $env:TEMP ("tag-photo-" + [guid]::NewGuid().ToString("N") + ".jpg")

try {
    $response = (& curl.exe -L -sS --max-time 45 --connect-timeout 10 -A $ua -o $temp -w "%{http_code}|%{content_type}|%{size_download}" $Url 2>$null) -join ""
    $parts = $response -split "\|"
    $status = if ($parts.Count -ge 3) { [int]$parts[0] } else { -1 }
    $type = if ($parts.Count -ge 2) { $parts[1] } else { "unknown" }
    if ($status -ne 200) {
        Write-Output "HTTP $status ($type)"
        exit 1
    }
    $kb = [math]::Round((Get-Item $temp).Length / 1KB, 1)
    $m = [TagPhotoLuma]::Measure($temp)
    $luma = [math]::Round($m[0], 1)
    $tag = if ($luma -ge 115) { "DAY" } elseif ($luma -le 85) { "NIGHT" } else { "BOTH" }
    Write-Output "HTTP 200 | $type | $($m[1])x$($m[2]) | $kb KB"
    Write-Output "Luma $luma -> $tag"
    Write-Output "Review by eye if it is a sunset, sunrise, indoor or neutral scene."
}
finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
