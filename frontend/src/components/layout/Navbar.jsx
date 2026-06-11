// [BỔ SUNG] Import useState, useEffect
import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PlusCircle, Wallet, LogOut, ShieldAlert } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { jwtDecode } from 'jwt-decode';
import { isAdminRole } from '../../services/auth';
import { useWallet } from '../../context/WalletContext';

export default function Navbar({ isLoggedIn, onLogout, authVersion }) {
  const navigate = useNavigate();

  // Số dư ví lấy từ WalletContext — một nguồn duy nhất cho cả app,
  // tự làm mới mỗi 5 giây (không còn interval riêng trong Navbar).
  const { profile: walletInfo } = useWallet();
  // State phân quyền
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    // 1. Nếu chưa đăng nhập -> Dọn dẹp nhà cửa và nghỉ
    if (!isLoggedIn) {
      setIsAdmin(false);
      return;
    }

    const token = localStorage.getItem("accessToken");
    if (!token) return;

    try {
      // 2. Soi Token để hiện nút Admin Panel (ví do WalletContext lo)
      const decoded = jwtDecode(token);
      setIsAdmin(isAdminRole(decoded.role));
    } catch (error) {
      console.error("Token reading error:", error);
      setIsAdmin(false);
    }
  }, [isLoggedIn, authVersion]); // Đọc lại token khi phiên đăng nhập thay đổi

  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    toast.success("Log out successfully!");
    if (onLogout) onLogout(); 
    navigate("/login");
  };

  return (
    <nav className="border-b border-brand-line/70 bg-brand-dark/90 backdrop-blur-md sticky top-0 z-50">
      {/* Mobile: cho phép xuống dòng thay vì tràn ngang (chiều cao cố định chỉ từ sm trở lên) */}
      <div className="max-w-7xl mx-auto px-3 sm:px-6 py-3 sm:py-0 sm:h-20 flex flex-wrap justify-between items-center gap-y-2">

        <Link to="/" className="flex items-center space-x-2">
          {/* Logo */}
          <div className="w-8 h-8 rounded border border-brand-success flex items-center justify-center transform rotate-45">
            <div className="w-3 h-3 bg-brand-success rounded-full"></div>
          </div>
          <span className="text-lg sm:text-xl font-bold tracking-normal text-brand-cream">
            <span className="text-brand-success">CHARITY</span>.LEDGER
          </span>
        </Link>

        <div className="flex items-center gap-3 sm:gap-6 text-sm">
          
          {/* Menu Admin Panel nổi bật */}
          {isAdmin && (
            <Link to="/admin" className="flex items-center space-x-2 text-brand-success hover:text-brand-cream transition-colors font-bold border border-brand-success/30 px-3 py-1.5 rounded bg-brand-success/10">
              <ShieldAlert size={18} />
              <span>ADMIN PANEL</span>
            </Link>
          )}

          {!isAdmin && (
            <Link to="/create-campaign" className="flex items-center space-x-2 text-brand-creamMuted hover:text-brand-cream transition-colors">
              <PlusCircle size={18} />
              <span className="font-medium hidden sm:inline">CREATE CAMPAIGN</span>
            </Link>
          )}

          {isLoggedIn ? (
            <>
              {/* Phân tách UI Ví: Admin hiện nhãn Ban Quản Trị, User hiện số dư ví */}
              {isAdmin ? (
                // UI tinh tế dành riêng cho Admin
                <Link to="/admin" className="flex items-center space-x-3 bg-brand-card border border-brand-success/30 px-4 py-2 rounded-lg text-brand-success font-medium hover:border-brand-success/60 transition-colors">
                  <span className="w-2 h-2 bg-brand-success rounded-full animate-pulse"></span>
                  <span>Admin System</span>
                </Link>
              ) : (
                // UI Ví lấy data thật từ API dành cho User thường
                <Link to="/dashboard" className="flex items-center space-x-2 sm:space-x-3 bg-brand-card border border-brand-line px-3 sm:px-4 py-2 rounded-lg hover:border-brand-success/50 transition-colors">
                  <Wallet size={18} className="text-brand-creamMuted" />
                  <div className="flex flex-col text-right leading-none">
                    {walletInfo ? (
                      <>
                        <span className="font-bold text-brand-success text-sm">{(Number(walletInfo.availableBalance) || 0).toFixed(2)} TCL</span>
                        <span className="text-[10px] text-brand-creamMuted mt-1">{(Number(walletInfo.pendingBalance) || 0).toFixed(2)} pending</span>
                      </>
                    ) : (
                      <span className="font-bold text-brand-success text-sm">My Dashboard</span>
                    )}
                  </div>
                </Link>
              )}

              <button onClick={handleLogout} className="flex items-center space-x-2 text-red-500 hover:text-red-400 font-bold transition-colors ml-1 sm:ml-4">
                <LogOut size={18} />
                <span className="hidden sm:inline">LOG OUT</span>
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
