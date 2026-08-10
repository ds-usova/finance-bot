import { describe, expect, it } from 'vitest';

// A rule that holds for every file in the module is asserted once here rather than repeated per file.
// The sources are read through Vite's own glob rather than `node:fs`, which would need `@types/node`.
const sources = import.meta.glob('./**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

// A plan's own item ids and a design's decision numbers: ST01, RU03, GS01, D34, Q1, P01, B2.
const CITATION = /\b(?:ST|RU|RI|RS|GU|GI|GS)\d{2}\b|\b[DQPB]\d{1,2}\b/;

// Only a comment can cite one: a string literal holding something like `D5` is data, not a reference to a plan.
function citationsIn(contents: string): string[] {
  return contents
    .split('\n')
    .filter((line) => /(^|\s)(\/\/|\/\*|\*)/.test(line))
    .filter((line) => CITATION.test(line))
    .map((line) => line.trim());
}

describe('the module’s own conventions', () => {
  it('cites no plan step or design decision by number, in any comment', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !path.includes('/generated/'))
      .flatMap(([path, contents]) =>
        citationsIn(contents).map((line) => `${path.slice(2)}: ${line}`),
      );

    // Named in full rather than counted, so a failure says which comment to rewrite.
    expect(offenders).toEqual([]);
  });
});
