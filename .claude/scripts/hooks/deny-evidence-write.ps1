$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -notmatch "(?i)evidence\.(md|json)") {
    return
}

# Naming an evidence file to stage, commit, move, or inspect it is fine - none of those can change
# its content, and a file that moved still records the run that produced it.
if ($c -match "(?i)^\s*git\s+(add|commit|diff|mv|show|log|status|restore)\b") {
    return
}

@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = "An evidence file is written only by tools/plan-evidence/plan-evidence.sh, which measures what it records. A shell command that edits, moves, or deletes one produces a number nothing measured. Re-run the script to refresh it, or --verify to check it."
    }
} | ConvertTo-Json -Compress
