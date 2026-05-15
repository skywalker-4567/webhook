interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
}

export default function Pagination({
  currentPage,
  totalPages,
  onPrev,
  onNext,
}: PaginationProps) {
  const isFirst = currentPage === 0;
  const isLast  = currentPage >= totalPages - 1;

  return (
    <div className="flex items-center justify-between px-1 py-3 text-sm text-gray-400">
      <button
        onClick={onPrev}
        disabled={isFirst}
        className="px-3 py-1.5 rounded bg-gray-800 hover:bg-gray-700
                   disabled:opacity-40 disabled:cursor-not-allowed
                   text-gray-300 transition-colors text-xs font-medium"
      >
        ← Prev
      </button>

      <span className="text-xs">
        Page {currentPage + 1} of {Math.max(totalPages, 1)}
      </span>

      <button
        onClick={onNext}
        disabled={isLast}
        className="px-3 py-1.5 rounded bg-gray-800 hover:bg-gray-700
                   disabled:opacity-40 disabled:cursor-not-allowed
                   text-gray-300 transition-colors text-xs font-medium"
      >
        Next →
      </button>
    </div>
  );
}