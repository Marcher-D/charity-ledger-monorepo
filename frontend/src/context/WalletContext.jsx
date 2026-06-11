import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { jwtDecode } from 'jwt-decode';
import { apiRequest } from '../services/api';
import { isAdminRole } from '../services/auth';

// Nguồn số dư DUY NHẤT cho toàn bộ giao diện. Trước đây Navbar, Dashboard và
// CampaignDetail mỗi nơi tự gọi /users/me một kiểu — nạp tiền ở Dashboard xong
// sang trang chiến dịch thì form quyên góp vẫn giữ số dư cũ cho tới khi F5.
// Giờ mọi component đọc chung một profile, tự làm mới mỗi 5 giây.
const WalletContext = createContext({ profile: null, refreshWallet: async () => {} });

export function WalletProvider({ isLoggedIn, authVersion, children }) {
  const [profile, setProfile] = useState(null);

  const refreshWallet = useCallback(async () => {
    try {
      const data = await apiRequest('/api/v1/users/me');
      setProfile(data);
    } catch (err) {
      // Lỗi tạm thời (mạng, backend bận): giữ số cũ, vòng poll sau thử lại.
      console.error('Wallet refresh error:', err.message);
    }
  }, []);

  useEffect(() => {
    if (!isLoggedIn) {
      setProfile(null);
      return undefined;
    }

    const token = localStorage.getItem('accessToken');
    if (!token) {
      setProfile(null);
      return undefined;
    }

    try {
      // Admin không có ví — tuyệt đối không gọi API ví cho Admin.
      if (isAdminRole(jwtDecode(token).role)) {
        setProfile(null);
        return undefined;
      }
    } catch (error) {
      console.error('Token reading error (WalletContext):', error);
      setProfile(null);
      return undefined;
    }

    refreshWallet();
    const intervalId = setInterval(refreshWallet, 5000);
    return () => clearInterval(intervalId);
  }, [isLoggedIn, authVersion, refreshWallet]);

  return (
    <WalletContext.Provider value={{ profile, refreshWallet }}>
      {children}
    </WalletContext.Provider>
  );
}

export function useWallet() {
  return useContext(WalletContext);
}
