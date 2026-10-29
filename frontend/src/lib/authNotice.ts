/** Mensajes de retorno desde flujos que cambian el estado de la sesión. */
export function loginNotice(state: unknown): string | null {
  if (state === null || typeof state !== 'object') return null;
  const flags = state as { passwordChanged?: unknown; registered?: unknown };
  if (flags.passwordChanged === true) return 'Contraseña actualizada. Iniciá sesión con la nueva contraseña.';
  if (flags.registered === true) return 'Cuenta creada. Ya podés iniciar sesión.';
  return null;
}
