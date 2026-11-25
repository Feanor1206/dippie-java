import { runWithSharedModules } from './internal_loader.mjs';
import { labels } from './labels_fixture.mjs';
import { shouldInlineComponent } from './internal_loader.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

function chatClient(fetch) {
  const source = fs.readFileSync(new URL('../lib/services/chatService.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  runWithSharedModules(code, { module, exports: module.exports, fetch, TextDecoder, AbortSignal });
  return module.exports.sendChatMessage;
}

test('Chat client yields metadata and split UTF-8 tokens before the terminal event', async () => {
  let controller;
  const bytes = new TextEncoder().encode(': heartbeat\r\n\r\nevent: metadata\r\ndata: {"recommendedMovies":[{"id":"ep2"}],"followUps":[],"pipelineStages":{"search":true}}\r\n\r\nevent: token\r\ndata: {"text":"Chào "}\r\n\r\nevent: token\r\ndata: {"text":"bạn"}\r\n\r\n');
  const stream = new ReadableStream({ start(value) { controller = value; } });
  const updates = [];
  const request = chatClient(async (_, init) => {
    assert.match(init.headers.Accept, /text\/event-stream/);
    return new Response(stream, { headers: { 'Content-Type': 'text/event-stream; charset=utf-8' } });
  })('Jadoo', [], { onUpdate: (value) => updates.push(value) });
  for (const byte of bytes) controller.enqueue(Uint8Array.of(byte));
  await new Promise(setImmediate);
  assert.equal(updates.at(-1).text, 'Chào bạn');
  assert.equal(updates[0].recommendedMovies[0].id, 'ep2');
  controller.enqueue(new TextEncoder().encode('event: done\ndata: {"text":"Chào bạn","recommendedMovies":[{"id":"ep2"}],"followUps":[]}\n\n'));
  controller.close();
  const result = await request;
  assert.equal(result.text, 'Chào bạn');
  assert.equal(result.recommendedMovies[0].id, 'ep2');
});

test('Chat client rejects truncated, malformed and error streams instead of accepting a partial answer', async () => {
  for (const body of [
    'event: token\ndata: {"text":"partial"}\n\n',
    'event: token\ndata: {"text":42}\n\n',
    'event: done\ndata: {"text":"ok","recommendedMovies":{}}\n\n',
    'event: token\ndata: broken JSON\n\n',
    'event: error\ndata: {"text":"CineBot đang bận."}\n\n',
  ]) {
    await assert.rejects(chatClient(async () => new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }))('hi'));
  }
});

test('Cancelling a chat stream closes its reader and rejects with the cancellation', async () => {
  let cancelled = false;
  const controller = new AbortController();
  const stream = new ReadableStream({ cancel() { cancelled = true; } });
  const request = chatClient(async () => new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))(
    'hi', [], { signal: controller.signal },
  );
  await new Promise(setImmediate);
  controller.abort();
  await assert.rejects(request, { name: 'AbortError' });
  assert.equal(cancelled, true);
});

