import { describe, expect, it } from 'vitest';

// A rule that holds for every file in the module is asserted once here rather than repeated per file.
// The sources are read through Vite's own glob rather than `node:fs`, which would need `@types/node`.
const sources = import.meta.glob('./**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const CITATION = /\b(?:ST|RU|RI|RS|GU|GI|GS)\d{2}\b|\b[ADQPB]\d{1,2}\b/;

// Carried as data rather than as a comment, which the scan would read as a citation of its own, and shown in
// the failure so it says what shape it refused.
const EXAMPLES = 'ST01, RU03, GS01, D34, A26, Q1, P01, B2';

// Only a comment can cite one: a string literal holding something like `D5` is data, not a reference to a plan.
function citationsIn(contents: string): string[] {
  return contents
    .split('\n')
    .filter((line) => /(^|\s)(\/\/|\/\*|\*)/.test(line))
    .filter((line) => CITATION.test(line))
    .map((line) => line.trim());
}

const STRING_LITERAL = /(['"`])((?:(?!\1)[^\\\n]|\\.)*)\1/g;

// A responsive `col-span-*` writes the whole `grid-column` shorthand, so beside a `col-start-*` in the same list it
// throws the start line away from that breakpoint up and leaves the cell to auto-placement. Rows likewise.
function spansDroppingAStartIn(contents: string): string[] {
  return [...contents.matchAll(STRING_LITERAL)]
    .map((match) => match[2])
    .filter((literal) => {
      const tokens = literal.split(/\s+/);
      return (['col', 'row'] as const).some(
        (axis) =>
          tokens.some((token) => new RegExp(`^${axis}-start-`).test(token)) &&
          tokens.some((token) => new RegExp(`^[\\w-]+:${axis}-span-`).test(token)),
      );
    });
}

describe('the module’s own conventions', () => {
  it('never lets a breakpoint’s span move a grid cell off the line it starts on', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !path.includes('/generated/') && !path.endsWith('conventions.test.ts'))
      .flatMap(([path, contents]) =>
        spansDroppingAStartIn(contents).map((literal) => `${path.slice(2)}: '${literal}'`),
      );

    expect(
      offenders,
      'class lists pairing a col-start or row-start with a breakpoint’s span on the same axis; end the cell with col-end or row-end instead',
    ).toEqual([]);
  });


  it('cites no plan step, design decision or acceptance scenario by number, in any comment', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !path.includes('/generated/'))
      .flatMap(([path, contents]) =>
        citationsIn(contents).map((line) => `${path.slice(2)}: ${line}`),
      );

    // Named in full rather than counted, so a failure says which comment to rewrite.
    expect(
      offenders,
      `comments citing a plan step, a decision or a scenario by number, such as ${EXAMPLES}`,
    ).toEqual([]);
  });
});
