@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = "Use the Grep tool instead of shell grep - it's unprompted, needs no cd or absolute path, and supports -o, -A/-B/context, regex, and glob filtering directly. See CLAUDE.md 'Reading files'."
    }
} | ConvertTo-Json -Compress