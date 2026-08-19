$j = [Console]::In.ReadToEnd() | ConvertFrom-Json
$c = $j.tool_input.command

if ($c -notmatch '(?i)^\s*git\s+commit\b') {
    return
}

# Only the message is searched. A pathspec may legitimately name a plan file, and so may a heredoc's own
# surrounding syntax, so the ids are looked for where a reader would meet them.
$messages = @()
foreach ($m in [regex]::Matches($c, "(?s)-m\s+'([^']*)'")) { $messages += $m.Groups[1].Value }
foreach ($m in [regex]::Matches($c, '(?s)-m\s+"([^"]*)"')) { $messages += $m.Groups[1].Value }
foreach ($m in [regex]::Matches($c, '(?s)--message=(\S+)')) { $messages += $m.Groups[1].Value }

if (-not $messages) {
    return
}

# ST01, RU02, RI03, RS04, GU05, GI06, GS07, P01 - the plan's own step ids, and D3/F12/A7/Q2 from a design.
$stepId = '\b(ST|RU|RI|RS|GU|GI|GS|P|D|F|A|Q|R)\d{1,3}\b'

$found = @()
foreach ($message in $messages) {
    foreach ($hit in [regex]::Matches($message, $stepId)) {
        if ($found -notcontains $hit.Value) { $found += $hit.Value }
    }
}

if (-not $found) {
    return
}

$names = $found -join ', '

@{
    hookSpecificOutput = @{
        hookEventName = "PreToolUse"
        permissionDecision = "deny"
        permissionDecisionReason = "A commit message names no plan step, design entry or finding, and this one " +
            "names $names. An id belongs to a document that is archived once the work lands, so the message stops " +
            "resolving the moment it would be read. Say what the commit does instead. " +
            "See .claude/skills/plan-task/SKILL.md, 'An ID never leaves those places'."
    }
} | ConvertTo-Json -Compress
