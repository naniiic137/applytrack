import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { STALE_VERSION_MESSAGE, errorMessage, isStaleVersion } from './errors';

describe('errorMessage', () => {
  it('turns an optimistic-locking 409 into a friendly "updated elsewhere" message', () => {
    const stale = new ApiError(409, { status: 409, title: 'Conflict', detail: 'raw', code: 'stale_version' });

    expect(isStaleVersion(stale)).toBe(true);
    expect(errorMessage(stale)).toBe(STALE_VERSION_MESSAGE);
  });

  it('keeps the server message for other errors, including other 409s', () => {
    const duplicate = new ApiError(409, { status: 409, detail: 'An account with this email already exists' });

    expect(isStaleVersion(duplicate)).toBe(false);
    expect(errorMessage(duplicate)).toBe('An account with this email already exists');
    expect(errorMessage('weird')).toBe('Something went wrong');
  });
});
