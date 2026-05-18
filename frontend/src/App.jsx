import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { useState, useEffect } from 'react';

// --- IMPORTS CHUẨN 100% THEO FILE VẬT LÝ TRÊN TREE CỦA BẠN ---

// 1. Layout (Nằm trong src/components/layout/)
import MainLayout from './components/layout/MainLayout';

// 2. Public Pages (Nằm trong src/pages/public/)
import Home from './pages/public/Home'; // [ĐÃ MỞ LẠI] Trỏ thẳng vào file rời bạn vừa tạo
import Login from './pages/public/Login';
import Register from './pages/public/Register'; 

// Tạm thời comment vì trên cây thư mục của bạn chưa có file vật lý này
// import CampaignDetail from './pages/public/CampaignDetail';

// 3. User Pages (Nằm trong src/pages/user/)
import Dashboard from './pages/user/Dashboard';
import CreateCampaign from './pages/user/CreateCampaign';

// 4. Admin Pages (Nằm trong src/pages/admin/AdminPanel.jsx)
import AdminPanel from './pages/admin/AdminPanel';

export default function App() {
  // [BỔ SUNG] 1. Khởi tạo State toàn cục quản lý trạng thái đăng nhập
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  // [BỔ SUNG] 2. Kiểm tra token khi web vừa khởi động
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) setIsLoggedIn(true);
  }, []);

  // [BỔ SUNG] 3. Hàm kích hoạt đổi trạng thái (sẽ truyền cho trang Login/Register)
  const handleLoginSuccess = () => {
    setIsLoggedIn(true);
  };

  // [BỔ SUNG] 4. Hàm đăng xuất (sẽ truyền cho Navbar)
  const handleLogoutSuccess = () => {
    setIsLoggedIn(false);
  };
  
  return (
    <Router>
      {/* Cấu hình trạm Toast toàn cục để hứng lỗi từ API Backend */}
      <Toaster 
        position="top-right" 
        toastOptions={{
          duration: 4000,
          style: {
            background: '#0f172a', // Tông tối tiệp với bg-brand-dark
            color: '#e2e8f0',
            border: '1px solid #1e293b',
            fontSize: '14px',
          },
          error: {
            style: { borderColor: '#ef4444' }, // Viền đỏ khi dính lỗi
          },
          success: {
            style: { borderColor: '#10b981' }, // Viền xanh khi thành công
          }
        }} 
      />
      
      <Routes>
        {/* [BỔ SUNG] Truyền State và Hàm Logout vào Layout để nó truyền tiếp cho Navbar */}
        <Route element={<MainLayout isLoggedIn={isLoggedIn} onLogout={handleLogoutSuccess} />}>
          {/* Route trang chủ gọi trực tiếp Component Home rời của bạn */}
          <Route path="/" element={<Home />} />
          
          {/* [BỔ SUNG] Truyền hàm báo cáo thành công vào Login và Register */}
          <Route path="/login" element={<Login onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/register" element={<Register onLoginSuccess={handleLoginSuccess} />} />
          
          {/* Tạm thời đóng để tránh vỡ hệ thống, khi nào tạo file CampaignDetail.jsx thì mở ra xài */}
          {/* <Route path="/campaign/:id" element={<CampaignDetail />} /> */}
          
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/create-campaign" element={<CreateCampaign />} />
          <Route path="/admin" element={<AdminPanel />} />
        </Route>
      </Routes>
    </Router>
  );
}