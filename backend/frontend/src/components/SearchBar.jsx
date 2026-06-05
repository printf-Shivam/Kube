import { useState } from "react";

function SearchBar({ onSearch }) {
  const [query, setQuery] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!query.trim()) return;
    onSearch(query);
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="flex items-center bg-[#111827] border border-gray-700 rounded-full px-4 py-2 hover:border-gray-500 transition"
    >
      <input
        type="text"
        placeholder="Search..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="flex-1 bg-transparent outline-none text-sm text-[#e5e7eb] placeholder-gray-500"
      />

      <button
        type="submit"
        className="text-sm px-3 py-1 bg-[#1f2937] rounded-full hover:bg-[#374151]"
      >
        Search
      </button>
    </form>
  );
}

export default SearchBar;