# Appends one JSON line per sub-agent event to build/agent-log.jsonl.
# Fed by SubagentStart, SubagentStop and PreToolUse(Agent); the raw payload is
# kept in full so nothing is lost while the field set is still being learned.
$raw = [Console]::In.ReadToEnd()
$j = $raw | ConvertFrom-Json

$dir = Join-Path (git rev-parse --show-toplevel) 'build'
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
$log = Join-Path $dir 'agent-log.jsonl'

$entry = [ordered]@{
    ts         = (Get-Date).ToUniversalTime().ToString('o')
    event      = $j.hook_event_name
    session_id = $j.session_id
    agent_id   = $j.agent_id
    agent_type = $j.agent_type
    effort     = $j.effort.level
}
if ($j.hook_event_name -eq 'PreToolUse') {
    $entry.agent_type  = $j.tool_input.subagent_type
    $entry.model       = $j.tool_input.model
    $entry.description = $j.tool_input.description
    $j.tool_input.PSObject.Properties.Remove('prompt')
}
if ($j.hook_event_name -eq 'SubagentStop' -and $j.agent_transcript_path -and (Test-Path $j.agent_transcript_path)) {
    $seen = @{}
    $models = @{}
    $usage = [ordered]@{ input = 0; output = 0; cache_read = 0; cache_create = 0; turns = 0; tool_uses = 0 }
    foreach ($line in [System.IO.File]::ReadLines($j.agent_transcript_path)) {
        if ($line -notmatch '"role":"assistant"') { continue }
        try { $m = ($line | ConvertFrom-Json).message } catch { continue }
        if (-not $m -or -not $m.id -or $seen.ContainsKey($m.id)) {
            # streamed messages repeat one id per content block; usage is per message
            if ($m -and $m.content) { $usage.tool_uses += @($m.content | Where-Object { $_.type -eq 'tool_use' }).Count }
            continue
        }
        $seen[$m.id] = $true
        $usage.turns++
        if ($m.model) { $models[$m.model] = $true }
        if ($m.content) { $usage.tool_uses += @($m.content | Where-Object { $_.type -eq 'tool_use' }).Count }
        $u = $m.usage
        if ($u) {
            $usage.input        += [int]$u.input_tokens
            $usage.output       += [int]$u.output_tokens
            $usage.cache_read   += [int]$u.cache_read_input_tokens
            $usage.cache_create += [int]$u.cache_creation_input_tokens
        }
    }
    $usage.total = $usage.input + $usage.output + $usage.cache_read + $usage.cache_create
    $entry.model = ($models.Keys | Sort-Object) -join ','
    $entry.usage = $usage
}
$j.PSObject.Properties.Remove('last_assistant_message')
$entry.raw = $j

Add-Content -Path $log -Value ($entry | ConvertTo-Json -Compress -Depth 20)
