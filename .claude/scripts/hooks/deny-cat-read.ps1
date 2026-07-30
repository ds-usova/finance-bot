$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -match "(?i)/dev/null") {
    # no-op probes against the null device aren't real reads
    return
}

@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = "Use the Read tool to view file contents instead of shell cat - it's unprompted and needs no cd or absolute path. See CLAUDE.md 'Reading files'."
    }
} | ConvertTo-Json -Compress