import { useState, useEffect } from 'react';
import { apiRequest } from '../../services/api';
import { toast } from 'react-hot-toast';
import { Loader2 } from 'lucide-react';
import { jwtDecode } from 'jwt-decode'; // [BỔ SUNG] Máy soi token để nhận diện Admin
import { useNavigate } from 'react-router-dom'; // [BỔ SUNG] Công cụ chuyển hướng trang
import { isAdminRole } from '../../services/auth';

// Nhúng 2 Component con vào
import WalletPanel from '../../components/user/WalletPanel'; 
import TransactionHistory from '../../components/user/TransactionHistory';

export default function Dashboard() {
  const navigate = useNavigate(); // [BỔ SUNG] Khởi tạo điều hướng
  const [profile, setProfile] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isToggling, setIsToggling] = useState(false);

  const fetchDashboardData = async () => {
    try {
      const [profileData, txData] = await Promise.all([
        apiRequest('/api/v1/users/me'), 
        apiRequest('/api/v1/transactions/me?limit=5') 
      ]);
      setProfile(profileData);
      setTransactions(Array.isArray(txData) ? txData : txData.items || []); 
    } catch (err) {
      console.error("Lỗi tải Dashboard:", err);
      toast.error("Không thể tải dữ liệu ví!");
    } finally {
      setIsLoading(false);
    }
  };

  // [SỬA ĐOẠN NÀY] Thêm chốt chặn phân quyền ngay khi component mount
  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      try {
        const decoded = jwtDecode(token);
        // CHỐT CHẶN: Nếu tài khoản là ADMIN đi lạc vào đây -> Đá bay về AdminPanel ngay lập tức!
        if (isAdminRole(decoded.role)) {
          navigate('/admin', { replace: true });
          return; // [QUAN TRỌNG] Thoát luôn hàm, chặn đứng không cho gọi fetchDashboardData() bên dưới
        }
      } catch (error) {
        console.error("Lỗi giải mã token tại Dashboard:", error);
      }
    }

    // Nếu vượt qua bài kiểm tra (là User thường), tiến hành gọi API như cũ
    fetchDashboardData();
  }, [navigate]);

  // Hàm chuyển đổi quyền Validator
  const handleToggleValidator = async () => {
    setIsToggling(true);
    try {
      const currentAction = profile.activeValidator ? "OPT_OUT" : "OPT_IN";
      
      await apiRequest('/api/v1/transactions/validator/toggle', {
        method: 'POST',
        body: JSON.stringify({ action: currentAction }) 
      });
      
      toast.success(profile.activeValidator ? "Đã tắt Node Validator" : "Đã kích hoạt Node Validator PPoS!");
      fetchDashboardData(); 
    } catch (err) {
      toast.error(err.message || "Lỗi chuyển đổi Validator");
    } finally {
      setIsToggling(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center min-h-[50vh]">
        <Loader2 className="animate-spin text-emerald-500" size={40} />
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-12">
      <h1 className="text-3xl font-bold text-slate-200 mb-8">My Dashboard</h1>
      
      <WalletPanel 
        profile={profile} 
        isToggling={isToggling} 
        onToggleValidator={handleToggleValidator} 
      />
      
      <TransactionHistory transactions={transactions} />
    </div>
  );
}
