import React, { useState, useRef } from 'react';
import {
  Upload,
  FileCheck,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Loader2,
  RefreshCw,
  Layers,
  Database,
  ArrowRight,
  FileCode,
} from 'lucide-react';
import {
  validateCatalogPackage,
  importCatalogPackage,
  CatalogValidationResult,
  CatalogImportResponse,
} from '@/lib/services/catalogService';

const entityLabels: Record<string, string> = {
  genres: 'Thể loại', attributeScales: 'Thang điểm', sources: 'Nguồn', series: 'Phim bộ',
  seasons: 'Mùa', episodes: 'Tập', segments: 'Phân đoạn', characters: 'Nhân vật',
  mediaAssets: 'Tệp video', textTracks: 'Phụ đề', scenes: 'Phân cảnh',
};

const importMessages: Record<string, string> = {
  'Idempotent import: Package đã tồn tại với cùng revision và nội dung.': 'Tệp này đã được nhập trước đó với cùng phiên bản và nội dung.',
  'Nhập dữ liệu package thành công và ghi nhận outbox snapshot.': 'Đã nhập dữ liệu và đang chờ đồng bộ tìm kiếm.',
};

const indexStatusLabels: Record<string, string> = {
  READY: 'Đã đồng bộ', SYNCING: 'Đang đồng bộ', PENDING: 'Chờ xử lý',
  BLOCKED: 'Tạm dừng', SYNC_FAILED: 'Lỗi đồng bộ',
};

interface CatalogImportModalProps {
  onClose?: () => void;
  onImportSuccess?: (response: CatalogImportResponse) => void;
}