test('Resetting a pending chat discards its reply and allows a new conversation', async () => {
  const source = fs.readFileSync(new URL('../features/chat/components/CineBot.tsx', import.meta.url), 'utf8');
  const componentCode = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React, esModuleInterop: true,
  } }).outputText;
  let slots = [], index = 0, tree;
  const react = {
    useState(initial) {
      const i = index++;
      if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
      return [slots[i], (value) => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }];
    },
    useRef(initial) { const i = index++; return slots[i] ||= { current: initial }; },
    useEffect() {},
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      return { type, props: { ...props, children } };
    },
  };
  const requests = [];
  const module = { exports: {} };
  runWithSharedModules(componentCode, { module, exports: module.exports, console, Date, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return react;
      if (name === '@/lib/services/chatService') return { sendChatMessage: (text, history, options) => new Promise((resolve) => requests.push({ resolve, options })) };
      if (name === '@/lib/context/WatchlistContext') return { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist() {} }) };
      if (name === '@/lib/context/AuthContext') return { useAuth: () => ({ status: 'guest', user: null, token: null }) };
      return {};
    },
  });
  const render = () => { index = 0; tree = module.exports.CineBot({ isOpen: true, onClose() {} }); if (typeof tree?.type === 'function') { index = 0; tree = tree.type(tree.props); } };
  const nodes = (node, predicate) => !node || typeof node !== 'object' ? [] : [
    ...(predicate(node) ? [node] : []), ...((node.props?.children || []).flatMap((child) => Array.isArray(child) ? child.flatMap((item) => nodes(item, predicate)) : nodes(child, predicate))),
  ];
  const button = (label) => nodes(tree, (node) => node.type === 'button' && node.props['aria-label'] === label)[0];
  const input = () => nodes(tree, (node) => node.type === 'input')[0];
  render();
  input().props.onChange({ target: { value: 'First question' } }); render();
  button('Gửi tin nhắn').props.onClick(); render();
  requests[0].options.onUpdate({ text: 'First streamed fragment', recommendedMovies: [], followUps: [] }); render();
  assert.match(JSON.stringify(tree), /First streamed fragment/);
  button('Làm mới cuộc trò chuyện').props.onClick(); render();
  assert.equal(requests[0].options.signal.aborted, true);
  assert.equal(input().props.disabled, false);
  input().props.onChange({ target: { value: 'Second question' } }); render();
  button('Gửi tin nhắn').props.onClick(); render();
  assert.notEqual(requests[0].options.sessionId, requests[1].options.sessionId);
  requests[0].options.onUpdate({ text: 'Stale streamed fragment', recommendedMovies: [], followUps: [] }); render();
  assert.doesNotMatch(JSON.stringify(tree), /Stale streamed fragment/);
  requests[1].options.onUpdate({ text: 'New streamed fragment', recommendedMovies: [], followUps: [] }); render();
  assert.match(JSON.stringify(tree), /New streamed fragment/);
  requests[0].resolve({ text: 'Old reply' }); await new Promise(setImmediate); render();
  assert.equal(input().props.disabled, true, 'old request must not unlock the new request');
  requests[1].resolve({ text: 'New reply' }); await new Promise(setImmediate); render();
  assert.doesNotMatch(JSON.stringify(tree), /Old reply/);
  assert.match(JSON.stringify(tree), /New reply/);
  assert.doesNotMatch(JSON.stringify(tree), /New streamed fragment/);
  input().props.onChange({ target: { value: 'Follow-up' } }); render();
  button('Gửi tin nhắn').props.onClick(); render();
  assert.equal(requests[1].options.sessionId, requests[2].options.sessionId);
  requests[2].resolve({ text: 'Follow-up reply' }); await new Promise(setImmediate);
});

