$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -match "(?i)-i?name" -and $c -match "(?i)jar") {
    # jar hunts are handled by deny-find-jar.ps1's more specific message
    return
}

if ($c -match "(?i)-i?name") {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = "Use the Glob tool to locate files by name instead of shell find - it's unprompted and needs no cd or absolute path. See CLAUDE.md 'Reading files'."
        }
    } | ConvertTo-Json -Compress
}