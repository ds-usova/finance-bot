/** A call the test settles itself, so it can be held open across other work — a second interaction, or a
 * faster call made to answer first. */
export function inFlight<T>() {
  let answer!: (value: T) => void;
  let refuse!: (error: unknown) => void;
  const promise = new Promise<T>((resolve, reject) => {
    answer = resolve;
    refuse = reject;
  });
  return { promise, answer, refuse };
}
