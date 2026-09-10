---
name: angular-feature
description: Conventions for building an Angular feature in this app - standalone components, signals, facades, accessible tests. Use for any UI work.
---

# Angular feature

## Structure
```
features/<feature>/
  <feature>.routes.ts
  <feature>.facade.ts        # the ONLY thing that talks to the API
  <feature>-list.component.ts
  <feature>-detail.component.ts
  models/                    # types mirroring the API contract
```

## Rules
- **Standalone components only.** No NgModules.
- **Signals for state.** `signal`, `computed`, `linkedSignal`. `toSignal` for observables at the edge.
- **Components never inject `HttpClient`.** They inject the facade. This is what makes them testable
  and what stops API concerns leaking into templates.
- Facade exposes `readonly employees = signal<Employee[]>([])`, `readonly loading = signal(false)`,
  `readonly error = signal<string|null>(null)`. Every screen handles all three, plus empty.
- `ChangeDetectionStrategy.OnPush` everywhere.
- Strict TypeScript, `strictTemplates`, no `any`, no non-null `!` without a comment.
- Money is rendered through the shared `money` pipe. Never string-concatenate a currency symbol.
- Dates through the shared date pipe, ISO in transport, localised in display.
- 10,000-row lists use CDK virtual scroll **plus** server-side keyset paging. Not one or the other.

## Testing
Jest + Angular Testing Library.

```ts
it('shows a raise in the timeline after recording one', async () => {
  await render(SalaryTimelineComponent, { componentInputs: { records: [hire, raise] } });
  expect(await screen.findByRole('listitem', { name: /merit increase/i })).toBeVisible();
});
```

Query by **role and accessible name**. Never by CSS class or `data-testid` unless there is genuinely
no accessible handle — and if there isn't, that is an accessibility bug to fix, not to work around.

Components are tested against a stub facade; the facade is tested against a mocked `HttpClient`.

## Accessibility — not optional
Every interactive element reachable and operable by keyboard. Visible focus. Form controls have real
`<label>`s. Dialogs trap focus and restore it on close. Tables use `<th scope>`. Colour is never the
only signal — band position shows a chip with text, not just red or green. Run axe in CI.
