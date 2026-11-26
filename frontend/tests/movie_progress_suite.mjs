import { runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

function load(window) {
  const module = { exports: {} };
  const source = fs.readFileSync(new URL('../lib/services/movieProgress.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  runWithSharedModules(code, { module, exports: module.exports, window, URLSearchParams, Date, JSON, console });
  return module.exports;
}

function memoryWindow(search = '') {
  const stored = new Map();
  return {
    stored,
    window: {
      localStorage: {
        getItem: (key) => stored.get(key) ?? null,
        setItem: (key, value) => stored.set(key, value),
        removeItem: (key) => stored.delete(key),
      },
      location: { search },
    },
  };
}

test('movie progress is stored and resumed by movie id', () => {
  const { window, stored } = memoryWindow();
  const progress = load(window);
  assert.equal(progress.listMovieProgress().length, 0);
  progress.saveMovieProgress({ movieId: 'totoro', title: 'Totoro', positionMs: 65000, durationMs: 120000 }, Date.parse('2026-11-01T03:00:00Z'));
  progress.saveMovieProgress({ movieId: 'spirited-away', title: 'Spirited Away', positionMs: 1000, durationMs: 900000, completed: true }, Date.parse('2026-11-01T04:00:00Z'));
  const saved = progress.getMovieProgress('totoro');
  assert.equal(saved.positionMs, 65000);
  assert.equal(progress.isUsableMovieProgress(saved), true);
  assert.equal(progress.isUsableMovieProgress(progress.getMovieProgress('spirited-away')), false);
  assert.equal(progress.buildMovieProgressHref(saved), '/movies/totoro?t=65000');
  assert.equal(stored.size, 1);
  for (let i = 0; i < 40; i++) {
    progress.saveMovieProgress({ movieId: `film-${i}`, title: `Film ${i}`, positionMs: 1000 }, Date.parse('2026-11-01T05:00:00Z') + i);
  }
  assert.equal(progress.listMovieProgress().length, 30);
});

test('a t query on the film page overrides the stored resume point', () => {
  const { window } = memoryWindow('?t=15000');
  const progress = load(window);
  progress.saveMovieProgress({ movieId: 'totoro', title: 'Totoro', positionMs: 65000, durationMs: 120000 });
  assert.equal(progress.getMovieResumeSeconds('totoro'), 15);
});

test('Movie progress keeps guest and each signed-in viewer in separate collections', () => {
  const progress = load(memoryWindow().window);
  const draft = { movieId: 'film', title: 'Phim', positionMs: 10000 };
  progress.saveMovieProgress(draft, 100000);
  progress.saveMovieProgress({ ...draft, positionMs: 30000 }, 100000, 1);
  assert.equal(progress.getMovieProgress('film').positionMs, 10000);
  assert.equal(progress.getMovieProgress('film', 1).positionMs, 30000);
  assert.equal(progress.getMovieProgress('film', 2), null);
});

test('clearMovieProgress clears only the specified user bucket and near-end progress marks completed', () => {
  const progress = load(memoryWindow().window);
  progress.saveMovieProgress({ movieId: 'guest-film', title: 'Guest', positionMs: 20000, durationMs: 600000 }, 100000, null);
  progress.saveMovieProgress({ movieId: 'u1-film', title: 'User 1', positionMs: 20000, durationMs: 600000 }, 100000, 1);
  progress.saveMovieProgress({ movieId: 'u2-film', title: 'User 2', positionMs: 20000, durationMs: 600000 }, 100000, 2);

  progress.clearMovieProgress(1);
  assert.equal(progress.listMovieProgress(1).length, 0);
  assert.equal(progress.listMovieProgress(2).length, 1);
  assert.equal(progress.listMovieProgress(null).length, 1);

  const nearEnd = progress.saveMovieProgress(
    { movieId: 'short-film', title: 'Short', positionMs: 570000, durationMs: 600000, completed: false },
    100000,
    1
  );
  assert.equal(nearEnd.completed, true);
  assert.equal(progress.isUsableMovieProgress(nearEnd), false);
  assert.equal(progress.getMovieResumeSeconds('short-film', 1), 0);
});
