# Conventions > Code Style

What holds for production code in every module, whatever its stack. A module's own `code-style.md` carries its
language's idioms and never repeats what is here.

## One Class, One Reason to Change

**A class has one reason to change, and the reason names who would open it.** Ask who arrives with the next
change to this file. Two answers mean two classes, and the seam between them is where the collaborator goes.
A schema change and a capture-process change are two answers; so are a storage change and a cache-policy change.

**Where the split follows a package or module boundary, the module's architecture test enforces it** rather
than review.
