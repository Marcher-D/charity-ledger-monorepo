import { Link, useNavigate } from 'react-router-dom';
import { PlusCircle, Wallet, LogOut } from 'lucide-react'; // Bổ sung icon LogOut
import { toast } from 'react-hot-toast'; // Bổ sung để hiện thông báo

export default function Navbar() {
  const navigate = useNavigate();
  
  // Kiểm tra xem user đã đăng nhập chưa bằng cách check token trong két sắt
  const token = localStorage.getItem("accessToken");
  const isLoggedIn = !!token;

  // Hàm xử lý đăng xuất
  const handleLogout = () => {
    localStorage.removeItem("accessToken"); // Xóa vé VIP
    toast.success("Đã đăng xuất an toàn!");
    navigate("/login"); // Đá về trang đăng nhập
  };

  return (
    <nav className="border-b border-gray-800/50 bg-brand-dark/80 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-6 h-20 flex justify-between items-center">
        
        {/* Logo */}
        <Link to="/" className="flex items-center space-x-2">
          <div className="w-8 h-8 rounded border border-brand-success flex items-center justify-center transform rotate-45">
            <div className="w-3 h-3 bg-brand-success rounded-full"></div>
          </div>
          <span className="text-xl font-bold tracking-wider">
            <span className="text-brand-success">CHARITY</span>.LEDGER
          </span>
        </Link>

        {/* Right Actions */}
        <div className="flex items-center space-x-6 text-sm">
          <Link to="/create-campaign" className="flex items-center space-x-2 text-gray-400 hover:text-white transition-colors">
            <PlusCircle size={18} />
            <span className="font-medium">CREATE CAMPAIGN</span>
          </Link>

          {/* Render theo điều kiện: Đã đăng nhập thì hiện Ví + Đăng xuất, Chưa thì hiện Đăng nhập */}
          {isLoggedIn ? (
            <>
              {/* Wallet Info (Mock) */}
              <Link to="/dashboard" className="flex items-center space-x-3 bg-brand-card border border-gray-800 px-4 py-2 rounded-lg hover:border-brand-success/50 transition-colors">
                <Wallet size={18} className="text-gray-400" />
                <div className="flex flex-col text-right line-height-1">
                  <span className="font-bold text-brand-success leading-tight">$1,250.00</span>
                  <span className="text-[10px] text-gray-500 leading-tight">$100.00 pending</span>
                </div>
              </Link>

              {/* Nút Đăng Xuất */}
              <button 
                onClick={handleLogout} 
                className="flex items-center space-x-2 text-red-500 hover:text-red-400 font-bold transition-colors ml-4"
              >
                <LogOut size={18} />
                <span>LOG OUT</span>
              </button>
            </>
          ) : (
            <Link to="/login" className="px-6 py-2 bg-brand-success text-brand-dark font-bold rounded hover:opacity-90 transition-opacity">
              LOG IN
            </Link>
          )}
        </div>
      </div>
    </nav>
  );
}