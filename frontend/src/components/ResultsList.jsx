import ResultCard from "./ResultCard";

function ResultsList({ results, loading, error, query }) {
  if (loading) {
    return (
      <div className="flex justify-center mt-10">
        <div className="w-6 h-6 border-4 border-gray-700 border-t-blue-400 rounded-full animate-spin"></div>
      </div>
    );
  }

  if (error) {
    return <div className="text-red-400 mt-4">{error}</div>;
  }

  if (!results || results.length === 0) {
    return (
      <div className="text-gray-400 mt-4">
        No results found for "{query}"
      </div>
    );
  }

  return (
    <div className="max-w-2xl">
      <div className="text-sm text-gray-500 mb-4">
        About {results.length} results
      </div>

      {results.map((result, index) => (
        <ResultCard key={result.url} result={result} index={index} />
      ))}
    </div>
  );
}

export default ResultsList;