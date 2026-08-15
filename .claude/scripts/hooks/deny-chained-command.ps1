$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

# Blank the inside of quoted spans so an operator in a commit message or a pattern does not count as a chain.
# The quotes themselves stay, so every segment keeps its leading token.
$bare = $c -replace "'[^']*'", "''" -replace '"[^"]*"', '""'

# A redirect hands the command's output to a path, so it writes wherever that path points while the allow rule
# only ever saw the leading token. `sleep 1 > important` truncates a file through a rule that permits sleeping.
if ($bare -match '>') {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = 'A redirect writes to a path the allow rule never saw: the rule matches ' +
                'the command''s leading token, and everything after ">" is a file this call would create or ' +
                'truncate. Use the Write tool for a file, and the run directories a wrapper already makes for ' +
                "output. Refused: '$c'."
        }
    } | ConvertTo-Json -Compress
    return
}

if ($bare -notmatch '(;|&&|\|)') {
    return
}

$segments = $bare -split '(?:;|&&|\|)' |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -ne '' }

# A chain of git commands is allowed: `git add -A && git commit -m "..."` is one intent, not two.
$nonGit = $segments | Where-Object { $_ -notmatch '(?i)^git\s' } | Select-Object -First 1

$reason = $null
if ($nonGit) {
    $reason = 'Chained shell commands are matched as one string, so the allowlist rule for the first command ' +
              'cannot cover the rest and the whole call prompts. Issue each command as its own Bash call - ' +
              'several in one message run in parallel anyway. Never append an echo of "LABEL=$?": the tool ' +
              'result already reports the exit code, and each new label mints a rule that never matches again. ' +
              "Offending segment: '$nonGit'."
} else {
    # Every segment is git, so the chain is allowed - unless it smuggles a denied subcommand past the deny list,
    # which matches only on the whole command's prefix.
    $denied = $segments |
        Where-Object { $_ -match '(?i)^git\s+(push|rebase|branch|clean|checkout)\b' -or $_ -match '(?i)^git\s+reset\s+--hard\b' } |
        Select-Object -First 1
    if ($denied) {
        $reason = 'A chain starting with an allowed git command hides a denied one: the deny rule matches the ' +
                  'whole command''s prefix, so "git add -A && git push" never matches Bash(git push:*). Run ' +
                  "'$denied' as its own call, where its rule applies."
    }
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
