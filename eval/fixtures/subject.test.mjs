import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchesPassword } from './subject.mjs';
test('correct short password', () => assert.equal(matchesPassword('correct', 'correct'), true));
test('wrong short password', () => assert.equal(matchesPassword('wrong', 'correct'), false));
