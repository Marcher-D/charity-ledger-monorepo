import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layers, ArrowUpRight, Loader2 } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { apiRequest } from '../../services/api.jsx';

export default function Home() {
  const navigate = useNavigate();
  
  // 1. Khởi tạo State để quản lý dữ liệu và trạng thái tải
  const [campaigns, setCampaigns] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // 2. Fetch data ngay khi component vừa mount lên màn hình
  useEffect(() => {
    const fetchCampaigns = async () => {
      try {
        // Gọi API Public (Không cần truyền token). Có thể set limit=6 cho trang chủ gọn gàng
        const data = await apiRequest('/api/v1/campaigns?limit=6');
        
        // Theo tài liệu, Backend trả về dạng { items: [...], page, limit, total... }
        setCampaigns(data.items || []);
      } catch (err) {
        // Hứng thông báo lỗi quăng ra Toast
        toast.error(err.message || "Cannot load campaign list!");
      } finally {
        // Tắt vòng xoay loading dù thành công hay thất bại
        setIsLoading(false);
      }
    };

    fetchCampaigns();
  }, []);

  return (
    <div className="w-full min-h-[calc(100vh-5rem)] bg-brand-dark px-4 sm:px-6 py-10 sm:py-16 flex flex-col justify-center">
      <div className="max-w-5xl mx-auto w-full space-y-10 sm:space-y-16">
        
        {/* Khối Tiêu Đề Trung Tâm (Giữ nguyên) */}
        <div className="text-center space-y-4 max-w-2xl mx-auto">
          <h1 className="text-3xl sm:text-5xl md:text-6xl font-bold leading-tight tracking-normal text-brand-cream">
            Transparentizing <br/>
            <span className="text-emerald-400">Charitable fund flows.</span>
          </h1>
          <p className="font-copy text-brand-creamMuted text-base leading-relaxed">
            The system leverages a Pure Proof of Stake (PPoS) consensus mechanism to bring full transparency to charitable cash flows, guaranteeing that all donation transactions are immutably processed and verified by a decentralized network.
          </p>
        </div>

        {/* Khối Danh Sách Chiến Dịch Động */}
        <div className="space-y-6">
          <div className="flex justify-between items-center border-b border-brand-line pb-4">
            <h2 className="text-xs font-bold tracking-normal text-emerald-400 flex items-center gap-2">
              <Layers size={14} /> CAMPAIGN IN PROGRESS
            </h2>
            <span className="text-xs text-brand-creamMuted font-medium">
              {isLoading ? 'Đang đồng bộ...' : `Hiển thị ${campaigns.length} chiến dịch`}
            </span>
          </div>

          {/* Logic Render theo trạng thái */}
          {isLoading ? (
            // Trạng thái 1: Đang chờ API phản hồi
            <div className="flex justify-center py-16">
              <Loader2 className="animate-spin text-emerald-400" size={40} />
            </div>
          ) : campaigns.length === 0 ? (
            // Trạng thái 2: Gọi API thành công nhưng rỗng
            <div className="text-center py-16 text-brand-creamMuted border border-dashed border-brand-line rounded-2xl">
              No initialized campaign found.
            </div>
          ) : (
            // Trạng thái 3: Đổ dữ liệu thật lên giao diện
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {campaigns.map((campaign) => {
                const progressPercent = Math.min((campaign.currentRaised / campaign.targetAmount) * 100, 100);
                
                return (
                  <div 
                    key={campaign.id}
                    onClick={() => navigate(`/campaign/${campaign.id}`)}
                    className="bg-brand-card border border-brand-line rounded-2xl p-6 shadow-2xl hover:border-emerald-500/40 transition-all duration-300 cursor-pointer group relative overflow-hidden"
                  >
                    <div className="flex justify-between items-start mb-6">
                      <h3 className="font-bold text-lg text-brand-cream group-hover:text-emerald-400 transition-colors flex items-center gap-1">
                        # {campaign.title}
                        <ArrowUpRight size={16} className="opacity-0 group-hover:opacity-100 transform translate-y-1 group-hover:translate-y-0 transition-all" />
                      </h3>
                      <span className="px-2.5 py-0.5 bg-emerald-500/10 text-emerald-400 text-[10px] font-black rounded border border-emerald-500/20 tracking-normal">
                        {campaign.status}
                      </span>
                    </div>
                    
                    <div className="w-full bg-brand-dark rounded-full h-2 mb-4 overflow-hidden border border-brand-line">
                      <div 
                        className="bg-emerald-500 h-2 rounded-full shadow-[0_0_10px_rgba(16,185,129,0.3)] transition-all duration-500" 
                        style={{ width: `${progressPercent}%` }}
                      ></div>
                    </div>
                    
                    <div className="flex justify-between text-xs text-brand-creamMuted font-medium">
                      <span>Achieved: <strong className="text-brand-cream">${campaign.currentRaised.toLocaleString()}</strong></span>
                      <span>Target: ${campaign.targetAmount.toLocaleString()}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
