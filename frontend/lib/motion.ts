import React, { useEffect, useState } from 'react';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';

export const MOTION_STORAGE_KEY = 'dippie_motion';
export const MOTION_CHANGE_EVENT = 'dippie:motion-change';
export const LARGE_TEXT_STORAGE_KEY = 'dippie_large_text';
export const LARGE_TEXT_CHANGE_EVENT = 'dippie:large-text-change';

export const MOTION_EASING = {
  inOut: 'cubic-bezier(0.65, 0, 0.35, 1)',
  overshoot: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  out: 'cubic-bezier(0.22, 1, 0.36, 1)',
} as const;

let gsapConfigured = false;
let scrollTriggerPromise: Promise<typeof import('gsap/ScrollTrigger')['ScrollTrigger'] | null> | null = null;

function ensureGsapCore(): boolean {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false;
  if (!gsapConfigured) {
    gsap.config({ nullTargetWarn: false });
    gsap.registerPlugin(useGSAP);
    gsapConfigured = true;
  }
  return true;
}

function loadScrollTrigger() {
  if (!ensureGsapCore()) return Promise.resolve(null);
  if (!scrollTriggerPromise) {
    scrollTriggerPromise = import('gsap/ScrollTrigger')
      .then((mod) => {
        const plugin = mod.ScrollTrigger;
        gsap.registerPlugin(plugin);
        return plugin;
      })
      .catch(() => null);
  }
  return scrollTriggerPromise;
}

function readStoredMotion(): 'reduce' | 'full' | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage?.getItem(MOTION_STORAGE_KEY);
    if (raw === 'reduce' || raw === 'full') return raw;
  } catch {}
  return null;
}

function prefersSystemReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  try {
    return Boolean(window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  } catch {
    return false;
  }
}

export function isMotionReduced(): boolean {
  if (typeof document !== 'undefined' && document.documentElement?.dataset?.motion === 'reduce') {
    return true;
  }
  const stored = readStoredMotion();
  if (stored === 'reduce') return true;
  if (stored === 'full') return false;
  return prefersSystemReducedMotion();
}

export function syncDocumentMotionAttribute(): boolean {
  const stored = readStoredMotion();
  const reduced = stored === 'reduce' || (stored !== 'full' && prefersSystemReducedMotion());
  if (typeof document !== 'undefined' && document.documentElement) {
    if (reduced) {
      document.documentElement.setAttribute('data-motion', 'reduce');
    } else {
      document.documentElement.removeAttribute('data-motion');
    }
  }
  return reduced;
}

export function initMotionPreference(): () => void {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return () => {};
  }
  syncDocumentMotionAttribute();
  const media = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  const handleMediaChange = () => {
    syncDocumentMotionAttribute();
    if (typeof window.dispatchEvent === 'function' && typeof CustomEvent === 'function') {
      window.dispatchEvent(new CustomEvent(MOTION_CHANGE_EVENT));
    }
  };
  if (media && typeof media.addEventListener === 'function') {
    media.addEventListener('change', handleMediaChange);
    return () => media.removeEventListener('change', handleMediaChange);
  }
  return () => {};
}

export function setMotionReducedPreference(reduce: boolean): void {
  if (typeof window !== 'undefined') {
    try {
      window.localStorage?.setItem(MOTION_STORAGE_KEY, reduce ? 'reduce' : 'full');
    } catch {}
  }
  syncDocumentMotionAttribute();
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function' && typeof CustomEvent === 'function') {
    window.dispatchEvent(new CustomEvent(MOTION_CHANGE_EVENT));
  }
}

export function toggleMotionReduced(): boolean {
  const next = !isMotionReduced();
  setMotionReducedPreference(next);
  return next;
}

export function useReducedMotion(): { reduced: boolean; toggle: () => void } {
  const [reduced, setReduced] = useState<boolean>(() => isMotionReduced());

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const sync = () => setReduced(syncDocumentMotionAttribute());
    sync();
    const media = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
    if (typeof window.addEventListener === 'function') {
      window.addEventListener(MOTION_CHANGE_EVENT, sync);
      window.addEventListener('storage', sync);
    }
    if (media && typeof media.addEventListener === 'function') {
      media.addEventListener('change', sync);
    }
    return () => {
      if (typeof window.removeEventListener === 'function') {
        window.removeEventListener(MOTION_CHANGE_EVENT, sync);
        window.removeEventListener('storage', sync);
      }
      if (media && typeof media.removeEventListener === 'function') {
        media.removeEventListener('change', sync);
      }
    };
  }, []);

  return {
    reduced,
    toggle: () => {
      const next = toggleMotionReduced();
      setReduced(next);
    },
  };
}

function readStoredLargeText(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage?.getItem(LARGE_TEXT_STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

export function isLargeTextEnabled(): boolean {
  if (typeof document !== 'undefined' && document.documentElement?.dataset?.textSize === 'large') {
    return true;
  }
  return readStoredLargeText();
}

export function syncDocumentLargeTextAttribute(): boolean {
  const enabled = readStoredLargeText();
  if (typeof document !== 'undefined' && document.documentElement) {
    if (enabled) {
      document.documentElement.setAttribute('data-text-size', 'large');
    } else {
      document.documentElement.removeAttribute('data-text-size');
    }
  }
  return enabled;
}

export function initLargeTextPreference(): () => void {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return () => {};
  }
  syncDocumentLargeTextAttribute();
  return () => {};
}

export function setLargeTextPreference(enabled: boolean): void {
  if (typeof window !== 'undefined') {
    try {
      window.localStorage?.setItem(LARGE_TEXT_STORAGE_KEY, enabled ? '1' : '0');
    } catch {}
  }
  syncDocumentLargeTextAttribute();
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function' && typeof CustomEvent === 'function') {
    window.dispatchEvent(new CustomEvent(LARGE_TEXT_CHANGE_EVENT));
  }
}

