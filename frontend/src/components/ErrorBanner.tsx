interface ErrorBannerProps {
  message?: string;
  onRetry?: () => void;
}

export default function ErrorBanner({
  message = 'Something went wrong.',
  onRetry,
}: ErrorBannerProps) {
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3 rounded-lg
                    bg-red-950 border border-red-700 text-red-300 text-sm">
      <span>⚠ {message}</span>
      {onRetry && (
        <button
          onClick={onRetry}
          className="px-3 py-1 rounded bg-red-700 hover:bg-red-600
                     text-white text-xs font-medium transition-colors"
        >
          Retry
        </button>
      )}
    </div>
  );
}