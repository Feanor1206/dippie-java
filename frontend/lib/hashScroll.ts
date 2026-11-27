import { isMotionReduced } from '@/lib/motion';

const FIND_TIMEOUT_MS = 3000;
const SETTLE_MS = 1500;
const STEP_MS = 100;
const USER_SCROLL_EVENTS = ['wheel', 'touchstart', 'keydown', 'pointerdown'] as const;

export function scrollToHashTarget(targetId: string): () => void {
  if (typeof document === 'undefined' || typeof window === 'undefined' || !targetId) {
    return () => {};
  }
  let stopped = false;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let foundAt: number | null = null;
  let lastTop: number | null = null;
  const startedAt = Date.now();

  const stop = () => {
    if (stopped) return;
    stopped = true;
    if (timer) clearTimeout(timer);
    USER_SCROLL_EVENTS.forEach((name) => window.removeEventListener(name, stop));
  };
  USER_SCROLL_EVENTS.forEach((name) => window.addEventListener(name, stop, { passive: true }));

  const tick = () => {
    if (stopped) return;
    const now = Date.now();
    const target = document.getElementById(targetId);
    if (target) {
      const top = target.getBoundingClientRect().top + window.scrollY;
      if (lastTop === null || Math.abs(top - lastTop) > 4) {
        const smooth = lastTop === null && !isMotionReduced();
        target.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' });
        lastTop = top;
      }
      if (foundAt === null) foundAt = now;
      if (now - foundAt >= SETTLE_MS) {
        stop();
        return;
      }
    } else if (now - startedAt >= FIND_TIMEOUT_MS) {
      stop();
      return;
    }
    timer = setTimeout(tick, STEP_MS);
  };

  tick();
  return stop;
}
