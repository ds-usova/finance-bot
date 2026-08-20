# Conventions > Testing

What holds for tests in every module, whatever its stack. A module's own `testing.md` carries its tooling, its
test types and its naming, and never repeats what is here.

## Wait for What Is Eventual, Assert the Rest Once

A test that awaits polls for **one** condition: the thing that can only become true by waiting — a row has
reached its final state, a queue has drained, a message has been acknowledged. Once that condition holds, every
fact that follows from it is settled, and it is asserted once, outside the wait.

An assertion inside the poll that cannot become true by waiting — a field the write set in the same statement,
a count that will not change — is not waited for; it fails at the timeout instead of at once, and reads as
eventual when it is not.
