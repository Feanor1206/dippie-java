import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Download, FileUp, Plus, Trash2 } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import {
  MAX_QUIZ_CSV_BYTES,
  MAX_QUIZ_QUESTIONS_PER_SET,
  MAX_QUIZ_SETS_PER_ACCOUNT,
  QUIZ_GRADES,
  QUIZ_SUBJECTS,
  addQuizQuestion,
  createQuizSet,
  deleteQuizQuestion,
  deleteQuizSet,
  detectQuizCsvDelimiter,
  downloadQuizCsvTemplate,
  fetchQuizSetDetail,
  filterQuizCsvRows,
  getQuizGradeLabel,
  importQuizCsv,
  previewQuizCsv,
  updateChildQuizSettings,
  updateQuizQuestion,
  updateQuizSet,
  validateManualQuestionInput,
  type ChildQuizConfig,
  type QuizCsvImportResult,
  type QuizCsvPreview,
  type QuizGradeCode,
  type QuizOptionKey,
  type QuizQuestion,
  type QuizSet,
  type QuizSetDetail,
  type QuizSetsOverview,
  type QuizSubjectCode,
} from '@/features/quiz/quizService';

type BankDialog =
  | { kind: 'create-set' }
  | { kind: 'edit-set'; set: QuizSet }
  | { kind: 'delete-set'; set: QuizSet }
  | { kind: 'toggle-set'; set: QuizSet }
  | { kind: 'questions'; set: QuizSet }
  | { kind: 'csv'; set: QuizSet }
  | { kind: 'assign-set'; set: QuizSet; importSummary?: QuizCsvImportResult }
  | null;

function normalizeQuestionStem(text: string): string {
  return text.trim().replace(/\s+/g, ' ').toLocaleLowerCase('vi');
}

