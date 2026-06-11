import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster, toast } from 'react-hot-toast';
import { useState, useEffect } from 'react';
import { jwtDecode } from 'jwt-decode'; 

// --- IMPORTS ---
import MainLayout from './components/layout/MainLayout';
import Home from './pages/public/Home'; 
import Login from './pages/public/Login';
import Register from './pages/public/Register';
import CampaignDetail from './pages/public/CampaignDetail'; 
import Dashboard from './pages/user/Dashboard';
import CreateCampaign from './pages/user/CreateCampaign';
import AdminPanel from './pages/admin/AdminPanel';
import { isAdminRole } from './services/auth';
import { WalletProvider } from './context/WalletContext';

// =========================================================================
// [SỬA ĐỔI] CHỐT CHẶN BẢO MẬT PHÂN QUYỀN HAI CHIỀU (PROTECTED ROUTE)
// =========================================================================
const ProtectedRoute = ({ isLoggedIn, requireAdmin, children }) => {
  // 1. Nếu chưa đăng nhập -> Đá ra chuồng gà (Trang Login)
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }

  const token = localStorage.getItem('accessToken');
  let userRole = '';

  if (token) {
    try {
      const decoded = jwtDecode(token);
      userRole = decoded.role; // Lấy role thật từ Token ra để đối chiếu
    } catch (err) {
      console.error("Token reading error:", err);
      return <Navigate to="/login" replace />;
    }
  }

  const isAdmin = isAdminRole(userRole);

  // 2. TÌNH HUỐNG 1: Tuyến đường yêu cầu ADMIN, nhưng người vào không phải Admin
  if (requireAdmin) {
    if (!isAdmin) {
      toast.error("Prohibited area! Admin only.");
      return <Navigate to="/" replace />; // Đá về Trang chủ
    }
  } 
  
  // 3. TÌNH HUỐNG 2 [QUAN TRỌNG]: Tuyến đường thông thường của USER, nhưng ADMIN lại đi lạc vào
  else {
    if (isAdmin) {
      // Chặn đứng Admin tại cửa khẩu, điều hướng thẳng về tổng dinh Admin Panel
      // Tuyệt đối không cho 'children' (ở đây là Dashboard) có cơ hội render hay chạy useEffect gọi API ví lỗi
      return <Navigate to="/admin" replace />; 
    }
  }

  // 4. Vượt qua mọi bài kiểm tra -> Mở cửa cho vào Component hợp pháp
  return children;
};
// =========================================================================


export default function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  // State này giúp tránh lỗi nháy màn hình Login khi người dùng ấn F5 ở trang Dashboard
  const [isChecking, setIsChecking] = useState(true); 

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      setIsLoggedIn(true);
    }
    setIsChecking(false); // Quét xong két sắt thì báo cáo hoàn thành
  }, []);

  const handleLoginSuccess = () => {
    setIsLoggedIn(true);
    setAuthVersion(version => version + 1);
  };

  const handleLogoutSuccess = () => {
    setIsLoggedIn(false);
    setAuthVersion(version => version + 1);
  };
  
  // Nếu React còn đang bận lục két sắt tìm Token thì chưa vội render giao diện
  if (isChecking) {
    return <div className="min-h-screen bg-brand-dark"></div>; 
  }

  return (
    <Router>
      <Toaster 
        position="top-right" 
        toastOptions={{
          duration: 4000,
          style: {
            background: '#0f172a', 
            color: '#e2e8f0',
            border: '1px solid #1e293b',
            fontSize: '14px',
          },
          error: { style: { borderColor: '#ef4444' } }, 
          success: { style: { borderColor: '#10b981' } } 
        }} 
      />
      
      <WalletProvider isLoggedIn={isLoggedIn} authVersion={authVersion}>
      <Routes>
        <Route element={<MainLayout isLoggedIn={isLoggedIn} onLogout={handleLogoutSuccess} authVersion={authVersion} />}>
          
          {/* --- VÙNG XANH: KHÁCH VÀO TỰ DO --- */}
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/register" element={<Register onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/campaign/:id" element={<CampaignDetail />} />
          
          {/* --- VÙNG VÀNG: BẮT BUỘC ĐĂNG NHẬP (USER) --- */}
          <Route path="/dashboard" element={
            <ProtectedRoute isLoggedIn={isLoggedIn} requireAdmin={false}>
              <Dashboard />
            </ProtectedRoute> 
          } />
          
          <Route path="/create-campaign" element={
            <ProtectedRoute isLoggedIn={isLoggedIn} requireAdmin={false}>
              <CreateCampaign />
            </ProtectedRoute>
          } />
          
          {/* --- VÙNG ĐỎ: CHỈ DÀNH CHO ADMIN --- */}
          <Route path="/admin" element={
            <ProtectedRoute isLoggedIn={isLoggedIn} requireAdmin={true}>
              <AdminPanel />
            </ProtectedRoute>
          } />

        </Route>
      </Routes>
      </WalletProvider>
    </Router>
  );
}
