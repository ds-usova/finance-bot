$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -notmatch '(?i)^\s*git\s+commit\b') {
    return
}

# Everything after the subcommand, with the message and the flags removed, is the pathspec.
$rest = $c -replace '(?i)^\s*git\s+commit\b', ''
$rest = $rest -replace "-m\s+'[^']*'", ' ' -replace '-m\s+"[^"]*"', ' '
$rest = $rest -replace '--message=\S+', ' ' -replace '-m\s+\S+', ' '
$rest = $rest -replace '(?<=\s|^)--?[A-Za-z][\w-]*(=\S+)?', ' '

$paths = $rest -split '\s+' | Where-Object { $_ -ne '' -and $_ -ne '--' }

# No pathspec means the whole index is committed, which cannot leave a new file behind.
if (-not $paths) {
    return
}

$untracked = & git status --porcelain --untracked-files=all -- @paths 2>$null |
    Where-Object { $_ -like '`?`? *' }

if (-not $untracked) {
    return
}

$names = ($untracked | ForEach-Object { $_.Substring(3) }) -join ', '

@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = 'A commit naming paths takes only what git already tracks, so these files ' +
            "would be left out of it silently: $names. Run 'git add' on the same paths first, then commit. " +
            "See docs/conventions/version-control.md, 'Completeness'."
    }
} | ConvertTo-Json -Compress
