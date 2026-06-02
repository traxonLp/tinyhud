package de.traxonlp.tinyhud.media;

import de.traxonlp.tinyhud.TinyHUD;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Persistent PowerShell process that polls SMTC every 250 ms internally.
 * Avoids per-poll process startup cost. poll() returns cached state instantly.
 *
 * Output per cycle: 6 lines - title, artist, posMs, durMs, playing(1/0), art(base64|SAME|"").
 * "SAME" means the thumbnail bytes are unchanged (SHA-1 identical); Java keeps the last URL.
 */
class WindowsMediaBackend implements MediaBackend {

    // PowerShell script fragments

    private static final String SCRIPT_SETUP =
        "[Console]::OutputEncoding=[Text.Encoding]::UTF8;" +
        "$null=[Reflection.Assembly]::Load('System.Runtime.WindowsRuntime,Version=4.0.0.0," +
            "Culture=neutral,PublicKeyToken=b77a5c561934e089');" +
        "$_atm=[System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{" +
            "$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and " +
            "$_.GetParameters()[0].ParameterType.Name.StartsWith('IAsyncOperation')" +
        "}|Select-Object -First 1;" +
        "function Await($op,[type]$t){" +
            "$task=$_atm.MakeGenericMethod($t).Invoke($null,@($op));" +
            "$task.Wait(-1)|Out-Null;$task.Result" +
        "};" +
        // PowerShell can't bind WinRT projected COM objects to extension-method overloads or
        // interface params directly, so grab AsStreamForRead(IInputStream) and invoke it via
        // reflection - reflection marshals the RCW to the interface where a direct call fails.
        "$_asr=[System.IO.WindowsRuntimeStreamExtensions].GetMethods()|Where-Object{" +
            "$_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1" +
        "}|Select-Object -First 1;" +
        "try{" +
        "$mgr=Await " +
            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager," +
                "Windows.Media.Control,ContentType=WindowsRuntime]::RequestAsync()) " +
            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager," +
                "Windows.Media.Control,ContentType=WindowsRuntime]);" +
        "$lastArtHash='';" +
        "while($true){try{";

    private static final String SESSION_AUTO =
        "$s=$mgr.GetCurrentSession();" +
        "if($null -eq $s){$all=$mgr.GetSessions();if($all.Count -gt 0){$s=$all[0]}};";

    private static final String SESSION_SPOTIFY =
        "$s=$null;" +
        "foreach($sess in $mgr.GetSessions()){" +
            "if($sess.SourceAppUserModelId -match 'Spotify'){$s=$sess;break}};";

    private static final String LOOP_BODY =
        "if($null -eq $s){" +
            "Write-Output '';Write-Output '';Write-Output 0;Write-Output 0;Write-Output 0;Write-Output '';" +
        "}else{" +
            "$p=Await ($s.TryGetMediaPropertiesAsync()) " +
                "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties," +
                    "Windows.Media.Control,ContentType=WindowsRuntime]);" +
            "$tl=$s.GetTimelineProperties();" +
            "$pb=$s.GetPlaybackInfo().PlaybackStatus;" +
            // SMTC Position is a snapshot taken at LastUpdatedTime, which can be several seconds
            // in the past. Add the wall-clock time elapsed since then so the position is current
            // as of this poll. Guard against bogus LastUpdatedTime (default value -> huge delta).
            "$posMs=$tl.Position.TotalMilliseconds;" +
            "$endMs=$tl.EndTime.TotalMilliseconds;" +
            "if($pb.ToString() -eq 'Playing'){" +
                "$d=([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds;" +
                "if($d -gt 0 -and $d -lt 86400000){$posMs += $d}" +
            "};" +
            "if($posMs -lt 0){$posMs=0};" +
            "if($endMs -gt 0 -and $posMs -gt $endMs){$posMs=$endMs};" +
            "Write-Output $(if($null -ne $p.Title){$p.Title}else{''});" +
            "Write-Output $(if($null -ne $p.Artist){$p.Artist}else{''});" +
            "Write-Output([long]$posMs);" +
            "Write-Output([long]$endMs);" +
            "if($pb.ToString() -eq 'Playing'){Write-Output '1'}else{Write-Output '0'};" +
            "if($null -ne $p.Thumbnail){try{" +
                "$stype=[Windows.Storage.Streams.IRandomAccessStreamWithContentType," +
                    "Windows.Storage,ContentType=WindowsRuntime];" +
                "$stream=Await ($p.Thumbnail.OpenReadAsync()) $stype;" +
                "$netStream=$_asr.Invoke($null,@($stream));" +
                "$ms=New-Object System.IO.MemoryStream;" +
                "$netStream.CopyTo($ms);" +
                "$artBytes=$ms.ToArray();" +
                "$sha=[System.Security.Cryptography.SHA1]::Create();" +
                "$hash=[Convert]::ToBase64String($sha.ComputeHash($artBytes));" +
                "if($hash -ne $lastArtHash){$lastArtHash=$hash;" +
                    "Write-Output([Convert]::ToBase64String($artBytes))" +
                "}else{Write-Output 'SAME'}" +
            "}catch{Write-Output ''}}" +
            "else{$lastArtHash='';Write-Output ''}" +
        "}" +
        "}catch{" +
            "[Console]::Error.WriteLine($_.Exception.Message);" +
            "Write-Output '';Write-Output '';Write-Output 0;Write-Output 0;Write-Output 0;Write-Output '';" +
        "};" +
        "[System.Threading.Thread]::Sleep(250)}" +   // closes while($true)
        "}catch{" +
            "[Console]::Error.WriteLine('FATAL: '+$_.Exception.Message)" +
        "}";

