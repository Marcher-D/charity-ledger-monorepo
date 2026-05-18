import { Link } from 'react-router-dom';
// [BỔ SUNG] Import thư viện
import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api';

// [BỔ SUNG] Nhận hàm 'onLoginSuccess' từ App.jsx ném vào
export default function Login({ onLoginSuccess }) {
  // [BỔ SUNG] Khởi tạo State
  const [formData, setFormData] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const navigate = useNavigate(); // Khởi tạo hàm chuyển trang

  // [BỔ SUNG] Hàm xử lý Submit
  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({});

    try {
      // [BỔ SUNG] Gán kết quả trả về vào biến data để lấy token
      const data = await apiRequest('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify(formData)
      });
      
      // [BỔ SUNG] Kiểm tra, lưu token vào trình duyệt và điều hướng
      if (data && data.accessToken) {
        localStorage.setItem("accessToken", data.accessToken);
        toast.success("Đăng nhập thành công!");

        // [BỔ SUNG] Kích hoạt hàm báo cáo để đổi giao diện Navbar
        if (onLoginSuccess) onLoginSuccess();
        
        navigate('/dashboard'); // Đá người dùng vào thẳng bảng điều khiển
      }
    } catch (err) {
      // [BỔ SUNG] Bắt lỗi Message và Validation
      toast.error(err.message || 'Sai thông tin đăng nhập!');
      if (err.data && err.data.validation) {
        setErrors(err.data.validation);
      }
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-8rem)]">
      <div className="bg-white p-8 rounded-xl shadow-sm border border-gray-100 w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-12 h-12 bg-brand-success rounded-full flex items-center justify-center font-bold text-brand-dark mx-auto mb-4 text-xl">T</div>
          <h2 className="text-2xl font-bold text-brand-primary">Welcome Back</h2>
          <p className="text-gray-500 text-sm mt-2">Sign in to access your wallet and manage donations.</p>
        </div>

        <form className="space-y-5" onSubmit={handleSubmit /* [BỔ SUNG] Gắn hàm submit */}>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
            <input 
              type="email" 
              placeholder="alice@example.com" 
              value={formData.email} /* [BỔ SUNG] */
              onChange={(e) => setFormData({...formData, email: e.target.value})} /* [BỔ SUNG] */
              className={`w-full px-4 py-2 border ${errors.email ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success focus:border-transparent outline-none transition-all`} 
            />
            {/* [BỔ SUNG] Báo lỗi */}
            {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input 
              type="password" 
              placeholder="••••••••" 
              value={formData.password} /* [BỔ SUNG] */
              onChange={(e) => setFormData({...formData, password: e.target.value})} /* [BỔ SUNG] */
              className={`w-full px-4 py-2 border ${errors.password ? 'border-red-500' : 'border-gray-300'} rounded-md focus:ring-2 focus:ring-brand-success focus:border-transparent outline-none transition-all`} 
            />
            {/* [BỔ SUNG] Báo lỗi */}
            {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
          </div>
          
          <button type="submit" className="w-full py-2.5 bg-brand-primary text-white font-medium rounded-md hover:bg-brand-accent transition-colors shadow-sm">
            Sign In
          </button>
        </form>

        <p className="text-center text-sm text-gray-600 mt-6">
          Don't have an account? <Link to="/register" className="text-brand-success font-semibold hover:underline">Register here</Link>
        </p>
      </div>
    </div>
  );
}