$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -match "/\s*,\s*0\b" -or $c -match "/\s*,\s*/") {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = "Use Grep (with -A/context, or a two-step: Grep -n to find the line, then Read with offset/limit) instead of awk range extraction to pull part of a file. See CLAUDE.md 'Reading files'."
        }
    } | ConvertTo-Json -Compress
}