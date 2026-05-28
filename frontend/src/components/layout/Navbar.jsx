// [BỔ SUNG] Import useState, useEffect và apiRequest
import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PlusCircle, Wallet, LogOut, ShieldAlert } from 'lucide-react'; 
import { toast } from 'react-hot-toast';
import { jwtDecode } from 'jwt-decode'; 
import { apiRequest } from '../../services/api'; 

export default function Navbar({ isLoggedIn, onLogout }) {
  const navigate = useNavigate();
  
  // [BỔ SUNG] State lưu thông tin ví của người dùng
  const [walletInfo, setWalletInfo] = useState(null);
  
  let isAdmin = false;
  if (isLoggedIn) {
    const token = localStorage.getItem("accessToken");
    if (token) {
      try {
        const decoded = jwtDecode(token);
        isAdmin = decoded.role === 'ROLE_ADMIN'; 
      } catch (error) {
        console.error("Lỗi đọc token:", error);
      }
    }
  }

  // [BỔ SUNG] Tự động kéo số dư ví khi đã đăng nhập
  useEffect(() => {
    if (isLoggedIn) {
      apiRequest('/api/v1/users/me')
        .then(data => setWalletInfo(data))
        .catch(err => console.log("Lọc lỗi ngầm Navbar:", err.message));
    } else {
      setWalletInfo(null); // Đăng xuất thì dọn ví
    }
  }, [isLoggedIn]);

  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    toast.success("Đã đăng xuất an toàn!");
    if (onLogout) onLogout(); 
    navigate("/login");
  };

  return (
    <nav className="border-b border-gray-800/50 bg-brand-dark/80 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-6 h-20 flex justify-between items-center">
        
        <Link to="/" className="flex items-center space-x-2">
          {/* Logo */}
          <div className="w-8 h-8 rounded border border-brand-success flex items-center justify-center transform rotate-45">
            <div className="w-3 h-3 bg-brand-success rounded-full"></div>
          </div>
          <span className="text-xl font-bold tracking-wider">
            <span className="text-brand-success">CHARITY</span>.LEDGER
          </span>
        </Link>

        <div className="flex items-center space-x-6 text-sm">
          
          {isAdmin && (
            <Link to="/admin" className="flex items-center space-x-2 text-red-400 hover:text-red-300 transition-colors font-bold border border-red-500/30 px-3 py-1.5 rounded bg-red-500/10">
              <ShieldAlert size={18} />
              <span>ADMIN PANEL</span>
            </Link>
          )}

          <Link to="/create-campaign" className="flex items-center space-x-2 text-gray-400 hover:text-white transition-colors">
            <PlusCircle size={18} />
            <span className="font-medium">CREATE CAMPAIGN</span>
          </Link>

          {isLoggedIn ? (
            <>
              {/* [BỔ SUNG] Giao diện ví động, lấy data thật từ API */}
              <Link to="/dashboard" className="flex items-center space-x-3 bg-brand-card border border-gray-800 px-4 py-2 rounded-lg hover:border-brand-success/50 transition-colors">
                <Wallet size={18} className="text-gray-400" />
                <div className="flex flex-col text-right leading-none">
                  {walletInfo ? (
                    <>
                      <span className="font-bold text-brand-success text-sm">{walletInfo.availableBalance} TCL</span>
                      <span className="text-[10px] text-gray-500 mt-1">{walletInfo.pendingBalance} pending</span>
                    </>
                  ) : (
                    <span className="font-bold text-brand-success text-sm">My Dashboard</span>
                  )}
                </div>
              </Link>

              <button onClick={handleLogout} className="flex items-center space-x-2 text-red-500 hover:text-red-400 font-bold transition-colors ml-4">
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