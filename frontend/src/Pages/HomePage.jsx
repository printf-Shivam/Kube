import SearchBar from "../components/SearchBar";
import { useNavigate } from "react-router-dom";
import logo from "../assets/logo.png";

function HomePage() {
  const navigate = useNavigate();

  const handleSearch = (query) => {
    navigate(`/search?q=${encodeURIComponent(query)}`);
  };

  return (
    <div className="h-screen flex flex-col items-center justify-center bg-[#0b0f14] text-[#e5e7eb]">
      
      <img src={logo} alt="Kube Logo" className="w-56 md:w-64 mb-8" />

      <div className="w-full max-w-xl">
        <SearchBar onSearch={handleSearch} />
      </div>

    </div>
  );
}

export default HomePage;