import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, configureClient, request, toQueryString } from './client';
import { api } from './endpoints';

function jsonResponse(status: number, body?: unknown, contentType = 'application/json') {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType },
  });
}

describe('toQueryString', () => {
  it('repeats array values and skips empty ones', () => {
    expect(toQueryString({ status: ['APPLIED', 'OFFER'], q: 'acme', tag: '', page: 0, size: undefined })).toBe(
      '?status=APPLIED&status=OFFER&q=acme&page=0',
    );
    expect(toQueryString({})).toBe('');
  });
});

describe('request', () => {
  const fetchMock = vi.fn();
  const onUnauthorized = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    configureClient({ getToken: () => 'token-123', onUnauthorized });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    onUnauthorized.mockReset();
  });

  it('sends the bearer token and JSON body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 7, status: 'OFFER' }));

    const result = await api.changeStatus(7, 'OFFER', 3);

    expect(result).toEqual({ id: 7, status: 'OFFER' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/applications/7/status');
    expect(init.method).toBe('PATCH');
    expect(init.headers.Authorization).toBe('Bearer token-123');
    expect(JSON.parse(init.body)).toEqual({ status: 'OFFER', version: 3 });
  });

  it('does not send the token for anonymous calls', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { token: 't', expiresAt: '', user: {} }));

    await api.login('a@b.dev', 'pw');

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('turns problem details into an ApiError with field errors', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        400,
        { title: 'Validation failed', status: 400, detail: 'One or more fields are invalid', errors: { company: 'must not be blank' } },
        'application/problem+json',
      ),
    );

    const error = (await request('/api/applications', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('One or more fields are invalid');
    expect(error.fieldErrors).toEqual({ company: 'must not be blank' });
  });

  it('calls the unauthorized handler on 401 so the app can log out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { title: 'Unauthorized', status: 401 }));

    await expect(api.stats()).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('returns undefined for 204 No Content', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(api.deleteApplication(3)).resolves.toBeUndefined();
  });
});