export function ParentQuizBankSection({
  token,
  overview,
  loading,
  loadError,
  childProfiles = [],
  quizConfigs = [],
  onChildQuizUpdated,
  sessionPin = '',
  onPinVerified,
  onPinRejected,
  onRefresh,
}: {
  token: string;
  overview: QuizSetsOverview | null;
  loading: boolean;
  loadError: string;
  childProfiles?: ChildProfile[];
  quizConfigs?: ChildQuizConfig[];
  onChildQuizUpdated?: (updated: ChildQuizConfig) => void;
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
  onPinRejected?: () => void;
  onRefresh: () => Promise<void>;
}) {
  const [dialog, setDialog] = useState<BankDialog>(null);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const [setName, setSetName] = useState('');
  const [setDescription, setSetDescription] = useState('');
  const [setSubject, setSetSubject] = useState<QuizSubjectCode>('KHAC');
  const [setGrade, setSetGrade] = useState<string>('');
  const [openCsvAfterCreate, setOpenCsvAfterCreate] = useState(false);

  const [detail, setDetail] = useState<QuizSetDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<QuizQuestion | null>(null);
  const [showManualForm, setShowManualForm] = useState(false);
  const [qText, setQText] = useState('');
  const [optA, setOptA] = useState('');
  const [optB, setOptB] = useState('');
  const [optC, setOptC] = useState('');
  const [optD, setOptD] = useState('');
  const [qAnswer, setQAnswer] = useState<QuizOptionKey>('A');
  const [qExplanation, setQExplanation] = useState('');
  const [qSubject, setQSubject] = useState<QuizSubjectCode>('TOAN');
  const [qGrade, setQGrade] = useState<QuizGradeCode>('LOP_1');
  const [qActive, setQActive] = useState(true);

  const [csvText, setCsvText] = useState('');
  const [pastedCsvText, setPastedCsvText] = useState('');
  const [csvFileName, setCsvFileName] = useState('');
  const [csvDelimiterUsed, setCsvDelimiterUsed] = useState<',' | ';' | '\t'>(',');
  const [csvPreview, setCsvPreview] = useState<QuizCsvPreview | null>(null);
  const [csvPreviewLoading, setCsvPreviewLoading] = useState(false);
  const [previewedSignature, setPreviewedSignature] = useState('');
  const [existingQuestionsInSet, setExistingQuestionsInSet] = useState<QuizQuestion[]>([]);
  const [expandedRows, setExpandedRows] = useState<Record<number, boolean>>({});
  const [onlyErrorRows, setOnlyErrorRows] = useState(false);
  const [skipDuplicates, setSkipDuplicates] = useState(false);
  const previewRequestRef = useRef(0);

  const [assignChildIds, setAssignChildIds] = useState<number[]>([]);

  const sanitizePin = (value: string) => value.replace(/[^0-9]/g, '').slice(0, 6);

  const loadSetDetail = useCallback(
    async (setId: number) => {
      setDetailLoading(true);
      setDialogError('');
      try {
        const loaded = await fetchQuizSetDetail(token, setId);
        setDetail(loaded);
      } catch (err: unknown) {
        setDialogError(err instanceof Error ? err.message : 'Chưa tải được danh sách câu hỏi.');
      } finally {
        setDetailLoading(false);
      }
    },
    [token]
  );

  useEffect(() => {
    if (dialog?.kind === 'questions') {
      void loadSetDetail(dialog.set.id);
    } else {
      setDetail(null);
      setShowManualForm(false);
      setEditingQuestion(null);
    }
  }, [dialog?.kind, dialog?.kind === 'questions' ? dialog.set.id : 0, loadSetDetail]);

  useEffect(() => {
    if (dialog?.kind !== 'csv') {
      setExistingQuestionsInSet([]);
      return;
    }
    if (dialog.set.questionCount <= 0) {
      setExistingQuestionsInSet([]);
      return;
    }
    let cancelled = false;
    fetchQuizSetDetail(token, dialog.set.id)
      .then((loaded) => {
        if (!cancelled) {
          setExistingQuestionsInSet(loaded.questions);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setExistingQuestionsInSet([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [dialog?.kind, dialog?.kind === 'csv' ? dialog.set.id : 0, dialog?.kind === 'csv' ? dialog.set.questionCount : 0, token]);

  const closeDialog = () => {
    if (busy) return;
    previewRequestRef.current += 1;
    setDialog(null);
    setPin('');
    setDialogError('');
    setCsvText('');
    setPastedCsvText('');
    setCsvFileName('');
    setCsvPreview(null);
    setCsvPreviewLoading(false);
    setPreviewedSignature('');
    setExpandedRows({});
    setOnlyErrorRows(false);
    setSkipDuplicates(false);
  };

  const defaultAssignChildSelection = useCallback(
    (targetSetId: number): number[] => {
      if (childProfiles.length === 0) return [];
      const notYetAssigned = childProfiles.filter((child) => {
        const cfg = quizConfigs.find((item) => item.profileId === child.id);
        return !cfg || !cfg.activeSetIds.includes(targetSetId);
      });
      if (notYetAssigned.length > 0) {
        return [notYetAssigned[0].id];
      }
      return [childProfiles[0].id];
    },
    [childProfiles, quizConfigs]
  );

  const openDialog = (next: BankDialog) => {
    setFeedback(null);
    setDialogError('');
    setPin(sessionPin);
    if (next?.kind === 'create-set') {
      setSetName('');
      setSetDescription('');
      setSetSubject('KHAC');
      setSetGrade('');
      setOpenCsvAfterCreate(false);
    } else if (next?.kind === 'edit-set') {
      setSetName(next.set.name);
      setSetDescription(next.set.description || '');
      setSetSubject(next.set.defaultSubject);
      setSetGrade(next.set.defaultGrade || '');
    } else if (next?.kind === 'csv') {
      previewRequestRef.current += 1;
      setCsvText('');
      setPastedCsvText('');
      setCsvFileName('');
      setCsvDelimiterUsed(',');
      setCsvPreview(null);
      setCsvPreviewLoading(false);
      setPreviewedSignature('');
      setExpandedRows({});
      setOnlyErrorRows(false);
      setSkipDuplicates(false);
    } else if (next?.kind === 'assign-set') {
      setAssignChildIds(defaultAssignChildSelection(next.set.id));
    }
    setDialog(next);
  };

  const openManualAddForm = (set: QuizSet) => {
    setEditingQuestion(null);
    setQText('');
    setOptA('');
    setOptB('');
    setOptC('');
    setOptD('');
    setQAnswer('A');
    setQExplanation('');
    setQSubject(set.defaultSubject);
    setQGrade(set.defaultGrade || 'LOP_1');
    setQActive(true);
    setShowManualForm(true);
    setDialogError('');
  };

  const openManualEditForm = (question: QuizQuestion) => {
    setEditingQuestion(question);
    setQText(question.questionText);
    setOptA(question.optionA);
    setOptB(question.optionB);
    setOptC(question.optionC);
    setOptD(question.optionD);
    setQAnswer(question.correctOption);
    setQExplanation(question.explanation || '');
    setQSubject(question.subject);
    setQGrade(question.grade);
    setQActive(question.active);
    setShowManualForm(true);
    setDialogError('');
  };

  const handleDownloadTemplate = async () => {
    setFeedback(null);
    try {
      const content = await downloadQuizCsvTemplate(token);
      const contentWithBom = content.startsWith('\uFEFF') ? content : `\uFEFF${content}`;
      if (typeof window !== 'undefined' && typeof Blob !== 'undefined' && typeof URL !== 'undefined') {
        const blob = new Blob([contentWithBom], { type: 'text/csv;charset=utf-8' });
        const href = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = href;
        anchor.download = 'mau-cau-hoi-dippie.csv';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(href);
      }
      setFeedback({ type: 'success', text: 'Đã tải tệp mẫu CSV (mở trực tiếp bằng Excel tiếng Việt không lỗi dấu).' });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        text: err instanceof Error ? err.message : 'Chưa tải được tệp mẫu CSV.',
      });
    }
  };

  const handleExportInvalidRows = () => {
    if (!csvPreview) return;
    const invalidRows = csvPreview.rows.filter((row) => !row.valid);
    if (invalidRows.length === 0) return;
    const escapeCell = (val: string) =>
      val.includes(',') || val.includes('"') || val.includes('\n') || val.includes('\r')
        ? `"${val.replace(/"/g, '""')}"`
        : val;
    const header = 'cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop,loi_can_sua';
    const bodyLines = invalidRows.map((row) =>
      [
        row.questionText || '',
        row.optionA || '',
        row.optionB || '',
        row.optionC || '',
        row.optionD || '',
        row.correctOption || '',
        row.explanation || '',
        row.subject || '',
        row.grade || '',
        (row.errors || []).join('; '),
      ]
        .map(escapeCell)
        .join(',')
    );
    const csvWithBom = `\uFEFF${[header, ...bodyLines].join('\n')}`;
    if (typeof window !== 'undefined' && typeof Blob !== 'undefined' && typeof URL !== 'undefined') {
      const blob = new Blob([csvWithBom], { type: 'text/csv;charset=utf-8' });
      const href = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = href;
      anchor.download = 'dong-loi-cau-hoi.csv';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(href);
    }
  };

  const runCsvPreview = async (setId: number, content: string) => {
    const reqId = ++previewRequestRef.current;
    setCsvPreview(null);
    setPreviewedSignature('');
    setExpandedRows({});
    setOnlyErrorRows(false);
    setSkipDuplicates(false);

    if (!content.trim()) {
      setCsvPreviewLoading(false);
      return;
    }
    const byteLength = new TextEncoder().encode(content).length;
    if (byteLength > MAX_QUIZ_CSV_BYTES) {
      setDialogError('Tệp CSV vượt quá giới hạn 1 MB.');
      setCsvPreviewLoading(false);
      return;
    }
    setCsvDelimiterUsed(detectQuizCsvDelimiter(content));
    setCsvPreviewLoading(true);
    setDialogError('');
    try {
      const preview = await previewQuizCsv(token, setId, content);
      if (reqId !== previewRequestRef.current) return;
      setCsvPreview(preview);
      setPreviewedSignature(`${setId}::${content}`);
    } catch (err: unknown) {
      if (reqId !== previewRequestRef.current) return;
      setCsvPreview(null);
      setPreviewedSignature('');
      setDialogError(err instanceof Error ? err.message : 'Chưa kiểm tra được tệp CSV.');
    } finally {
      if (reqId === previewRequestRef.current) {
        setCsvPreviewLoading(false);
      }
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>, setId: number) => {
    const file = event.target.files?.[0];
    if (!file) return;
    previewRequestRef.current += 1;
    setCsvPreview(null);
    setPreviewedSignature('');
    setCsvFileName(file.name);
    if (file.size > MAX_QUIZ_CSV_BYTES) {
      setDialogError('Tệp CSV vượt quá giới hạn 1 MB.');
      setCsvText('');
      return;
    }
    const text = await file.text();
    setCsvText(text);
    await runCsvPreview(setId, text);
  };

  const duplicateAnalysis = useMemo(() => {
    const existingSet = new Set(existingQuestionsInSet.map((q) => normalizeQuestionStem(q.questionText)));
    const seenInFile = new Set<string>();
    const existingDupRowNumbers = new Set<number>();
    const inFileDupRowNumbers = new Set<number>();
    const allDupRowNumbers = new Set<number>();

    if (csvPreview && Array.isArray(csvPreview.rows)) {
      for (const row of csvPreview.rows) {
        if (!row.valid || !row.questionText) continue;
        const stem = normalizeQuestionStem(row.questionText);
        if (!stem) continue;
        if (existingSet.has(stem)) {
          existingDupRowNumbers.add(row.rowNumber);
          allDupRowNumbers.add(row.rowNumber);
        } else if (seenInFile.has(stem)) {
          inFileDupRowNumbers.add(row.rowNumber);
          allDupRowNumbers.add(row.rowNumber);
        }
        seenInFile.add(stem);
      }
    }
    return {
      existingDupRowNumbers,
      inFileDupRowNumbers,
      allDupRowNumbers,
      totalDuplicates: allDupRowNumbers.size,
    };
  }, [csvPreview, existingQuestionsInSet]);

  const currentSignature = dialog?.kind === 'csv' ? `${dialog.set.id}::${csvText}` : '';
  const isCsvPreviewFresh = Boolean(
    dialog?.kind === 'csv' &&
      csvPreview &&
      !csvPreviewLoading &&
      !csvPreview.headerError &&
      csvText.trim().length > 0 &&
      previewedSignature === currentSignature
  );

  const effectiveCsvImportCount = useMemo(() => {
    if (!csvPreview) return 0;
    if (!skipDuplicates || duplicateAnalysis.totalDuplicates === 0) {
      return csvPreview.willImportCount;
    }
    const nonDuplicateValid = csvPreview.rows.filter(
      (row) => row.valid && !duplicateAnalysis.allDupRowNumbers.has(row.rowNumber)
    ).length;
    return Math.min(nonDuplicateValid, csvPreview.remainingCapacity || nonDuplicateValid);
  }, [csvPreview, skipDuplicates, duplicateAnalysis]);

  const handleSetMutationSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!dialog || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError(pinErr);
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      if (dialog.kind === 'create-set') {
        const trimmedName = setName.trim();
        if (!trimmedName) {
          setDialogError('Vui lòng nhập tên bộ câu hỏi.');
          setBusy(false);
          return;
        }
        const created = await createQuizSet(token, {
          pin,
          name: trimmedName,
          description: setDescription.trim() || null,
          defaultSubject: setSubject,
          defaultGrade: setGrade ? (setGrade as QuizGradeCode) : null,
        });
        onPinVerified?.(pin);
        await onRefresh();
        if (openCsvAfterCreate) {
          setDialog({ kind: 'csv', set: created });
          setCsvText('');
          setCsvFileName('');
          setCsvPreview(null);
          setPreviewedSignature('');
          setFeedback({ type: 'success', text: `Đã tạo bộ "${trimmedName}". Mời bạn chọn tệp CSV để nhập câu hỏi.` });
        } else {
          closeDialog();
          setFeedback({ type: 'success', text: `Đã tạo bộ câu hỏi "${trimmedName}".` });
        }
      } else if (dialog.kind === 'edit-set') {
        const trimmedName = setName.trim();
        if (!trimmedName) {
          setDialogError('Vui lòng nhập tên bộ câu hỏi.');
          setBusy(false);
          return;
        }
        await updateQuizSet(token, dialog.set.id, {
          pin,
          name: trimmedName,
          description: setDescription.trim() || null,
          defaultSubject: setSubject,
          defaultGrade: setGrade ? (setGrade as QuizGradeCode) : null,
        });
        onPinVerified?.(pin);
        await onRefresh();
        closeDialog();
        setFeedback({ type: 'success', text: `Đã cập nhật bộ câu hỏi "${trimmedName}".` });
      } else if (dialog.kind === 'toggle-set') {
        const nextActive = !dialog.set.active;
        await updateQuizSet(token, dialog.set.id, {
          pin,
          active: nextActive,
        });
        onPinVerified?.(pin);
        await onRefresh();
        closeDialog();
        setFeedback({
          type: 'success',
          text: nextActive
            ? `Đã bật lại bộ câu hỏi "${dialog.set.name}".`
            : `Đã tạm tắt bộ câu hỏi "${dialog.set.name}".`,
        });
      } else if (dialog.kind === 'delete-set') {
        const removedName = dialog.set.name;
        await deleteQuizSet(token, dialog.set.id, pin);
        onPinVerified?.(pin);
        await onRefresh();
        closeDialog();
        setFeedback({ type: 'success', text: `Đã xóa bộ câu hỏi "${removedName}".` });
      } else if (dialog.kind === 'csv') {
        if (!csvText.trim()) {
          setDialogError('Vui lòng chọn tệp CSV trước khi nhập.');
          setBusy(false);
          return;
        }
        if (!isCsvPreviewFresh) {
          setDialogError('Bản xem trước CSV đã cũ hoặc đang kiểm tra lại. Vui lòng đợi kiểm tra xong rồi mới nhập.');
          setBusy(false);
          return;
        }
        const payloadCsv =
          skipDuplicates && duplicateAnalysis.totalDuplicates > 0
            ? filterQuizCsvRows(csvText, duplicateAnalysis.allDupRowNumbers)
            : csvText;
        const result = await importQuizCsv(token, dialog.set.id, pin, payloadCsv);
        onPinVerified?.(pin);
        await onRefresh();
        const summaryLine =
          result.skippedInvalidCount > 0
            ? `Đã nhập ${result.importedCount} câu hợp lệ (bỏ qua ${result.skippedInvalidCount} dòng lỗi). Bộ "${result.set.name}" hiện có ${result.totalQuestionsInSet} câu sử dụng được.`
            : `Đã nhập ${result.importedCount} câu hỏi vào bộ "${result.set.name}" (${result.totalQuestionsInSet} câu sử dụng được).`;
        setFeedback({
          type: 'success',
          text: summaryLine,
        });
        setAssignChildIds(defaultAssignChildSelection(result.set.id));
        setDialog({ kind: 'assign-set', set: result.set, importSummary: result });
      } else if (dialog.kind === 'assign-set') {
        if (assignChildIds.length === 0) {
          setDialogError('Hãy chọn ít nhất 1 bé để gán bộ câu hỏi này.');
          setBusy(false);
          return;
        }
        const assignedNames: string[] = [];
        for (const childId of assignChildIds) {
          const child = childProfiles.find((item) => item.id === childId);
          const cfg = quizConfigs.find((item) => item.profileId === childId);
          const gradeToKeep: QuizGradeCode =
            cfg?.grade ?? (child?.quizGrade as QuizGradeCode | undefined) ?? dialog.set.defaultGrade ?? 'LOP_1';
          const existingIds = cfg?.activeSetIds?.length
            ? cfg.activeSetIds
            : (overview?.sets ?? []).filter((item) => item.active && item.builtIn).map((item) => item.id);
          const nextIds = existingIds.includes(dialog.set.id) ? existingIds : [...existingIds, dialog.set.id];
          const updated = await updateChildQuizSettings(token, childId, {
            pin,
            grade: gradeToKeep,
            activeSetIds: nextIds,
          });
          onChildQuizUpdated?.(updated);
          if (child?.name || updated.name) {
            assignedNames.push(updated.name || child?.name || '');
          }
        }
        onPinVerified?.(pin);
        await onRefresh();
        const targetSetName = dialog.set.name;
        closeDialog();
        setFeedback({
          type: 'success',
          text: `Đã gán bộ "${targetSetName}" cho ${assignedNames.filter(Boolean).join(', ') || 'bé'}.`,
        });
      }
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa lưu được thay đổi.');
    } finally {
      setBusy(false);
    }
  };

  const handleSaveManualQuestion = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (dialog?.kind !== 'questions' || busy) return;
    const validationErr = validateManualQuestionInput({
      questionText: qText,
      optionA: optA,
      optionB: optB,
      optionC: optC,
      optionD: optD,
      correctOption: qAnswer,
      explanation: qExplanation,
    });
    if (validationErr) {
      setDialogError(validationErr);
      return;
    }
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError(pinErr);
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      const payload = {
        pin,
        questionText: qText.trim(),
        optionA: optA.trim(),
        optionB: optB.trim(),
        optionC: optC.trim(),
        optionD: optD.trim(),
        correctOption: qAnswer,
        explanation: qExplanation.trim() || null,
        subject: qSubject,
        grade: qGrade,
        active: qActive,
      };
      if (editingQuestion) {
        await updateQuizQuestion(token, dialog.set.id, editingQuestion.id, payload);
      } else {
        await addQuizQuestion(token, dialog.set.id, payload);
      }
      onPinVerified?.(pin);
      setShowManualForm(false);
      setEditingQuestion(null);
      await loadSetDetail(dialog.set.id);
      await onRefresh();
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa lưu được câu hỏi.');
    } finally {
      setBusy(false);
    }
  };

  const handleToggleQuestionActive = async (question: QuizQuestion) => {
    if (dialog?.kind !== 'questions' || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError('Nhập mã PIN phụ huynh ở phía trên để bật/tắt hoặc xóa câu hỏi.');
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      await updateQuizQuestion(token, dialog.set.id, question.id, {
        pin,
        active: !question.active,
      });
      onPinVerified?.(pin);
      await loadSetDetail(dialog.set.id);
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa cập nhật được trạng thái câu hỏi.');
    } finally {
      setBusy(false);
    }
  };

  const handleDeleteQuestion = async (question: QuizQuestion) => {
    if (dialog?.kind !== 'questions' || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError('Nhập mã PIN phụ huynh ở phía trên để bật/tắt hoặc xóa câu hỏi.');
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      await deleteQuizQuestion(token, dialog.set.id, question.id, pin);
      onPinVerified?.(pin);
      await loadSetDetail(dialog.set.id);
      await onRefresh();
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa xóa được câu hỏi.');
    } finally {
      setBusy(false);
    }
  };

  const toggleAssignChild = (profileId: number) => {
    setAssignChildIds((prev) =>
      prev.includes(profileId) ? prev.filter((id) => id !== profileId) : [...prev, profileId]
    );
  };

  const toggleExpandRow = (rowNumber: number) => {
    setExpandedRows((prev) => ({ ...prev, [rowNumber]: !prev[rowNumber] }));
  };

  const sets = overview?.sets ?? [];
  const ownSetCount = overview?.ownSetCount ?? 0;
  const maxSets = overview?.maxSetsPerAccount ?? MAX_QUIZ_SETS_PER_ACCOUNT;
  const reachedSetLimit = ownSetCount >= maxSets;
  const visiblePreviewRows = csvPreview
    ? onlyErrorRows
      ? csvPreview.rows.filter((row) => !row.valid)
      : csvPreview.rows
    : [];

  return (
    <section
      id="parent-quiz-bank-section"
      aria-labelledby="parent-quiz-bank-heading"
      data-testid="parent-quiz-bank-section"
      className="mt-6 scroll-mt-20 rounded-xl border border-surface-border bg-paper-white p-5 sm:p-6"
    >
      <div className="flex flex-col gap-3 border-b border-surface-border pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 id="parent-quiz-bank-heading" className="text-base font-bold text-ink">
              Ngân hàng câu hỏi cho bé
            </h3>
            <span className="rounded-full border border-surface-border bg-surface-muted px-2.5 py-0.5 text-[0.6875rem] font-bold text-ink">
              Bộ riêng: {ownSetCount}/{maxSets}
            </span>
          </div>
          <p className="mt-1 text-base leading-relaxed text-muted">
            Dùng sẵn Toán tự động, Tiếng Anh từ vựng, Đố vui &amp; Khoa học hoặc tự thêm phiếu ôn tập cuối tuần từ trường bằng tay hay tệp bảng tính CSV (tối đa {MAX_QUIZ_QUESTIONS_PER_SET} câu/bộ, chỉ hiển thị trong tài khoản gia đình bạn).
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <button
            type="button"
            data-testid="download-quiz-csv-template-button"
            onClick={() => void handleDownloadTemplate()}
            className="btn-secondary min-h-[44px] text-xs font-bold"
          >
            <Download size={14} aria-hidden="true" />
            Tải mẫu CSV
          </button>
          <button
            type="button"
            data-testid="create-quiz-set-button"
            disabled={reachedSetLimit}
            onClick={() => openDialog({ kind: 'create-set' })}
            className="btn-primary min-h-[44px] text-xs font-bold disabled:opacity-50"
          >
            <Plus size={14} aria-hidden="true" />
            Tạo bộ câu hỏi
          </button>
        </div>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="parent-quiz-bank-feedback"
          className={`mt-4 rounded-xl border p-3.5 text-sm ${
            feedback.type === 'error'
              ? 'border-red-500/40 bg-red-500/10 text-red-900'
              : 'border-brand/50 bg-brand/15 text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      {loadError ? (
        <div
          role="alert"
          className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-3 text-xs text-red-900"
        >
          <span>{loadError}</span>
          <button type="button" onClick={() => void onRefresh()} className="btn-secondary min-h-[44px] px-3 text-xs font-bold">
            Thử lại
          </button>
        </div>
      ) : loading && sets.length === 0 ? (
        <p role="status" className="mt-4 rounded-xl border border-surface-border bg-surface-muted p-4 text-center text-sm text-muted">
          Đang tải ngân hàng câu hỏi…
        </p>
      ) : (
        <ul className="mt-4 grid grid-cols-1 gap-3 lg:grid-cols-2">
          {sets.map((set) => (
            <li
              key={set.id}
              data-testid={`quiz-set-card-${set.id}`}
              className={`flex flex-col justify-between gap-3 rounded-xl border p-4 ${
                set.active
                  ? 'border-surface-border bg-surface'
                  : 'border-surface-border/60 bg-surface-muted opacity-80'
              }`}
            >
              <div className="space-y-1.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-bold text-ink">{set.name}</p>
                  <div className="flex flex-wrap items-center gap-1">
                    <span
                      className={`rounded-full border px-2 py-0.5 text-[0.625rem] font-bold ${
                        set.builtIn
                          ? 'border-ink/40 bg-mint text-ink'
                          : 'border-ink/40 bg-[#f2d18c]/60 text-ink'
                      }`}
                    >
                      {set.builtIn ? 'Có sẵn' : 'Bộ riêng'}
                    </span>
                    {!set.active && (
                      <span className="rounded-full border border-red-800/40 bg-red-50 px-2 py-0.5 text-[0.625rem] font-bold text-red-900">
                        Đang tắt
                      </span>
                    )}
                  </div>
                </div>

                {set.description && (
                  <p className="text-xs leading-relaxed text-muted">{set.description}</p>
                )}

                <p className="text-xs font-semibold text-brand-dark">
                  {set.dynamicGenerator
                    ? 'Tự động tạo theo lớp · Không giới hạn câu'
                    : set.builtIn
                    ? `${set.questionCount} câu có sẵn · Môn ${set.defaultSubjectLabel}${
                        set.defaultGradeLabel ? ` · ${set.defaultGradeLabel}` : ''
                      }`
                    : `${set.questionCount}/${set.maxQuestionsPerSet} câu · Môn ${set.defaultSubjectLabel}${
                        set.defaultGradeLabel ? ` · ${set.defaultGradeLabel}` : ''
                      }`}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {!set.dynamicGenerator && (
                  <button
                    type="button"
                    data-testid={`manage-quiz-questions-button-${set.id}`}
                    onClick={() => openDialog({ kind: 'questions', set })}
                    className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
                  >
                    {set.builtIn ? 'Xem câu hỏi' : 'Câu hỏi'}
                  </button>
                )}
                {!set.builtIn && (
                  <>
                    <button
                      type="button"
                      data-testid={`upload-csv-button-${set.id}`}
                      onClick={() => openDialog({ kind: 'csv', set })}
                      className="btn-primary min-h-[44px] px-3 text-xs font-bold"
                    >
                      <FileUp size={13} aria-hidden="true" />
                      Nhập CSV
                    </button>
                    {set.active && childProfiles.length > 0 && (
                      <button
                        type="button"
                        data-testid={`assign-set-to-child-button-${set.id}`}
                        onClick={() => openDialog({ kind: 'assign-set', set })}
                        className="btn-secondary min-h-[44px] px-3 text-xs font-bold text-brand-dark"
                      >
                        Gán cho bé
                      </button>
                    )}
                    <button
                      type="button"
                      data-testid={`toggle-quiz-set-button-${set.id}`}
                      onClick={() => openDialog({ kind: 'toggle-set', set })}
                      className="btn-secondary min-h-[44px] px-3 text-xs"
                    >
                      {set.active ? 'Tạm tắt' : 'Bật lại'}
                    </button>
                    <button
                      type="button"
                      data-testid={`edit-quiz-set-button-${set.id}`}
                      onClick={() => openDialog({ kind: 'edit-set', set })}
                      className="btn-secondary min-h-[44px] px-3 text-xs"
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      data-testid={`delete-quiz-set-button-${set.id}`}
                      onClick={() => openDialog({ kind: 'delete-set', set })}
                      className="btn-secondary min-h-[44px] px-3 text-xs text-red-900"
                    >
                      Xóa
                    </button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {dialog && (dialog.kind === 'create-set' || dialog.kind === 'edit-set' || dialog.kind === 'toggle-set' || dialog.kind === 'delete-set') && (
        <SiteDialog
          title={
            dialog.kind === 'create-set'
              ? 'Tạo bộ câu hỏi mới'
              : dialog.kind === 'edit-set'
              ? `Sửa bộ · ${dialog.set.name}`
              : dialog.kind === 'toggle-set'
              ? dialog.set.active
                ? `Tạm tắt bộ · ${dialog.set.name}`
                : `Bật lại bộ · ${dialog.set.name}`
              : `Xóa bộ · ${dialog.set.name}`
          }
          tone={dialog.kind === 'delete-set' ? 'danger' : 'default'}
          size={dialog.kind === 'create-set' || dialog.kind === 'edit-set' ? 'lg' : 'md'}
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSetMutationSubmit} className="space-y-4">
            {(dialog.kind === 'create-set' || dialog.kind === 'edit-set') && (
              <>
                <div>
                  <label htmlFor="quiz-set-name" className="mb-1.5 block text-xs font-semibold text-ink">
                    Tên bộ câu hỏi
                  </label>
                  <input
                    id="quiz-set-name"
                    data-testid="quiz-set-name-input"
                    type="text"
                    maxLength={100}
                    required
                    disabled={busy}
                    value={setName}
                    onChange={(event) => setSetName(event.target.value)}
                    placeholder="Ví dụ: Ôn tập tuần 5 - Lớp 3"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="quiz-set-desc" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mô tả ngắn (không bắt buộc)
                  </label>
                  <input
                    id="quiz-set-desc"
                    type="text"
                    maxLength={300}
                    disabled={busy}
                    value={setDescription}
                    onChange={(event) => setSetDescription(event.target.value)}
                    placeholder="Ví dụ: Phiếu cô giáo gửi cuối tuần"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label htmlFor="quiz-set-subject" className="mb-1.5 block text-xs font-semibold text-ink">
                      Môn học mặc định
                    </label>
                    <select
                      id="quiz-set-subject"
                      data-testid="quiz-set-subject-select"
                      disabled={busy}
                      value={setSubject}
                      onChange={(event) => setSetSubject(event.target.value as QuizSubjectCode)}
                      className="min-h-[44px] w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-sm text-ink"
                    >
                      {QUIZ_SUBJECTS.map((sub) => (
                        <option key={sub.code} value={sub.code}>
                          {sub.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="quiz-set-grade" className="mb-1.5 block text-xs font-semibold text-ink">
                      Lớp mặc định (tuỳ chọn)
                    </label>
                    <select
                      id="quiz-set-grade"
                      data-testid="quiz-set-grade-select"
                      disabled={busy}
                      value={setGrade}
                      onChange={(event) => setSetGrade(event.target.value)}
                      className="min-h-[44px] w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-sm text-ink"
                    >
                      <option value="">Nhiều lớp</option>
                      {QUIZ_GRADES.map((gr) => (
                        <option key={gr.code} value={gr.code}>
                          {gr.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                {dialog.kind === 'create-set' && (
                  <label className="flex min-h-[44px] items-center gap-2.5 rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-xs font-semibold text-ink cursor-pointer">
                    <input
                      type="checkbox"
                      data-testid="create-set-open-csv-checkbox"
                      checked={openCsvAfterCreate}
                      disabled={busy}
                      onChange={(event) => setOpenCsvAfterCreate(event.target.checked)}
                      className="h-4 w-4 accent-brand"
                    />
                    <span>Chuyển sang tải tệp CSV ngay sau khi tạo bộ</span>
                  </label>
                )}
              </>
            )}

            {dialog.kind === 'toggle-set' && (
              <p className="text-sm leading-relaxed text-muted">
                {dialog.set.active
                  ? `Tạm tắt bộ "${dialog.set.name}"? Các câu hỏi vẫn được giữ lại nhưng sẽ tạm không hiện với các bé.`
                  : `Bật lại bộ "${dialog.set.name}" để có thể gán cho các bé?`}
              </p>
            )}

            {dialog.kind === 'delete-set' && (
              <p className="text-sm leading-relaxed text-muted">
                Xóa hẳn bộ <strong>{dialog.set.name}</strong> và toàn bộ {dialog.set.questionCount} câu hỏi bên trong? Báo cáo tổng kết trước đó của bé vẫn được giữ lại.
              </p>
            )}

            <div>
              <label htmlFor="quiz-bank-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="quiz-bank-pin"
                data-testid="quiz-bank-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(sanitizePin(event.target.value))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink"
              />
            </div>

            {dialogError && (
              <div role="alert" data-testid="quiz-bank-dialog-error" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            <div className="dialog-sticky-footer flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button type="button" disabled={busy} onClick={closeDialog} className="btn-secondary w-full sm:w-auto text-xs">
                Hủy
              </button>
              <button
                type="submit"
                data-testid="quiz-bank-dialog-submit"
                disabled={busy}
                className={
                  dialog.kind === 'delete-set'
                    ? 'inline-flex min-h-[44px] w-full sm:w-auto items-center justify-center border-2 border-ink bg-red-800 px-5 py-2.5 text-xs font-bold text-paper-white shadow-brand-sm hover:bg-red-900 cursor-pointer disabled:opacity-50'
                    : 'btn-primary w-full sm:w-auto text-xs font-bold'
                }
              >
                {busy
                  ? 'Đang xử lý…'
                  : dialog.kind === 'create-set'
                  ? openCsvAfterCreate
                    ? 'Tạo bộ & Nhập CSV'
                    : 'Tạo bộ câu hỏi'
                  : dialog.kind === 'edit-set'
                  ? 'Lưu thay đổi'
                  : dialog.kind === 'toggle-set'
                  ? dialog.set.active
                    ? 'Tạm tắt bộ'
                    : 'Bật lại bộ'
                  : 'Xóa bộ câu hỏi'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}

      {dialog?.kind === 'csv' && (
        <SiteDialog title={`Nhập CSV · ${dialog.set.name}`} size="xl" onClose={closeDialog} busy={busy}>
          <form onSubmit={handleSetMutationSubmit} data-testid="quiz-csv-upload-form" className="space-y-4">
            <div className="rounded-xl border border-surface-border bg-surface-muted p-3.5 text-sm space-y-2">
              <p className="font-semibold text-ink">
                Cột chuẩn: <code className="font-mono text-xs">cau_hoi, a, b, c, d, dap_an, giai_thich, mon, lop</code>
              </p>
              <p className="text-xs leading-relaxed text-muted">
                Nhận trực tiếp cả dấu phẩy (<code>,</code>), dấu chấm phẩy (<code>;</code>) và phím Tab từ Excel tiếng Việt hoặc Google Trang tính. Tối đa 1 MB/tệp, {MAX_QUIZ_QUESTIONS_PER_SET} câu/bộ.
              </p>
              <button
                type="button"
                onClick={() => void handleDownloadTemplate()}
                className="btn-secondary min-h-[44px] px-3.5 text-xs font-bold"
              >
                <Download size={14} aria-hidden="true" />
                Tải tệp mẫu CSV (3 câu ví dụ)
              </button>
            </div>

            <div className="space-y-2">
              <span className="block text-xs font-semibold text-ink">Tệp câu hỏi từ máy của bạn</span>
              <div className="flex flex-wrap items-center gap-3 rounded-xl border border-surface-border bg-paper-white p-3">
                <label
                  htmlFor="quiz-csv-file-input"
                  className={`btn-secondary min-h-[44px] px-4 text-xs font-bold ${
                    busy ? 'opacity-50 pointer-events-none' : 'cursor-pointer'
                  }`}
                >
                  <FileUp size={14} aria-hidden="true" />
                  Chọn tệp CSV từ máy
                </label>
                <input
                  id="quiz-csv-file-input"
                  data-testid="quiz-csv-file-input"
                  type="file"
                  accept=".csv,.tsv,text/csv,text/tab-separated-values"
                  disabled={busy}
                  onChange={(event) => void handleFileChange(event, dialog.set.id)}
                  className="sr-only"
                />
                <span className="min-w-0 flex-1 truncate text-xs text-muted">
                  {csvFileName ? `Đã chọn: ${csvFileName}` : 'Chưa chọn tệp nào'}
                </span>
              </div>
              {csvFileName && csvDelimiterUsed !== ',' && (
                <p data-testid="csv-delimiter-detected-note" className="text-xs font-medium text-brand-dark">
                  Đã tự nhận diện tệp ngăn cách bằng {csvDelimiterUsed === ';' ? 'dấu chấm phẩy (;) từ Excel' : 'ký tự Tab'}.
                </p>
              )}

              <details data-testid="csv-paste-details" className="rounded-xl border border-surface-border bg-paper-white p-3">
                <summary className="flex min-h-[44px] items-center text-xs font-bold text-ink cursor-pointer">
                  Hoặc dán trực tiếp các dòng từ Excel / Google Trang tính
                </summary>
                <div className="mt-2 space-y-2">
                  <label htmlFor="quiz-csv-paste-input" className="sr-only">
                    Nội dung bảng câu hỏi dán từ Excel
                  </label>
                  <textarea
                    id="quiz-csv-paste-input"
                    name="csvPaste"
                    data-testid="quiz-csv-paste-input"
                    rows={4}
                    disabled={busy}
                    value={pastedCsvText}
                    onChange={(event) => setPastedCsvText(event.target.value)}
                    placeholder={'cau_hoi\ta\tb\tc\td\tdap_an\n5 + 5 = ?\t8\t9\t10\t11\tC'}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted p-2.5 font-mono text-xs text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                  <div className="flex justify-end">
                    <button
                      type="button"
                      data-testid="quiz-csv-paste-preview-button"
                      disabled={busy || !pastedCsvText.trim()}
                      onClick={() => {
                        setCsvFileName('Nội dung vừa dán');
                        setCsvText(pastedCsvText);
                        void runCsvPreview(dialog.set.id, pastedCsvText);
                      }}
                      className="btn-secondary min-h-[44px] px-3.5 text-xs font-bold disabled:opacity-50"
                    >
                      Kiểm tra nội dung vừa dán
                    </button>
                  </div>
                </div>
              </details>
            </div>

            {csvPreviewLoading && (
              <p role="status" data-testid="csv-preview-loading" className="rounded-lg border border-surface-border bg-surface-muted p-3 text-xs font-medium text-muted">
                Đang kiểm tra từng dòng trong tệp CSV… Nút nhập sẽ mở sau khi kiểm tra xong.
              </p>
            )}

            {csvPreview && !csvPreviewLoading && (
              <div data-testid="quiz-csv-preview-panel" className="space-y-3 rounded-xl border border-ink/35 bg-paper-white p-3.5">
                {csvPreview.headerError ? (
                  <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 p-3 text-xs font-semibold text-red-900">
                    {csvPreview.headerError}
                  </div>
                ) : (
                  <>
                    <div className="grid grid-cols-2 gap-2 text-center text-xs sm:grid-cols-4">
                      <div className="rounded-lg bg-surface-muted p-2.5">
                        <p className="text-[0.6875rem] text-muted">Tổng dòng</p>
                        <p className="text-sm font-bold text-ink">{csvPreview.totalRows}</p>
                      </div>
                      <div className="rounded-lg bg-mint/60 p-2.5">
                        <p className="text-[0.6875rem] text-muted">Hợp lệ</p>
                        <p data-testid="csv-valid-count" className="text-sm font-bold text-ink">{csvPreview.validCount}</p>
                      </div>
                      <div className="rounded-lg bg-red-50 p-2.5">
                        <p className="text-[0.6875rem] text-muted">Có lỗi</p>
                        <p data-testid="csv-invalid-count" className="text-sm font-bold text-red-900">{csvPreview.invalidCount}</p>
                      </div>
                      <div className="rounded-lg bg-[#fef3c7]/70 p-2.5">
                        <p className="text-[0.6875rem] text-muted">Chỗ trống còn lại</p>
                        <p data-testid="csv-remaining-capacity" className="text-sm font-bold text-ink">
                          {csvPreview.remainingCapacity} câu
                        </p>
                      </div>
                    </div>

                    {duplicateAnalysis.totalDuplicates > 0 && (
                      <div
                        role="status"
                        data-testid="csv-duplicate-warning"
                        className="rounded-xl border-2 border-amber-700/45 bg-[#fef3c7]/80 p-3 text-xs text-ink space-y-2"
                      >
                        <p className="font-bold">
                          Cảnh báo câu hỏi trùng: phát hiện {duplicateAnalysis.totalDuplicates} dòng trùng nội dung câu hỏi
                          {duplicateAnalysis.existingDupRowNumbers.size > 0
                            ? ` (${duplicateAnalysis.existingDupRowNumbers.size} câu đã có sẵn trong bộ`
                            : ' ('}
                          {duplicateAnalysis.existingDupRowNumbers.size > 0 && duplicateAnalysis.inFileDupRowNumbers.size > 0
                            ? `, ${duplicateAnalysis.inFileDupRowNumbers.size} câu lặp lại ngay trong tệp)`
                            : duplicateAnalysis.inFileDupRowNumbers.size > 0
                            ? `${duplicateAnalysis.inFileDupRowNumbers.size} câu lặp lại ngay trong tệp)`
                            : ')'}
                          .
                        </p>
                        <label className="inline-flex min-h-[44px] items-center gap-2 font-semibold cursor-pointer">
                          <input
                            type="checkbox"
                            data-testid="csv-skip-duplicates-checkbox"
                            checked={skipDuplicates}
                            onChange={(event) => setSkipDuplicates(event.target.checked)}
                            className="h-4 w-4 accent-brand"
                          />
                          <span>Tự động bỏ qua các dòng bị trùng khi nhập ({duplicateAnalysis.totalDuplicates} câu)</span>
                        </label>
                      </div>
                    )}

                    {csvPreview.invalidCount > 0 && (
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-xs text-muted">Nhấn vào từng dòng để xem đầy đủ 4 đáp án A–D và đáp án đúng.</span>
                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            type="button"
                            data-testid="csv-export-errors-button"
                            onClick={handleExportInvalidRows}
                            className="btn-secondary min-h-[44px] px-3 text-xs font-semibold"
                          >
                            <Download size={13} aria-hidden="true" />
                            <span>Tải dòng lỗi (.csv)</span>
                          </button>
                          <button
                            type="button"
                            data-testid="csv-filter-errors-toggle"
                            aria-pressed={onlyErrorRows}
                            onClick={() => setOnlyErrorRows((prev) => !prev)}
                            className="btn-secondary min-h-[44px] px-3 text-xs font-semibold"
                          >
                            {onlyErrorRows ? 'Hiện tất cả dòng' : `Chỉ hiện dòng lỗi (${csvPreview.invalidCount})`}
                          </button>
                        </div>
                      </div>
                    )}

                    <ul className="max-h-64 space-y-2 overflow-y-auto pr-1 text-xs">
                      {visiblePreviewRows.map((row) => {
                        const isExpanded = Boolean(expandedRows[row.rowNumber]);
                        const isDupExisting = duplicateAnalysis.existingDupRowNumbers.has(row.rowNumber);
                        const isDupInFile = duplicateAnalysis.inFileDupRowNumbers.has(row.rowNumber);
                        const correctUpper = (row.correctOption || '').trim().toUpperCase();
                        return (
                          <li
                            key={row.rowNumber}
                            data-testid={`csv-preview-row-${row.rowNumber}`}
                            className={`rounded-lg border p-2.5 ${
                              row.valid
                                ? isDupExisting || isDupInFile
                                  ? 'border-amber-700/45 bg-[#fef3c7]/45 text-ink'
                                  : 'border-ink/25 bg-mint/25 text-ink'
                                : 'border-red-800/35 bg-red-50 text-red-950'
                            }`}
                          >
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <div className="flex flex-wrap items-center gap-1.5">
                                <span className="font-bold">Dòng {row.rowNumber}</span>
                                <span className="rounded bg-paper-white/80 px-1.5 py-0.5 text-[0.6875rem] font-bold">
                                  {row.valid ? `Hợp lệ · ${row.subjectLabel} · ${row.gradeLabel}` : 'Dòng lỗi — sẽ bỏ qua'}
                                </span>
                                {(isDupExisting || isDupInFile) && (
                                  <span className="rounded border border-amber-800/40 bg-[#f2d18c] px-1.5 py-0.5 text-[0.6875rem] font-bold text-ink">
                                    {isDupExisting ? 'Trùng câu đã có' : 'Trùng câu trong tệp'}
                                  </span>
                                )}
                              </div>
                              <button
                                type="button"
                                data-testid={`csv-expand-row-${row.rowNumber}`}
                                aria-expanded={isExpanded}
                                onClick={() => toggleExpandRow(row.rowNumber)}
                                className="inline-flex min-h-[44px] items-center rounded-md border border-ink/30 bg-paper-white px-3 text-xs font-semibold text-ink hover:bg-mint/40 cursor-pointer"
                              >
                                {isExpanded ? 'Thu gọn' : 'Xem đáp án'}
                              </button>
                            </div>

                            {row.questionText && (
                              <p className="mt-1 font-semibold text-ink">{row.questionText}</p>
                            )}

                            {isExpanded && (
                              <div
                                data-testid={`csv-row-details-${row.rowNumber}`}
                                className="mt-2 space-y-1.5 rounded-lg border border-surface-border bg-paper-white p-2.5 text-ink"
                              >
                                <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
                                  {(
                                    [
                                      ['A', row.optionA],
                                      ['B', row.optionB],
                                      ['C', row.optionC],
                                      ['D', row.optionD],
                                    ] as const
                                  ).map(([key, val]) => {
                                    const isCorrect = correctUpper === key;
                                    return (
                                      <div
                                        key={key}
                                        data-testid={`csv-row-${row.rowNumber}-opt-${key}`}
                                        className={`flex items-center justify-between gap-2 rounded px-2 py-1 ${
                                          isCorrect ? 'border border-ink bg-mint font-bold' : 'bg-surface-muted'
                                        }`}
                                      >
                                        <span>
                                          {key}. {val || '—'}
                                        </span>
                                        {isCorrect && (
                                          <span className="shrink-0 rounded bg-paper-white px-1.5 py-0.5 text-[0.625rem] font-bold text-brand-dark">
                                            Đáp án đúng
                                          </span>
                                        )}
                                      </div>
                                    );
                                  })}
                                </div>
                                {row.explanation && (
                                  <p className="text-xs text-muted">Giải thích: {row.explanation}</p>
                                )}
                              </div>
                            )}

                            {!row.valid && row.errors.length > 0 && (
                              <ul className="mt-1.5 list-disc pl-4 text-xs text-red-900">
                                {row.errors.map((errText, idx) => (
                                  <li key={idx}>{errText}</li>
                                ))}
                              </ul>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  </>
                )}
              </div>
            )}

            <div>
              <label htmlFor="quiz-csv-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="quiz-csv-pin"
                data-testid="quiz-csv-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(sanitizePin(event.target.value))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink"
              />
            </div>

            {dialogError && (
              <div role="alert" data-testid="quiz-csv-error" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            <div className="dialog-sticky-footer flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button type="button" disabled={busy} onClick={closeDialog} className="btn-secondary w-full sm:w-auto text-xs">
                Hủy
              </button>
              <button
                type="submit"
                data-testid="quiz-csv-import-submit"
                disabled={busy || !isCsvPreviewFresh || effectiveCsvImportCount <= 0}
                className="btn-primary w-full sm:w-auto text-xs font-bold disabled:opacity-50"
              >
                {busy
                  ? 'Đang nhập…'
                  : isCsvPreviewFresh && effectiveCsvImportCount > 0
                  ? `Nhập ${effectiveCsvImportCount} câu hợp lệ`
                  : 'Nhập câu hỏi'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}

      {dialog?.kind === 'assign-set' && (
        <SiteDialog
          title={dialog.importSummary ? 'Đã lưu câu hỏi · Gán cho bé' : `Gán bộ câu hỏi · ${dialog.set.name}`}
          size="lg"
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSetMutationSubmit} data-testid="csv-assign-child-form" className="space-y-4">
            {dialog.importSummary && (
              <div
                role="status"
                data-testid="csv-usable-saved-summary"
                className="rounded-xl border-2 border-ink bg-mint/45 p-4 text-sm text-ink space-y-1"
              >
                <p className="font-bold">
                  {dialog.importSummary.skippedInvalidCount > 0
                    ? `Đã nhập ${dialog.importSummary.importedCount} câu hợp lệ (bỏ qua ${dialog.importSummary.skippedInvalidCount} dòng lỗi).`
                    : `Đã lưu ${dialog.importSummary.importedCount} câu hỏi hợp lệ vào bộ "${dialog.set.name}".`}
                </p>
                <p className="text-xs text-ink/90">
                  Tổng cộng bộ <strong>{dialog.set.name}</strong> hiện có{' '}
                  <strong>{dialog.importSummary.totalQuestionsInSet} câu hỏi sử dụng được</strong>.
                </p>
              </div>
            )}

            {childProfiles.length === 0 ? (
              <div className="rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-sm text-muted">
                Chưa có hồ sơ bé nào để gán ngay. Hãy thêm hồ sơ bé ở phần trên rồi bấm &ldquo;Gán cho bé&rdquo; trên thẻ bộ câu hỏi nhé.
              </div>
            ) : (
              <>
                <fieldset className="space-y-2">
                  <legend className="text-sm font-semibold text-ink">
                    Chọn bé sẽ dùng bộ câu hỏi &ldquo;{dialog.set.name}&rdquo;
                  </legend>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {childProfiles.map((child) => {
                      const checked = assignChildIds.includes(child.id);
                      const cfg = quizConfigs.find((item) => item.profileId === child.id);
                      const childGrade = cfg?.grade ?? (child.quizGrade as QuizGradeCode | undefined) ?? 'LOP_1';
                      const childGradeLabel = cfg?.gradeLabel ?? child.quizGradeLabel ?? getQuizGradeLabel(childGrade);
                      const hasGradeMismatch =
                        Boolean(dialog.set.defaultGrade) && dialog.set.defaultGrade !== childGrade;
                      return (
                        <label
                          key={child.id}
                          className={`flex min-h-[52px] items-start gap-3 rounded-xl border p-3 text-xs cursor-pointer ${
                            checked
                              ? 'border-2 border-ink bg-mint/55 text-ink'
                              : 'border-surface-border bg-paper-white text-muted hover:text-ink'
                          }`}
                        >
                          <input
                            type="checkbox"
                            data-testid={`assign-set-child-checkbox-${child.id}`}
                            checked={checked}
                            disabled={busy}
                            onChange={() => toggleAssignChild(child.id)}
                            className="mt-1 h-4 w-4 shrink-0 accent-brand"
                          />
                          <ChildAvatarBadge avatarKey={child.avatarKey} size="sm" />
                          <span className="min-w-0 flex-1">
                            <span className="block font-bold text-ink">{child.name}</span>
                            <span className="block text-[0.6875rem] text-muted">Đang học: {childGradeLabel}</span>
                            {hasGradeMismatch && (
                              <span className="mt-0.5 block text-[0.6875rem] font-medium text-amber-900">
                                Lưu ý: Bộ này mặc định cho {dialog.set.defaultGradeLabel || getQuizGradeLabel(dialog.set.defaultGrade)}; hệ thống sẽ ưu tiên câu cùng khối lớp của bé trước.
                              </span>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </fieldset>

                <div>
                  <label htmlFor="assign-set-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh
                  </label>
                  <input
                    id="assign-set-pin"
                    data-testid="assign-set-pin-input"
                    type="password"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink"
                  />
                </div>
              </>
            )}

            {dialogError && (
              <div role="alert" data-testid="assign-set-error" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            <div className="dialog-sticky-footer flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                type="button"
                data-testid="close-after-csv-import-button"
                disabled={busy}
                onClick={closeDialog}
                className="btn-secondary w-full sm:w-auto text-xs"
              >
                {dialog.importSummary ? 'Để sau, đóng lại' : 'Hủy'}
              </button>
              {childProfiles.length > 0 && (
                <button
                  type="submit"
                  data-testid="assign-imported-set-button"
                  disabled={busy || assignChildIds.length === 0}
                  className="btn-primary w-full sm:w-auto text-xs font-bold disabled:opacity-50"
                >
                  {busy ? 'Đang gán…' : 'Gán cho bé'}
                </button>
              )}
            </div>
          </form>
        </SiteDialog>
      )}

      {dialog?.kind === 'questions' && (
        <SiteDialog title={`Câu hỏi · ${dialog.set.name}`} size="xl" onClose={closeDialog} busy={busy}>
          <div data-testid="quiz-questions-manager" className="space-y-4">
            {!dialog.set.builtIn && (
              <div className="flex flex-wrap items-end justify-between gap-2 rounded-xl border border-surface-border bg-surface-muted p-3">
                <div className="flex-1 min-w-[140px]">
                  <label htmlFor="quiz-questions-pin" className="mb-1 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh (dùng khi thêm/sửa/xóa)
                  </label>
                  <input
                    id="quiz-questions-pin"
                    data-testid="quiz-questions-pin-input"
                    type="password"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    autoComplete="off"
                    maxLength={6}
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink"
                  />
                </div>
                <button
                  type="button"
                  data-testid="add-manual-question-button"
                  disabled={busy || (detail?.questions.length ?? 0) >= MAX_QUIZ_QUESTIONS_PER_SET}
                  onClick={() => openManualAddForm(dialog.set)}
                  className="btn-primary min-h-[44px] px-3 text-xs font-bold disabled:opacity-50"
                >
                  <Plus size={14} aria-hidden="true" />
                  Thêm câu hỏi
                </button>
              </div>
            )}

            {showManualForm && !dialog.set.builtIn && (
              <form
                onSubmit={handleSaveManualQuestion}
                data-testid="manual-question-form"
                className="space-y-3 rounded-xl border-2 border-ink bg-mint/20 p-3.5"
              >
                <p className="text-xs font-bold text-ink">
                  {editingQuestion ? 'Sửa câu hỏi' : 'Thêm câu hỏi thủ công'}
                </p>
                <div>
                  <label htmlFor="manual-q-text" className="mb-1 block text-xs font-semibold text-ink">
                    Câu hỏi
                  </label>
                  <textarea
                    id="manual-q-text"
                    data-testid="manual-q-text"
                    rows={2}
                    maxLength={400}
                    required
                    disabled={busy}
                    value={qText}
                    onChange={(event) => setQText(event.target.value)}
                    placeholder="Ví dụ: 8 × 7 bằng bao nhiêu?"
                    className="w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink"
                  />
                </div>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  <div>
                    <label htmlFor="manual-q-a" className="mb-1 block text-xs font-semibold text-ink">Đáp án A</label>
                    <input id="manual-q-a" data-testid="manual-q-a" type="text" maxLength={200} required disabled={busy} value={optA} onChange={(e) => setOptA(e.target.value)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink" />
                  </div>
                  <div>
                    <label htmlFor="manual-q-b" className="mb-1 block text-xs font-semibold text-ink">Đáp án B</label>
                    <input id="manual-q-b" data-testid="manual-q-b" type="text" maxLength={200} required disabled={busy} value={optB} onChange={(e) => setOptB(e.target.value)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink" />
                  </div>
                  <div>
                    <label htmlFor="manual-q-c" className="mb-1 block text-xs font-semibold text-ink">Đáp án C</label>
                    <input id="manual-q-c" data-testid="manual-q-c" type="text" maxLength={200} required disabled={busy} value={optC} onChange={(e) => setOptC(e.target.value)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink" />
                  </div>
                  <div>
                    <label htmlFor="manual-q-d" className="mb-1 block text-xs font-semibold text-ink">Đáp án D</label>
                    <input id="manual-q-d" data-testid="manual-q-d" type="text" maxLength={200} required disabled={busy} value={optD} onChange={(e) => setOptD(e.target.value)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink" />
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label htmlFor="manual-q-answer" className="mb-1 block text-xs font-semibold text-ink">Đáp án đúng</label>
                    <select id="manual-q-answer" data-testid="manual-q-answer" disabled={busy} value={qAnswer} onChange={(e) => setQAnswer(e.target.value as QuizOptionKey)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-2 text-xs font-bold text-ink">
                      <option value="A">A</option>
                      <option value="B">B</option>
                      <option value="C">C</option>
                      <option value="D">D</option>
                    </select>
                  </div>
                  <div>
                    <label htmlFor="manual-q-subject" className="mb-1 block text-xs font-semibold text-ink">Môn học</label>
                    <select id="manual-q-subject" data-testid="manual-q-subject" disabled={busy} value={qSubject} onChange={(e) => setQSubject(e.target.value as QuizSubjectCode)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-2 text-xs text-ink">
                      {QUIZ_SUBJECTS.map((s) => (
                        <option key={s.code} value={s.code}>{s.label}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="manual-q-grade" className="mb-1 block text-xs font-semibold text-ink">Lớp</label>
                    <select id="manual-q-grade" data-testid="manual-q-grade" disabled={busy} value={qGrade} onChange={(e) => setQGrade(e.target.value as QuizGradeCode)} className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-2 text-xs text-ink">
                      {QUIZ_GRADES.map((g) => (
                        <option key={g.code} value={g.code}>{g.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div>
                  <label htmlFor="manual-q-explanation" className="mb-1 block text-xs font-semibold text-ink">
                    Giải thích ngắn khi bé làm sai (tuỳ chọn)
                  </label>
                  <input
                    id="manual-q-explanation"
                    data-testid="manual-q-explanation"
                    type="text"
                    maxLength={400}
                    disabled={busy}
                    value={qExplanation}
                    onChange={(event) => setQExplanation(event.target.value)}
                    placeholder="Ví dụ: 8 × 7 = 56 trong bảng nhân 8"
                    className="min-h-[44px] w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink"
                  />
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setShowManualForm(false);
                      setEditingQuestion(null);
                    }}
                    className="btn-secondary min-h-[44px] px-3.5 text-xs"
                  >
                    Đóng
                  </button>
                  <button
                    type="submit"
                    data-testid="manual-question-submit"
                    disabled={busy}
                    className="btn-primary min-h-[44px] px-4 text-xs font-bold"
                  >
                    {busy ? 'Đang lưu…' : editingQuestion ? 'Cập nhật câu hỏi' : 'Lưu câu hỏi'}
                  </button>
                </div>
              </form>
            )}

            {dialogError && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            {detailLoading ? (
              <p role="status" className="py-4 text-center text-xs text-muted">Đang tải danh sách câu hỏi…</p>
            ) : !detail || detail.questions.length === 0 ? (
              <p className="rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-center text-sm text-muted">
                Bộ này chưa có câu hỏi nào. Bạn có thể bấm &ldquo;Thêm câu hỏi&rdquo; hoặc tải tệp CSV lên nhé.
              </p>
            ) : (
              <ul className="max-h-72 space-y-2 overflow-y-auto pr-1 text-xs">
                {detail.questions.map((question) => (
                  <li
                    key={question.id}
                    data-testid={`quiz-question-item-${question.id}`}
                    className={`rounded-lg border p-3 space-y-2 ${
                      question.active ? 'border-surface-border bg-paper-white' : 'border-surface-border/50 bg-surface-muted opacity-75'
                    }`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-1 text-[0.6875rem] font-bold">
                        <span className="rounded bg-mint/70 px-1.5 py-0.5 text-ink">{question.subjectLabel}</span>
                        <span className="rounded bg-[#f2d18c]/60 px-1.5 py-0.5 text-ink">{question.gradeLabel}</span>
                        {!question.active && (
                          <span className="rounded bg-red-100 px-1.5 py-0.5 text-red-900">Đang tắt</span>
                        )}
                      </div>
                      {!dialog.set.builtIn && (
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => openManualEditForm(question)}
                            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-surface-border px-3 text-xs font-semibold text-ink hover:bg-mint/40 cursor-pointer"
                          >
                            Sửa
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void handleToggleQuestionActive(question)}
                            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-surface-border px-3 text-xs font-semibold text-ink hover:bg-mint/40 cursor-pointer"
                          >
                            {question.active ? 'Tắt' : 'Bật'}
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void handleDeleteQuestion(question)}
                            aria-label="Xóa câu hỏi"
                            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-red-800/35 px-3 text-xs font-semibold text-red-900 hover:bg-red-50 cursor-pointer"
                          >
                            <Trash2 size={14} aria-hidden="true" />
                          </button>
                        </div>
                      )}
                    </div>
                    <p className="font-bold text-ink">{question.questionText}</p>
                    <div className="grid grid-cols-1 gap-1 sm:grid-cols-2 text-xs">
                      {(['A', 'B', 'C', 'D'] as const).map((key) => {
                        const text =
                          key === 'A'
                            ? question.optionA
                            : key === 'B'
                            ? question.optionB
                            : key === 'C'
                            ? question.optionC
                            : question.optionD;
                        return (
                          <span
                            key={key}
                            className={`rounded px-2 py-1 ${
                              question.correctOption === key ? 'bg-mint/70 font-bold text-ink' : 'text-muted'
                            }`}
                          >
                            {key}. {text}
                          </span>
                        );
                      })}
                    </div>
                    {question.explanation && (
                      <p className="text-xs text-muted">Giải thích: {question.explanation}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </SiteDialog>
      )}
    </section>
  );
}
