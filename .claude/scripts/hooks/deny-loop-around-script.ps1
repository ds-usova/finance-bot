$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -notmatch "(?i)^\s*for\s+\S+\s+in\b") {
    return
}

$reason = $null
if ($c -match "(?i)plan\.sh") {
    $reason = "plan.sh's tick/show accept multiple IDs in one call - pass them all at once (e.g. 'plan.sh tick ST01 ST02 ST03 --plan <file>') instead of looping."
} elseif ($c -match "(?i)(agent-test\.sh|inspect-jar\.sh|gradlew)") {
    $reason = "Looping over an already-allowlisted script breaks permission matching (the command no longer starts with the allowed prefix). Call it once if it accepts multiple arguments, or issue separate Bash invocations instead of a shell loop."
}

if ($reason) {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = $reason
        }
    } | ConvertTo-Json -Compress
}
