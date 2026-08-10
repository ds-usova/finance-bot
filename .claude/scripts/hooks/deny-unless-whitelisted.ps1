# Refuses any shell command no `permissions.allow` rule names, rather than letting it raise an approval
# prompt. A prompt is the thing that ties someone to the terminal: an agent that stops on a refusal can be
# left alone, one that waits on a dialog cannot.
#
# The allow lists in `.claude/settings.json` and `.claude/settings.local.json` are the only source of truth,
# read fresh on every call, so the whitelist never drifts from a second copy kept here.
#
# This script only ever refuses. When a rule looks like it covers the command it stays silent and the normal
# permission evaluation decides, so a mistake here can withhold a command but can never grant one.

$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$cmd = $j.tool_input.command
if (-not $cmd) { return }
$cmd = $cmd.Trim()
if ($cmd -eq '') { return }

# The tool whose rules apply — Bash(...) rules gate the Bash tool, PowerShell(...) rules the PowerShell one.
$tool = $j.tool_name
if ($tool -ne 'Bash' -and $tool -ne 'PowerShell') { return }

# A prefix rule is matched here by plain string comparison, but the permission system's own matcher first has to
# decompose the command, and it gives up on one carrying `$` or a backtick. A command this script lets through on
# the strength of `Bash(wc:*)` can therefore still fail to match that same rule - and a command that is neither
# refused here nor granted there is the one thing this whole arrangement exists to prevent: an approval prompt,
# waiting on somebody who is not at the terminal. So anything holding one is refused outright, whatever rule
# appears to cover it.
if ($cmd -match '[$`]') {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = "This command carries a '`$' or a backtick, which the permission " +
                "matcher cannot decompose - so it would raise an approval prompt even though a rule appears " +
                "to cover it, and a prompt nobody answers hangs the run. Write the command without it: a " +
                "literal dollar in a path is usually a file the Read, Glob or Grep tool should be opening " +
                "instead. Refused: '$cmd'."
        }
    } | ConvertTo-Json -Compress
    return
}

$repoRoot = Join-Path $PSScriptRoot '../../..'
$settingsFiles = @(
    (Join-Path $repoRoot '.claude/settings.json'),
    (Join-Path $repoRoot '.claude/settings.local.json')
)

$rules = @()
$readAny = $false
foreach ($file in $settingsFiles) {
    if (-not (Test-Path $file)) { continue }
    try {
        $settings = Get-Content $file -Raw | ConvertFrom-Json
    } catch {
        # A malformed settings file must not quietly widen what may run.
        @{
            hookSpecificOutput = @{
                hookEventName = "PreToolUse"
                permissionDecision = "deny"
                permissionDecisionReason = "The whitelist could not be read: $file is not valid JSON, so no " +
                    "command can be checked against it. Fix that file before running anything else."
            }
        } | ConvertTo-Json -Compress
        return
    }
    $readAny = $true
    if ($settings.permissions -and $settings.permissions.allow) {
        $rules += $settings.permissions.allow
    }
}

if (-not $readAny) {
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = "No settings file was found under .claude/, so there is no whitelist to " +
                "check this command against."
        }
    } | ConvertTo-Json -Compress
    return
}

# `Bash(git add:*)` and `Bash(ls:*)` are prefix rules; `Bash(bash script.sh --help)` is an exact one. A prefix
# matches only at a word boundary, so `Bash(ls:*)` covers `ls -a` and leaves `lsof` to be refused.
foreach ($rule in $rules) {
    if ($rule -notmatch "^$tool\((.*)\)$") { continue }
    $pattern = $Matches[1]

    if ($pattern.EndsWith(':*')) {
        $prefix = $pattern.Substring(0, $pattern.Length - 2)
    } elseif ($pattern.EndsWith(' *')) {
        $prefix = $pattern.Substring(0, $pattern.Length - 2)
    } else {
        if ($cmd -eq $pattern) { return }
        continue
    }

    if ($cmd -eq $prefix -or $cmd.StartsWith($prefix + ' ')) { return }
}

@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = "No allow rule covers this command, and this project refuses what it has " +
            "not whitelisted rather than asking - nobody may be at the terminal to answer. Use a tool or " +
            "script that is already allowed, or ask for a rule to be added to permissions.allow in " +
            ".claude/settings.json. Refused: '$cmd'."
    }
} | ConvertTo-Json -Compress