test('Chat changes actor without exposing old messages or reusing the session', async () => {
  const source = fs.readFileSync(new URL('../features/chat/components/CineBot.tsx', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React, esModuleInterop: true } }).outputText;
  let slots = [], index = 0, actor = { status: 'guest', user: null, token: null }, key, cleanup;
  const requests = [];
  const react = {
    useState(initial) { const i = index++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial; return [slots[i], (v) => { slots[i] = typeof v === 'function' ? v(slots[i]) : v; }]; },
    useRef(initial) { const i = index++; return slots[i] ||= { current: initial }; },
    useEffect(fn, deps) { if (deps.length === 0 && !cleanup) cleanup = fn(); },
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      return { type, props: { ...props, children } };
    },
  };
  const module = { exports: {} };
  runWithSharedModules(code, { module, exports: module.exports, console, Date, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return react;
      if (name === '@/lib/context/AuthContext') return { useAuth: () => actor };
      if (name === '@/lib/context/WatchlistContext') return { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist() {} }) };
      if (name === '@/lib/services/chatService') return { sendChatMessage: (text, history, options) => new Promise((resolve) => requests.push({ resolve, options })) };
      return {};
    },
  });
  let tree;
  const render = (isOpen = true) => {
    index = 0;
    const root = module.exports.CineBot({ isOpen, onClose() {} });
    if (typeof root?.type === 'function') {
      if (root.props.key !== key) { cleanup?.(); cleanup = undefined; slots = []; key = root.props.key; }
      index = 0; tree = root.type(root.props);
    } else tree = root;
  };
  const nodes = (node, predicate) => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap((n) => nodes(n, predicate)) : [...(predicate(node) ? [node] : []), ...nodes(node.props?.children, predicate)];
  const input = () => nodes(tree, (n) => n.type === 'input')[0];
  const send = () => nodes(tree, (n) => n.props?.['aria-label'] === 'Gửi tin nhắn')[0].props.onClick();
  render(); input().props.onChange({ target: { value: 'Private guest question' } }); render(); send(); render();
  render(false); render();
  assert.match(JSON.stringify(tree), /Private guest question/);
  actor = { status: 'authenticated', user: { id: 8 }, token: 'account-token' }; render();
  assert.equal(requests[0].options.signal.aborted, true);
  assert.doesNotMatch(JSON.stringify(tree), /Private guest question/);
  input().props.onChange({ target: { value: 'Account question' } }); render(); send(); render();
  assert.notEqual(requests[0].options.sessionId, requests[1].options.sessionId);
  requests[0].resolve({ text: 'Old private reply' }); await new Promise(setImmediate); render();
  assert.doesNotMatch(JSON.stringify(tree), /Old private reply/);
  actor = { status: 'checking', user: null, token: null }; render();
  assert.equal(requests[1].options.signal.aborted, true);
  assert.doesNotMatch(JSON.stringify(tree), /Account question/);
});

test('Oversized messages show an error without entering history', async () => {
  const source = fs.readFileSync(new URL('../features/chat/components/CineBot.tsx', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React, esModuleInterop: true } }).outputText;
  let slots = [], index = 0, requests = [];
  const react = { useState(v) { const i = index++; if (!(i in slots)) slots[i] = typeof v === 'function' ? v() : v; return [slots[i], (n) => { slots[i] = typeof n === 'function' ? n(slots[i]) : n; }]; }, useRef(v) { const i = index++; return slots[i] ||= { current: v }; }, useEffect() {}, createElement(type, props, ...children) { return { type, props: { ...props, children } }; } };
  const module = { exports: {} };
  runWithSharedModules(code, { module, exports: module.exports, console, Date, AbortController, setTimeout, clearTimeout, require(name) {
      if (name === '@/lib/labels') return labels;
    if (name === 'react') return react;
    if (name === '@/lib/context/AuthContext') return { useAuth: () => ({ status: 'guest', user: null, token: null }) };
    if (name === '@/lib/context/WatchlistContext') return { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist() {} }) };
    if (name === '@/lib/services/chatService') return { sendChatMessage: (text, history) => { requests.push({ text, history }); return new Promise(() => {}); } };
    return {};
  } });
  let tree;
  const render = () => { index = 0; tree = module.exports.CineBot({ isOpen: true, onClose() {} }); if (typeof tree?.type === 'function') { index = 0; tree = tree.type(tree.props); } };
  const nodes = (n, p) => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap((x) => nodes(x, p)) : [...(p(n) ? [n] : []), ...nodes(n.props?.children, p)];
  const input = () => nodes(tree, (n) => n.type === 'input')[0];
  const send = () => nodes(tree, (n) => n.props?.['aria-label'] === 'Gửi tin nhắn')[0].props.onClick();
  render(); assert.equal(input().props.maxLength, 4000);
  input().props.onChange({ target: { value: 'x'.repeat(4001) } }); render(); send(); render();
  assert.equal(requests.length, 0);
  assert.ok(nodes(tree, (n) => n.props?.role === 'alert').length);
  input().props.onChange({ target: { value: 'Valid question' } }); render(); send(); render();
  assert.equal(requests.length, 1);
  assert.ok(requests[0].history.every((m) => m.text.length <= 4000));
});

