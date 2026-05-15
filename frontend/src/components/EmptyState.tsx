interface EmptyStateProps {
  cols?: number;
  message?: string;
}

export default function EmptyState({
  cols = 4,
  message = 'No data available.',
}: EmptyStateProps) {
  return (
    <tr>
      <td
        colSpan={cols}
        className="px-4 py-10 text-center text-sm text-gray-500"
      >
        {message}
      </td>
    </tr>
  );
}