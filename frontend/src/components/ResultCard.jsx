function ResultCard({ result, index }) {
    return (
      <div className="bg-[#111827] border border-gray-800 p-4 rounded-lg mb-4 hover:border-gray-600 transition">
  
        <a
          href={result.url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-gray-500"
        >
          {result.url}
        </a>
  
        <a
          href={result.url}
          target="_blank"
          rel="noopener noreferrer"
          className="block text-lg text-blue-400 hover:underline"
        >
          {result.title}
        </a>
  
        <div className="text-sm text-gray-400 mt-1">
          Rank #{index + 1} • Score: {result.score.toFixed(4)}
        </div>
  
      </div>
    );
  }
  
  export default ResultCard;