test('Chat client sends the session, cancellation signal, and bounded recent history', async () => {
  const source = fs.readFileSync(new URL('../lib/services/chatService.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  let sent;
  runWithSharedModules(code, { module, exports: module.exports, fetch: async (url, init) => {
    sent = { url, init, body: JSON.parse(init.body) };
    return { ok: true, json: async () => ({ text: 'ok' }) };
  } });
  const controller = new AbortController();
  const history = Array.from({ length: 30 }, (_, i) => ({ sender: 'user', text: `message ${i}` }));
  await module.exports.sendChatMessage('last', history, { sessionId: 'conversation-1', signal: controller.signal });
  assert.equal(sent.body.sessionId, 'conversation-1');
  assert.equal(sent.init.signal.aborted, false);
  assert.equal(sent.body.messages.length, 20);
  assert.equal(sent.body.messages[0].content, 'message 10');
});

test('Chat labels clean filenames and multilingual genre duplicates', () => {
  assert.equal(labels.cleanMediaTitle('Xin Chào Jadoo [kS3zyuaG4q0].mp4'), 'Xin Chào Jadoo');
  assert.equal(labels.chatGenres(['Hoạt hình', 'Animation', '애니메이션', 'Hài hước', 'Comedy', '코미디', 'Gia đình', 'Family', '가족']), 'Hoạt hình, Hài hước, Gia đình');
});

test('C4: Chat client sends itemIds and documentIds in display order for assistant history entries and reads errorData.detail', async () => {
  const source = fs.readFileSync(new URL('../lib/services/chatService.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  let captured;
  runWithSharedModules(code, {
    module,
    exports: module.exports,
    fetch: async (_url, init) => {
      captured = JSON.parse(init.body);
      return { ok: true, json: async () => ({ text: 'Cảnh tiếp theo đây.' }) };
    },
  });

  const history = [
    { id: 'w', sender: 'bot', text: 'Xin chào' },
    { id: 'u1', sender: 'user', text: 'Nobita đi vũ trụ' },
    {
      id: 'b1',
      sender: 'bot',
      text: 'Tìm thấy 2 tập.',
      movies: [
        {
          id: 'ep_doraemon_1',
          title: 'Tập 1',
          evidence: [
            { documentId: 'seg_1', text: 'cảnh 1', startMs: 1000, endMs: 2000, itemType: 'segment' },
            { documentId: 'seg_2', text: 'cảnh 2', startMs: 3000, endMs: 4000, itemType: 'segment' },
          ],
        },
        {
          id: 'ep_doraemon_2',
          title: 'Tập 2',
          evidence: [],
        },
      ],
    },
  ];

  await module.exports.sendChatMessage('Cảnh sau đó thì sao', history);
  assert.equal(captured.messages[0].role, 'assistant');
  assert.equal(captured.messages[0].itemIds, undefined);
  assert.deepEqual(captured.messages[2].itemIds, ['ep_doraemon_1', 'ep_doraemon_2']);
  assert.deepEqual(captured.messages[2].documentIds, ['seg_1', 'seg_2']);

  const detailErrorModule = { exports: {} };
  runWithSharedModules(code, {
    module: detailErrorModule,
    exports: detailErrorModule.exports,
    fetch: async () => ({
      ok: false,
      status: 400,
      json: async () => ({ detail: 'Thiếu trường query hoặc messages.' }),
    }),
  });
  await assert.rejects(
    detailErrorModule.exports.sendChatMessage('   '),
    /Thiếu trường query hoặc messages\./
  );
});

test('CineBot renders role="log" aria-live="polite" on message list and uses 16px mobile input', () => {
  const source = fs.readFileSync(new URL('../features/chat/components/CineBot.tsx', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React, esModuleInterop: true } }).outputText;
  let slots = [], index = 0;
  const react = {
    useState(v) { const i = index++; if (!(i in slots)) slots[i] = typeof v === 'function' ? v() : v; return [slots[i], (n) => { slots[i] = typeof n === 'function' ? n(slots[i]) : n; }]; },
    useRef(v) { const i = index++; return slots[i] ||= { current: v }; },
    useEffect() {},
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      return { type, props: { ...props, children } };
    },
  };
  const module = { exports: {} };
  runWithSharedModules(code, {
    module,
    exports: module.exports,
    console,
    Date,
    AbortController,
    setTimeout,
    clearTimeout,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return react;
      if (name === '@/lib/context/AuthContext') return { useAuth: () => ({ status: 'guest', user: null, token: null }) };
      if (name === '@/lib/context/WatchlistContext') return { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist() {} }) };
      if (name === '@/lib/services/chatService') return { sendChatMessage: async () => ({ text: 'ok' }) };
      return {};
    },
  });
  let tree = module.exports.CineBot({ isOpen: true, onClose() {} });
  if (typeof tree?.type === 'function') { index = 0; tree = tree.type(tree.props); }
  const nodes = (n, p) => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap((x) => nodes(x, p)) : [...(p(n) ? [n] : []), ...nodes(n.props?.children, p)];

  const log = nodes(tree, (n) => n.props?.['data-testid'] === 'cinebot-message-list')[0];
  assert.ok(log);
  assert.equal(log.props.role, 'log');
  assert.equal(log.props['aria-live'], 'polite');

  const input = nodes(tree, (n) => n.type === 'input')[0];
  assert.ok(input.props.className.includes('text-base'), 'Mobile input must use 16px (text-base) to avoid iOS zoom');
});

