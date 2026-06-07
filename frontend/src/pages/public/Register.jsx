import { Link, useNavigate } from 'react-router-dom'; // [BỔ SUNG] Import useNavigate
import { UserPlus } from 'lucide-react';
// [BỔ SUNG] Import thư viện quản lý State, Toast và API helper
import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api';
import { jwtDecode } from 'jwt-decode';
import { isAdminRole } from '../../services/auth';

// [BỔ SUNG] Nhận hàm 'onLoginSuccess' từ App.jsx ném vào
export default function Register({ onLoginSuccess }) {
  // [BỔ SUNG] Khởi tạo State lưu dữ liệu form và lỗi validation
  const [formData, setFormData] = useState({ username: '', email: '', password: '' });
  const [errors, setErrors] = useState({});
  const navigate = useNavigate(); // [BỔ SUNG] Khởi tạo navigate

  // [BỔ SUNG] Hàm xử lý Submit gọi API
  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({}); // Reset lỗi cũ

    try {
      // [BỔ SUNG] Gán kết quả vào biến data
      const data = await apiRequest('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify(formData)
      });
      
      // [BỔ SUNG] Lưu token, báo cáo trạng thái và chuyển trang
      if (data && data.accessToken) {
        localStorage.setItem("accessToken", data.accessToken);  

        // 1. Soi quyền từ Token trước
        const decoded = jwtDecode(data.accessToken);
        if (onLoginSuccess) onLoginSuccess(); // Kích hoạt đổi UI Navbar
        
        // 2. Chuyển hướng trúng đích né vùng lỗi
        if (isAdminRole(decoded.role)) {
          navigate('/admin', { replace: true });
        } else {
          navigate('/dashboard', { replace: true });
        }

        // 3. Bật trạng thái login sau khi đã đổi tuyến đường
        toast.success("Đăng ký thành công!");
      }
    } catch (err) {
      // [BỔ SUNG] Xử lý lỗi Message cho Toast
      toast.error(err.message || 'Đăng ký thất bại!');
      
      // [BỔ SUNG] Xử lý lỗi Validation Field-level
      if (err.data && err.data.validation) {
        setErrors(err.data.validation);
      }
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-5rem)] bg-brand-dark px-4">
      <div className="bg-brand-card border border-gray-800 p-8 rounded-2xl shadow-2xl w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-12 h-12 bg-brand-success/10 border border-brand-success/30 rounded-xl flex items-center justify-center font-bold text-brand-success mx-auto mb-4">
            <UserPlus size={24} />
          </div>
          <h2 className="text-2xl font-bold text-white tracking-tight">Create Account</h2>
          <p className="text-brand-grayText text-sm mt-2">
            Join the ledger to create campaigns, stake tokens, and validate transactions.
          </p>
        </div>

        {/* Form Đăng ký khớp 100% với các trường Validation của Backend */}
        <form className="space-y-5" onSubmit={handleSubmit /* [BỔ SUNG] Gọi hàm submit */}>
          <div>
            <label className="block text-xs font-bold tracking-widest text-brand-success uppercase mb-1.5">Username</label>
            <input 
              type="text" 
              placeholder="alice" 
              value={formData.username} /* [BỔ SUNG] Bind value */
              onChange={(e) => setFormData({...formData, username: e.target.value})} /* [BỔ SUNG] Handle change */
              /* [BỔ SUNG] Đổi màu viền linh hoạt nếu có lỗi */
              className={`w-full bg-brand-dark/50 border ${errors.username ? 'border-red-500' : 'border-gray-800'} rounded-lg px-4 py-2.5 text-sm text-white focus:outline-none focus:border-brand-success/60 transition-colors`} 
            />
            {/* [BỔ SUNG] Text hiển thị lỗi Field-level */}
            {errors.username && <p className="text-red-500 text-xs mt-1">{errors.username}</p>}
          </div>
          <div>
            <label className="block text-xs font-bold tracking-widest text-brand-success uppercase mb-1.5">Email Address</label>
            <input 
              type="email" 
              placeholder="alice@example.com" 
              value={formData.email} /* [BỔ SUNG] */
              onChange={(e) => setFormData({...formData, email: e.target.value})} /* [BỔ SUNG] */
              className={`w-full bg-brand-dark/50 border ${errors.email ? 'border-red-500' : 'border-gray-800'} rounded-lg px-4 py-2.5 text-sm text-white focus:outline-none focus:border-brand-success/60 transition-colors`} 
            />
            {/* [BỔ SUNG] Text hiển thị lỗi Field-level */}
            {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
          </div>
          <div>
            <label className="block text-xs font-bold tracking-widest text-brand-success uppercase mb-1.5">Password</label>
            <input 
              type="password" 
              placeholder="Min 8 characters" 
              value={formData.password} /* [BỔ SUNG] */
              onChange={(e) => setFormData({...formData, password: e.target.value})} /* [BỔ SUNG] */
              className={`w-full bg-brand-dark/50 border ${errors.password ? 'border-red-500' : 'border-gray-800'} rounded-lg px-4 py-2.5 text-sm text-white focus:outline-none focus:border-brand-success/60 transition-colors`} 
            />
            {/* [BỔ SUNG] Text hiển thị lỗi Field-level */}
            {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
          </div>
          
          <button type="submit" className="w-full py-3 bg-brand-success text-brand-dark font-extrabold rounded-lg hover:opacity-90 transition-opacity shadow-[0_0_15px_rgba(0,229,143,0.2)] text-sm">
            REGISTER ACCOUNT
          </button>
        </form>

        <p className="text-center text-sm text-brand-grayText mt-6">
          Already have an account? <Link to="/login" className="text-brand-success font-semibold hover:underline">Log in here</Link>
        </p>
      </div>
    </div>
  );
}
