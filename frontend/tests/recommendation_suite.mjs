import { runWithSharedModules } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from '../node_modules/typescript/lib/typescript.js';

function load(fetch) {
  const source = fs.readFileSync(new URL('../lib/services/recommendationService.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const module = { exports: {} };
  runWithSharedModules(code, { module, exports: module.exports, fetch, process: { env: {} }, Error });
  return module.exports;
}

test('recommendations send the current bearer token and preserve media routing fields', async () => {
  let call;
  const service = load(async (url, options) => {
    call = { url, options };
    return { ok: true, json: async () => ({ recommendedMovies: [{ id: 'e1', title: 'Tập 1', item_type: 'episode', series_id: 's1' }], reasoning: 'Gợi ý' }) };
  });
  const result = await service.getRecommendations('token');
  assert.equal(call.url, '/api/recommend');
  assert.equal(call.options.headers.Authorization, 'Bearer token');
  assert.equal(result.movies[0].series_id, 's1');
  assert.equal(result.reasoning, 'Gợi ý');
});

test('guest suggestions send only local film IDs and no authorization', async () => {
  const service = load(async (_url, options) => {
    assert.equal(options.headers.Authorization, undefined);
    assert.deepEqual(JSON.parse(options.body), { limit: 6, likedMovieIds: ['recently-viewed'] });
    return { ok: true, json: async () => ({ recommendedMovies: [], reasoning: '' }) };
  });
  await service.getRecommendations(null, undefined, ['recently-viewed']);
});

test('recommendations reject malformed server responses', async () => {
  const service = load(async () => ({ ok: true, json: async () => ({ recommendedMovies: null }) }));
  await assert.rejects(service.getRecommendations(null));
});

test('recommendations send moodPreset per C3 and surface server 400 Vietnamese message', async () => {
  const bodies = [];
  const service = load(async (_url, options) => {
    const parsed = JSON.parse(options.body);
    bodies.push(parsed);
    if (parsed.moodPreset === 'unknown') {
      return { ok: false, status: 400, json: async () => ({ message: 'Tâm trạng không hợp lệ.' }) };
    }
    return {
      ok: true,
      json: async () => ({
        recommendedMovies: [{ id: 'ep_peppa_1', title: 'Giờ đi ngủ', item_type: 'episode', series_id: 'ser_peppa' }],
        reasoning: 'Gợi ý nhẹ nhàng trước giờ ngủ.',
      }),
    };
  });

  assert.equal(service.MOOD_PRESETS.length, 6);
  const res = await service.getRecommendations('tok', undefined, [], { moodPreset: 'bedtime' });
  assert.deepEqual(bodies[0], { limit: 6, likedMovieIds: [], moodPreset: 'bedtime' });
  assert.equal(res.movies[0].id, 'ep_peppa_1');

  await assert.rejects(
    service.getRecommendations(null, undefined, [], { moodPreset: 'unknown' }),
    /Tâm trạng không hợp lệ\./
  );
});