export function CatalogImportModal({ onClose, onImportSuccess }: CatalogImportModalProps) {
  const [jsonInput, setJsonInput] = useState<string>('');
  const [fileName, setFileName] = useState<string | null>(null);
  const [parsingError, setParsingError] = useState<string | null>(null);

  const [isValidating, setIsValidating] = useState<boolean>(false);
  const [validationResult, setValidationResult] = useState<CatalogValidationResult | null>(null);

  const [isImporting, setIsImporting] = useState<boolean>(false);
  const [importResult, setImportResult] = useState<CatalogImportResponse | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const validatedHashRef = useRef<string | null>(null);
  const inputVersionRef = useRef(0);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const inputVersion = ++inputVersionRef.current;

    setFileName(file.name);
    setParsingError(null);
    setValidationResult(null);
    setImportResult(null);
    setImportError(null);
    validatedHashRef.current = null;

    const reader = new FileReader();
    reader.onload = (event) => {
      if (inputVersion !== inputVersionRef.current) return;
      inputVersionRef.current += 1;
      setValidationResult(null);
      validatedHashRef.current = null;
      const content = event.target?.result as string;
      setJsonInput(content);
    };
    reader.onerror = () => {
      if (inputVersion !== inputVersionRef.current) return;
      setParsingError('Chưa đọc được tệp JSON đã chọn.');
    };
    reader.readAsText(file);
  };

  const parseJsonSafely = () => {
    if (!jsonInput.trim()) {
      setParsingError('Chọn tệp JSON hoặc dán nội dung vào ô bên dưới.');
      return null;
    }
    try {
      const parsed = JSON.parse(jsonInput);
      setParsingError(null);
      return parsed;
    } catch (err: any) {
      setParsingError(`Tệp JSON chưa đúng định dạng: ${err?.message || err}`);
      return null;
    }
  };

  const handleValidate = async () => {
    const parsed = parseJsonSafely();
    if (!parsed) return;

    setIsValidating(true);
    setValidationResult(null);
    setImportResult(null);
    setImportError(null);
    validatedHashRef.current = null;
    const inputVersion = inputVersionRef.current;

    try {
      const result = await validateCatalogPackage(parsed);
      if (inputVersion !== inputVersionRef.current) return;
      setValidationResult(result);
      if (result.valid) {
        validatedHashRef.current = result.contentHash || 'valid_hash';
      }
    } catch (err: any) {
      if (inputVersion === inputVersionRef.current) setParsingError(`Chưa kiểm tra được trên máy chủ: ${err?.message || err}`);
    } finally {
      setIsValidating(false);
    }
  };

  const handleImport = async () => {
    const parsed = parseJsonSafely();
    if (!parsed) return;

    if (!validationResult || !validationResult.valid || !validatedHashRef.current) {
      setImportError('Dữ liệu chưa được kiểm tra hoặc vừa thay đổi. Nhấn "Kiểm tra và xem trước" trước khi nhập.');
      return;
    }

    setIsImporting(true);
    setImportError(null);

    try {
      const res = await importCatalogPackage(parsed);
      setImportResult(res);
      if (onImportSuccess) {
        onImportSuccess(res);
      }
    } catch (err: any) {
      setImportError(err?.message || 'Chưa nhập được dữ liệu. Bạn thử lại nhé.');
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <div className="rounded-xl border border-surface-border bg-surface-elevated p-6 shadow-2xl">
      <div className="flex items-center justify-between border-b border-surface-border pb-4">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-lg bg-brand/10 text-brand-dark">
            <Database size={22} />
          </div>
          <div>
            <h2 className="text-lg font-bold text-ink">Nhập tệp JSON</h2>
            <p className="text-xs text-muted">
              Kiểm tra và xem trước thay đổi trước khi nhập.
            </p>
          </div>
        </div>
        {onClose && (
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-muted hover:bg-surface-hover hover:text-ink cursor-pointer"
          >
            ✕
          </button>
        )}
      </div>

      <div className="mt-5 space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="btn-secondary flex items-center gap-2 text-xs font-semibold"
          >
            <Upload size={14} />
            <span>Chọn tệp JSON…</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json,application/json"
            onChange={handleFileUpload}
            className="hidden"
          />
          {fileName && (
            <span className="flex items-center gap-1.5 rounded bg-surface px-2.5 py-1 text-xs text-muted border border-surface-border">
              <FileCode size={13} className="text-brand-dark" />
              {fileName}
            </span>
          )}
        </div>

        <div>
          <label htmlFor="catalog-json" className="mb-1 block text-xs font-medium text-muted">
            Nội dung tệp JSON:
          </label>
          <textarea
            id="catalog-json"
            value={jsonInput}
            onChange={(e) => {
              inputVersionRef.current += 1;
              setJsonInput(e.target.value);
              setValidationResult(null);
              setImportResult(null);
              validatedHashRef.current = null;
            }}
            placeholder='Dán nội dung JSON theo mẫu dippie.catalog.v1…'
            rows={8}
            data-testid="catalog-json-textarea"
            className="w-full rounded-lg border border-surface-border bg-surface p-3 font-mono text-xs text-ink placeholder-zinc-500 focus:border-brand focus:outline-none"
          />
        </div>

        {parsingError && (
          <div className="flex items-center gap-2 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-900">
            <XCircle size={16} className="shrink-0" />
            <span>{parsingError}</span>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-3 pt-2">
          <button
            data-testid="catalog-validate-button"
            type="button"
            onClick={handleValidate}
            disabled={isValidating || !jsonInput.trim()}
            className="btn-secondary flex items-center gap-2 text-xs font-bold disabled:opacity-50"
          >
            {isValidating ? (
              <>
                <Loader2 size={14} className="animate-spin text-brand-dark" />
                <span>Đang kiểm tra…</span>
              </>
            ) : (
              <>
                <FileCheck size={14} className="text-brand-dark" />
                <span>Kiểm tra và xem trước</span>
              </>
            )}
          </button>

          <button
            data-testid="catalog-import-button"
            type="button"
            onClick={handleImport}
            disabled={
              isImporting ||
              !jsonInput.trim() ||
              (validationResult !== null && !validationResult.valid)
            }
            className="btn-primary flex items-center gap-2 text-xs font-bold disabled:opacity-50"
          >

            {isImporting ? (
              <>
                <Loader2 size={14} className="animate-spin text-zinc-950" />
                <span>Đang nhập dữ liệu…</span>
              </>
            ) : (
              <>
                <Database size={14} />
                <span>Nhập dữ liệu</span>
              </>
            )}
          </button>
        </div>

        {validationResult && (
          <div data-testid="catalog-validation-panel" className="mt-4 rounded-xl border border-surface-border bg-surface p-4">
            <div className="flex items-center justify-between border-b border-surface-border pb-3">
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                {validationResult.valid ? (
                  <span data-testid="catalog-validation-status" className="flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-3 py-1 text-xs font-bold text-emerald-900 border border-emerald-500/30">
                    <CheckCircle2 size={14} /> Tệp hợp lệ
                  </span>
                ) : (
                  <span data-testid="catalog-validation-status" className="flex items-center gap-1.5 rounded-full bg-red-500/15 px-3 py-1 text-xs font-bold text-red-900 border border-red-500/30">
                    <XCircle size={14} /> Tệp có {validationResult.errors.length} lỗi
                  </span>
                )}
                {validationResult.packageId && (
                  <span className="max-w-full break-words text-xs text-muted">
                    Mã tệp: <strong className="text-ink">{validationResult.packageId}</strong> (Phiên bản {validationResult.revision})
                  </span>
                )}
              </div>
              {validationResult.contentHash && (
                <span className="font-mono text-[0.6875rem] text-muted" title={validationResult.contentHash}>
                  SHA-256: {validationResult.contentHash.slice(0, 12)}…
                </span>
              )}
            </div>

            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div className="rounded-lg bg-surface-elevated p-2.5 border border-surface-border">
                <span className="text-[0.6875rem] text-muted">Tạo mới</span>
                <p className="text-base font-bold text-emerald-900">+{validationResult.toCreate}</p>
              </div>
              <div className="rounded-lg bg-surface-elevated p-2.5 border border-surface-border">
                <span className="text-[0.6875rem] text-muted">Cập nhật</span>
                <p className="text-base font-bold text-amber-900">~{validationResult.toUpdate}</p>
              </div>
              <div className="rounded-lg bg-surface-elevated p-2.5 border border-surface-border">
                <span className="text-[0.6875rem] text-muted">Không đổi</span>
                <p className="text-base font-bold text-muted">{validationResult.unchanged}</p>
              </div>
              <div className="rounded-lg bg-surface-elevated p-2.5 border border-surface-border">
                <span className="text-[0.6875rem] text-muted">Xung đột</span>
                <p className={`text-base font-bold ${validationResult.conflicts > 0 ? 'text-red-900' : 'text-muted'}`}>
                  {validationResult.conflicts}
                </p>
              </div>
            </div>

            {validationResult.errors && validationResult.errors.length > 0 && (
              <div className="mt-4">
                <h4 className="mb-2 text-xs font-semibold text-red-900">Chi tiết lỗi:</h4>
                <div className="max-h-56 overflow-x-auto overflow-y-auto rounded-lg border border-red-500/20 bg-surface-elevated text-xs">
                  <table className="w-full text-left">
                    <thead className="border-b border-surface-border bg-surface text-muted">
                      <tr>
                        <th className="p-2">Vị trí</th>
                        <th className="p-2">Mã lỗi</th>
                        <th className="p-2">Mô tả</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-surface-border font-mono text-[0.6875rem]">
                      {validationResult.errors.map((err, idx) => (
                        <tr key={idx} className="hover:bg-red-500/5">
                          <td className="p-2 text-amber-900">{err.path}</td>
                          <td className="p-2 text-muted">{err.code}</td>
                          <td className="p-2 font-sans text-ink">{err.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {importError && (
          <div data-testid="catalog-import-error" className="flex items-center gap-2 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-900">
            <XCircle size={16} className="shrink-0" />
            <span>{importError}</span>
          </div>
        )}

        {importResult && (
          <div data-testid="catalog-import-success-panel" className="mt-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4">
            <div className="flex items-center gap-2 text-emerald-900">

              <CheckCircle2 size={18} />
              <h3 className="text-sm font-bold">Đã nhập dữ liệu</h3>
            </div>
            <p className="mt-1 text-xs text-muted">{importMessages[importResult.message] || importResult.message}</p>

            <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-muted sm:grid-cols-4">
              {importResult.counts &&
                Object.entries(importResult.counts).map(([entity, count]) => (
                  <div key={entity} className="rounded bg-surface p-2 border border-surface-border">
                    <span className="text-[0.6875rem] text-muted">{entityLabels[entity] || entity}:</span>
                    <strong className="ml-1 text-ink">{count}</strong>
                  </div>
                ))}
            </div>

            <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-emerald-500/20 pt-2 text-[0.6875rem] text-muted">
              <span>Mã lần nhập: <code className="text-emerald-900">{importResult.importId}</code></span>
              <span>Trạng thái tìm kiếm: {indexStatusLabels[importResult.indexStatus || ''] || importResult.indexStatus || 'Chưa rõ'}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
