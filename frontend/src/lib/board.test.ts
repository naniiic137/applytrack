import { describe, expect, it } from 'vitest';
import { summary } from '../test/fixtures';
import { groupByStatus, moveApplication, parseTags } from './board';

describe('groupByStatus', () => {
  it('creates a column for every status, even empty ones, keeping order', () => {
    const columns = groupByStatus([
      summary({ id: 1, status: 'APPLIED' }),
      summary({ id: 2, status: 'OFFER' }),
      summary({ id: 3, status: 'APPLIED' }),
    ]);

    expect(Object.keys(columns)).toEqual(['WISHLIST', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'GHOSTED']);
    expect(columns.APPLIED.map((a) => a.id)).toEqual([1, 3]);
    expect(columns.OFFER.map((a) => a.id)).toEqual([2]);
    expect(columns.WISHLIST).toEqual([]);
  });
});

describe('moveApplication', () => {
  const now = new Date(2026, 2, 10, 12, 0);

  it('changes the status, moves the card to the top and does not mutate the input', () => {
    const list = [summary({ id: 1 }), summary({ id: 2, status: 'WISHLIST', appliedOn: null })];

    const result = moveApplication(list, 2, 'APPLIED', now);

    expect(result.map((a) => [a.id, a.status])).toEqual([
      [2, 'APPLIED'],
      [1, 'APPLIED'],
    ]);
    expect(result[0].appliedOn).toBe('2026-03-10');
    expect(list[1].status).toBe('WISHLIST');
  });

  it('keeps an existing applied date', () => {
    const result = moveApplication([summary({ id: 1, appliedOn: '2026-01-05' })], 1, 'INTERVIEW', now);
    expect(result[0].appliedOn).toBe('2026-01-05');
  });

  it('returns the same array when nothing changes', () => {
    const list = [summary({ id: 1, status: 'OFFER' })];
    expect(moveApplication(list, 1, 'OFFER', now)).toBe(list);
    expect(moveApplication(list, 99, 'REJECTED', now)).toBe(list);
  });
});

describe('parseTags', () => {
  it('trims, lowercases, drops empties and de-duplicates', () => {
    expect(parseTags(' React, java ,, REACT,Remote ')).toEqual(['react', 'java', 'remote']);
    expect(parseTags('')).toEqual([]);
  });
});
