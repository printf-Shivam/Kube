import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import SearchBar from "../components/SearchBar";
import ResultsList from "../components/ResultsList";
import logo from "../assets/logo.png";

function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const query = searchParams.get("q") || "";

  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchResults = async (searchQuery) => {
    setLoading(true);
    setError(null);

    try {
      const res = await fetch(
        `http://localhost:8080/api/search?q=${encodeURIComponent(searchQuery)}`
      );

      const data = await res.json();

      const formatted = Object.entries(data)
        .map(([url, score]) => ({
          url,
          title: url.replace("https://", "").replace("http://", "").split("/")[0],
          score,
        }))
        .sort((a, b) => b.score - a.score);

      setResults(formatted);
    } catch {
      setError("Error fetching results");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (query) fetchResults(query);
  }, [query]);

  const handleSearch = (newQuery) => {
    setSearchParams({ q: newQuery });
  };

  return (
    <div className="min-h-screen bg-[#0b0f14] text-[#e5e7eb] px-6 pt-6">

      {/* Top bar */}
      <div className="flex items-center gap-6 mb-6">

      <img
        src={logo}
        alt="Kube Logo"
        className="w-28 md:w-32 cursor-pointer"
        onClick={() => navigate("/")}
        />

        <div className="w-full max-w-2xl">
          <SearchBar onSearch={handleSearch} />
        </div>

      </div>

      {/* Results */}
      <ResultsList
        results={results}
        loading={loading}
        error={error}
        query={query}
      />

    </div>
  );
}

export default SearchPage;