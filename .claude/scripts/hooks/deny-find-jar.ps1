$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command
if ($c -match "(?i)-i?name" -and $c -match "(?i)jar") {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = "Use tools/inspect-jar instead of hunting jars with find - it answers dependency questions directly. See tools/README.md."
        }
    } | ConvertTo-Json -Compress
}