export function toggleLargeText(): boolean {
  const next = !isLargeTextEnabled();
  setLargeTextPreference(next);
  return next;
}

export function useLargeText(): { largeText: boolean; toggle: () => void } {
  const [largeText, setLargeText] = useState<boolean>(() => isLargeTextEnabled());

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const sync = () => setLargeText(syncDocumentLargeTextAttribute());
    sync();
    if (typeof window.addEventListener === 'function') {
      window.addEventListener(LARGE_TEXT_CHANGE_EVENT, sync);
      window.addEventListener('storage', sync);
    }
    return () => {
      if (typeof window.removeEventListener === 'function') {
        window.removeEventListener(LARGE_TEXT_CHANGE_EVENT, sync);
        window.removeEventListener('storage', sync);
      }
    };
  }, []);

  return {
    largeText,
    toggle: () => {
      const next = toggleLargeText();
      setLargeText(next);
    },
  };
}

function canRunGsapDom(scopeRef: React.RefObject<HTMLElement | null>): boolean {
  return Boolean(
    typeof React.useLayoutEffect === 'function' &&
      typeof window !== 'undefined' &&
      typeof document !== 'undefined' &&
      scopeRef.current &&
      typeof scopeRef.current.querySelectorAll === 'function'
  );
}

function isNearOrAboveViewport(element: HTMLElement): boolean {
  if (typeof window === 'undefined' || typeof element.getBoundingClientRect !== 'function') {
    return true;
  }
  const viewportHeight = window.innerHeight || 800;
  const rect = element.getBoundingClientRect();
  return rect.top <= viewportHeight * 0.96;
}

export function useHeroEntrance(scopeRef: React.RefObject<HTMLElement | null>): void {
  if (typeof React.useLayoutEffect !== 'function') return;
  useGSAP(
    () => {
      if (!canRunGsapDom(scopeRef) || !ensureGsapCore() || isMotionReduced()) return;
      const root = scopeRef.current;
      if (!root) return;
      const words = Array.from(root.querySelectorAll<HTMLElement>('[data-hero-word]'));
      const fades = Array.from(root.querySelectorAll<HTMLElement>('[data-hero-fade]'));
      if (words.length > 0) {
        gsap.fromTo(
          words,
          { y: 14, opacity: 0.01 },
          {
            y: 0,
            opacity: 1,
            duration: 0.52,
            stagger: 0.055,
            ease: 'power2.out',
            clearProps: 'transform,opacity',
          }
        );
      }
      if (fades.length > 0) {
        gsap.fromTo(
          fades,
          { y: 12, opacity: 0.01 },
          {
            y: 0,
            opacity: 1,
            duration: 0.52,
            delay: 0.08,
            stagger: 0.06,
            ease: 'power2.out',
            clearProps: 'transform,opacity',
          }
        );
      }
    },
    { scope: scopeRef }
  );
}

export function useScrollReveal(
  scopeRef: React.RefObject<HTMLElement | null>,
  dependencies: unknown[] = []
): void {
  if (typeof React.useLayoutEffect !== 'function') return;
  useGSAP(
    (_context, contextSafe) => {
      if (!canRunGsapDom(scopeRef) || !ensureGsapCore() || isMotionReduced()) return;
      const root = scopeRef.current;
      if (!root || !contextSafe) return;
      let disposed = false;

      const animateOnce = (targets: HTMLElement[], stagger = 0) => {
        if (disposed || isMotionReduced() || targets.length === 0) return;
        gsap.fromTo(
          targets,
          { y: 14, opacity: 0.01 },
          {
            y: 0,
            opacity: 1,
            duration: 0.46,
            stagger,
            ease: 'power2.out',
            clearProps: 'transform,opacity',
          }
        );
      };

      const setup = contextSafe((plugin: NonNullable<Awaited<ReturnType<typeof loadScrollTrigger>>> | null) => {
        if (disposed || isMotionReduced() || !scopeRef.current) return;
        const container = scopeRef.current;
        const groups = Array.from(container.querySelectorAll<HTMLElement>('[data-reveal-group]'));
        for (const group of groups) {
          if (group.dataset.revealed === '1') continue;
          const items = Array.from(group.querySelectorAll<HTMLElement>('[data-reveal-item]')).slice(0, 18);
          const targets = items.length > 0 ? items : [group];
          group.dataset.revealed = '1';
          for (const el of targets) {
            el.style.opacity = '';
          }
          if (!plugin || isNearOrAboveViewport(group)) {
            animateOnce(targets, items.length > 1 ? 0.055 : 0);
          } else {
            plugin.create({
              trigger: group,
              start: 'top 95%',
              once: true,
              onEnter: () => animateOnce(targets, items.length > 1 ? 0.055 : 0),
            });
          }
        }

        const singles = Array.from(container.querySelectorAll<HTMLElement>('[data-reveal-section]'));
        for (const section of singles) {
          if (section.dataset.revealed === '1') continue;
          section.dataset.revealed = '1';
          section.style.opacity = '';
          if (!plugin || isNearOrAboveViewport(section)) {
            animateOnce([section]);
          } else {
            plugin.create({
              trigger: section,
              start: 'top 95%',
              once: true,
              onEnter: () => animateOnce([section]),
            });
          }
        }
      });

      void loadScrollTrigger().then((plugin) => {
        if (disposed) return;
        setup(plugin);
        plugin?.refresh();
      });

      return () => {
        disposed = true;
      };
    },
    { scope: scopeRef, dependencies }
  );
}
