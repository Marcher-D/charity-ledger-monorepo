import { useState, useEffect } from 'react';
import { apiRequest } from '../../services/api';
import { toast } from 'react-hot-toast';
import { Loader2 } from 'lucide-react';

// Nhúng 2 Component con vào
import WalletPanel from '../../components/user/WalletPanel'; 
import TransactionHistory from '../../components/user/TransactionHistory';

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isToggling, setIsToggling] = useState(false);

  const fetchDashboardData = async () => {
    try {
      const [profileData, txData] = await Promise.all([
        apiRequest('/api/v1/users/me'), 
        apiRequest('/api/v1/transactions?limit=5') 
      ]);
      setProfile(profileData);
      setTransactions(txData.items || []); 
    } catch (err) {
      toast.error("Không thể tải dữ liệu ví!");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  // Hàm chuyển đổi quyền Validator
  const handleToggleValidator = async () => {
    setIsToggling(true);
    try {
      await apiRequest('/api/v1/transactions/validator/toggle', {
        method: 'POST',
        body: JSON.stringify({ enable: !profile.activeValidator }) 
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
      
      {/* Truyền dữ liệu và truyền cả cái hàm onToggleValidator xuống cho component con */}
      <WalletPanel 
        profile={profile} 
        isToggling={isToggling} 
        onToggleValidator={handleToggleValidator} 
      />
      
      <TransactionHistory transactions={transactions} />
    </div>
  );
}