# Review: The Connector Files a Message's Spending with a Classifier

**Nothing open.**

## Bug

**`ai-connector-service` — a refused expense's description is logged at WARN**

- **Given** the classifier mode, and the ledger refusing an expense with INVALID_ARGUMENT
- **When** the turn files that expense
- **Then** no line above debug carries the user's text
- **Actual** one WARN carries the expense's description; an unusable read-out expense's WARN can carry its amount or currency code
- **Test** `ClassifierTurnTest#whenLedgerRefusesExpense_thenNoLineAboveDebugCarriesItsDescription`, disabled
- **Fix** log the expense's position, not its description, and drop the value from the domain exception messages logged at WARN — unverified · `ClassifierTurn.java`
- **Status** done · directly

**`ai-connector-service` — a classifier answer with no confidence files as low confidence, not as a failure**

- **Given** the classifier mode, and a classifier answer whose question carries a choice and probabilities but no confidence
- **When** the answer is read back
- **Then** the call fails, and every usable expense goes to the catch-all's first category
- **Actual** the absent confidence reads as 0, so the expense goes to the catch-all's most probable category and is counted low-confidence
- **Test** `SystemOneRequestMapperTest#whenAnswerCarriesNoConfidence_thenThrowsClassificationFailedException`, disabled
- **Fix** refuse an absent or non-numeric confidence or probability when reading the answer back — unverified · `SystemOneRequestMapper.java`
- **Status** done · directly
