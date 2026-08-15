$j = [Console]::In.ReadToEnd() | ConvertFrom-Json

$path = $j.tool_input.file_path
if (-not $path) {
    return
}

# One spelling, so a Windows path matches the same rule a POSIX one does.
$p = $path -replace '\\', '/'

# What ships as a plugin: the skills, the sub-agents they spawn, the mechanics those invoke, the templates they
# point at. Hooks are the exception - they are wired from this repository's settings.json, are pulled nowhere,
# and exist precisely to name the tooling they enforce.
if ($p -notmatch '\.claude/(skills|agents|templates|scripts)/') {
    return
}
if ($p -match '\.claude/scripts/hooks/') {
    return
}

# Edit carries the replacement, Write the whole file. Either is what is about to land.
$content = $j.tool_input.new_string
if ($null -eq $content) {
    $content = $j.tool_input.content
}
if ($null -eq $content) {
    return
}

# A name, never a shape. "<module>/docs/conventions.md" travels; "web-app" does not.
$tokens = @('ledger-service', 'web-app', 'ai-connector-service', 'bot.finance', 'tools/')
$hit = $tokens | Where-Object { $content -match [regex]::Escape($_) } | Select-Object -First 1

if ($hit) {
    $reason = "'$hit' is this repository's answer, and $p ships into other repositories as a plugin. A skill " +
              'names the subject it needs - the build command, the test-type mapping, the sub-agent models - ' +
              'and lets the repository state it in its own conventions; see .claude/README.md. Write the ' +
              'placeholder form (<module>, <the module conventions>) or point at the file that owns the answer. ' +
              'Hooks under .claude/scripts/hooks are exempt: they are configuration, not plugin.'
    @{
        hookSpecificOutput = @{
            hookEventName = "PreToolUse"
            permissionDecision = "deny"
            permissionDecisionReason = $reason
        }
    } | ConvertTo-Json -Compress
}
