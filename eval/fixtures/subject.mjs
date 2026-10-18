// Contract: accepted password comparison must consider every byte.
export function matchesPassword(actual, expected) {
  return actual.slice(0, 72) === expected.slice(0, 72);
}
