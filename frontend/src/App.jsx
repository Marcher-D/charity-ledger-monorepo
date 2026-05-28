import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster, toast } from 'react-hot-toast';
import { useState, useEffect } from 'react';
import { jwtDecode } from 'jwt-decode'; // [BỔ SUNG] Import máy soi JWT

// --- IMPORTS ---
import MainLayout from './components/layout/MainLayout';
import Home from './pages/public/Home'; 
import Login from './pages/public/Login';
import Register from './pages/public/Register';
import CampaignDetail from './pages/public/CampaignDetail'; 
import Dashboard from './pages/user/Dashboard';
import CreateCampaign from './pages/user/CreateCampaign';
import AdminPanel from './pages/admin/AdminPanel';

// =========================================================================
// [BỔ SUNG] CHỐT CHẶN BẢO MẬT (PROTECTED ROUTE)
// =========================================================================
const ProtectedRoute = ({ isLoggedIn, requireAdmin, children }) => {
  // 1. Nếu chưa đăng nhập -> Đá ra chuồng gà (Trang Login)
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }

  // 2. Nếu route này yêu cầu quyền Admin, lôi Token ra soi
  if (requireAdmin) {
    const token = localStorage.getItem('accessToken');
    try {
      const decoded = jwtDecode(token);
      if (decoded.role !== 'ROLE_ADMIN') {
        toast.error("Khu vực cấm! Chỉ dành cho Quản trị viên.");
        return <Navigate to="/" replace />; // Không phải Admin -> Đá về Trang chủ
      }
    } catch (err) {
      // Token bị hỏng / giải mã lỗi -> Đá ra trang Login bắt đăng nhập lại
      return <Navigate to="/login" replace />;
    }
  }

  // 3. Vượt qua mọi bài kiểm tra -> Mở cửa cho vào Component
  return children;
};
// =========================================================================


export default function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  // [BỔ SUNG] State này giúp tránh lỗi nháy màn hình Login khi người dùng ấn F5 ở trang Dashboard
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
  };

  const handleLogoutSuccess = () => {
    setIsLoggedIn(false);
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
      
      <Routes>
        <Route element={<MainLayout isLoggedIn={isLoggedIn} onLogout={handleLogoutSuccess} />}>
          
          {/* --- VÙNG XANH: KHÁCH VÀO TỰ DO --- */}
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/register" element={<Register onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/campaign/:id" element={<CampaignDetail />} />
          
          {/* --- VÙNG VÀNG: BẮT BUỘC ĐĂNG NHẬP (USER) --- */}
          <Route path="/dashboard" element={
            <ProtectedRoute isLoggedIn={isLoggedIn}>
              <Dashboard />
            </ProtectedRoute> 
          } />
          
          <Route path="/create-campaign" element={
            <ProtectedRoute isLoggedIn={isLoggedIn}>
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
    </Router>
  );
}