    // State

    private volatile MediaInfo cached      = MediaInfo.EMPTY;
    private volatile String    lastArt     = null;
    private volatile String    activeSource = null;
    private volatile Process   proc        = null;

    // MediaBackend

    @Override
    public synchronized MediaInfo poll(String source) {
        if (!source.equals(activeSource) || proc == null || !proc.isAlive()) {
            startProcess(source);
        }
        return cached;
    }

    // Internal

    private void startProcess(String source) {
        Process old = proc;
        proc = null;
        if (old != null) old.destroyForcibly();

        activeSource = source;
        cached = MediaInfo.EMPTY;
        lastArt = null;

        String session = "spotify".equals(source) ? SESSION_SPOTIFY : SESSION_AUTO;
        String script  = SCRIPT_SETUP + session + LOOP_BODY;

        try {
            Process p = new ProcessBuilder(
                    "powershell.exe", "-Sta", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(false)
                    .start();
            proc = p;

            // stderr logger
            Thread stderr = new Thread(() -> drainStderr(p), "tinyhud-smtc-err");
            stderr.setDaemon(true);
            stderr.start();

            // stdout reader
            Thread reader = new Thread(() -> readLoop(p), "tinyhud-smtc-reader");
            reader.setDaemon(true);
            reader.start();

            TinyHUD.LOGGER.info("[TinyHUD] SMTC process started (source='{}')", source);
        } catch (Exception e) {
            TinyHUD.LOGGER.warn("[TinyHUD] Failed to start SMTC process: {}", e.getMessage());
        }
    }

    private void readLoop(Process p) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            boolean firstData = true;
            while (true) {
                String title   = br.readLine(); if (title   == null) break;
                String artist  = br.readLine(); if (artist  == null) break;
                String posStr  = br.readLine(); if (posStr  == null) break;
                String durStr  = br.readLine(); if (durStr  == null) break;
                String playStr = br.readLine(); if (playStr == null) break;
                String artLine = br.readLine(); if (artLine == null) break;

                title  = title.trim();
                artist = artist.trim();

                String trimmedArt = artLine.trim();
                if ("SAME".equals(trimmedArt)) {
                    // keep lastArt unchanged - same String instance reused
                } else if (!trimmedArt.isEmpty()) {
                    lastArt = "base64:" + trimmedArt;
                } else {
                    lastArt = null;
                }

                MediaInfo info = new MediaInfo(
                        title, artist,
                        parseLong(posStr), parseLong(durStr),
                        "1".equals(playStr.trim()),
                        System.nanoTime(),
                        lastArt);
                cached = info;

                if (firstData && info.hasData()) {
                    firstData = false;
                    TinyHUD.LOGGER.info("[TinyHUD] SMTC first data: title='{}' artist='{}' hasArt={}",
                            title, artist, lastArt != null);
                }
            }
        } catch (Exception e) {
            TinyHUD.LOGGER.debug("[TinyHUD] SMTC reader ended: {}", e.getMessage());
        }
        // Process died - next poll() call will restart it
        cached = MediaInfo.EMPTY;
    }

    private static void drainStderr(Process p) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getErrorStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) TinyHUD.LOGGER.debug("[TinyHUD] SMTC: {}", line.trim());
            }
        } catch (Exception ignored) {}
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
