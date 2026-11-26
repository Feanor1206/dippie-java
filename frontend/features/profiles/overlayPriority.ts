export type PlayerOverlayKind = 'time-up' | 'watch-pet' | 'recap' | 'vip' | 'loot' | null;

export type PlayerOverlayState = {
  hasTimeUpLock: boolean;
  hasWatchPetLock: boolean;
  hasRecapGate: boolean;
  hasVipLock: boolean;
  hasLootOverlay: boolean;
};

export function resolveActivePlayerOverlay(state: PlayerOverlayState): PlayerOverlayKind {
  if (state.hasTimeUpLock) return 'time-up';
  if (state.hasWatchPetLock) return 'watch-pet';
  if (state.hasRecapGate) return 'recap';
  if (state.hasVipLock) return 'vip';
  if (state.hasLootOverlay) return 'loot';
  return null;
}

export function isFullScreenPlayerOverlay(kind: PlayerOverlayKind): boolean {
  return kind === 'time-up' || kind === 'watch-pet' || kind === 'recap' || kind === 'vip';
}
