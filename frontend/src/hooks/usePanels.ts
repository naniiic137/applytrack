import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * The detail drawer and the create/edit form are driven by URL search params
 * (?app=12, ?new=1, ?edit=12) so they survive reloads and can be linked to.
 */
export function usePanels() {
  const [params, setParams] = useSearchParams();

  const numberParam = (name: string) => {
    const value = Number(params.get(name));
    return Number.isInteger(value) && value > 0 ? value : null;
  };

  const update = useCallback(
    (mutate: (p: URLSearchParams) => void) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          mutate(next);
          return next;
        },
        { replace: false },
      );
    },
    [setParams],
  );

  return {
    openAppId: numberParam('app'),
    editAppId: numberParam('edit'),
    isCreating: params.get('new') === '1',
    openApp: (id: number) => update((p) => { p.delete('new'); p.delete('edit'); p.set('app', String(id)); }),
    closeApp: () => update((p) => p.delete('app')),
    startCreate: () => update((p) => { p.delete('edit'); p.set('new', '1'); }),
    startEdit: (id: number) => update((p) => { p.delete('new'); p.set('edit', String(id)); }),
    closeForm: () => update((p) => { p.delete('new'); p.delete('edit'); }),
  };
}
