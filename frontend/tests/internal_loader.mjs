import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import ts from '../node_modules/typescript/lib/typescript.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

const INTERNAL_MODULES = {
  '@/lib/services/libraryService': 'lib/services/libraryService.ts',
  '@/features/profile/useProfileMetadata': 'features/profile/useProfileMetadata.ts',
  '@/features/series/hooks/useSeriesPlayback': 'features/series/hooks/useSeriesPlayback.ts',
  '@/features/series/components/EpisodePlayerPanel': 'features/series/components/EpisodePlayerPanel.tsx',
  '@/features/series/components/EpisodeSidebar': 'features/series/components/EpisodeSidebar.tsx',
  '@/lib/hooks/useCatalogData': 'lib/hooks/useCatalogData.ts',
};

const INLINE_PRESENTATIONAL_NAMES = new Set([
  'EpisodePlayerPanel',
  'EpisodeSidebar',
  'TimelineListCard',
  'MissingItemCard',
  'ChatMovieCard',
  'SimilarMovieCard',
  'ActionIcon',
]);

export function shouldInlineComponent(type) {
  return typeof type === 'function' && INLINE_PRESENTATIONAL_NAMES.has(type.name);
}

const transpiledCache = new Map();

function getTranspiled(relPath) {
  const fullPath = path.join(root, relPath);
  let code = transpiledCache.get(fullPath);
  if (!code) {
    const source = fs.readFileSync(fullPath, 'utf8');
    code = ts.transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        jsx: ts.JsxEmit.React,
        esModuleInterop: true,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText;
    transpiledCache.set(fullPath, code);
  }
  return { fullPath, code };
}

export function createInternalResolver(baseContext, customRequire) {
  const cache = new Map();
  const wrappedRequire = (name) => {
    const relPath = INTERNAL_MODULES[name] ?? SHARED_MODULES[name];
    if (relPath) {
      if (cache.has(name)) return cache.get(name);
      const { fullPath, code } = getTranspiled(relPath);
      const mod = { exports: {} };
      cache.set(name, mod.exports);
      const ctx = {
        AbortController, setTimeout, clearTimeout, URL, Event,
        ...baseContext,
        module: mod,
        exports: mod.exports,
        require: wrappedRequire,
      };
      vm.runInNewContext(code, ctx, { filename: fullPath });
      cache.set(name, mod.exports);
      return mod.exports;
    }
    return customRequire(name);
  };
  wrappedRequire.handlesSharedModules = true;
  return wrappedRequire;
}

const SHARED_MODULES = {
  '@/lib/services/apiRequest': 'lib/services/apiRequest.ts',
  '@/lib/services/sessionStorage': 'lib/services/sessionStorage.ts',
  '@/lib/safeUrl': 'lib/safeUrl.ts',
  '@/lib/timestamps': 'lib/timestamps.ts',
  '@/lib/services/playbackReportService': 'lib/services/playbackReportService.ts',
  '@/lib/services/passwordResetService': 'lib/services/passwordResetService.ts',
  '@/lib/services/profileService': 'lib/services/profileService.ts',
  '@/lib/services/screenTimeService': 'lib/services/screenTimeService.ts',
  '@/lib/services/bountyService': 'lib/services/bountyService.ts',
  '@/lib/services/weekendVaultService': 'lib/services/weekendVaultService.ts',
  '@/lib/services/vipService': 'lib/services/vipService.ts',
  '@/lib/services/playFeaturesService': 'lib/services/playFeaturesService.ts',
  '@/lib/context/ScreenTimeContext': 'lib/context/ScreenTimeContext.tsx',
  '@/features/profiles/hooks/useScreenTimeHeartbeat': 'features/profiles/hooks/useScreenTimeHeartbeat.ts',
  '@/features/profiles/hooks/usePlayFeatures': 'features/profiles/hooks/usePlayFeatures.ts',
  '@/features/profiles/components/ScreenTimeChip': 'features/profiles/components/ScreenTimeChip.tsx',
  '@/features/profiles/components/ScreenTimeLockOverlay': 'features/profiles/components/ScreenTimeLockOverlay.tsx',
  '@/features/profiles/components/KidBountyBoard': 'features/profiles/components/KidBountyBoard.tsx',
  '@/features/profiles/components/WeekendVaultBanner': 'features/profiles/components/WeekendVaultBanner.tsx',
  '@/features/profiles/components/KidModeDashboard': 'features/profiles/components/KidModeDashboard.tsx',
  '@/features/profiles/components/VipBadge': 'features/profiles/components/VipBadge.tsx',
  '@/features/profiles/components/VipUnlockDialog': 'features/profiles/components/VipUnlockDialog.tsx',
  '@/features/account/components/ParentBountySection': 'features/account/components/ParentBountySection.tsx',
  '@/features/account/components/ParentWeekendVaultSection': 'features/account/components/ParentWeekendVaultSection.tsx',
  '@/features/account/components/ParentVipSeriesSection': 'features/account/components/ParentVipSeriesSection.tsx',
  '@/features/profiles/components/WatchPetWidget': 'features/profiles/components/WatchPetWidget.tsx',
  '@/features/profiles/components/LootDropOverlay': 'features/profiles/components/LootDropOverlay.tsx',
  '@/features/profiles/components/RecapQuizOverlay': 'features/profiles/components/RecapQuizOverlay.tsx',
  '@/features/account/components/PlayFeaturesSettings': 'features/account/components/PlayFeaturesSettings.tsx',
  '@/components/ui/SiteDialog': 'components/ui/SiteDialog.tsx',
  '@/components/ui/YouTubeLink': 'components/ui/YouTubeLink.tsx',
  '@/features/profiles/components/ChildAvatarBadge': 'features/profiles/components/ChildAvatarBadge.tsx',
  '@/features/profiles/components/ExitKidModeDialog': 'features/profiles/components/ExitKidModeDialog.tsx',
  '@/features/profiles/components/ProfilePickerModal': 'features/profiles/components/ProfilePickerModal.tsx',
  '@/features/series/components/PlaybackReportButton': 'features/series/components/PlaybackReportButton.tsx',
  '@/features/library/components/GuestSyncDialog': 'features/library/components/GuestSyncDialog.tsx',
  '@/features/auth/components/PasswordResetRequestForm': 'features/auth/components/PasswordResetRequestForm.tsx',
};

export function runWithSharedModules(code, context, options) {
  if (context.require?.handlesSharedModules) return vm.runInNewContext(code, context, options);
  const modules = new Map();
  const requireShared = (name) => {
    const relPath = SHARED_MODULES[name];
    if (!relPath) return context.require?.(name);
    if (modules.has(name)) return modules.get(name);
    const { fullPath, code: source } = getTranspiled(relPath);
    const module = { exports: {} };
    vm.runInNewContext(source, { AbortController, setTimeout, clearTimeout, URL, Event, ...context, module, exports: module.exports, require: requireShared }, { filename: fullPath });
    modules.set(name, module.exports);
    return module.exports;
  };
  return vm.runInNewContext(code, { ...context, require: requireShared }, options);
}