test('Findings 3, 5 & 7: CineBot marks external-only episodes with a confirmation-gated Mở trên YouTube, maps canonical/multilingual genres, and enforces 44px card actions', async () => {
  const playbackSourceCode = ts.transpileModule(
    fs.readFileSync(new URL('../lib/services/playbackSource.ts', import.meta.url), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  const playbackSourceMod = { exports: {} };
  runWithSharedModules(playbackSourceCode, { module: playbackSourceMod, exports: playbackSourceMod.exports, URL });

  const mediaRoutingCode = ts.transpileModule(
    fs.readFileSync(new URL('../lib/services/mediaRouting.ts', import.meta.url), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  const mediaRoutingMod = { exports: {} };
  runWithSharedModules(mediaRoutingCode, { module: mediaRoutingMod, exports: mediaRoutingMod.exports, URLSearchParams });

  const source = fs.readFileSync(new URL('../features/chat/components/CineBot.tsx', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React, esModuleInterop: true },
  }).outputText;

  let slots = [], index = 0;
  const react = {
    useState(v) { const i = index++; if (!(i in slots)) slots[i] = typeof v === 'function' ? v() : v; return [slots[i], (n) => { slots[i] = typeof n === 'function' ? n(slots[i]) : n; }]; },
    useRef(v) { const i = index++; return slots[i] ||= { current: v }; },
    useEffect() {},
    createElement(type, props, ...children) {
      if (shouldInlineComponent(type)) return type(props || {});
      return { type, props: { ...props, children } };
    },
  };
  const module = { exports: {} };
  runWithSharedModules(code, {
    module,
    exports: module.exports,
    console,
    Date,
    AbortController,
    setTimeout,
    clearTimeout,
    require(name) {
      if (name === '@/lib/labels') return labels;
      if (name === 'react') return react;
      if (name === 'react-router') return { Link: 'route-link' };
      if (name.includes('mediaRouting')) return mediaRoutingMod.exports;
      if (name.includes('playbackSource')) return playbackSourceMod.exports;
      if (name === '@/lib/context/AuthContext') return { useAuth: () => ({ status: 'guest', user: null, token: null }) };
      if (name === '@/lib/context/WatchlistContext') return { useWatchlist: () => ({ isWatchlisted: () => false, toggleWatchlist() {} }) };
      if (name === '@/lib/services/chatService') {
        return {
          sendChatMessage: async () => ({
            text: 'Đây là các tập Công chúa Ori mùa 5.',
            recommendedMovies: [
              {
                id: 'ep_ori_s05_0001',
                title: 'Nhiệm Vụ Bất Khả Thi',
                year: 2011,
                rating: 0,
                averageRating: null,
                ratingCount: 0,
                runtime: '22m',
                genres: ['animation', 'comedy', 'family', 'slice_of_life', 'school'],
                description: '',
                poster: '',
                backdrop: '',
                item_type: 'episode',
                series_id: 'ser_ori',
                episode_id: 'ep_ori_s05_0001',
                externalOnly: true,
                youtubeVideoId: 'SSubOeKMP2c',
              },
              {
                id: 'ep_ori_s05_0002',
                title: 'Tập chỉ có cờ externalOnly',
                year: 2011,
                rating: 0,
                averageRating: 8.5,
                ratingCount: 2,
                runtime: '22m',
                genres: ['animation', 'Hoạt hình', 'Animation', '애니메이션', 'comedy', 'Hài hước', 'Comedy', '코미디', 'family', 'Gia đình', 'Family', '가족', 'slice_of_life', '일상'],
                description: '',
                poster: '',
                backdrop: '',
                item_type: 'episode',
                series_id: 'ser_ori',
                episode_id: 'ep_ori_s05_0002',
                externalOnly: true,
              },
            ],
            followUps: [],
          }),
        };
      }
      return {};
    },
  });

  const render = () => {
    index = 0;
    let tree = module.exports.CineBot({ isOpen: true, onClose() {} });
    if (typeof tree?.type === 'function') {
      index = 0;
      tree = tree.type(tree.props);
    }
    return tree;
  };
  const nodes = (n, p) => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap((x) => nodes(x, p)) : [...(p(n) ? [n] : []), ...nodes(n.props?.children, p)];
  const textOf = (n) => !n || typeof n === 'boolean' ? '' : typeof n === 'string' || typeof n === 'number' ? String(n) : Array.isArray(n) ? n.map(textOf).join(' ') : textOf(n.props?.children);

  let tree = render();
  nodes(tree, (n) => n.type === 'input')[0].props.onChange({ target: { value: 'Công chúa Ori mùa 5 tập 1' } });
  tree = render();
  await nodes(tree, (n) => n.props?.['aria-label'] === 'Gửi tin nhắn')[0].props.onClick();
  tree = render();

  const badges = nodes(tree, (n) => n.props?.['data-testid'] === 'cinebot-external-badge');
  assert.equal(badges.length, 2);
  assert.match(textOf(badges[0]), /Chỉ mở trên YouTube/);

  const ytLink = nodes(tree, (n) => n.props?.testId === 'cinebot-open-youtube-link')[0];
  assert.ok(ytLink);
  assert.equal(ytLink.props.url, 'https://www.youtube.com/watch?v=SSubOeKMP2c&t=0');
  assert.ok(ytLink.props.className.includes('min-h-[44px]'));
  assert.match(textOf(ytLink), /Mở trên YouTube/);

  const saveBtns = nodes(tree, (n) => n.type === 'button' && textOf(n).includes('Xem sau'));
  assert.equal(saveBtns.length, 2);
  for (const btn of saveBtns) {
    assert.ok(btn.props.className.includes('min-h-[44px]'));
    assert.equal(btn.props.className.includes('min-h-10'), false);
  }

  const fullText = textOf(tree);
  assert.ok(fullText.includes('Hoạt hình, Hài hước, Gia đình, Đời thường, Học đường'));
  assert.equal(fullText.includes('slice_of_life'), false);
  assert.equal(fullText.includes('애니메이션'), false);
  assert.equal(fullText.includes('코미디'), false);
  assert.equal(fullText.includes('가족'), false);
  assert.equal(fullText.includes('일상'), false);
});
